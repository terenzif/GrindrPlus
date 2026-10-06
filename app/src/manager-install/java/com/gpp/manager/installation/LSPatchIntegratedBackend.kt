package com.gpp.manager.installation

import com.gpp.manager.installation.steps.Print

import android.content.Context
import com.reandroid.apk.ApkModule
import com.reandroid.xml.StyleDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.io.File
import java.io.IOException

/**
 * Default Morphe A [PatchBackend]: integrated LSPatch (`-l 2`) with optional Maps API key rewrite.
 *
 * Keystore args match [com.gpp.manager.utils.KeyStoreUtils] defaults.
 */
class LSPatchIntegratedBackend : PatchBackend {
    companion object {
        const val MAPS_API_KEY_NAME = "com.google.android.geo.API_KEY"
        const val DEFAULT_KEYSTORE_PASSWORD = "password"
        const val DEFAULT_KEY_ALIAS = "alias"
        const val DEFAULT_KEY_PASSWORD = "password"
    }

    override suspend fun patch(
        context: Context,
        inputApks: List<File>,
        outputDir: File,
        modFile: File,
        keyStore: File,
        keyStorePassword: String,
        keyAlias: String,
        keyPassword: String,
        customMapsApiKey: String?,
        embedModule: Boolean,
        print: Print,
    ) {
        if (inputApks.isEmpty()) {
            throw IOException("No valid APK files found to patch")
        }

        applyCustomMapsApiKey(inputApks, customMapsApiKey, print)

        if (!embedModule) {
            copyWithoutEmbed(inputApks, outputDir, print)
            return
        }

        print("Starting LSPatch process with ${inputApks.size} APK files")

        val apkFilePaths = inputApks.map { it.absolutePath }.toTypedArray()

        val logger = object : Logger() {
            override fun d(message: String?) {
                message?.let { print("DEBUG: $it") }
            }

            override fun i(message: String?) {
                message?.let { print("INFO: $it") }
            }

            override fun e(message: String?) {
                message?.let { print("ERROR: $it") }
            }
        }

        print("Using mod file: ${modFile.absolutePath}")
        print("Using keystore: ${keyStore.absolutePath}")

        withContext(Dispatchers.IO) {
            LSPatch(
                logger,
                *apkFilePaths,
                "-o", outputDir.absolutePath,
                "-l", "2",
                "-f",
                "-v",
                "-m", modFile.absolutePath,
                "-k", keyStore.absolutePath,
                keyStorePassword,
                keyAlias,
                keyPassword,
            ).doCommandLine()
        }

        val patchedFiles = outputDir.listFiles()
        if (patchedFiles.isNullOrEmpty()) {
            throw IOException("Patching failed - no output files generated")
        }

        print("Patching completed successfully")
        print("Generated ${patchedFiles.size} patched files")

        patchedFiles.forEachIndexed { index, file ->
            print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
        }
    }

    private fun applyCustomMapsApiKey(
        inputApks: List<File>,
        customMapsApiKey: String?,
        print: Print,
    ) {
        if (customMapsApiKey == null) return

        try {
            print("Attempting to apply custom Maps API key...")
            val baseApk = inputApks.find {
                it.name == "base.apk" || it.name.startsWith("base.apk-")
            } ?: inputApks.first()

            print("Using ${baseApk.name} for Maps API key modification")
            val apkModule = ApkModule.loadApkFile(baseApk)

            val metaElements = apkModule.androidManifest.applicationElement.getElements { element ->
                element.name == "meta-data"
            }

            var found = false
            while (metaElements.hasNext() && !found) {
                val element = metaElements.next()
                val nameAttr = element.searchAttributeByName("name")

                if (nameAttr != null && nameAttr.valueString == MAPS_API_KEY_NAME) {
                    val valueAttr = element.searchAttributeByName("value")
                    if (valueAttr != null) {
                        print("Found Maps API key element, replacing with custom key")
                        valueAttr.setValueAsString(StyleDocument.parseStyledString(customMapsApiKey))
                        found = true
                    }
                }
            }

            if (found) {
                print("Successfully replaced Maps API key, saving APK")
                apkModule.writeApk(baseApk)
            } else {
                print("Maps API key element not found in manifest, skipping replacement")
            }
        } catch (e: Exception) {
            print("Error applying Maps API key: ${e.message}")
        }
    }

    private fun copyWithoutEmbed(
        inputApks: List<File>,
        outputDir: File,
        print: Print,
    ) {
        print("Skipping LSPatch as embedModule is disabled")

        inputApks.forEach { apkFile ->
            val outputFile = File(outputDir, apkFile.name)
            apkFile.copyTo(outputFile, overwrite = true)
            print("Copied ${apkFile.name} to output directory")
        }

        val copiedFiles = outputDir.listFiles()
        if (copiedFiles.isNullOrEmpty()) {
            throw IOException("Copying APKs failed - no output files generated")
        }

        print("Copying completed successfully")
        print("Copied ${copiedFiles.size} files")

        copiedFiles.forEachIndexed { index, file ->
            print("  ${index + 1}. ${file.name} (${file.length() / 1024}KB)")
        }
    }
}
