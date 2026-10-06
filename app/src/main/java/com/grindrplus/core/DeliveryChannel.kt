package com.grindrplus.core

import com.grindrplus.BuildConfig

/**
 * Product delivery channel for the dual-APK split (ADR 0005).
 * UI label stays "GrindrPlus" for both; package IDs differ.
 */
enum class DeliveryChannel {
    /** Rootless Manager — Morphe A/B + LSPatch. Package: com.grindrplus.morphe */
    MORPHE,

    /** Rooted LSPosed module + Manager without LSPatch. Package: com.grindrplus.alloy */
    ALLOY,

    /** Internal slim embed payload for LSPatch -m (not a user-facing product). */
    EMBED,
    ;

    val isRootlessManager: Boolean get() = this == MORPHE
    val isRootedModule: Boolean get() = this == ALLOY
    val showsInstallTab: Boolean get() = this == MORPHE

    companion object {
        val current: DeliveryChannel =
            when (BuildConfig.DELIVERY_CHANNEL) {
                "alloy" -> ALLOY
                "embed" -> EMBED
                else -> MORPHE
            }
    }
}
