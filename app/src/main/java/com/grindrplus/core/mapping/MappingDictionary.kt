package com.grindrplus.core.mapping

import android.content.Context
import org.json.JSONObject
import java.io.IOException

/**
 * Runtime loader for versioned mapping packs under `assets/mappings/<versionCode>.json`.
 *
 * Not yet wired into [com.grindrplus.GrindrPlus] / [com.grindrplus.core.Obfuscation]:
 * call sites still use compile-time constants. This stub is the Fase B entry point
 * described in the Project store doc `docs/mapping-dictionary-plan.md`.
 */
object MappingDictionary {
    private const val ASSET_DIR = "mappings"
    private const val SCHEMA_VERSION = 1

    @Volatile
    private var active: MappingPack? = null

    val current: MappingPack?
        get() = active

    /**
     * Load pack for [versionCode] from module assets.
     * Returns null if the asset is missing or invalid — callers should soft-fail.
     */
    fun load(context: Context, versionCode: Int): MappingPack? {
        val assetPath = "$ASSET_DIR/$versionCode.json"
        val pack = runCatching {
            context.assets.open(assetPath).bufferedReader().use { reader ->
                parsePack(JSONObject(reader.readText()))
            }
        }.getOrElse { err ->
            if (err is IOException) return null
            throw err
        }
        if (pack.schemaVersion > SCHEMA_VERSION) {
            return null
        }
        active = pack
        return pack
    }

    fun className(key: String): String =
        active?.symbols?.get(key)?.name.orEmpty()

    fun symbol(key: String): MappingSymbol? =
        active?.symbols?.get(key)

    fun clear() {
        active = null
    }

    internal fun parsePack(root: JSONObject): MappingPack {
        val symbolsJson = root.optJSONObject("symbols") ?: JSONObject()
        val symbols = linkedMapOf<String, MappingSymbol>()
        for (key in symbolsJson.keys()) {
            val obj = symbolsJson.getJSONObject(key)
            symbols[key] = MappingSymbol(
                kind = obj.optString("kind", "class"),
                name = obj.optString("name", ""),
                fingerprint = obj.optString("fingerprint").ifEmpty { null },
                method = obj.optString("method").ifEmpty { null },
                note = obj.optString("note").ifEmpty { null },
            )
        }

        val hooksJson = root.optJSONObject("hooks") ?: JSONObject()
        val hooks = linkedMapOf<String, MappingHookStatus>()
        for (key in hooksJson.keys()) {
            val obj = hooksJson.getJSONObject(key)
            hooks[key] = MappingHookStatus(
                status = obj.optString("status", "unverified"),
                reason = obj.optString("reason").ifEmpty { null },
            )
        }

        return MappingPack(
            schemaVersion = root.optInt("schemaVersion", 1),
            versionName = root.optString("versionName"),
            versionCode = root.optInt("versionCode"),
            confidence = root.optString("confidence"),
            generatedFrom = root.optString("generatedFrom"),
            symbols = symbols,
            hooks = hooks,
        )
    }
}
