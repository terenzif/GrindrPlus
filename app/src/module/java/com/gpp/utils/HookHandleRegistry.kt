package com.gpp.utils

import io.github.libxposed.api.XposedInterface
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks hook handles by stable id for hot-reload replace / unhook.
 */
object HookHandleRegistry {
    private val byId = ConcurrentHashMap<String, XposedInterface.HookHandle>()

    fun put(id: String?, handle: XposedInterface.HookHandle) {
        if (id.isNullOrEmpty()) return
        byId[id] = handle
    }

    fun get(id: String): XposedInterface.HookHandle? = byId[id]

    fun remove(id: String?) {
        if (id.isNullOrEmpty()) return
        byId.remove(id)
    }

    fun clear() {
        byId.clear()
    }

    fun all(): Collection<XposedInterface.HookHandle> = byId.values.toList()

    fun replaceAll(oldHandles: List<XposedInterface.HookHandle>) {
        // Default hot-reload path: drop old generation hooks; new install re-registers.
        oldHandles.forEach { runCatching { it.unhook() } }
        clear()
    }
}

fun interface Unhook {
    fun unhook()
}
