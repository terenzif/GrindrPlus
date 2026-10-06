package com.gpp.manager.installation.steps

import android.content.Context
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.gpp.BuildConfig
import com.gpp.manager.play.PlayHttpClient
import com.gpp.manager.play.PlayStoreSession
import com.gpp.manager.utils.download
import com.gpp.manager.utils.validateFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Downloads Grindr split APKs from Google Play using Aurora OSS [gplayapi]
 * (anonymous dispenser + purchase/delivery), then zips them for [ExtractBundleStep].
 *
 * Replaces CDN URL downloads when [grindrUrl] is blank — no Aurora Store app, no APK host.
 *
 * Pack-aware: prefer a versionCode that matches a mapping pack / BuildConfig tip when
 * Play delivery allows it (see ADR 0003). Soft-fail back to Play tip on delivery errors.
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

        print("Authenticating with Play (anonymous dispenser, Aurora protocol)...")
        val http = PlayHttpClient()
        val auth = try {
            PlayStoreSession.buildAnonymousAuth(context, http)
        } catch (e: Exception) {
            throw IOException(
                "Play anonymous auth failed (${e.message}). " +
                    "Check network / Cloudflare, or use Custom Files with a local Grindr APK.",
                e
            )
        }

        print("Fetching Grindr details from Play...")
        val app = try {
            AppDetailsHelper(auth).using(http)
                .getAppByPackageName(PlayStoreSession.GRINDR_PACKAGE)
        } catch (e: Exception) {
            throw IOException("Play app details failed: ${e.message}", e)
        }

        val tipVersionCode = app.versionCode
        var versionCode = tipVersionCode
        val softPreferTarget = preferredVersionCode > 0L &&
            tipVersionCode > 0L &&
            preferredVersionCode != tipVersionCode

        if (preferredVersionCode > 0L) {
            if (tipVersionCode == preferredVersionCode) {
                print(
                    "Play tip matches preferred target versionCode=$versionCode " +
                        "(align tip with a mapping pack when possible)"
                )
            } else {
                print(
                    "Play tip versionCode=$tipVersionCode differs from preferred " +
                        "BuildConfig target=$preferredVersionCode — " +
                        "soft-preferring target for delivery (pack tip alignment)"
                )
                versionCode = preferredVersionCode
            }
        }
        if (versionCode <= 0L) {
            throw IOException("Play returned invalid versionCode for Grindr")
        }

        val offerType = if (app.offerType > 0) app.offerType else 1
        print("Requesting delivery for ${app.displayName} vc=$versionCode ot=$offerType...")

        val files = try {
            PurchaseHelper(auth).using(http).purchase(
                packageName = PlayStoreSession.GRINDR_PACKAGE,
                versionCode = versionCode,
                offerType = offerType,
            )
        } catch (e: Exception) {
            if (softPreferTarget) {
                print(
                    "Preferred versionCode=$preferredVersionCode delivery failed " +
                        "(${e.message}); falling back to Play tip=$tipVersionCode"
                )
                versionCode = tipVersionCode
                print("Requesting delivery for ${app.displayName} vc=$versionCode ot=$offerType...")
                try {
                    PurchaseHelper(auth).using(http).purchase(
                        packageName = PlayStoreSession.GRINDR_PACKAGE,
                        versionCode = versionCode,
                        offerType = offerType,
                    )
                } catch (fallback: Exception) {
                    throw IOException(
                        "Play purchase/delivery failed for tip vc=$tipVersionCode: ${fallback.message}",
                        fallback
                    )
                }
            } else {
                throw IOException("Play purchase/delivery failed: ${e.message}", e)
            }
        }

        val apkFiles = files.filter { it.url.isNotBlank() && it.name.endsWith(".apk", ignoreCase = true) }
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

    companion object {
        fun preferredTargetVersionCode(): Long {
            val codes = BuildConfig.TARGET_GRINDR_VERSION_CODES
            return if (codes.isNotEmpty()) codes[0].toLong() else 0L
        }
    }
}
