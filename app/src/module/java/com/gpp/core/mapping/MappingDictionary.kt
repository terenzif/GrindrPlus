package com.gpp.core.mapping

import android.content.Context
import com.gpp.core.LogSource
import com.gpp.core.Logger
import org.json.JSONArray
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
 *
 * Pack schema is version 2. Schema v1 packs soft-migrate to v2 in memory on load.
 */
object MappingDictionary {
    private const val ASSET_DIR = "mappings"
    private const val SCHEMA_VERSION = 2
    private const val CACHE_SUBDIR = "mapping-packs-cache"
    private const val INDEX_FILE_NAME = "index.json"
    private const val REMOTE_CONNECT_TIMEOUT_MS = 3_000
    private const val REMOTE_READ_TIMEOUT_MS = 3_000

    /**
     * Default remote base (no trailing slash). Override via [loadForVersion] `remoteBaseUrl`
     * or a one-line file `mapping_pack_base_url.txt` under the cache parent (filesDir).
     *
     * Pack URL: `{base}/{versionCode}.json`
     * Catalog URL: `{base}/index.json`
     */
    const val DEFAULT_REMOTE_BASE_URL =
        "https://raw.githubusercontent.com/terenzif/grindr-plus-plus/master/mapping-packs"

    @Volatile
    private var active: MappingPack? = null

    val current: MappingPack?
        get() = active

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
     * Fetch `{base}/index.json` (soft-fail). On success, cache under
     * `mapping-packs-cache/index.json`. On remote failure, try the on-device cache.
     */
    fun loadIndex(
        cacheDir: File,
        remoteBaseUrl: String = DEFAULT_REMOTE_BASE_URL,
    ): MappingPackIndex? {
        val resolvedBase = resolveRemoteBaseUrl(cacheDir, remoteBaseUrl)
        val remoteJson = fetchRemoteText("$resolvedBase/$INDEX_FILE_NAME")
        if (remoteJson != null) {
            val index = runCatching { parseIndex(JSONObject(remoteJson)) }.getOrElse { err ->
                Logger.w(
                    "Invalid remote mapping pack index: ${err.message}",
                    LogSource.MODULE
                )
                null
            }
            if (index != null) {
                writeIndexCache(cacheDir, remoteJson)
                return index
            }
        }
        return loadIndexFromCache(cacheDir)
    }

