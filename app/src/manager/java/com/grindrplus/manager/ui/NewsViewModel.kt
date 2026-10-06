package com.grindrplus.manager.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.time.Instant

/**
 * News tab: GitHub Releases for this fork (not Telegram).
 * Soft-fails on network/parse — UI shows empty list + wiki card.
 */
class NewsViewModel : ViewModel() {
    val releases = mutableStateListOf<Release>()
    val isLoading = mutableStateOf(true)
    val errorMessage = mutableStateOf<String?>(null)

    private var hasFetched = false

    companion object {
        private const val TAG = "NewsViewModel"
        private const val RELEASES_URL =
            "https://api.github.com/repos/terenzif/GrindrPlus/releases"
        private val client = OkHttpClient()
    }

    fun fetchReleases(forceRefresh: Boolean = false) {
        if (hasFetched && !forceRefresh) return
        if (forceRefresh) {
            hasFetched = false
            errorMessage.value = null
        }
        hasFetched = true
        isLoading.value = true

        viewModelScope.launch {
            try {
                val body = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(RELEASES_URL)
                        .header("Accept", "application/vnd.github.v3+json")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code}")
                        }
                        response.body?.string()
                            ?: throw Exception("Empty response body")
                    }
                }
                val parsed = parseReleases(body)
                releases.clear()
                releases.addAll(parsed)
            } catch (e: Exception) {
                Logger.w("$TAG: soft-fail fetching releases: ${e.message}")
                errorMessage.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun parseReleases(jsonContent: String): List<Release> {
        val jsonArray = JSONArray(jsonContent)
        val result = mutableListOf<Release>()
        for (i in 0 until jsonArray.length()) {
            val release = jsonArray.getJSONObject(i)
            val name = if (!release.isNull("name") && release.getString("name").isNotBlank()) {
                release.getString("name")
            } else {
                release.getString("tag_name")
            }
            val description = if (!release.isNull("body")) {
                release.getString("body")
            } else {
                "No description"
            }
            val authorObj = release.getJSONObject("author")
            val author = authorObj.getString("login")
            val avatarUrl = authorObj.getString("avatar_url")
            val publishedAt = Instant.parse(release.getString("published_at"))
            result.add(Release(name, description, author, avatarUrl, publishedAt))
        }
        return result.sortedByDescending { it.publishedAt }
    }
}
