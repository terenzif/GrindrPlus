package com.grindrplus.manager.installation.steps

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.data.models.PlayFile
import com.aurora.gplayapi.exceptions.GooglePlayException
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.AuthHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.grindrplus.BuildConfig
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.grindrplus.manager.play.InstalledPackageExporter
import com.grindrplus.manager.play.PlayHttpClient
import com.grindrplus.manager.play.PlayPackageCerts
import com.grindrplus.manager.play.PlayStoreSession
import com.grindrplus.manager.utils.download
import com.grindrplus.manager.utils.validateFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Downloads Grindr split APKs from Google Play using Aurora OSS [gplayapi], then zips them
 * for [ExtractBundleStep].
 *
 * Auth: **local Google account** (AccountManager Play token) when available, else anonymous
 * dispenser — same split Aurora Store uses for Google vs Anonymous login. Purchase mirrors
 * Aurora (`acquire` → purchase → delivery) with session rotation + installed-APK fallback.
 */
class PlayGrindrDownloadStep(
    private val bundleFile: File,
    private val preferredVersionCode: Long = preferredTargetVersionCode(),
) : BaseStep() {

    override val name = "Downloading Grindr (Play)"

    override suspend fun doExecute(context: Context, print: Print) {
        if (bundleFile.exists() && bundleFile.length() > 0 && validateFile(bundleFile)) {
            print("Existing Grindr bundle found, skipping Play download")
            return
        }

        print(
            "Authenticating with Play " +
                "(local Google/AccountManager→AC2DM→AAS, else anonymous dispenser)..."
        )
        val http = PlayHttpClient()
        var localEmails = com.grindrplus.manager.play.PlayLocalAccountAuth
            .googleAccountEmails(context)
        if (localEmails.isNotEmpty()) {
            print("On-device Google account(s): ${localEmails.joinToString()}")
        } else {
            print(
                "No Google accounts visible to this app yet (Android account visibility). " +
                    "A picker may appear — select the account you use in Play Store."
            )
        }
        var auth = try {
            PlayStoreSession.buildPreferredAuth(context, http, preferLocal = true)
        } catch (e: Exception) {
            throw IOException(
                "Play auth failed (${e.message}). " +
                    "If you see HTTP 403/429, Cloudflare blocked the dispenser — " +
                    "use Custom Files (Grindr APK from storage) instead of retrying. " +
                    "If prompted for Google account access, approve and tap Install again.",
                e
            )
        }
        localEmails = com.grindrplus.manager.play.PlayLocalAccountAuth
            .googleAccountEmails(context)
        if (!auth.isAnonymous) {
            print("Using on-device Google account: ${auth.email} (personal Play session)")
        } else {
            print(
                "Using anonymous dispenser session: ${auth.email}" +
                    if (localEmails.isNotEmpty()) {
                        " — local token failed; approve Google consent if shown, then retry"
                    } else {
                        " — pick a Google account on retry for personal Play access"
                    }
            )
        }

        print("Fetching Grindr details from Play...")
        val app = try {
            AppDetailsHelper(auth).using(http)
                .getAppByPackageName(PlayStoreSession.GRINDR_PACKAGE)
        } catch (e: Exception) {
            throw IOException("Play app details failed: ${e.message}", e)
        }

        val versionCode = app.versionCode
        if (preferredVersionCode > 0L) {
            if (versionCode == preferredVersionCode) {
                print("Play tip matches target versionCode=$versionCode")
            } else {
                print(
                    "Play tip versionCode=$versionCode " +
                        "(fork target=$preferredVersionCode) — downloading tip"
                )
            }
        }
        if (versionCode <= 0L) {
            throw IOException("Play returned invalid versionCode for Grindr")
        }

        val offerTypes = linkedSetOf(
            app.offerType.takeIf { it > 0 } ?: 1,
            1,
            0,
        )
        val installedCertHash = PlayPackageCerts.latestEncodedHash(
            context,
            PlayStoreSession.GRINDR_PACKAGE,
        )
        if (installedCertHash != null) {
            print("Installed Grindr signing cert available — will retry purchase with cert hash if needed")
        }

        print(
            "Requesting delivery for ${app.displayName} vc=$versionCode " +
                "offerTypes=${offerTypes.joinToString()}..."
        )

        val files = try {
            purchaseWithRetries(
                context = context,
                http = http,
                initialAuth = auth,
                versionCode = versionCode,
                offerTypes = offerTypes,
                installedCertHash = installedCertHash,
                print = print,
            )
        } catch (e: IOException) {
            // Dating / age-gated apps often refuse anonymous dispenser accounts (status 3).
            // Aurora Store then needs a personal Google login — we can't ask for that here.
            // If Grindr is already installed (unpatched), export those APKs and continue LSPatch.
            if (tryExportInstalledFallback(context, print)) {
                return
            }
            throw e
        }

        val apkFiles = files.filter {
            it.url.isNotBlank() && it.name.endsWith(".apk", ignoreCase = true)
        }
        if (apkFiles.isEmpty()) {
            throw IOException("Play returned no APK splits (${files.size} file(s))")
        }

        print("Downloading ${apkFiles.size} split(s) from Play CDN...")
        val workDir = File(bundleFile.parentFile, "play-grindr-tmp").also {
            it.deleteRecursively()
            it.mkdirs()
        }

        try {
            for ((index, playFile) in apkFiles.withIndex()) {
                val out = File(workDir, playFile.name.ifBlank { "split-$index.apk" })
                print("  (${index + 1}/${apkFiles.size}) ${out.name} (${playFile.size / 1024}KB)")
                val result = download(context, out, playFile.url, print)
                if (!result.success || !out.exists() || out.length() <= 0) {
                    throw IOException("Failed to download ${out.name}: ${result.reason}")
                }
            }

            print("Packaging splits into bundle archive...")
            if (bundleFile.exists()) bundleFile.delete()
            ZipOutputStream(FileOutputStream(bundleFile)).use { zip ->
                workDir.listFiles()?.filter { it.extension.equals("apk", true) }?.forEach { apk ->
                    zip.putNextEntry(ZipEntry(apk.name))
                    apk.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }

            if (!bundleFile.exists() || bundleFile.length() <= 0) {
                throw IOException("Failed to write Grindr bundle zip")
            }
            print("Grindr Play download complete (${bundleFile.length() / 1024 / 1024}MB)")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun tryExportInstalledFallback(context: Context, print: Print): Boolean {
        val pkg = PlayStoreSession.GRINDR_PACKAGE
        if (!InstalledPackageExporter.isInstalled(context, pkg)) {
            print("No installed Grindr to fall back to — use Custom Files")
            return false
        }
        if (InstalledPackageExporter.looksLsPatched(context, pkg)) {
            print(
                "Installed Grindr already looks LSPatched — refusing nested patch. " +
                    "Use Custom Files with a clean Play/APKMirror build."
            )
            return false
        }
        return try {
            print(
                "Play anonymous delivery blocked (common for dating apps on dispenser accounts). " +
                    "Falling back to installed Grindr APKs (same outcome as Custom Files)."
            )
            InstalledPackageExporter.exportToZip(context, pkg, bundleFile, print)
            true
        } catch (e: Exception) {
            print("Installed Grindr export failed: ${e.message}")
            false
        }
    }

    /**
     * Aurora Store binds [PurchaseHelper] to a session and refreshes on spoof/auth drift.
     * Personal (local) sessions: try offerType / cert-hash variants once — do **not** fall
     * back to anonymous rotation (that reintroduces the dating-app status-3 lottery).
     * Anonymous: rotate dispenser accounts like picking another lottery ticket.
     */
    private fun purchaseWithRetries(
        context: Context,
        http: PlayHttpClient,
        initialAuth: AuthData,
        versionCode: Long,
        offerTypes: Set<Int>,
        installedCertHash: String?,
        print: Print,
    ): List<PlayFile> {
        var auth = initialAuth
        var lastError: Exception? = null
        val maxRotations = if (initialAuth.isAnonymous) MAX_AUTH_ROTATIONS else 1

        repeat(maxRotations) { rotation ->
            if (rotation > 0) {
                print(
                    "Rotating Play session (attempt ${rotation + 1}/$maxRotations, " +
                        "anonymous dispenser)..."
                )
                auth = try {
                    PlayStoreSession.buildAnonymousAuth(context, http)
                } catch (e: Exception) {
                    lastError = e
                    print("Auth refresh failed: ${e.message}")
                    return@repeat
                }
            }

            if (!AuthHelper.using(http).isValid(auth, PlayStoreSession.GRINDR_PACKAGE)) {
                // Still try purchase — details already worked for the first session.
                print("Auth validation soft-fail for Grindr details; continuing purchase")
            }

            val helper = PurchaseHelper(auth).using(http)
            val certVariants = buildList {
                add(null) // prefer clean Play APK (no installed cert) — LSPatch resigns anyway
                if (!installedCertHash.isNullOrBlank()) add(installedCertHash)
            }

            var hitStatus3 = false
            for (offerType in offerTypes) {
                for (certHash in certVariants) {
                    val label = "ot=$offerType" +
                        if (certHash != null) "+cert" else ""
                    try {
                        print("Play purchase ($label)...")
                        val files = helper.purchase(
                            packageName = PlayStoreSession.GRINDR_PACKAGE,
                            versionCode = versionCode,
                            offerType = offerType,
                            certificateHash = certHash,
                        )
                        if (files.isNotEmpty()) {
                            print("Play purchase OK ($label) — ${files.size} file(s)")
                            return files
                        }
                    } catch (e: GooglePlayException.AppNotPurchased) {
                        lastError = e
                        hitStatus3 = true
                        print("Play delivery status 3 ($label): ${e.reason}")
                    } catch (e: GooglePlayException.AppNotSupported) {
                        lastError = e
                        print("Play app not supported ($label): ${e.message}")
                    } catch (e: GooglePlayException.AppRemoved) {
                        throw IOException("Grindr removed from Play: ${e.message}", e)
                    } catch (e: Exception) {
                        lastError = e
                        print("Play purchase error ($label): ${e.message}")
                    }
                }
            }
            if (hitStatus3 && auth.isAnonymous) {
                print("All offer/cert variants hit status 3 for this session — rotating account")
            } else if (hitStatus3 && !auth.isAnonymous) {
                print(
                    "Personal account still got status 3 — not rotating to anonymous. " +
                        "Check that this Google account can open Grindr in Play Store."
                )
            }
        }

        val kind = if (initialAuth.isAnonymous) {
            "$MAX_AUTH_ROTATIONS anonymous session(s)"
        } else {
            "personal account ${initialAuth.email}"
        }
        throw IOException(
            "Play purchase/delivery failed after $kind: " +
                (lastError?.message ?: "unknown") +
                ". Retry Install (approve Google consent if prompted), or use Custom Files.",
            lastError
        )
    }

    companion object {
        private const val MAX_AUTH_ROTATIONS = 4

        fun preferredTargetVersionCode(): Long {
            val codes = BuildConfig.TARGET_GRINDR_VERSION_CODES
            return if (codes.isNotEmpty()) codes[0].toLong() else 0L
        }
    }
}
