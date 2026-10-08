package com.gpp.manager.vector

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.core.LogSource
import com.gpp.core.Logger
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/**
 * Alloy Manager ↔ Vector via libxposed/service (API 102).
 * Complements [VectorModToggle] (enable/disable); does not replace it.
 */
object VectorFrameworkBridge {
    const val PREFS_GROUP = "grindmod_alloy"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceRef = AtomicReference<XposedService?>(null)
    private val listeners = CopyOnWriteArrayList<(XposedService?) -> Unit>()
    private var registered = false

    @Volatile
    var statusLine: String = "Vector: not connected"
        private set

    fun start(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (registered) return
        registered = true
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                serviceRef.set(service)
                statusLine = try {
                    val remote = service.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L
                    "Vector: ${service.frameworkName} ${service.frameworkVersion} " +
                        "(API ${service.apiVersion}, remotePrefs=$remote)"
                } catch (t: Throwable) {
                    "Vector: bound (${t.message})"
                }
                Logger.i(statusLine, LogSource.MANAGER)
                notifyListeners(service)
            }

            override fun onServiceDied(service: XposedService) {
                if (serviceRef.get() === service) {
                    serviceRef.set(null)
                    statusLine = "Vector: disconnected"
                    notifyListeners(null)
                }
            }
        })
    }

    fun service(): XposedService? = serviceRef.get()

    fun isConnected(): Boolean = serviceRef.get() != null

    fun addListener(listener: (XposedService?) -> Unit) {
        listeners.add(listener)
        listener(serviceRef.get())
    }

    fun remotePreferences(): SharedPreferences? =
        try {
            serviceRef.get()?.getRemotePreferences(PREFS_GROUP)
        } catch (t: Throwable) {
            Logger.w("remotePreferences: ${t.message}", LogSource.MANAGER)
            null
        }

    fun putRemoteBoolean(key: String, value: Boolean): Boolean {
        val prefs = remotePreferences() ?: return false
        return try {
            prefs.edit().putBoolean(key, value).apply()
            true
        } catch (t: Throwable) {
            Logger.w("putRemoteBoolean($key): ${t.message}", LogSource.MANAGER)
            false
        }
    }

    fun runningGrindrTargets(): List<HookedTarget> =
        try {
            serviceRef.get()?.runningTargets
                ?.filter { it.processName.contains("grindr", ignoreCase = true) }
                ?: emptyList()
        } catch (t: Throwable) {
            Logger.w("getRunningTargets: ${t.message}", LogSource.MANAGER)
            emptyList()
        }

    /**
     * Request code hot-reload for Grindr processes. Not for Settings toggles / Modding active.
     */
    fun requestCodeHotReload(
        onResult: (ok: Boolean, message: String) -> Unit,
    ) {
        val svc = serviceRef.get()
        if (svc == null) {
            onResult(false, "Vector service not connected")
            return
        }
        val targets = runningGrindrTargets()
        if (targets.isEmpty()) {
            onResult(false, "No running Grindr target with module loaded")
            return
        }
        var remaining = targets.size
        val messages = mutableListOf<String>()
        for (target in targets) {
            try {
                svc.hotReloadModule(
                    target,
                    Bundle().apply { putString("reason", "grindmod_manager") },
                    object : XposedService.HotReloadCallback {
                        override fun onHotReloadResult(t: HookedTarget, result: HotReloadResult) {
                            messages += "${t.processName}: ${result.status}" +
                                (result.message?.let { " ($it)" } ?: "")
                            if (--remaining <= 0) {
                                val ok = messages.any {
                                    it.contains("SUCCEEDED", ignoreCase = true)
                                }
                                mainHandler.post {
                                    onResult(ok, messages.joinToString("; "))
                                }
                            }
                        }
                    },
                )
            } catch (t: Throwable) {
                messages += "${target.processName}: ${t.message}"
                if (--remaining <= 0) {
                    mainHandler.post { onResult(false, messages.joinToString("; ")) }
                }
            }
        }
    }

    fun grindrScoped(): Boolean =
        try {
            serviceRef.get()?.scope?.any { it.contains(GRINDR_PACKAGE_NAME) } == true
        } catch (_: Throwable) {
            false
        }

    private fun notifyListeners(service: XposedService?) {
        mainHandler.post {
            listeners.forEach { it(service) }
        }
    }
}
