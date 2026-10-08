package com.gpp.hooks

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.gpp.BuildConfig
import com.gpp.GrindrPlus
import com.gpp.GrindrPlus.restartGrindr
import com.gpp.core.Config
import com.gpp.core.loge
import com.gpp.core.logi
import com.gpp.core.logw
import com.gpp.debug.AgentDebugLog
import com.gpp.ui.Utils.getId
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.hook
import com.gpp.utils.hookConstructor
import java.io.File
import java.util.WeakHashMap

class StatusDialog : Hook(
    "Status Dialog",
    "Check whether GrindrPlus is alive or not"
) {
    private val tabView = "com.google.android.material.tabs.TabLayout\$TabView"
    private val homeActivity = "com.grindrapp.android.ui.home.HomeActivity"
    private val attachedHome = WeakHashMap<Activity, Boolean>()

    override fun init() {
        // Legacy Material tabs (account/store/etc.) — first tab long-press.
        findClass(tabView).hookConstructor(HookStage.AFTER) { param ->
            val tabView = param.thisObject() as View

            tabView.post {
                val parent = tabView.parent as? ViewGroup
                val position = parent?.indexOfChild(tabView) ?: -1
                // #region agent log
                AgentDebugLog.log(
                    hypothesisId = "H25",
                    location = "StatusDialog.TabView.ctor",
                    message = "tabview_ctor",
                    data = mapOf("position" to position, "parent" to (parent?.javaClass?.simpleName ?: "null")),
                    runId = "e2e-features",
                )
                // #endregion

                if (position == 0) {
                    tabView.setOnLongClickListener { v ->
                        showGrindrPlusDialog(v.context)
                        true
                    }
                }
            }
        }

        // Home V2 is Compose (`home_v2_container`) — TabView never constructed (H25/H26).
        // Long-press bottom-left band ≈ first nav item.
        // Note: Hooker also matches inherited Activity.dispatchTouchEvent — filter to Home only.
        runCatching {
            val homeClazz = findClass(homeActivity)
            homeClazz.hook("dispatchTouchEvent", HookStage.BEFORE) { param ->
                val activity = param.thisObject() as? Activity ?: return@hook
                if (!homeClazz.isInstance(activity)) return@hook
                val event = param.arg<MotionEvent>(0)
                ensureHomeLongPress(activity)
                homeDetectors[activity]?.onTouchEvent(event)
            }
        }.onFailure {
            logw("StatusDialog HomeActivity hook failed: ${it.message}")
            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H26",
                location = "StatusDialog.init",
                message = "home_hook_failed",
                data = mapOf("err" to (it.message ?: "?")),
                runId = "e2e-features",
            )
            // #endregion
        }

        // Material BottomNavigationView fallback (non-Compose layouts).
        runCatching {
            findClass("com.google.android.material.bottomnavigation.BottomNavigationView")
                .hookConstructor(HookStage.AFTER) { param ->
                    val nav = param.thisObject() as ViewGroup
                    nav.post {
                        if (nav.childCount > 0) {
                            val first = nav.getChildAt(0)
                            // #region agent log
                            AgentDebugLog.log(
                                hypothesisId = "H26",
                                location = "StatusDialog.BottomNav.ctor",
                                message = "bottom_nav_attached",
                                data = mapOf(
                                    "childCount" to nav.childCount,
                                    "first" to first.javaClass.simpleName,
                                ),
                                runId = "e2e-features",
                            )
                            // #endregion
                            first.setOnLongClickListener { v ->
                                showGrindrPlusDialog(v.context)
                                true
                            }
                        }
                    }
                }
        }.onFailure {
            // Optional — class may be tree-shaken if unused.
        }
    }

    private val homeDetectors = WeakHashMap<Activity, GestureDetector>()

    private fun ensureHomeLongPress(activity: Activity) {
        if (attachedHome[activity] == true) return
        attachedHome[activity] = true

        val density = activity.resources.displayMetrics.density
        val bottomBandPx = (72f * density)
        val firstTabWidthFraction = 0.22f

        val detector = GestureDetector(
            activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onLongPress(e: MotionEvent) {
                    val root = activity.window?.decorView ?: return
                    val h = root.height.toFloat().coerceAtLeast(1f)
                    val w = root.width.toFloat().coerceAtLeast(1f)
                    val inBottom = e.y >= (h - bottomBandPx)
                    val inFirst = e.x <= (w * firstTabWidthFraction)
                    // #region agent log
                    AgentDebugLog.log(
                        hypothesisId = "H26",
                        location = "StatusDialog.homeLongPress",
                        message = "home_long_press",
                        data = mapOf(
                            "inBottom" to inBottom,
                            "inFirst" to inFirst,
                            "yFrac" to (e.y / h),
                            "xFrac" to (e.x / w),
                        ),
                        runId = "e2e-features",
                    )
                    // #endregion
                    if (inBottom && inFirst) {
                        showGrindrPlusDialog(activity)
                    }
                }
            },
        )
        homeDetectors[activity] = detector

        // #region agent log
        val homeId = getId("home_v2_container", "id", activity)
        val homeView = if (homeId != 0) activity.findViewById<View>(homeId) else null
        AgentDebugLog.log(
            hypothesisId = "H26",
            location = "StatusDialog.ensureHomeLongPress",
            message = "home_detector_ready",
            data = mapOf(
                "homeId" to homeId,
                "homeView" to (homeView?.javaClass?.simpleName ?: "null"),
            ),
            runId = "e2e-features",
        )
        // #endregion
    }

    private fun showGrindrPlusDialog(context: Context) {
        // #region agent log
        AgentDebugLog.log(
            hypothesisId = "H22",
            location = "StatusDialog.showGrindrPlusDialog",
            message = "status_dialog_open",
            data = emptyMap(),
            runId = "e2e-features",
        )
        // #endregion
        GrindrPlus.currentActivity?.runOnUiThread {
            GrindrPlus.executeAsync {
                try {
                    val packageManager = context.packageManager
                    val packageInfo = packageManager.getPackageInfo(context.packageName, 0)

                    val appVersionName = packageInfo.versionName
                    val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }

                    val packageName = context.packageName
                    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
                    val androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    val moduleVersion = try {
                        BuildConfig.VERSION_NAME
                    } catch (e: Exception) {
                        "Unknown"
                    }

                    val bridgeStatus = if (GrindrPlus.bridgeClient.isConnected()) {
                        "Connected"
                    } else {
                        "Disconnected"
                    }

                    val androidDeviceIdStatus = (Config.get("android_device_id", "") as String)
                        .let { id -> if (id.isNotEmpty()) "Spoofing ($id)" else "Not Spoofing (stock)" }

                    var isLSPosed = false
                    var isRooted = false
                    if (GrindrPlus.bridgeClient.isConnected()) {
                        isLSPosed = GrindrPlus.bridgeClient.isLSPosed()
                        isRooted = GrindrPlus.bridgeClient.isRooted()
                    }

                    val message = buildString {
                        appendLine("GrindrPlus is active and running")
                        appendLine()
                        appendLine("App Information:")
                        appendLine("• Version: $appVersionName ($appVersionCode)")
                        appendLine("• Package: $packageName")
                        appendLine("• Android ID: $androidDeviceIdStatus")
                        appendLine()
                        appendLine("Module Information:")
                        appendLine("• GrindrPlus: $moduleVersion")
                        appendLine("• Bridge Status: $bridgeStatus")
                        if (GrindrPlus.bridgeClient.isConnected()) {
                            appendLine("• Vector/Xposed: $isLSPosed")
                            appendLine("• Rooted: $isRooted")
                        }
                        appendLine()
                        appendLine("Device Information:")
                        appendLine("• Device: $deviceModel")
                        appendLine("• Android: $androidVersion")
                        appendLine()
                        appendLine("Long-press the first bottom tab (home) to show this dialog")
                    }

                    GrindrPlus.runOnMainThread {
                        AlertDialog.Builder(context)
                            .setTitle("GrindrPlus")
                            .setMessage(message)
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .setNegativeButton("Restart") { dialog, _ ->
                                dialog.dismiss()
                                performCacheClearOperation(context)
                            }
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .show()
                    }

                } catch (e: Exception) {
                    GrindrPlus.runOnMainThread {
                        AlertDialog.Builder(context)
                            .setTitle("GrindrPlus")
                            .setMessage("GrindrPlus is active and running\n\nError retrieving details: ${e.message}")
                            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                            .show()
                    }
                }
            }
        }
    }

    private fun performCacheClearOperation(context: Context) {
        GrindrPlus.executeAsync {
            val operationResult = CacheClearResult()

            try {
                val cacheDirs = getCacheDirectories(context)

                cacheDirs.forEach { cacheDir ->
                    if (cacheDir.exists() && cacheDir.canWrite()) {
                        val clearResult = clearDirectoryContents(cacheDir)
                        operationResult.addResult(clearResult)
                    }
                }

                clearTemporarySharedPreferences(context, operationResult)
                clearWebViewCache(context, operationResult)

                val totalSizeMB = operationResult.totalSize / (1024 * 1024)
                logi("Cache operation completed: ${operationResult.totalFiles} files removed, ${totalSizeMB}MB freed")
                restartGrindr(100, "Restarting Grindr... (${totalSizeMB}MB freed)")
            } catch (e: Exception) {
                loge("Cache clear operation failed: ${e.message}")
                GrindrPlus.runOnMainThread {
                    GrindrPlus.showToast(Toast.LENGTH_LONG, "Cache clear operation failed: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun getCacheDirectories(context: Context): List<File> {
        val directories = mutableListOf<File>()

        context.cacheDir?.let { directories.add(it) }
        context.externalCacheDir?.let { directories.add(it) }

        return directories
    }

    private fun clearDirectoryContents(directory: File): ClearOperationResult {
        val result = ClearOperationResult()

        if (!directory.exists() || !directory.isDirectory) {
            return result
        }

        try {
            directory.listFiles()?.forEach { file ->
                val fileSize = if (file.isFile) file.length() else 0L

                val deleted = if (file.isDirectory) {
                    val subResult = clearDirectoryContents(file)
                    result.addSubResult(subResult)
                    file.delete()
                } else {
                    file.delete()
                }

                if (deleted) {
                    result.filesCleared++
                    result.bytesCleared += fileSize
                }
            }
        } catch (e: SecurityException) {
            logw("Permission denied accessing directory: ${directory.absolutePath}")
        } catch (e: Exception) {
            logw("Error processing directory: ${directory.absolutePath} - ${e.message}")
        }

        return result
    }

    private fun clearWebViewCache(context: Context, operationResult: CacheClearResult) {
        try {
            val webViewCacheDir = File(context.filesDir.parent, "app_webview")
            if (webViewCacheDir.exists()) {
                val clearResult = clearDirectoryContents(webViewCacheDir)
                operationResult.addResult(clearResult)
            }
        } catch (e: Exception) {
            logw("WebView cache clear failed: ${e.message}")
        }
    }

    private fun clearTemporarySharedPreferences(context: Context, operationResult: CacheClearResult) {
        try {
            val sharedPrefsDir = File(context.filesDir.parent, "shared_prefs")
            if (sharedPrefsDir.exists()) {
                sharedPrefsDir.listFiles()
                    ?.filter { it.name.contains("cache", ignoreCase = true) || it.name.contains("temp", ignoreCase = true) }
                    ?.forEach { file ->
                        val size = file.length()
                        if (file.delete()) {
                            operationResult.totalFiles++
                            operationResult.totalSize += size
                        }
                    }
            }
        } catch (e: Exception) {
            logw("Temporary shared preferences clear failed: ${e.message}")
        }
    }

    private data class ClearOperationResult(
        var filesCleared: Int = 0,
        var bytesCleared: Long = 0L
    ) {
        fun addSubResult(other: ClearOperationResult) {
            filesCleared += other.filesCleared
            bytesCleared += other.bytesCleared
        }
    }

    private data class CacheClearResult(
        var totalFiles: Int = 0,
        var totalSize: Long = 0L
    ) {
        fun addResult(result: ClearOperationResult) {
            totalFiles += result.filesCleared
            totalSize += result.bytesCleared
        }
    }
}
