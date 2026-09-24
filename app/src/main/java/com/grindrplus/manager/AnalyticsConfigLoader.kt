package com.grindrplus.manager

import com.grindrplus.core.Constants
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Remote analytics control plane — [Constants.ANALYTICS_CONFIG_ENDPOINT] (`analytics.json` on GitHub).
 * No hard-coded third-party host: if the file disables telemetry or omits host/domain, nothing is sent.
 */
data class AnalyticsRemoteConfig(
    val enabled: Boolean,
    val host: String,
    val domain: String,
    val docsUrl: String,
) {
    val isReady: Boolean
        get() = enabled && host.isNotBlank() && domain.isNotBlank()
}

object AnalyticsConfigLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(
        url: String = Constants.ANALYTICS_CONFIG_ENDPOINT,
    ): AnalyticsRemoteConfig = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "GrindrPlus/terenzif (Android; analytics-config)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logger.w("analytics.json HTTP ${response.code} — telemetry off")
                    return@withContext disabled()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext disabled()
                val obj = JSONObject(body)
                AnalyticsRemoteConfig(
                    enabled = obj.optBoolean("enabled", false),
                    host = obj.optString("host", "").trim(),
                    domain = obj.optString("domain", "").trim(),
                    docsUrl = obj.optString("docs", Constants.ANALYTICS_DOCS_URL).trim()
                        .ifBlank { Constants.ANALYTICS_DOCS_URL },
                )
            }
        } catch (e: Exception) {
            Logger.w("analytics.json fetch failed: ${e.message} — telemetry off")
            disabled()
        }
    }

    fun disabled() = AnalyticsRemoteConfig(
        enabled = false,
        host = "",
        domain = "",
        docsUrl = Constants.ANALYTICS_DOCS_URL,
    )
}
