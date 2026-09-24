package com.grindrplus.manager.play

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.grindrplus.manager.installation.Print
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Export an installed package's base + split APKs into a zip suitable for
 * [com.grindrplus.manager.installation.steps.ExtractBundleStep] — same shape as Play / Custom Files.
 *
 * Used when anonymous Play delivery returns status 3 but Grindr is already on-device
 * (typical for dating apps on dispenser accounts; Aurora Store then needs a personal login).
 */
object InstalledPackageExporter {

    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** True if base.apk already embeds LSPatch (re-patching would nest loaders). */
    fun looksLsPatched(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            ZipFile(appInfo.sourceDir).use { zip ->
                zip.entries().asSequence().any { entry ->
                    val name = entry.name
                    name.startsWith("assets/lspatch/") ||
                        (name.contains("lspatch", ignoreCase = true) && name.endsWith(".so"))
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun exportToZip(
        context: Context,
        packageName: String,
        bundleFile: File,
        print: Print,
    ) {
        val pm = context.packageManager
        val appInfo = pm.getApplicationInfo(packageName, 0)
        val apkPaths = buildList {
            add(appInfo.sourceDir)
            appInfo.splitSourceDirs?.let { addAll(it) }
        }.filter { File(it).exists() && File(it).length() > 0 }

        if (apkPaths.isEmpty()) {
            throw IllegalStateException("No APK paths for $packageName")
        }

        print("Exporting ${apkPaths.size} installed APK(s) for $packageName...")
        if (bundleFile.exists()) bundleFile.delete()
        ZipOutputStream(FileOutputStream(bundleFile)).use { zip ->
            for (path in apkPaths) {
                val src = File(path)
                val entryName = when {
                    path == appInfo.sourceDir || src.name == "base.apk" -> "base.apk"
                    src.name.startsWith("split_") -> src.name.removePrefix("split_")
                    else -> src.name
                }
                print("  + $entryName (${src.length() / 1024}KB)")
                zip.putNextEntry(ZipEntry(entryName))
                src.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        if (!bundleFile.exists() || bundleFile.length() <= 0) {
            throw IllegalStateException("Failed to write installed-package bundle")
        }
        print("Installed package export complete (${bundleFile.length() / 1024 / 1024}MB)")
    }
}
