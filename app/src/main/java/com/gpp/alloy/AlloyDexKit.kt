package com.gpp.alloy

import android.content.Context
import com.gpp.GrindrPlus
import com.gpp.core.DeliveryChannel
import com.gpp.core.Logger
import com.gpp.core.LogSource
import org.luckypray.dexkit.DexKitBridge
import java.io.Closeable

/**
 * NexAlloy-style DexKit façade for the Alloy (rooted) channel — ADR 0006.
 * Soft no-op on Morphe/embed builds where dexkit may be compileOnly-only.
 */
object AlloyDexKit : Closeable {
    @Volatile
    private var bridge: DexKitBridge? = null

    val isAvailable: Boolean
        get() = DeliveryChannel.current.isRootedModule && bridge != null

    fun ensureInitialized(context: Context): Boolean {
        if (!DeliveryChannel.current.isRootedModule) return false
        if (bridge != null) return true
        return try {
            System.loadLibrary("dexkit")
            val apkPath = context.applicationInfo.sourceDir
            bridge = DexKitBridge.create(apkPath)
            Logger.i("AlloyDexKit initialized from $apkPath", LogSource.MODULE)
            true
        } catch (t: Throwable) {
            Logger.w("AlloyDexKit unavailable: ${t.message}", LogSource.MODULE)
            bridge = null
            false
        }
    }

    /**
     * Find a single class by required string constants (soft-fail → null).
     */
    fun findClassByStrings(vararg strings: String): Class<*>? {
        val kit = bridge ?: return null
        if (strings.isEmpty()) return null
        return try {
            val hostLoader = try {
                GrindrPlus.context.classLoader
            } catch (_: Throwable) {
                javaClass.classLoader
            }
            val results = kit.findClass {
                matcher {
                    usingStrings = strings.toList()
                }
            }
            val data = results.singleOrNull() ?: results.firstOrNull() ?: return null
            data.getInstance(hostLoader)
        } catch (t: Throwable) {
            Logger.w("AlloyDexKit findClassByStrings failed: ${t.message}", LogSource.MODULE)
            null
        }
    }

    override fun close() {
        try {
            bridge?.close()
        } catch (_: Throwable) {
        }
        bridge = null
    }
}
