package com.grindrplus.manager.installation

import android.content.Context
import com.grindrplus.morphe.b.MorpheBPatchEngine
import java.io.File
import java.io.IOException

/**
 * Morphe A (+ optional Morphe B) rootless orchestrator around [PatchBackend].
 *
 * Owns the patch half of the install pipeline:
 * **select APKs → [Morphe B static] → [PatchBackend.patch] → output for SessionInstaller**.
 *
 * Session install remains with
 * [com.grindrplus.manager.installation.steps.InstallApkStep] /
 * [com.grindrplus.manager.utils.SessionInstaller].
 */
class MorpheOrchestrator(
    private val patchBackend: PatchBackend = LSPatchIntegratedBackend(),
    private val morpheB: MorpheBPatchEngine = MorpheBPatchEngine(),
    private val applyMorpheB: Boolean = true,
) {
    /**
     * Patch [inputApks] into [outputDir] for a subsequent session install.
     *
     * @return files under [outputDir] after a successful patch (APKs and any LSPatch sidecars)
     */
    suspend fun patchForInstall(
        context: Context,
        inputApks: List<File>,
        outputDir: File,
        modFile: File,
        keyStore: File,
        keyStorePassword: String = LSPatchIntegratedBackend.DEFAULT_KEYSTORE_PASSWORD,
        keyAlias: String = LSPatchIntegratedBackend.DEFAULT_KEY_ALIAS,
        keyPassword: String = LSPatchIntegratedBackend.DEFAULT_KEY_PASSWORD,
        customMapsApiKey: String? = null,
        embedModule: Boolean = true,
        print: Print,
        cleanOutput: Boolean = true,
    ): List<File> {
        if (cleanOutput) {
            print("Cleaning output directory...")
            outputDir.mkdirs()
            outputDir.listFiles()?.forEach { it.delete() }
        }

        val apks = inputApks.filter { it.exists() && it.length() > 0 }.toMutableList()
        if (apks.isEmpty()) {
            throw IOException("No valid APK files found to patch")
        }

        // No-embed copies pre-signed APKs; mutating them here breaks PackageManager verify.
        // Embed path: LSPatch re-signs outputs after reading mutated inputs.
        if (applyMorpheB && embedModule) {
            try {
                morpheB.apply(apks, print)
            } catch (t: Throwable) {
                print("Morphe B failed (continuing with Morphe A only): ${t.message}")
            }
        } else if (applyMorpheB && !embedModule) {
            print("Morphe B: skipped (no-embed preserves signatures from SignClonedGrindrApk)")
        }

        patchBackend.patch(
            context = context,
            inputApks = apks,
            outputDir = outputDir,
            modFile = modFile,
            keyStore = keyStore,
            keyStorePassword = keyStorePassword,
            keyAlias = keyAlias,
            keyPassword = keyPassword,
            customMapsApiKey = customMapsApiKey,
            embedModule = embedModule,
            print = print,
        )

        val output = outputDir.listFiles()?.toList().orEmpty()
        if (output.isEmpty()) {
            throw IOException("Patching failed - no output files generated")
        }
        return output
    }

    /**
     * Convenience: select `*.apk` from [fromDir], then [patchForInstall].
     */
    suspend fun patchForInstallFromDir(
        context: Context,
        fromDir: File,
        outputDir: File,
        modFile: File,
        keyStore: File,
        keyStorePassword: String = LSPatchIntegratedBackend.DEFAULT_KEYSTORE_PASSWORD,
        keyAlias: String = LSPatchIntegratedBackend.DEFAULT_KEY_ALIAS,
        keyPassword: String = LSPatchIntegratedBackend.DEFAULT_KEY_PASSWORD,
        customMapsApiKey: String? = null,
        embedModule: Boolean = true,
        print: Print,
        cleanOutput: Boolean = true,
    ): List<File> = patchForInstall(
        context = context,
        inputApks = selectInputApks(fromDir),
        outputDir = outputDir,
        modFile = modFile,
        keyStore = keyStore,
        keyStorePassword = keyStorePassword,
        keyAlias = keyAlias,
        keyPassword = keyPassword,
        customMapsApiKey = customMapsApiKey,
        embedModule = embedModule,
        print = print,
        cleanOutput = cleanOutput,
    )

    companion object {
        /** Select non-empty `*.apk` files from a split/extract directory. */
        fun selectInputApks(fromDir: File): List<File> =
            fromDir.listFiles()
                ?.filter { it.name.endsWith(".apk") && it.exists() && it.length() > 0 }
                .orEmpty()
    }
}
