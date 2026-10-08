package com.gpp.manager.installation.steps

import android.content.Context
import com.gpp.manager.installation.LSPatchIntegratedBackend
import com.gpp.manager.installation.MorpheOrchestrator
import com.gpp.manager.installation.PatchBackend
import java.io.File

// 5th
class PatchApkStep(
    private val unzipFolder: File,
    private val outputDir: File,
    private val modFile: File,
    private val keyStore: File,
    private val customMapsApiKey: String?,
    private val embedLSPatch: Boolean = true,
    patchBackend: PatchBackend = LSPatchIntegratedBackend(),
) : BaseStep() {
    override val name = "Patching Grindr APK"

    private val orchestrator = MorpheOrchestrator(patchBackend)

    override suspend fun doExecute(context: Context, print: Print) {
        orchestrator.patchForInstallFromDir(
            context = context,
            fromDir = unzipFolder,
            outputDir = outputDir,
            modFile = modFile,
            keyStore = keyStore,
            keyStorePassword = LSPatchIntegratedBackend.DEFAULT_KEYSTORE_PASSWORD,
            keyAlias = LSPatchIntegratedBackend.DEFAULT_KEY_ALIAS,
            keyPassword = LSPatchIntegratedBackend.DEFAULT_KEY_PASSWORD,
            customMapsApiKey = customMapsApiKey,
            embedModule = embedLSPatch,
            print = print,
        )
    }
}
