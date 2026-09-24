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
            // Direct URL: /counter/[PATH].json — leading slash → //home.json
            val home = fetchCount("$base/counter//home.json")
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

    private fun fetchCount(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GrindrPlus/terenzif (Android; goatcounter-stats)")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            // GoatCounter returns 404 + {"count":"0"} when a path has never been seen.
            if (!response.isSuccessful && response.code != 404) {
                Logger.w("GoatCounter counter HTTP ${response.code} for $url")
                return null
            }
            if (body.isBlank()) {
                return if (response.code == 404) "0" else null
            }
            return try {
                val obj = JSONObject(body)
                // Prefer `count`; GoatCounter formats with thin spaces as thousands separators.
                obj.optString("count")
                    .ifBlank { obj.optString("count_unique") }
                    .trim()
                    .ifBlank { if (response.code == 404) "0" else null }
            } catch (_: Exception) {
                if (response.code == 404) "0" else null
            }
        }
    }
}
