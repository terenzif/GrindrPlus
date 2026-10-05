package com.grindrplus.core.mapping

/**
 * Parsed mapping pack for one Grindr [versionCode].
 *
 * Logical keys (e.g. `core.userAgent`, `ProfileDetails.DISTANCE_UTILS`) stay stable
 * across Grindr releases; [symbols] hold the R8 / DEX names for that build.
 *
 * Empty [MappingSymbol.name] means "skip this hook site" (same contract as Obfuscation "").
 *
 * Schema v2 symbol [MappingSymbol.kind] values: `"class"`, `"method"`, `"field"`.
 * For method/field, [MappingSymbol.name] is the member name; owning class may be in [MappingSymbol.note].
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
    /** `"class"`, `"method"`, `"field"`, or other loader-accepted kind string. */
    val kind: String,
    val name: String,
    val fingerprint: String? = null,
    /** Legacy v1 method name on a class symbol; prefer `kind: "method"` in schema v2. */
    val method: String? = null,
    val note: String? = null,
) {
    val isPresent: Boolean get() = name.isNotEmpty()
}

data class MappingHookStatus(
    val status: String,
    val reason: String? = null,
)

/**
 * Catalog of available mapping packs (`index.json` at the remote / bundled base).
 * Catalog [schemaVersion] is independent of pack [MappingPack.schemaVersion].
 */
data class MappingPackIndex(
    val schemaVersion: Int,
    val packs: List<MappingPackIndexEntry>,
)

data class MappingPackIndexEntry(
    val versionCode: Int,
    val versionName: String,
    val confidence: String,
)
