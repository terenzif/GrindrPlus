package com.gpp.hooks

import com.gpp.core.mapping.MappingDictionary
import com.gpp.core.logd
import com.gpp.core.loge
import com.gpp.core.logi
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.SoftSkipException
import com.gpp.utils.hook
import android.os.Handler
import android.os.Looper

/**
 * Prevents WebSocket disconnections when the app backgrounds.
 * Causes battery drain — use with caution.
 *
 * 26.16.1 remap: client `com.grindrapp.android.network.websocket.a`, factory `jcd`.
 * SafeDK `internal.b` is absent on this build (soft-skipped).
 */
class WebSocketAlive : Hook(
    "Keep Alive WebSocket",
    "Prevents WebSocket disconnections when app goes to background. Causes battery drain, use with caution."
) {
    private val webSocketClientImpl = MappingDictionary.resolve(
        "WebSocketAlive.CLIENT",
        "com.grindrapp.android.network.websocket.a"
    )
    private val webSocketFactory = MappingDictionary.resolve(
        "WebSocketAlive.FACTORY",
        "jcd"
    )

    override fun init() {
        var ok = 0
        ok += hookWebSocketLifecycle()
        ok += hookWebSocketFactory()
        if (ok == 0) {
            throw SoftSkipException("Keep Alive WebSocket: client/factory classes not found")
        }
        logi("Keep Alive WebSocket: applied $ok hook group(s)")
    }

    private fun hookWebSocketLifecycle(): Int {
        return try {
            val clazz = findClass(webSocketClientImpl)
            // Historic name + current short name for close
            listOf("disconnect", "a", "d").forEach { method ->
                try {
                    clazz.hook(method, HookStage.BEFORE) { param ->
                        if (isBackgroundTriggeredDisconnect()) {
                            logd("Preventing background-triggered WebSocket $method")
                            param.setResult(null)
                        }
                    }
                } catch (_: Throwable) {
                }
            }
            try {
                clazz.hook("onClosed", HookStage.AFTER) { param ->
                    if (param.args().size >= 3) {
                        val code = param.arg<Int>(1)
                        val reason = param.arg<String>(2)
                        if (shouldAutoReconnect(code, reason)) {
                            scheduleReconnection(param.thisObject(), 2000)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            try {
                clazz.hook("onFailure", HookStage.AFTER) { param ->
                    if (param.args().size >= 2) {
                        val throwable = param.arg<Throwable>(1)
                        val message = throwable.message?.lowercase() ?: ""
                        if (isNetworkRelatedFailure(message)) {
                            scheduleReconnection(param.thisObject(), 5000)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
            logi("Hooked WebSocket client $webSocketClientImpl")
            1
        } catch (e: Exception) {
            loge("Failed to hook WebSocket lifecycle: $e")
            0
        }
    }

    private fun hookWebSocketFactory(): Int {
        return try {
            findClass(webSocketFactory).hook("a", HookStage.AFTER) { param ->
                if (param.args().isNotEmpty()) {
                    logd("WebSocket connection created")
                }
            }
            1
        } catch (e: Exception) {
            loge("Failed to hook WebSocket factory: $e")
            0
        }
    }

    private fun isBackgroundTriggeredDisconnect(): Boolean {
        val stackTrace = Thread.currentThread().stackTrace
        return stackTrace.any {
            it.methodName.contains("background", ignoreCase = true) ||
                it.methodName.contains("pause", ignoreCase = true) ||
                it.methodName.contains("onStop", ignoreCase = true)
        }
    }

    private fun shouldAutoReconnect(code: Int, reason: String?): Boolean {
        val r = reason?.lowercase() ?: ""
        return code == 1001 || r.contains("background") || r.contains("going away")
    }

    private fun isNetworkRelatedFailure(message: String): Boolean =
        message.contains("network") ||
            message.contains("socket") ||
            message.contains("timeout") ||
            message.contains("unreachable")

    private fun scheduleReconnection(client: Any, delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val connect = client.javaClass.methods.firstOrNull {
                    it.name == "connect" || it.name == "b" || it.name == "c"
                }
                connect?.takeIf { it.parameterTypes.isEmpty() }?.invoke(client)
            } catch (e: Exception) {
                logd("WebSocket reconnect attempt failed: ${e.message}")
            }
        }, delayMs)
    }
}
