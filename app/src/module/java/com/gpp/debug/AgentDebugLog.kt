package com.gpp.debug

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Session 9ee712 debug sink: Logcat tag + HTTP ingest (via adb reverse).
 * Do not log secrets / PII.
 */
object AgentDebugLog {
    private const val TAG = "DBG9EE712"
    private const val SESSION = "9ee712"
    private const val ENDPOINT =
        "http://127.0.0.1:7460/ingest/2a97dbce-7fc4-48c2-ab29-50c355ebd391"
    private val executor = Executors.newSingleThreadExecutor()

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
        val payload = JSONObject()
            .put("sessionId", SESSION)
            .put("runId", runId)
            .put("hypothesisId", hypothesisId)
            .put("location", location)
            .put("message", message)
            .put("timestamp", System.currentTimeMillis())
            .put(
                "data",
                JSONObject().also { obj ->
                    data.forEach { (k, v) -> obj.put(k, v ?: JSONObject.NULL) }
                },
            )
        Log.i(TAG, payload.toString())
        executor.execute {
            runCatching {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 1500
                    readTimeout = 1500
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", SESSION)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                conn.responseCode
                conn.disconnect()
            }
        }
    }
}
