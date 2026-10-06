package com.grindrplus.core

import android.content.Context
import android.content.pm.PackageManager

/** Clone discovery helpers (no Manager UI dependency — slim embed safe). */
object GrindrCloneUtils {
    const val GRINDR_PACKAGE_PREFIX = "com.grindrapp.android."

    fun getExistingClones(context: Context): List<String> {
        return try {
            context.packageManager.getInstalledPackages(0)
                .filter { it.packageName.startsWith(GRINDR_PACKAGE_PREFIX) }
                .map { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isGrindrInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(Constants.GRINDR_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
