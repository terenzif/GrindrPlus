package com.grindrplus.core.mapping

import android.content.Context
import com.grindrplus.core.LogSource
import com.grindrplus.core.Logger
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Runtime loader for versioned mapping packs under `assets/mappings/<versionCode>.json`
 * and optionally from a remote GitHub URL (see [DEFAULT_REMOTE_BASE_URL]).
 *
 * Prefer [loadForVersion] at Xposed init: remote → disk cache → module APK assets → literals.
 * Soft-fail: missing/invalid packs leave [current] null so callers fall back to compile-time literals.
 */
object MappingDictionary {
    private const val ASSET_DIR = "mappings"
    private const val SCHEMA_VERSION = 1
    private const val CACHE_SUBDIR = "mapping-packs-cache"
    private const val REMOTE_CONNECT_TIMEOUT_MS = 3_000
    private const val REMOTE_READ_TIMEOUT_MS = 3_000

    /**
     * Default remote base (no trailing slash). Override via [loadForVersion] `remoteBaseUrl`
     * or a one-line file `mapping_pack_base_url.txt` under the cache parent (filesDir).
     *
     * Pack URL: `{base}/{versionCode}.json`
     */
    const val DEFAULT_REMOTE_BASE_URL =
        "https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs"

    /** Core symbols required for a pack to count as usable (ready-for-lab gate). */
    val CORE_SYMBOL_KEYS: List<String> = listOf(
        "core.userAgent",
        "core.userSession",
        "core.deviceInfo",
        "core.grindrLocationProvider",
        "core.serverDrivenCascadeRepo",
    )

    @Volatile
    private var active: MappingPack? = null

    val current: MappingPack?
        get() = active

    /**
     * Whether [pack] (or the active pack) has non-empty names for every [CORE_SYMBOL_KEYS] entry.
     */
    fun hasCompleteCore(pack: MappingPack? = active): Boolean {
        if (pack == null) return false
        return CORE_SYMBOL_KEYS.all { key ->
            pack.symbols[key]?.name?.isNotEmpty() == true
        }
    }

    fun missingCoreKeys(pack: MappingPack? = active): List<String> {
        if (pack == null) return CORE_SYMBOL_KEYS
        return CORE_SYMBOL_KEYS.filter { key ->
            pack.symbols[key]?.name.isNullOrEmpty()
        }
    }
    /**
     * Load order for device [versionCode]:
     * 1. remote JSON (soft-fail network/parse)
     * 2. on-device file cache
     * 3. module APK assets ([loadFromModuleApk])
     *
     * Never throws for I/O / network — returns null so init can continue with literals.
     */
    fun loadForVersion(
        modulePath: String,
        versionCode: Int,
        cacheDir: File,
        remoteBaseUrl: String = DEFAULT_REMOTE_BASE_URL,
        fetchRemote: Boolean = true,
    ): MappingPack? {
        val resolvedBase = resolveRemoteBaseUrl(cacheDir, remoteBaseUrl)
        if (fetchRemote) {
            fetchRemotePack(resolvedBase, versionCode)?.let { json ->
                decodeAndActivate(json, versionCode)?.let { pack ->
                    writeCache(cacheDir, versionCode, json)
                    Logger.i(
                        "Mapping pack loaded from remote ($resolvedBase/$versionCode.json)",
                        LogSource.MODULE
                    )
                    return pack
                }
            }
        }

        loadFromCache(cacheDir, versionCode)?.let { return it }

        return loadFromModuleApk(modulePath, versionCode)
    }

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
     * Prefer [loadFromModuleApk] / [loadForVersion] when running inside Grindr via Xposed.
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

    private fun resolveRemoteBaseUrl(cacheDir: File, fallback: String): String {
        val overrideFile = File(cacheDir, "mapping_pack_base_url.txt")
        val fromFile = runCatching {
            if (overrideFile.isFile) {
                overrideFile.readText().lineSequence().firstOrNull()?.trim().orEmpty()
            } else {
                ""
            }
        }.getOrDefault("")
        val base = fromFile.ifEmpty { fallback }.trimEnd('/')
        return base
    }

    private fun fetchRemotePack(baseUrl: String, versionCode: Int): String? {
        val url = "$baseUrl/$versionCode.json"
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
                readTimeout = REMOTE_READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "GrindrPlus-MappingDictionary/1 (+https://github.com/terenzif/GrindrPlus)"
                )
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                Logger.w(
                    "Remote mapping pack HTTP $code for $url",
                    LogSource.MODULE
                )
                runCatching { conn.errorStream?.close() }
                return@runCatching null
            }
            conn.inputStream.use { input ->
                input.bufferedReader().readText()
            }
        }.getOrElse { err ->
            Logger.w(
                "Remote mapping pack fetch failed for versionCode=$versionCode: ${err.message}",
                LogSource.MODULE
            )
            null
        }
    }

    private fun cacheFile(cacheDir: File, versionCode: Int): File =
        File(File(cacheDir, CACHE_SUBDIR), "$versionCode.json")

    private fun writeCache(cacheDir: File, versionCode: Int, json: String) {
        runCatching {
            val file = cacheFile(cacheDir, versionCode)
            file.parentFile?.mkdirs()
            file.writeText(json)
        }.onFailure { err ->
            Logger.w(
                "Failed to cache mapping pack versionCode=$versionCode: ${err.message}",
                LogSource.MODULE
            )
        }
    }

    private fun loadFromCache(cacheDir: File, versionCode: Int): MappingPack? {
        val file = cacheFile(cacheDir, versionCode)
        if (!file.isFile) return null
        val json = runCatching { file.readText() }.getOrElse { err ->
            Logger.w(
                "Mapping pack cache read failed for versionCode=$versionCode: ${err.message}",
                LogSource.MODULE
            )
            return null
        }
        return decodeAndActivate(json, versionCode)?.also {
            Logger.i(
                "Mapping pack loaded from device cache (versionCode=$versionCode)",
                LogSource.MODULE
            )
        }
    }
}
