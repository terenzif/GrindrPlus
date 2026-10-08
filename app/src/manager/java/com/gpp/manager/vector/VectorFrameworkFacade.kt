package com.gpp.manager.vector

import android.content.Context
import com.gpp.core.DeliveryChannel
import com.gpp.core.LogSource
import com.gpp.core.Logger

/**
 * Reflection façade so Morphe Manager compiles without alloy-only libxposed/service.
 */
object VectorFrameworkFacade {
    private const val BRIDGE = "com.gpp.manager.vector.VectorFrameworkBridge"

    fun start(context: Context) {
        if (!DeliveryChannel.current.isRootedModule) return
        runCatching {
            Class.forName(BRIDGE)
                .getMethod("start", Context::class.java)
                .invoke(null, context)
        }.onFailure {
            Logger.w("VectorFrameworkBridge.start: ${it.message}", LogSource.MANAGER)
        }
    }

    fun statusLine(): String {
        if (!DeliveryChannel.current.isRootedModule) return ""
        return runCatching {
            Class.forName(BRIDGE).getDeclaredField("statusLine").get(null) as String
        }.getOrDefault("Vector: unavailable")
    }

    fun isConnected(): Boolean {
        if (!DeliveryChannel.current.isRootedModule) return false
        return runCatching {
            Class.forName(BRIDGE).getMethod("isConnected").invoke(null) as Boolean
        }.getOrDefault(false)
    }

    fun requestCodeHotReload(onResult: (Boolean, String) -> Unit) {
        if (!DeliveryChannel.current.isRootedModule) {
            onResult(false, "Alloy only")
            return
        }
        runCatching {
            val bridge = Class.forName(BRIDGE)
            val callbackClass = Class.forName("kotlin.jvm.functions.Function2")
            // Use method that takes Function2 via Kotlin - invoke through MethodHandles is messy.
            // Direct: getMethod("requestCodeHotReload", Function2) — Kotlin generates that.
            val method = bridge.methods.first { it.name == "requestCodeHotReload" }
            method.invoke(null, onResult)
        }.onFailure {
            onResult(false, it.message ?: "hot-reload unavailable")
        }
    }

    fun putRemoteBoolean(key: String, value: Boolean): Boolean {
        if (!DeliveryChannel.current.isRootedModule) return false
        return runCatching {
            Class.forName(BRIDGE)
                .getMethod("putRemoteBoolean", String::class.java, Boolean::class.javaPrimitiveType)
                .invoke(null, key, value) as Boolean
        }.getOrDefault(false)
    }
}
