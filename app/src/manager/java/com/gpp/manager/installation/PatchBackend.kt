package com.gpp.manager.installation

import android.content.Context
import java.io.File

/**
 * Morphe A seam: turn input Grindr APKs + module into patched artifacts ready for session install.
 *
 * Default implementation is [LSPatchIntegratedBackend] (`-l 2` integrated embed).
 */
interface PatchBackend {
    suspend fun patch(
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
    )
}