    /**
     * Load pack for [versionCode] from the module APK on disk (Vector / Xposed path).
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
     * - No active pack: [fallback] (legacy compile-time literals).
     * - Active pack and key present: [MappingSymbol.name] (may be empty = explicit skip).
     * - Active pack and key absent: empty string (soft-skip; do **not** use [fallback]).
     */
    fun resolve(key: String, fallback: String): String {
        val pack = active ?: return fallback
        val symbol = pack.symbols[key] ?: return ""
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

    /**
     * Soft-validate fingerprints on class symbols in the active pack.
     * Logs warnings on hard mismatches; never throws.
     *
     * @return number of hard mismatches (struct / implements / field / data-class)
     */
    fun validateActiveFingerprints(classLoader: ClassLoader): Int {
        val pack = active ?: return 0
        var mismatches = 0
        for ((key, symbol) in pack.symbols) {
            if (symbol.kind != "class" || !symbol.isPresent || symbol.fingerprint.isNullOrBlank()) {
                continue
            }
            val clazz = runCatching { classLoader.loadClass(symbol.name) }.getOrNull()
            if (clazz == null) {
                Logger.d(
                    "Fingerprint skip $key: class ${symbol.name} not loaded yet",
                    LogSource.MODULE
                )
                continue
            }
            val result = TargetFingerprint.matches(clazz, symbol.fingerprint)
            when {
                !result.matched -> {
                    mismatches++
                    Logger.w(
                        "Fingerprint mismatch for $key (${symbol.name}) " +
                            "[${result.mode}]: ${result.detail}",
                        LogSource.MODULE
                    )
                }
                result.mode == TargetFingerprint.Result.Mode.UNSUPPORTED -> {
                    Logger.d(
                        "Fingerprint advisory for $key: ${result.detail}",
                        LogSource.MODULE
                    )
                }
            }
        }
        if (mismatches > 0) {
            Logger.w(
                "Mapping fingerprint mismatches: $mismatches " +
                    "(hooks may be stale — check mapping pack)",
                LogSource.MODULE
            )
        }
        return mismatches
    }

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

    /**
     * Hot-reload pack for [versionCode] (remote → cache → assets) and re-validate fingerprints.
     * Soft-fail: returns null and leaves previous [current] if reload fails.
     */
    fun reloadForVersion(
        modulePath: String,
        versionCode: Int,
        cacheDir: File,
        classLoader: ClassLoader? = null,
        remoteBaseUrl: String = DEFAULT_REMOTE_BASE_URL,
    ): MappingPack? {
        val previous = active
        clear()
        val pack = loadForVersion(
            modulePath = modulePath,
            versionCode = versionCode,
            cacheDir = cacheDir,
            remoteBaseUrl = remoteBaseUrl,
            fetchRemote = true,
        )
        if (pack == null) {
            active = previous
            Logger.w(
                "Hot-reload failed for versionCode=$versionCode — keeping previous pack",
                LogSource.MODULE
            )
            return previous
        }
        if (classLoader != null) {
            validateActiveFingerprints(classLoader)
        }
        Logger.i(
            "Mapping pack hot-reloaded: ${pack.versionName} (${pack.versionCode})",
            LogSource.MODULE
        )
        return pack
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

    /**
     * Parse a pack JSON object. Schema v1 soft-migrates to v2 in memory
     * (schemaVersion=2, symbols preserved, hooks map always present).
     *
     * Symbol [MappingSymbol.kind] accepts `"class"`, `"method"`, `"field"` (and stores others
     * flexibly). For method/field, [MappingSymbol.name] is the member name; owner class may
     * appear in [MappingSymbol.note].
     */
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

        val rawSchema = root.optInt("schemaVersion", 1)
        val schemaVersion = if (rawSchema <= 1) SCHEMA_VERSION else rawSchema

        return MappingPack(
            schemaVersion = schemaVersion,
            versionName = root.optString("versionName"),
            versionCode = root.optInt("versionCode"),
            confidence = root.optString("confidence"),
            generatedFrom = root.optString("generatedFrom"),
            symbols = symbols,
            hooks = hooks,
        )
    }

    /**
     * Parse `{ "schemaVersion": 1, "packs": [ { versionCode, versionName, confidence } ] }`.
     */
    internal fun parseIndex(root: JSONObject): MappingPackIndex {
        val packsArray = root.optJSONArray("packs") ?: JSONArray()
        val packs = ArrayList<MappingPackIndexEntry>(packsArray.length())
        for (i in 0 until packsArray.length()) {
            val obj = packsArray.optJSONObject(i) ?: continue
            packs.add(
                MappingPackIndexEntry(
                    versionCode = obj.optInt("versionCode"),
                    versionName = obj.optString("versionName"),
                    confidence = obj.optString("confidence"),
                )
            )
        }
        return MappingPackIndex(
            schemaVersion = root.optInt("schemaVersion", 1),
            packs = packs,
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

    private fun fetchRemotePack(baseUrl: String, versionCode: Int): String? =
        fetchRemoteText("$baseUrl/$versionCode.json")

    private fun fetchRemoteText(url: String): String? {
        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = REMOTE_CONNECT_TIMEOUT_MS
                readTimeout = REMOTE_READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "GrindrPlusPlus-MappingDictionary/1 (+https://github.com/terenzif/grindr-plus-plus)"
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
                "Remote mapping fetch failed for $url: ${err.message}",
                LogSource.MODULE
            )
            null
        }
    }

    private fun cacheFile(cacheDir: File, versionCode: Int): File =
        File(File(cacheDir, CACHE_SUBDIR), "$versionCode.json")

    private fun indexCacheFile(cacheDir: File): File =
        File(File(cacheDir, CACHE_SUBDIR), INDEX_FILE_NAME)

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

    private fun writeIndexCache(cacheDir: File, json: String) {
        runCatching {
            val file = indexCacheFile(cacheDir)
            file.parentFile?.mkdirs()
            file.writeText(json)
        }.onFailure { err ->
            Logger.w(
                "Failed to cache mapping pack index: ${err.message}",
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

    private fun loadIndexFromCache(cacheDir: File): MappingPackIndex? {
        val file = indexCacheFile(cacheDir)
        if (!file.isFile) return null
        val json = runCatching { file.readText() }.getOrElse { err ->
            Logger.w(
                "Mapping pack index cache read failed: ${err.message}",
                LogSource.MODULE
            )
            return null
        }
        return runCatching { parseIndex(JSONObject(json)) }.getOrElse { err ->
            Logger.w(
                "Invalid cached mapping pack index: ${err.message}",
                LogSource.MODULE
            )
            null
        }
    }
}
