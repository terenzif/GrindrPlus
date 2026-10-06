package com.gpp.bridge

import com.gpp.core.LogSource
import com.gpp.core.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process-wide bridge handle shared by Manager (main) and module runtime.
 * Manager must not depend on [com.gpp.GrindrPlus] (module source set).
 */
object BridgeAccess {
    @Volatile
    var client: BridgeClient? = null

    fun requireClient(): BridgeClient =
        client ?: error("BridgeClient not initialized")

    private val asyncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun executeAsync(block: suspend () -> Unit) {
        asyncScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Logger.e("Async bridge task failed: ${e.message}", LogSource.MANAGER)
                Logger.writeThrowable(e)
            }
        }
    }
}
