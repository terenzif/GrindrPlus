package com.gpp

import android.content.SharedPreferences
import android.util.Log
import com.gpp.core.LogSource
import com.gpp.core.Logger
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Method

/**
 * Process-wide handle to the active [XposedModule] generation (API 102).
 */
object GppXposed {
    @Volatile
    var module: XposedModule? = null
        private set

    fun attach(module: XposedModule) {
        this.module = module
    }

    fun require(): XposedModule =
        module ?: error("GppXposedModule not attached — hooks before onModuleLoaded?")

    fun logFrameworkCaps() {
        val m = module ?: return
        try {
            val props = m.frameworkProperties
            val remote = props and XposedInterface.PROP_CAP_REMOTE != 0L
            Logger.i(
                "Vector/framework=${m.frameworkName} ${m.frameworkVersion} " +
                    "(code=${m.frameworkVersionCode}) api=${m.apiVersion} remotePrefs=$remote",
                LogSource.MODULE,
            )
            m.log(Log.INFO, "GrindMod", "API ${m.apiVersion} caps remote=$remote")
        } catch (t: Throwable) {
            Logger.w("Framework caps probe failed: ${t.message}", LogSource.MODULE)
        }
    }

    fun deoptimize(executable: Executable): Boolean =
        try {
            require().deoptimize(executable)
        } catch (t: Throwable) {
            Logger.w("deoptimize failed: ${t.message}", LogSource.MODULE)
            false
        }

    fun remotePreferences(group: String): SharedPreferences? =
        try {
            require().getRemotePreferences(group)
        } catch (_: UnsupportedOperationException) {
            null
        } catch (t: Throwable) {
            Logger.w("getRemotePreferences($group): ${t.message}", LogSource.MODULE)
            null
        }

    /**
     * Invoke via framework invoker. [mode]: `origin` | `hooked` | `special`.
     * Mapping packs may set member remap `"invoke": "special|direct|hooked"` later.
     */
    fun invoke(
        method: Method,
        mode: String,
        thisObject: Any?,
        args: Array<Any?>,
    ): Any? {
        val invoker = require().getInvoker(method)
        when (mode.lowercase()) {
            "origin", "direct" -> invoker.setType(XposedInterface.Invoker.Type.ORIGIN)
            "hooked", "full" -> invoker.setType(XposedInterface.Invoker.Type.Chain.FULL)
            "special" -> {
                requireNotNull(thisObject) { "invokeSpecial needs thisObject" }
                return invoker.invokeSpecial(thisObject, *args)
            }
            else -> invoker.setType(XposedInterface.Invoker.Type.Chain.FULL)
        }
        return invoker.invoke(thisObject, *args)
    }

    fun <T> newInstance(constructor: Constructor<T>, vararg args: Any?): T {
        return require().getInvoker(constructor).newInstance(*args)
    }
}
