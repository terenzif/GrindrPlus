package com.grindrplus.core

import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.findClass

class InstanceManager(private val classLoader: ClassLoader) {
    private val instances = mutableMapOf<String, Any>()
    private val callbacks = mutableMapOf<String, ((Any) -> Unit)?>()

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
                Logger.writeRaw(t.stackTraceToString())
            }
        }
        return hooked
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getInstance(className: String): T? {
        return instances[className] as? T
    }

    fun setCallback(className: String, callback: ((Any) -> Unit)?) {
        callbacks[className] = callback
        instances[className]?.let { callback?.invoke(it) }
    }
}
