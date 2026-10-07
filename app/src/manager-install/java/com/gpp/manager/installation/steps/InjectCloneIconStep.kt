package com.gpp.manager.installation.steps

import android.content.Context
import com.github.diamondminer88.zip.ZipWriter
import java.io.File
import java.util.zip.ZipFile

/**
 * Replaces common launcher icon entries in cloned Grindr APKs with the Grindr++ brand PNG
 * from Manager assets (`assets/brand/ic_launcher_*.png`).
 */
class InjectCloneIconStep(
    private val folder: File,
) : BaseStep() {
    override val name = "Inject Grindr++ icon"

    override suspend fun doExecute(context: Context, print: Print) {
        val iconBytes = loadBestIcon(context, print) ?: run {
            print("WARN: brand icon assets missing — skipping icon inject")
            return
        }

        val apkFiles = folder.listFiles()?.filter { it.name.endsWith(".apk") } ?: emptyList()
        for (apk in apkFiles) {
            if (!apk.name.contains("base", ignoreCase = true) &&
                !apk.name.equals("base.apk", ignoreCase = true)
            ) {
                // Prefer base; still try all APKs that contain launcher mipmaps
            }
            injectIntoApk(apk, iconBytes, print)
        }
        print("Grindr++ icon inject completed")
    }

    private fun loadBestIcon(context: Context, print: Print): ByteArray? {
        val candidates = listOf(
            "brand/ic_launcher_192.png",
            "brand/ic_launcher_144.png",
            "brand/icon-master.png",
        )
        for (path in candidates) {
            try {
                context.assets.open(path).use { return it.readBytes() }
            } catch (_: Exception) {
                print("DEBUG: asset $path not found")
            }
        }
        return null
    }

    private fun injectIntoApk(apk: File, iconBytes: ByteArray, print: Print) {
        val launcherEntries = ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .map { it.name }
                .filter { name ->
                    val lower = name.lowercase()
                    lower.startsWith("res/mipmap") &&
                        (lower.contains("ic_launcher") || lower.contains("ic_launcher_round") ||
                            lower.contains("ic_launcher_foreground")) &&
                        (lower.endsWith(".png") || lower.endsWith(".webp"))
                }
                .toList()
        }

        if (launcherEntries.isEmpty()) {
            print("No launcher mipmaps in ${apk.name} — skip")
            return
        }

        print("Injecting icon into ${apk.name} (${launcherEntries.size} entries)")
        ZipWriter(apk, true).use { zip ->
            for (entry in launcherEntries) {
                zip.deleteEntry(entry, /* fillVoid = */ true)
                zip.writeEntry(entry, iconBytes)
            }
        }
    }
}
