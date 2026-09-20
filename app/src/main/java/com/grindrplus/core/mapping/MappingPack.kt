package com.grindrplus.core.mapping

/**
 * Parsed mapping pack for one Grindr [versionCode].
 *
 * Logical keys (e.g. `core.userAgent`, `ProfileDetails.DISTANCE_UTILS`) stay stable
 * across Grindr releases; [symbols] hold the R8 / DEX names for that build.
 *
 * Empty [MappingSymbol.name] means "skip this hook site" (same contract as Obfuscation "").
 */
data class MappingPack(
    val schemaVersion: Int,
    val versionName: String,
    val versionCode: Int,
    val confidence: String,
    val generatedFrom: String,
    val symbols: Map<String, MappingSymbol>,
    val hooks: Map<String, MappingHookStatus>,
)

data class MappingSymbol(
    val kind: String,
    val name: String,
    val fingerprint: String? = null,
    val method: String? = null,
    val note: String? = null,
) {
    val isPresent: Boolean get() = name.isNotEmpty()
}

data class MappingHookStatus(
    val status: String,
    val reason: String? = null,
)
