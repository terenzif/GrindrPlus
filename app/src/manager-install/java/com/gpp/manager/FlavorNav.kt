package com.gpp.manager

import com.gpp.manager.ui.installNavItem

/** Morphe contributes the rootless Install tab. */
object FlavorNav {
    fun installTab(): MainNavItem? = installNavItem()
}
