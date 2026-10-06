package com.grindrplus.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Opt-in anonymous telemetry (hook/pack/error counters only).
 *
 * Never records profile IDs, chat, location, tokens, or stable device IDs.
 * Flushes to [endpointUrl] when set (JSON array POST). Falls back to local queue only.
 */
object AnonymousTelemetry {
    private val queue = ConcurrentLinkedQueue<JSONObject>()
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    var endpointUrl: String? = null

    fun isEnabled(): Boolean = Config.get("analytics", true) as? Boolean == true

    fun record(event: String, props: Map<String, Any?> = emptyMap()) {
        if (!isEnabled()) return
        val payload = JSONObject()
        payload.put("event", event)
        payload.put("ts", System.currentTimeMillis())
        payload.put("delivery", DeliveryChannel.current.name.lowercase())
        val data = JSONObject()
        props.forEach { (k, v) ->
            if (v != null) data.put(k, v)
        }
        payload.put("props", data)
        queue.offer(payload)
        while (queue.size > 200) {
            queue.poll()
        }
        Logger.d("telemetry queued: $event", LogSource.MODULE)
    }

    fun recordHookStatus(hookName: String, status: String) {
        record("hook_status", mapOf("hook" to hookName, "status" to status))
    }

    fun recordPackLoad(versionCode: Int, source: String, ok: Boolean) {
        record(
            "pack_load",
            mapOf("versionCode" to versionCode, "source" to source, "ok" to ok)
        )
    }

    /**
     * POST queued events as a JSON array to [endpointUrl].
     * No-op when analytics off or endpoint blank. Failures keep events in queue.
     */
    fun flush() {
        if (!isEnabled()) {
            Logger.d("telemetry flush skipped (disabled)", LogSource.MODULE)
            return
        }
        val endpoint = endpointUrl?.takeIf { it.isNotBlank() }
            ?: (Config.get("analytics_endpoint", "") as? String)?.takeIf { it.isNotBlank() }
        if (endpoint.isNullOrBlank()) {
            Logger.d("telemetry flush skipped (no endpoint)", LogSource.MODULE)
            return
        }
        thread(name = "AnonymousTelemetryFlush", isDaemon = true) {
            flushSync(endpoint)
        }
    }

    internal fun flushSync(endpoint: String): Boolean {
        val batch = mutableListOf<JSONObject>()
        while (batch.size < 50) {
            val item = queue.poll() ?: break
            batch += item
        }
        if (batch.isEmpty()) return true
        return try {
            val body = JSONArray(batch).toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", "GrindrPlus-Telemetry/1")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    batch.asReversed().forEach { queue.offer(it) }
                    Logger.w("telemetry flush HTTP ${resp.code}", LogSource.MODULE)
                    false
                } else {
                    Logger.i("telemetry flush ok: ${batch.size} events", LogSource.MODULE)
                    true
                }
            }
        } catch (t: Throwable) {
            batch.asReversed().forEach { queue.offer(it) }
            Logger.w("telemetry flush failed: ${t.message}", LogSource.MODULE)
            false
        }
    }

    fun pendingCount(): Int = queue.size
}
