package com.gpp.manager.installation.steps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Copies split APKs from the Play-installed [GRINDR_PACKAGE_NAME] into a zip bundle
 * for [ExtractBundleStep]. Does not download from CDN or Play CDN.
 */
class ExportInstalledGrindrStep(
    private val bundleFile: File,
) : BaseStep() {
    override val name = "Export installed Grindr"

    override suspend fun doExecute(context: Context, print: Print) {
        val pm = context.packageManager
        val appInfo: ApplicationInfo = try {
            pm.getApplicationInfo(GRINDR_PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            throw IOException(
                "Grindr ($GRINDR_PACKAGE_NAME) is not installed. " +
                    "Install it from the Play Store, then create Grindr++ from GrindMod."
            )
        }

        val apkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.forEach { add(it) }
        }.filterNotNull().distinct()

        if (apkPaths.isEmpty()) {
            throw IOException("No APK paths found for installed Grindr")
        }

        print("Found ${apkPaths.size} installed Grindr APK(s)")
        apkPaths.forEachIndexed { index, path ->
            val f = File(path)
            print("  ${index + 1}. ${f.name} (${f.length() / 1024 / 1024}MB)")
        }

        if (bundleFile.exists()) {
            bundleFile.delete()
        }
        bundleFile.parentFile?.mkdirs()

        ZipOutputStream(FileOutputStream(bundleFile)).use { zos ->
            for (path in apkPaths) {
                val src = File(path)
                if (!src.isFile || src.length() <= 0) {
                    throw IOException("Invalid installed APK: $path")
                }
                // Preserve split naming expected by ExtractBundleStep (base.apk / split_*.apk)
                val entryName = src.name
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(src).use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }

        print(
            "Exported installed Grindr to ${bundleFile.name} " +
                "(${bundleFile.length() / 1024 / 1024}MB)"
        )
    }
}
