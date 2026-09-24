package com.grindrplus.manager

import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Public GoatCounter visitor-counter JSON (no API token).
 * Requires “Allow adding visitor counts on your website” in GoatCounter site settings.
 *
 * @see <a href="https://www.goatcounter.com/help/visitor-counter">visitor-counter docs</a>
 */
data class GoatCounterSnapshot(
    val totalLabel: String,
    val weekLabel: String,
    val homeLabel: String,
    val publicUrl: String,
)

object GoatCounterStats {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        siteBase: String,
        publicUrl: String = siteBase,
    ): GoatCounterSnapshot? = withContext(Dispatchers.IO) {
        val base = siteBase.trimEnd('/')
        if (!base.startsWith("https://")) return@withContext null
        try {
            val total = fetchCount("$base/counter/TOTAL.json")
            val week = fetchCount("$base/counter/TOTAL.json?start=week")
            val home = fetchCount("$base/counter/${encodePath("/home")}.json")
            GoatCounterSnapshot(
                totalLabel = total ?: "—",
                weekLabel = week ?: "—",
                homeLabel = home ?: "—",
                publicUrl = publicUrl.ifBlank { base },
            )
        } catch (e: Exception) {
            Logger.w("GoatCounter stats fetch failed: ${e.message}")
            null
        }
    }

    private fun encodePath(path: String): String =
        java.net.URLEncoder.encode(path, Charsets.UTF_8.name())

    private fun fetchCount(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GrindrPlus/terenzif (Android; goatcounter-stats)")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.w("GoatCounter counter HTTP ${response.code} for $url")
                return null
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return null
            val obj = JSONObject(body)
            // Prefer `count`; GoatCounter formats with thin spaces as thousands separators.
            return obj.optString("count")
                .ifBlank { obj.optString("count_unique") }
                .trim()
                .ifBlank { null }
        }
    }
}
