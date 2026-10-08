package com.gpp.manager.installation

import android.content.Context
import android.widget.Toast
import com.gpp.manager.ManagerBridge
import com.gpp.manager.installation.steps.CheckStorageSpaceStep
import com.gpp.manager.installation.steps.CloneGrindrStep
import com.gpp.manager.installation.steps.DownloadStep
import com.gpp.manager.installation.steps.ExportInstalledGrindrStep
import com.gpp.manager.installation.steps.ExtractBundleStep
import com.gpp.manager.installation.steps.InjectCloneIconStep
import com.gpp.manager.installation.steps.InstallApkStep
import com.gpp.manager.installation.steps.PatchApkStep
import com.gpp.manager.installation.steps.Print
import com.gpp.manager.installation.steps.SignClonedGrindrApk
import com.gpp.manager.installation.steps.Step
import com.gpp.manager.utils.KeyStoreUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * Morphe install orchestrator.
 *
 * Primary product path [createOrUpdateGrindrPlus]: export Play-installed Grindr →
 * clone as [PRODUCT_CLONE_PACKAGE] / [PRODUCT_CLONE_LABEL] → inject brand icon →
 * LSPatch embed → session install.
 */
class Installation(
    private val context: Context,
    val version: String,
    private val modUrl: String,
    private val mapsApiKey: String?,
) {
    private val keyStoreUtils = KeyStoreUtils(context)
    private val folder = context.getExternalFilesDir(null)
        ?: throw IOException("External files directory not available")
    private val unzipFolder = File(folder, "splitApks/").also { it.mkdirs() }
    private val outputDir = File(folder, "LSPatchOutput/").also { it.mkdirs() }
    private val modFile = File(folder, "mod-$version.zip")
    private val bundleFile = File(folder, "grindr-$version.zip")

    private val installStep = InstallApkStep(outputDir)

    companion object {
        /** Stable product clone package (coexists with stock Play Grindr). */
        const val PRODUCT_CLONE_PACKAGE = "com.grindrapp.android.plus"

        /** Launcher label for the patched clone. */
        const val PRODUCT_CLONE_LABEL = "Grindr++"
    }

    /**
     * Create or update the Grindr++ clone from the device's Play-installed Grindr.
     */
    suspend fun createOrUpdateGrindrPlus(print: Print) = performOperation(
        steps = listOf(
            CheckStorageSpaceStep(folder),
            ExportInstalledGrindrStep(bundleFile),
            DownloadStep(modFile, modUrl, "mod"),
            ExtractBundleStep(bundleFile, unzipFolder),
            CloneGrindrStep(
                folder = unzipFolder,
                packageName = PRODUCT_CLONE_PACKAGE,
                appName = PRODUCT_CLONE_LABEL,
                debuggable = false,
            ),
            InjectCloneIconStep(unzipFolder),
            SignClonedGrindrApk(keyStoreUtils, unzipFolder),
            PatchApkStep(
                unzipFolder,
                outputDir,
                modFile,
                keyStoreUtils.keyStore,
                mapsApiKey,
                embedLSPatch = true,
            ),
            installStep,
        ),
        operationName = "grindr_plus_from_installed",
        print = print,
    )

    /** Offline fallback: local Grindr bundle zip + local mod zip (same-package patch). */
    suspend fun installCustom(
        bundleFile: File,
        modFile: File,
        print: Print,
    ) = performOperation(
        steps = listOf(
            CheckStorageSpaceStep(folder),
            ExtractBundleStep(bundleFile, unzipFolder),
            PatchApkStep(unzipFolder, outputDir, modFile, keyStoreUtils.keyStore, mapsApiKey),
            InstallApkStep(outputDir),
        ),
        operationName = "custom_install",
        print = print,
    )

    suspend fun performOperation(
        steps: List<Step>,
        operationName: String,
        onSuccess: suspend () -> Unit = {},
        print: Print,
    ) = try {
        withContext(Dispatchers.IO) {
            ManagerBridge.plausible?.pageView("app://grindrmod/$operationName")

            val time = measureTimeMillis {
                for (step in steps) {
                    print("Executing step: ${step.name}")

                    val time = measureTimeMillis {
                        step.execute(context, print)
                    }

                    print("Step ${step.name} completed in ${time / 1000} seconds")
                }
            }

            ManagerBridge.plausible?.event(
                "${operationName}_success",
                "app://grindrmod/${operationName}_success",
                props = mapOf("time" to time),
            )

            onSuccess()
        }
    } catch (e: CancellationException) {
        print("$operationName was cancelled")
        showToast("$operationName was cancelled")
        ManagerBridge.plausible?.event(
            "${operationName}_cancelled",
            "app://grindrmod/${operationName}_cancelled",
        )
        throw e
    } catch (e: Exception) {
        val errorMsg = "$operationName failed: ${e.localizedMessage}"
        ManagerBridge.plausible?.event(
            "${operationName}_failed",
            "app://grindrmod/${operationName}_failure",
            props = mapOf("error" to e.message),
        )
        print(errorMsg)
        showToast(errorMsg)
        cleanupOnFailure()
        throw e
    }

    private fun cleanupOnFailure() {
        try {
            unzipFolder.listFiles()?.forEach { it.delete() }
            outputDir.listFiles()?.forEach { it.delete() }

            if (bundleFile.exists() && bundleFile.length() <= 100) bundleFile.delete()
            if (modFile.exists() && modFile.length() <= 100) modFile.delete()
        } catch (_: Exception) {
        }
    }

    fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
