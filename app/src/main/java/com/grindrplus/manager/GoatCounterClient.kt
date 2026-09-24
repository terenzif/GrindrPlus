package com.grindrplus.manager

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import com.grindrplus.BuildConfig
import com.grindrplus.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GoatCounter collector using the public `/count` pixel endpoint (no API token in the APK).
 * Site base example: `https://YOURCODE.goatcounter.com`
 */
class GoatCounterClient(
    private val siteBase: String,
    private val context: Context? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val countUrl: String =
        siteBase.trimEnd('/') + "/count"

    private val userAgent: String =
        "GrindrPlus/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.SDK_INT}; goatcounter)"

    fun pageView(path: String, title: String? = null, props: Map<String, Any?> = emptyMap()) {
        val normalized = normalizePath(path)
        val t = title ?: normalized
        val q = propsToQuery(props)
        enqueue(path = normalized, title = t, event = false, query = q)
    }

    fun event(name: String, props: Map<String, Any?> = emptyMap()) {
        // GoatCounter: event names must not start with '/'
        val eventName = name.trim().removePrefix("/").ifBlank { "event" }
        enqueue(
            path = eventName,
            title = eventName,
            event = true,
            query = propsToQuery(props),
        )
    }

    private fun enqueue(
        path: String,
        title: String,
        event: Boolean,
        query: String?,
    ) {
        scope.launch {
            try {
                val url = countUrl.toHttpUrlOrNull()?.newBuilder() ?: return@launch
                url.addQueryParameter("p", path)
                url.addQueryParameter("t", title)
                if (event) url.addQueryParameter("e", "true")
                if (!query.isNullOrBlank()) url.addQueryParameter("q", query)
                screenSizeParam()?.let { url.addQueryParameter("s", it) }
                url.addQueryParameter("rnd", System.currentTimeMillis().toString())

                val request = Request.Builder()
                    .url(url.build())
                    .header("User-Agent", userAgent)
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Logger.w("GoatCounter HTTP ${response.code} for $path")
                    }
                }
            } catch (e: Exception) {
                Logger.w("GoatCounter send failed: ${e.message}")
            }
        }
    }

    private fun normalizePath(raw: String): String {
        var p = raw.trim()
        // Convert old Plausible-style URLs to GoatCounter paths
        p = p.removePrefix("app://grindrplus")
        if (!p.startsWith("/")) p = "/$p"
        if (p == "/") p = "/home"
        return p
    }

    private fun propsToQuery(props: Map<String, Any?>): String? {
        if (props.isEmpty()) return null
        return props.entries
            .filter { it.value != null }
            .joinToString("&") { (k, v) ->
                "${k}=${v.toString().take(80)}"
            }
            .ifBlank { null }
    }

    private fun screenSizeParam(): String? {
        val ctx = context ?: return null
        return try {
            val dm: DisplayMetrics = ctx.resources.displayMetrics
            "${dm.widthPixels},${dm.heightPixels},${dm.density.toInt().coerceAtLeast(1)}"
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Process-wide analytics facade (replaces Plausible companion).
 */
object ForkAnalytics {
    @Volatile
    var client: GoatCounterClient? = null

    fun pageView(path: String, title: String? = null, props: Map<String, Any?> = emptyMap()) {
        client?.pageView(path, title, props)
    }

    fun event(name: String, props: Map<String, Any?> = emptyMap()) {
        client?.event(name, props)
    }
}
