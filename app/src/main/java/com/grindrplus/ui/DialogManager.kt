package com.grindrplus.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.grindrplus.BuildConfig
import com.grindrplus.GrindrPlus
import com.grindrplus.core.Logger
import com.grindrplus.core.LogSource
import com.grindrplus.core.Utils

object DialogManager {
    @Volatile var shouldShowVersionMismatchDialog = false
    @Volatile var shouldShowBridgeConnectionError = false
    @Volatile var hasCheckedVersions = false

    fun checkVersionCodes(context: Context, versionCodes: IntArray, versionNames: Array<String>) {
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode.toLong()
        }

        val isVersionNameSupported = pkgInfo.versionName in versionNames
        val isVersionCodeSupported = versionCodes.any { it.toLong() == versionCode }

        if (!isVersionNameSupported || !isVersionCodeSupported) {
            val installedInfo = "${pkgInfo.versionName} (code: $versionCode)"
            val expectedInfo = "${versionNames.joinToString(", ")} " +
                    "(code: ${versionCodes.joinToString(", ")})"
            shouldShowVersionMismatchDialog = true
            Logger.w("Version mismatch detected. Installed: $installedInfo, Required: $expectedInfo", LogSource.MODULE)
        }

        hasCheckedVersions = true
    }

    fun showVersionMismatchDialog(activity: Activity) {
        try {
            val context = activity.applicationContext
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val installedInfo = "${pkgInfo.versionName} (code: $versionCode)"
            val expectedInfo = "${BuildConfig.TARGET_GRINDR_VERSION_NAMES.joinToString(", ")} " +
                    "(code: ${BuildConfig.TARGET_GRINDR_VERSION_CODES.joinToString(", ")})"

            val dialog = AlertDialog.Builder(activity)
                .setTitle("GrindrPlus: Version Mismatch")
                .setMessage("Incompatible Grindr version detected.\n\n" +
                        "• Installed: $installedInfo\n" +
                        "• Required: $expectedInfo\n\n" +
                        "GrindrPlus has been disabled. Please install a compatible Grindr version.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .create()
            dialog.show()
            Logger.i("Version mismatch dialog shown", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to show version mismatch dialog: ${e.message}", LogSource.MODULE)
            Utils.showToast(Toast.LENGTH_LONG, "Version mismatch detected. Please install a compatible Grindr version.", activity)
        }
    }

    fun showBridgeConnectionError(activity: Activity? = null) {
        try {
            val targetActivity = activity ?: GrindrPlus.currentActivity

            if (targetActivity != null) {
                val dialog = AlertDialog.Builder(targetActivity)
                    .setTitle("Bridge Connection Failed")
                    .setMessage("Failed to connect to the bridge service. The module will not work properly.\n\n" +
                            "This may be caused by:\n" +
                            "• Battery optimization settings\n" +
                            "• System killing background processes\n" +
                            "• App being force stopped\n\n" +
                            "Try restarting the app or reinstalling the module.")
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setCancelable(false)
                    .create()

                targetActivity.runOnUiThread {
                    dialog.show()
                }

                Logger.i("Bridge connection error dialog shown", LogSource.MODULE)
            } else {
                Utils.showToast(Toast.LENGTH_LONG, "Bridge service connection failed - module features unavailable")
            }
        } catch (e: Exception) {
            Logger.e("Failed to show bridge error dialog: ${e.message}", LogSource.MODULE)
            Utils.showToast(Toast.LENGTH_LONG, "Bridge service connection failed - module features unavailable")
        }
    }

    fun showAgeVerificationComplianceDialog(activity: Activity) {
        try {
            val dialog = AlertDialog.Builder(activity)
                .setTitle("Age Verification Required")
                .setMessage("You are accessing Grindr from the UK where age verification is legally mandated.\n\n" +
                        "LEGAL COMPLIANCE NOTICE:\n" +
                        "GrindrPlus does NOT bypass, disable, or interfere with age verification systems. Any attempt to circumvent age verification requirements is illegal under UK law and is strictly prohibited.\n\n" +
                        "MANDATORY REQUIREMENTS:\n" +
                        "1. Complete age verification using the official Grindr application\n" +
                        "2. Comply with all UK legal verification processes\n" +
                        "3. Install GrindrPlus only after successful verification through official channels\n\n" +
                        "WARNING:\n" +
                        "The developers of this module are not responsible for any legal consequences resulting from non-compliance with age verification requirements.")
                .setPositiveButton("I Understand") { dialog, _ ->
                    activity.finish()
                    dialog.dismiss()
                    Utils.showToast(Toast.LENGTH_LONG,
                        "Please complete age verification in the official Grindr app first, then reinstall GrindrPlus", activity)
                }
                .setNegativeButton("Exit App") { dialog, _ ->
                    dialog.dismiss()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setCancelable(false)
                .create()

            dialog.show()
            Logger.i("Age verification compliance dialog shown", LogSource.MODULE)

        } catch (e: Exception) {
            Logger.e("Failed to show age verification dialog: ${e.message}", LogSource.MODULE)
            Utils.showToast(Toast.LENGTH_LONG,
                "Age verification required. Please use official Grindr app to verify, then reinstall GrindrPlus.", activity)
            activity.finish()
        }
    }

    fun showMapsApiKeyDialog(activity: Activity) {
        try {
            AlertDialog.Builder(activity)
                .setTitle("Maps API Key Required")
                .setMessage("Maps functionality requires a Google Maps API key for LSPatch users due to signature validation issues.\n\n" +
                        "Quick Setup:\n" +
                        "1. Create a Google Cloud project at console.cloud.google.com\n" +
                        "2. Enable: Maps SDK for Android, Geocoding API, Maps JavaScript API\n" +
                        "3. Create API key with NO restrictions\n" +
                        "4. Add key to GrindrPlus settings\n" +
                        "5. REINSTALL GrindrPlus (restart won't work)\n\n" +
                        "Note: Google may request credit card for free tier.")
                .setPositiveButton("Open Console") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            data = "https://console.cloud.google.com/".toUri()
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }

                        val appContext = activity.applicationContext
                        appContext.startActivity(intent)

                    } catch (e: Exception) {
                        Utils.showToast(Toast.LENGTH_LONG, "Unable to open browser. Please visit console.cloud.google.com manually", activity)
                    }
                }
                .setNegativeButton("Dismiss") { dialog, _ -> dialog.dismiss() }
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(true)
                .show()
        } catch (e: Exception) {
            Logger.e("Maps API key dialog error: ${e.message}")
        }
    }
}
