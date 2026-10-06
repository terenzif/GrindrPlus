package com.gpp.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import com.gpp.BuildConfig
import com.gpp.GrindrPlus
import com.gpp.core.Logger
import com.gpp.core.LogSource
import com.gpp.core.Utils

object DialogManager {
    /** Soft tip warning only — never aborts module init. */
    @Volatile var shouldShowVersionMismatchDialog = false
    /** Soft awareness when no pack and version is outside BuildConfig tip arrays. */
    @Volatile var shouldShowNoPackWarning = false
    @Volatile var shouldShowBridgeConnectionError = false
    @Volatile var hasCheckedVersions = false
    @Volatile var hasMappingPack = false

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
            val tipInfo = "${versionNames.joinToString(", ")} " +
                    "(code: ${versionCodes.joinToString(", ")})"
            shouldShowVersionMismatchDialog = true
            Logger.w(
                "Version tip mismatch (soft warning; init continues). " +
                    "Installed: $installedInfo, Tip: $tipInfo",
                LogSource.MODULE
            )
        }

        hasCheckedVersions = true
    }

    /**
     * Record whether a mapping pack loaded for the installed version.
     * When there is no pack and [versionCode] is outside tip arrays, prefer a soft
     * no-pack awareness dialog over the generic tip-mismatch dialog.
     */
    fun checkPackPresence(hasPack: Boolean, versionCode: Long, tipVersionCodes: IntArray) {
        hasMappingPack = hasPack
        if (!hasPack && tipVersionCodes.none { it.toLong() == versionCode }) {
            shouldShowNoPackWarning = true
            shouldShowVersionMismatchDialog = false
            Logger.w(
                "No mapping pack for versionCode=$versionCode — features may be limited " +
                    "(soft warning; init continues)",
                LogSource.MODULE
            )
        }
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
            val tipInfo = "${BuildConfig.TARGET_GRINDR_VERSION_NAMES.joinToString(", ")} " +
                    "(code: ${BuildConfig.TARGET_GRINDR_VERSION_CODES.joinToString(", ")})"

            val statusLine = if (hasMappingPack) {
                "A mapping pack is loaded — pack-driven mode is active. " +
                    "Some hooks may still differ from the tip build."
            } else {
                "No mapping pack for this build — GrindrPlus is running in experimental/limited mode. " +
                    "Some features may not work."
            }

            val dialog = AlertDialog.Builder(activity)
                .setTitle("GrindrPlus: Version Tip Notice")
                .setMessage(
                    "Installed Grindr differs from the module tip versions.\n\n" +
                        "• Installed: $installedInfo\n" +
                        "• Tip: $tipInfo\n\n" +
                        "$statusLine\n\n" +
                        "Initialization continues; this is an awareness notice only."
                )
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(true)
                .create()
            dialog.show()
            Logger.i("Version tip soft-warning dialog shown", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to show version tip dialog: ${e.message}", LogSource.MODULE)
            Utils.showToast(
                Toast.LENGTH_LONG,
                "Grindr version differs from tip — running in limited/experimental mode.",
                activity
            )
        }
    }

    fun showNoPackWarningDialog(activity: Activity) {
        try {
            val context = activity.applicationContext
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }

            val dialog = AlertDialog.Builder(activity)
                .setTitle("GrindrPlus: Limited Features")
                .setMessage(
                    "No mapping pack for versionCode $versionCode — features may be limited.\n\n" +
                        "GrindrPlus will keep running with compile-time fallbacks where possible. " +
                        "This is an awareness notice only; the module is not disabled."
                )
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setIcon(android.R.drawable.ic_dialog_info)
                .setCancelable(true)
                .create()
            dialog.show()
            Logger.i("No-pack soft-warning dialog shown", LogSource.MODULE)
        } catch (e: Exception) {
            Logger.e("Failed to show no-pack dialog: ${e.message}", LogSource.MODULE)
            Utils.showToast(
                Toast.LENGTH_LONG,
                "No mapping pack for this Grindr version — features may be limited.",
                activity
            )
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
