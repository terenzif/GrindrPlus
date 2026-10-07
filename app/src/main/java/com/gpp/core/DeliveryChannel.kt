package com.gpp.core

import com.gpp.BuildConfig

/**
 * Product delivery channel for the dual-APK split (ADR 0005).
 * Manager UI label is GrindMod for both; Grindr++ is the product / Morphe clone.
 * Package IDs differ (com.gpp.morphe / com.gpp.alloy).
 */
enum class DeliveryChannel {
    /** Rootless Manager / LSPatch installer only — not a Vector module. Package: com.gpp.morphe */
    MORPHE,

    /** Sole rooted Vector module + Manager without LSPatch. Package: com.gpp.alloy */
    ALLOY,

    /** Internal slim embed payload for LSPatch -m (not a user-facing product). */
    EMBED,
    ;

    val isRootlessManager: Boolean get() = this == MORPHE
    val isRootedModule: Boolean get() = this == ALLOY
    val showsInstallTab: Boolean get() = this == MORPHE

    /**
     * Package that hosts [com.gpp.bridge.BridgeService] for IPC.
     * Slim embed payload is not an installed app — bind to Morphe Manager.
     */
    val bridgeHostPackage: String
        get() = when (this) {
            EMBED -> "com.gpp.morphe"
            else -> BuildConfig.APPLICATION_ID
        }

    companion object {
        val current: DeliveryChannel =
            when (BuildConfig.DELIVERY_CHANNEL) {
                "alloy" -> ALLOY
                "embed" -> EMBED
                else -> MORPHE
            }
    }
}
