package com.gpp.core

import com.gpp.utils.HookStage
import com.gpp.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.findClass
import java.util.concurrent.ConcurrentHashMap

class InstanceManager(private val classLoader: ClassLoader) {
    private val instances = ConcurrentHashMap<String, Any>()
    private val callbacks = ConcurrentHashMap<String, (Any) -> Unit>()

    /**
     * Hook constructors for the given classes. Missing / unloadable classes are logged and
     * skipped so a single stale mapping cannot abort module init before HookManager.
     *
     * @return names that were successfully hooked
     */
    fun hookClassConstructors(vararg classNames: String): List<String> {
        val hooked = mutableListOf<String>()
        classNames.forEach { className ->
            try {
                val clazz = findClass(className, classLoader)
                clazz.hookConstructor(HookStage.AFTER) { param ->
                    val instance = param.thisObject()
                    instances[className] = instance
                    callbacks[className]?.invoke(instance)
                }
                hooked.add(className)
            } catch (t: Throwable) {
                Logger.e(
                    "Skipping InstanceManager hook for missing class $className: ${t.message}",
                    LogSource.MODULE
                )
                Logger.writeThrowable(t)
            }
        }
        return hooked
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getInstance(className: String): T? {
        return instances[className] as? T
    }

    fun setCallback(className: String, callback: ((Any) -> Unit)?) {
        if (callback == null) {
            callbacks.remove(className)
        } else {
            callbacks[className] = callback
        }
        instances[className]?.let { callback?.invoke(it) }
    }
}
