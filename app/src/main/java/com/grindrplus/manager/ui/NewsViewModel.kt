package com.grindrplus.manager.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grindrplus.core.Logger
import com.grindrplus.manager.CHANNEL_PING_URL
import com.grindrplus.manager.GPlusMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * News tab: fork announcements from [CHANNEL_PING_URL] (`news.json`), not GitHub Releases
 * (those stay on Home). Soft-fail on network/parse.
 */
class NewsViewModel : ViewModel() {
    val messages = mutableStateListOf<GPlusMessage>()
    val isLoading = mutableStateOf(true)
    val errorMessage = mutableStateOf<String?>(null)

    private var hasFetched = false

    companion object {
        private const val TAG = "NewsViewModel"
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun fetchNews(forceRefresh: Boolean = false) {
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
                        .url(CHANNEL_PING_URL)
                        .header("User-Agent", "GrindrPlus/terenzif (Android; news)")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw Exception("HTTP ${response.code}")
                        }
                        response.body?.string()
                            ?: throw Exception("Empty response body")
                    }
                }
                val parsed = parseMessages(body)
                messages.clear()
                messages.addAll(parsed)
            } catch (e: Exception) {
                Logger.w("$TAG: soft-fail fetching news: ${e.message}")
                errorMessage.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun parseMessages(jsonContent: String): List<GPlusMessage> {
        val jsonArray = JSONArray(jsonContent)
        val result = mutableListOf<GPlusMessage>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val id = obj.optString("message_id").ifBlank { i.toString() }
            val text = obj.optString("text")
            val date = obj.optLong("date", 0L)
            if (text.isNotBlank()) {
                result.add(GPlusMessage(id, text, date))
            }
        }
        return result.sortedByDescending { it.timestamp }
    }
}
