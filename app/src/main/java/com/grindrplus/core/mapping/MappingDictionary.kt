package com.grindrplus.core.mapping

import android.content.Context
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import org.json.JSONObject
import java.io.File
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
        val json = runCatching {
            ZipFile(File(modulePath)).use { zip ->
                val entry = zip.getEntry("assets/$assetPath")
                    ?: zip.getEntry(assetPath)
                    ?: return null
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
        }.getOrElse { err ->
            Logger.w(
                "Mapping pack I/O failed for versionCode=$versionCode: ${err.message}",
                LogSource.MODULE
            )
            return null
        }
        return decodeAndActivate(json, versionCode)
    }

    /**
     * Load pack for [versionCode] from [context] assets (manager app / tests).
     * Prefer [loadFromModuleApk] when running inside Grindr via Xposed.
     */
    fun load(context: Context, versionCode: Int): MappingPack? {
        val assetPath = "$ASSET_DIR/$versionCode.json"
        val json = runCatching {
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }.getOrElse { err ->
            Logger.w(
                "Mapping pack I/O failed for versionCode=$versionCode: ${err.message}",
                LogSource.MODULE
            )
            return null
        }
        return decodeAndActivate(json, versionCode)
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

    /**
     * Parse [json] and activate when schema/version are compatible with [expectedVersionCode].
     * Soft-fails format/shape errors: logs a warning and returns null (keeps literals).
     */
    internal fun decodeAndActivate(json: String, expectedVersionCode: Int): MappingPack? {
        val pack = runCatching {
            parsePack(JSONObject(json))
        }.getOrElse { err ->
            Logger.w(
                "Invalid mapping pack for versionCode=$expectedVersionCode: ${err.message}",
                LogSource.MODULE
            )
            return null
        }
        return activateIfCompatible(pack, expectedVersionCode)
    }

    private fun activateIfCompatible(
        pack: MappingPack,
        expectedVersionCode: Int? = null,
    ): MappingPack? {
        if (pack.schemaVersion > SCHEMA_VERSION) {
            Logger.w(
                "Mapping pack schemaVersion=${pack.schemaVersion} unsupported " +
                    "(max=$SCHEMA_VERSION) — leaving pack unloaded",
                LogSource.MODULE
            )
            return null
        }
        if (expectedVersionCode != null && pack.versionCode != expectedVersionCode) {
            Logger.w(
                "Mapping pack versionCode mismatch: embedded=${pack.versionCode} " +
                    "expected=$expectedVersionCode — leaving pack unloaded",
                LogSource.MODULE
            )
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
