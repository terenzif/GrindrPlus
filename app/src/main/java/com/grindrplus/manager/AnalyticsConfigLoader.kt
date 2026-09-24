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
 * GoatCounter: set `site` to `https://YOURCODE.goatcounter.com` (no API token in the APK).
 */
data class AnalyticsRemoteConfig(
    val enabled: Boolean,
    /** GoatCounter site origin, e.g. `https://mycode.goatcounter.com` */
    val site: String,
    val publicUrl: String,
    val docsUrl: String,
    val provider: String,
) {
    val isReady: Boolean
        get() = enabled &&
            provider.equals("goatcounter", ignoreCase = true) &&
            site.startsWith("https://") &&
            site.contains("goatcounter", ignoreCase = true)
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
                val site = obj.optString("site", "")
                    .ifBlank { obj.optString("host", "") } // legacy alias
                    .trim()
                    .trimEnd('/')
                AnalyticsRemoteConfig(
                    enabled = obj.optBoolean("enabled", false),
                    site = site,
                    publicUrl = obj.optString("public_url", site).trim()
                        .ifBlank { site },
                    docsUrl = obj.optString("docs", Constants.ANALYTICS_DOCS_URL).trim()
                        .ifBlank { Constants.ANALYTICS_DOCS_URL },
                    provider = obj.optString("provider", "goatcounter").trim()
                        .ifBlank { "goatcounter" },
                )
            }
        } catch (e: Exception) {
            Logger.w("analytics.json fetch failed: ${e.message} — telemetry off")
            disabled()
        }
    }

    fun disabled() = AnalyticsRemoteConfig(
        enabled = false,
        site = "",
        publicUrl = "",
        docsUrl = Constants.ANALYTICS_DOCS_URL,
        provider = "goatcounter",
    )
}
