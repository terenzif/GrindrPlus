package com.gpp.core

import android.content.Context
import java.io.File

/** Device capability probes shared by Bridge + Manager (safe for slim embed). */
object DeviceFlags {
    fun isRooted(context: Context): Boolean {
        return try {
            val markers = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/data/local/tmp/su",
            )
            markers.any { File(it).exists() } ||
                File("/system/app/Superuser.apk").exists() ||
                context.packageManager.getInstalledPackages(0)
                    .any { it.packageName.contains("magisk", ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Vector (and compatible Xposed frameworks) hook this method to return true when active.
     * Method name stays `isLSPosed` for module/framework compatibility. Default false.
     */
    @JvmStatic
    fun isLSPosed(): Boolean = false
}
