package com.grindrplus.manager.ui

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindrplus.core.Logger
import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.manager.play.InstalledPackageExporter
import com.grindrplus.manager.play.PlayStoreSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class InstallScreenViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _loadingText = MutableStateFlow("Loading available versions...")

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val versionData = mutableStateListOf<Data>()

    fun loadVersionData(context: Context, manifestUrl: String) {
        _isLoading.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            val textUpdateJob = launch {
                delay(10000)
                _loadingText.value = "Still loading... Check your internet connectivity."
            }

            val result = fetchInstallVersions(context.applicationContext, manifestUrl)

            textUpdateJob.cancel()

            if (result.isSuccess) {
                versionData.clear()
                versionData.addAll(result.getOrThrow())
                Logger.d("Found ${versionData.size} mapping-backed install versions")
            } else {
                _errorMessage.value = result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                Logger.e("Error loading version data: ${_errorMessage.value}")
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchInstallVersions(
        context: Context,
        manifestUrl: String,
    ): Result<List<Data>> = withContext(Dispatchers.IO) {
        try {
            val modUrl = fetchModUrl(manifestUrl)
            val packs = fetchMappingIndex(context)
            if (packs.isEmpty()) {
                return@withContext Result.failure(
                    IOException("No mapping packs found (remote index + assets empty)"),
                )
            }
            val installedVc = InstalledPackageExporter.installedVersionCode(
                context,
                PlayStoreSession.GRINDR_PACKAGE,
            )
            val rows = packs.map { pack ->
                val installed = installedVc != null && installedVc == pack.versionCode
                Data(
                    modVer = displayLabel(pack.versionName, pack.versionCode, installed),
                    grindrUrl = "",
                    modUrl = modUrl,
                    versionCode = pack.versionCode,
                    versionName = pack.versionName,
                    isInstalled = installed,
                )
            }.sortedByDescending { it.versionCode }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchModUrl(manifestUrl: String): String {
        val client = HttpClient.instance
        val maxRetries = 3
        var lastException: Exception? = null
        for (attempt in 1..maxRetries) {
            try {
                Logger.d("Loading mod URL from $manifestUrl (Attempt $attempt/$maxRetries)")
                val request = Request.Builder().url(manifestUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Server error: ${response.code}")
                    val body = response.body?.string() ?: throw IOException("Empty response body")
                    return parseLatestModUrl(body)
                }
            } catch (e: Exception) {
                lastException = e
                Logger.e("Manifest attempt $attempt failed: ${e.message}")
                if (attempt < maxRetries) Thread.sleep(2000)
            }
        }
        throw lastException ?: IOException("Failed to load manifest after $maxRetries retries")
    }

    private fun fetchMappingIndex(context: Context): List<MappingPackRef> {
        val remote = tryFetchRemoteIndex()
        if (remote.isNotEmpty()) return remote
        Logger.w("Remote mapping index unavailable — using bundled assets")
        return loadBundledIndex(context)
    }

    private fun tryFetchRemoteIndex(): List<MappingPackRef> {
        val url = "${MappingDictionary.DEFAULT_REMOTE_BASE_URL}/index.json"
        return try {
            val request = Request.Builder().url(url).build()
            HttpClient.instance.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.e("Mapping index HTTP ${response.code}")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                parseMappingIndex(body)
            }
        } catch (e: Exception) {
            Logger.e("Mapping index fetch failed: ${e.message}")
            emptyList()
        }
    }

    private fun loadBundledIndex(context: Context): List<MappingPackRef> {
        // Prefer assets/mappings/index.json
        try {
            context.assets.open("mappings/index.json").bufferedReader().use { reader ->
                val parsed = parseMappingIndex(reader.readText())
                if (parsed.isNotEmpty()) return parsed
            }
        } catch (_: Exception) {
        }
        // Fallback: numeric filenames under assets/mappings/
        return try {
            context.assets.list("mappings")
                ?.mapNotNull { name ->
                    val code = name.removeSuffix(".json").toLongOrNull() ?: return@mapNotNull null
                    val versionName = try {
                        context.assets.open("mappings/$name").bufferedReader().use { r ->
                            JSONObject(r.readText()).optString("versionName", "vc$code")
                        }
                    } catch (_: Exception) {
                        "vc$code"
                    }
                    MappingPackRef(versionCode = code, versionName = versionName)
                }
                ?.sortedByDescending { it.versionCode }
                .orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        data class MappingPackRef(
            val versionCode: Long,
            val versionName: String,
        )

        fun displayLabel(versionName: String, versionCode: Long, installed: Boolean): String {
            val base = if (versionName.isNotBlank()) {
                "$versionName ($versionCode)"
            } else {
                "versionCode $versionCode"
            }
            return if (installed) "$base • installed" else base
        }

        fun parseLatestModUrl(jsonData: String): String {
            val jsonObject = JSONObject(jsonData)
            val entries = mutableListOf<Pair<String, String>>()
            jsonObject.keys().forEach { key ->
                val arr = jsonObject.getJSONArray(key)
                if (arr.length() >= 2) {
                    val modUrl = arr.getString(1)
                    if (modUrl.isNotBlank()) entries.add(key to modUrl)
                }
            }
            if (entries.isEmpty()) {
                throw IOException("manifest.json has no mod URL entries")
            }
            // Prefer lexicographically latest key (historical modVer labels)
            return entries.maxBy { it.first }.second
        }

        fun parseMappingIndex(jsonData: String): List<MappingPackRef> {
            val trimmed = jsonData.trim()
            if (trimmed.isEmpty()) return emptyList()
            return try {
                when {
                    trimmed.startsWith("{") -> {
                        val root = JSONObject(trimmed)
                        val packs = root.optJSONArray("packs")
                            ?: throw JSONException("missing packs array")
                        parsePacksArray(packs)
                    }
                    trimmed.startsWith("[") -> parsePacksArray(JSONArray(trimmed))
                    else -> emptyList()
                }
            } catch (e: JSONException) {
                throw IOException("Invalid mapping index: ${e.localizedMessage}", e)
            }
        }

        private fun parsePacksArray(packs: JSONArray): List<MappingPackRef> {
            val result = mutableListOf<MappingPackRef>()
            for (i in 0 until packs.length()) {
                val obj = packs.getJSONObject(i)
                val code = obj.optLong("versionCode", 0L)
                if (code <= 0L) continue
                val name = obj.optString("versionName", "vc$code")
                result.add(MappingPackRef(versionCode = code, versionName = name))
            }
            return result.sortedByDescending { it.versionCode }
        }
    }
}
