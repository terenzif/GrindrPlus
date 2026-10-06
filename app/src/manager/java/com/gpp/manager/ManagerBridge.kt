package com.gpp.manager

import androidx.compose.runtime.mutableStateOf
import com.onebusaway.plausible.android.Plausible

/**
 * Cross-cutting Manager UI/analytics handles without tying Install code to [MainActivity]
 * (avoids compile cycles between Manager shell and manager-install).
 */
object ManagerBridge {
    var plausible: Plausible? = null
    val showUninstallDialog = mutableStateOf(false)
}
