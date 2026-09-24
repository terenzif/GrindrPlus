package com.grindrplus.manager.installation.steps

import android.content.Context
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.gplayapi.helpers.PurchaseHelper
import com.grindrplus.BuildConfig
import com.grindrplus.manager.installation.BaseStep
import com.grindrplus.manager.installation.Print
import com.grindrplus.manager.play.PlayHttpClient
import com.grindrplus.manager.play.PlayStoreSession
import com.grindrplus.manager.utils.download
import com.grindrplus.manager.utils.validateFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Downloads Grindr split APKs from Google Play using Aurora OSS [gplayapi]
 * (anonymous dispenser + purchase/delivery), then zips them for [ExtractBundleStep].
 *
 * Purchase uses [preferredVersionCode] (BuildConfig TARGET) when set — same as Aurora Store’s
 * version picker — not Play tip. Tip is only used when no target is configured.
 *
 * Replaces CDN URL downloads when [grindrUrl] is blank — no Aurora Store app, no APK host.
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
        // Aurora Store: pick any versionCode → PurchaseHelper.purchase(..., versionCode, ...).
        // Never silently fall back to tip — that installs an unsupported Grindr build.
        val versionCode = resolveDownloadVersionCode(tipVersionCode, preferredVersionCode)
        when {
            preferredVersionCode > 0L && tipVersionCode == preferredVersionCode ->
                print("Play tip matches target versionCode=$versionCode")
            preferredVersionCode > 0L ->
                print(
                    "Play tip versionCode=$tipVersionCode — " +
                        "requesting fork target versionCode=$preferredVersionCode " +
                        "(Aurora-style pin; no tip fallback)"
                )
            else ->
                print("No fork target set — downloading Play tip versionCode=$versionCode")
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
            val pinned = preferredVersionCode > 0L && versionCode == preferredVersionCode
            val tipHint = if (tipVersionCode > 0L && tipVersionCode != versionCode) {
                " Play tip is $tipVersionCode."
            } else {
                ""
            }
            throw IOException(
                if (pinned) {
                    "Play could not deliver Grindr versionCode=$versionCode " +
                        "(fork target; tip fallback disabled).$tipHint " +
                        "Use Custom Files with a matching Grindr APK " +
                        "(APKMirror / Aurora version picker), or update TARGET_GRINDR_VERSION_CODES. " +
                        "Cause: ${e.message}"
                } else {
                    "Play purchase/delivery failed: ${e.message}"
                },
                e,
            )
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

        /**
         * Prefer [preferredVersionCode] when set (BuildConfig target), else Play tip.
         * Throws if neither is a positive versionCode.
         */
        fun resolveDownloadVersionCode(
            tipVersionCode: Long,
            preferredVersionCode: Long,
        ): Long {
            val chosen = if (preferredVersionCode > 0L) {
                preferredVersionCode
            } else {
                tipVersionCode
            }
            if (chosen <= 0L) {
                throw IOException("Play returned invalid versionCode for Grindr")
            }
            return chosen
        }
    }
}
