package com.grindrplus.core.mapping

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Runtime loader for versioned mapping packs under `assets/mappings/<versionCode>.json`.
 *
 * Prefer [loadFromModuleApk] at Xposed init: [Context.getAssets] belongs to Grindr, not the module.
 * Soft-fail: missing/invalid packs leave [current] null so callers fall back to compile-time literals.
 */
object MappingDictionary {
    private const val ASSET_DIR = "mappings"
    private const val SCHEMA_VERSION = 1

    @Volatile
    private var active: MappingPack? = null

    val current: MappingPack?
        get() = active

    /**
     * Load pack for [versionCode] from the module APK on disk (LSPosed / Xposed path).
     * Returns null if the entry is missing or invalid — callers should soft-fail.
     */
    fun loadFromModuleApk(modulePath: String, versionCode: Int): MappingPack? {
        val assetPath = "$ASSET_DIR/$versionCode.json"
        val pack = runCatching {
            ZipFile(File(modulePath)).use { zip ->
                val entry = zip.getEntry("assets/$assetPath")
                    ?: zip.getEntry(assetPath)
                    ?: return null
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    parsePack(JSONObject(reader.readText()))
                }
            }
        }.getOrElse { err ->
            if (err is IOException) return null
            throw err
        }
        return activateIfCompatible(pack)
    }

    /**
     * Load pack for [versionCode] from [context] assets (manager app / tests).
     * Prefer [loadFromModuleApk] when running inside Grindr via Xposed.
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
        return activateIfCompatible(pack)
    }

    /**
     * Resolve a class/name symbol.
     * - No active pack, or key absent from pack: [fallback] (migration safety).
     * - Key present with empty `name`: empty string (explicit skip, same as Obfuscation).
     */
    fun resolve(key: String, fallback: String): String {
        val pack = active ?: return fallback
        val symbol = pack.symbols[key] ?: return fallback
        return symbol.name
    }

    fun className(key: String): String =
        active?.symbols?.get(key)?.name.orEmpty()

    /**
     * Method name on a symbol entry (`method` field), or [fallback] when no pack / key / method.
     */
    fun methodName(key: String, fallback: String? = null): String? {
        val pack = active ?: return fallback
        val symbol = pack.symbols[key] ?: return fallback
        return symbol.method ?: fallback
    }

    fun symbol(key: String): MappingSymbol? =
        active?.symbols?.get(key)

    fun requireClass(key: String): String {
        val name = className(key)
        if (name.isEmpty()) {
            throw IllegalStateException("MappingDictionary: missing or empty class for key=$key")
        }
        return name
    }

    fun clear() {
        active = null
    }

    /** Test / tooling: set the active pack after [parsePack]. Soft-rejects unsupported schema. */
    internal fun activate(pack: MappingPack): MappingPack? = activateIfCompatible(pack)

    private fun activateIfCompatible(pack: MappingPack): MappingPack? {
        if (pack.schemaVersion > SCHEMA_VERSION) {
            return null
        }
        active = pack
        return pack
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
