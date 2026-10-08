package com.gpp.manager.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gpp.core.Config
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.core.DeviceFlags
import com.gpp.manager.DATA_URL
import com.gpp.manager.ManagerBridge
import com.gpp.manager.TAG
import com.gpp.manager.activityScope
import com.gpp.manager.installation.Installation
import com.gpp.manager.installation.steps.Print
import com.gpp.manager.ui.components.BannerType
import com.gpp.manager.ui.components.FileDialog
import com.gpp.manager.ui.components.MessageBanner
import com.gpp.manager.ui.components.VersionSelector
import com.gpp.manager.utils.ErrorHandler
import com.gpp.manager.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val logEntries = mutableStateListOf<LogEntry>()

@Composable
fun InstallPage(
    context: Activity,
    innerPadding: PaddingValues,
    viewModel: InstallScreenViewModel = viewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val versionData = viewModel.versionData

    var selectedVersion by remember { mutableStateOf<Data?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var success by remember { mutableStateOf(false) }
    var warningBannerVisible by remember { mutableStateOf(true) }
    val vectorFrameworkPresent = DeviceFlags.isLSPosed()
    var rootedBannerVisible by remember { mutableStateOf(vectorFrameworkPresent) }
    var showCustomFileDialog by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var customVersionName by remember { mutableStateOf("custom") }
    var customBundleUri by remember { mutableStateOf<Uri?>(null) }
    var customModUri by remember { mutableStateOf<Uri?>(null) }

    val grindrInstalled = remember { isPackageInstalled(context, GRINDR_PACKAGE_NAME) }
    val cloneInstalled = remember {
        isPackageInstalled(context, Installation.PRODUCT_CLONE_PACKAGE)
    }

    val manifestUrl = (Config.get("custom_manifest", DATA_URL) as String).ifBlank { null }

    LaunchedEffect(Unit) {
        viewModel.loadVersionData(manifestUrl.toString())
    }

    LaunchedEffect(versionData.size) {
        if (selectedVersion == null && versionData.isNotEmpty()) {
            selectedVersion = versionData.first()
            addLog("Module build: ${selectedVersion?.modVer}", LogType.INFO)
        }
    }

    val print: Print = { output ->
        val logType = ConsoleLogger.parseLogType(output)
        context.runOnUiThread { addLog(output, logType) }
    }

    if (showCustomFileDialog) {
        FileDialog(
            context = context,
            onDismiss = { showCustomFileDialog = false },
            onSelect = { versionName, bundleUri, modUri ->
                customVersionName = versionName
                customBundleUri = bundleUri
                customModUri = modUri
                showCustomFileDialog = false
                addLog("Emergency custom files selected: $versionName", LogType.INFO)
            },
        )
    }

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)
            .fillMaxSize(),
    ) {
        when {
            isLoading -> LoadingScreen()
            errorMessage != null -> ErrorScreen(errorMessage!!) {
                viewModel.loadVersionData(manifestUrl.toString())
            }
            else -> {
                MessageBanner(
                    text = "• Creates Grindr++ from the Grindr APK already installed from Play\n" +
                        "• Stock Grindr stays untouched; Grindr++ is a separate clone\n" +
                        "• After a Play update, tap again to re-patch with mapping packs\n" +
                        "• Don't close GrindMod mid-patch",
                    isVisible = warningBannerVisible,
                    isPulsating = isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    type = BannerType.WARNING,
                    onDismiss = { warningBannerVisible = false },
                )

                if (vectorFrameworkPresent) {
                    MessageBanner(
                        text = "Vector detected — rooted users should use GrindMod Alloy " +
                            "(com.gpp.alloy) and the Modding toggle in Settings. " +
                            "This Install tab is the rootless Morphe path.",
                        isVisible = rootedBannerVisible,
                        isPulsating = true,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.ERROR,
                        onDismiss = { rootedBannerVisible = false },
                    )
                }

                if (!grindrInstalled) {
                    MessageBanner(
                        text = "Install Grindr from the Play Store first, then return here " +
                            "to create Grindr++.",
                        isVisible = true,
                        isPulsating = false,
                        modifier = Modifier.fillMaxWidth(),
                        type = BannerType.ERROR,
                        onDismiss = {},
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                VersionSelector(
                    versions = versionData,
                    selectedVersion = selectedVersion,
                    onVersionSelected = { selected ->
                        selectedVersion = selected
                        addLog("Module build ${selected.modVer}", LogType.INFO)
                    },
                    isEnabled = !isWorking,
                    modifier = Modifier.fillMaxWidth(),
                    customOption = null,
                )

                Spacer(modifier = Modifier.height(16.dp))

                ConsoleOutput(
                    logEntries = logEntries,
                    modifier = Modifier.weight(0.5f),
                    onClear = {
                        logEntries.clear()
                        addLog("Logs cleared", LogType.SUCCESS)
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = {
                            activityScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        StorageUtils.cleanupOldInstallationFiles(
                                            context,
                                            true,
                                            selectedVersion?.modVer,
                                        )
                                    }
                                    addLog("Cleaned up old files", LogType.SUCCESS)
                                } catch (e: Exception) {
                                    addLog("Cleanup failed: ${e.localizedMessage}", LogType.ERROR)
                                }
                            }
                        },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Clean Up", modifier = Modifier.padding(vertical = 8.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = {
                            when {
                                success -> launchPackage(
                                    context,
                                    Installation.PRODUCT_CLONE_PACKAGE,
                                    "Grindr++",
                                )
                                else -> startCreateGrindrPlus(
                                    version = selectedVersion,
                                    context = context,
                                    print = print,
                                    onStarted = { isWorking = true },
                                    onCompleted = { ok ->
                                        isWorking = false
                                        success = ok
                                    },
                                )
                            }
                        },
                        enabled = (selectedVersion != null || success) &&
                            !isWorking &&
                            (grindrInstalled || success),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = when {
                                isWorking -> "Patching…"
                                success -> "Open Grindr++"
                                cloneInstalled -> "Update Grindr++"
                                else -> "Create Grindr++"
                            },
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }

                TextButton(
                    onClick = { showAdvanced = !showAdvanced },
                    enabled = !isWorking,
                ) {
                    Text(if (showAdvanced) "Hide emergency fallback" else "Emergency: custom files")
                }

                if (showAdvanced) {
                    OutlinedButton(
                        onClick = { showCustomFileDialog = true },
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Select custom Grindr bundle + mod")
                    }
                    if (customBundleUri != null && customModUri != null) {
                        Button(
                            onClick = {
                                isWorking = true
                                activityScope.launch {
                                    try {
                                        val bundleFile = createTempFileFromUri(
                                            context,
                                            customBundleUri!!,
                                            "grindr-$customVersionName.zip",
                                        )
                                        val modFile = createTempFileFromUri(
                                            context,
                                            customModUri!!,
                                            "mod-$customVersionName.zip",
                                        )
                                        val mapsApiKey =
                                            (Config.get("maps_api_key", "") as String).ifBlank { null }
                                        val installation = Installation(
                                            context,
                                            customVersionName,
                                            modFile.absolutePath,
                                            mapsApiKey,
                                        )
                                        withContext(Dispatchers.IO) {
                                            installation.installCustom(bundleFile, modFile, print)
                                        }
                                        addLog("Custom install completed", LogType.SUCCESS)
                                        success = true
                                    } catch (e: Exception) {
                                        handleInstallationError(e, context)
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Run custom install (same-package)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Loading module builds…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
fun ErrorScreen(errorMessage: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Error: $errorMessage",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun startCreateGrindrPlus(
    version: Data?,
    context: Activity,
    print: Print,
    onStarted: () -> Unit,
    onCompleted: (Boolean) -> Unit,
) {
    if (version == null || version.modUrl.isBlank()) {
        addLog("Manifest entry missing mod URL.", LogType.ERROR)
        showToast(context, "Missing module download URL.")
        onCompleted(false)
        return
    }
    if (!isPackageInstalled(context, GRINDR_PACKAGE_NAME)) {
        addLog("Grindr is not installed from Play.", LogType.ERROR)
        showToast(context, "Install Grindr from Play Store first.")
        onCompleted(false)
        return
    }

    onStarted()
    addLog("Creating/updating Grindr++ from installed Grindr…", LogType.INFO)
    addLog("Module: ${version.modVer}", LogType.INFO)

    activityScope.launch {
        try {
            val mapsApiKey = (Config.get("maps_api_key", "") as String).ifBlank { null }
            val installation = Installation(
                context,
                version.modVer,
                version.modUrl,
                mapsApiKey,
            )
            withContext(Dispatchers.IO) {
                installation.createOrUpdateGrindrPlus(print)
            }
            addLog("Grindr++ ready!", LogType.SUCCESS)
            showToast(context, "Grindr++ installed")
            onCompleted(true)
        } catch (e: Exception) {
            handleInstallationError(e, context)
            onCompleted(false)
        }
    }
}

private suspend fun createTempFileFromUri(context: Context, uri: Uri, filename: String): File {
    return withContext(Dispatchers.IO) {
        val tempFile = File(context.filesDir, filename)
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            tempFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Failed to open input stream for URI: $uri")
        tempFile
    }
}

private fun handleInstallationError(e: Exception, context: Context) {
    val errorMessage = "ERROR: ${e.localizedMessage ?: "Unknown error"}"
    addLog(errorMessage, LogType.ERROR)

    if (errorMessage.contains("INCOMPATIBLE") || e.message?.contains("INCOMPATIBLE") == true) {
        if (context is Activity) {
            context.runOnUiThread { ManagerBridge.showUninstallDialog.value = true }
        } else {
            showToast(context, "Signature mismatch. Uninstall the previous Grindr++ clone first.")
        }
    } else {
        showToast(context, "Failed: ${e.localizedMessage}")
    }

    ErrorHandler.logError(context, TAG, "Installation failed", e)
}

private fun addLog(message: String, type: LogType = LogType.INFO) {
    if (message.contains("<>:")) {
        val prefix = message.split("<>:")[0]
        logEntries.find { it.message.startsWith(prefix) }?.let { logEntries.remove(it) }
    }
    logEntries.add(ConsoleLogger.log(message.replace("<>:", ":"), type))
}

private fun showToast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private fun launchPackage(context: Context, packageName: String, label: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        } else {
            showToast(context, "Could not launch $label")
        }
    } catch (e: Exception) {
        showToast(context, "Error launching $label: ${e.localizedMessage}")
    }
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}

fun installNavItem(): com.gpp.manager.MainNavItem =
    com.gpp.manager.MainNavItem(
        Icons.Rounded.Download,
        "Install",
        { InstallPage(it, this) },
    )
