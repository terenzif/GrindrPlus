package com.gpp.utils

import com.gpp.core.AnonymousTelemetry
import com.gpp.core.Config
import com.gpp.core.HookCircuitBreaker
import com.gpp.core.Logger
import com.gpp.core.mapping.MappingDictionary
import com.gpp.core.mapping.MappingHookStatus
import com.gpp.debug.AgentDebugLog
import com.gpp.hooks.AllowScreenshots
import com.gpp.hooks.AntiBlock
import com.gpp.hooks.AntiDetection
import com.gpp.hooks.BanManagement
import com.gpp.hooks.ChatIndicators
import com.gpp.hooks.ChatTerminal
import com.gpp.hooks.DisableAnalytics
import com.gpp.hooks.DisableBoosting
import com.gpp.hooks.DisableShuffle
import com.gpp.hooks.DisableUpdates
import com.gpp.hooks.EmptyCalls
import com.gpp.hooks.EnableUnlimited
import com.gpp.hooks.ExpiringMedia
import com.gpp.hooks.Favorites
import com.gpp.hooks.FeatureGranting
import com.gpp.hooks.LocalSavedPhrases
import com.gpp.hooks.LocationSpoofer
import com.gpp.hooks.NotificationAlerts
import com.gpp.hooks.OnlineIndicator
import com.gpp.hooks.ProfileDetails
import com.gpp.hooks.ProfileViews
import com.gpp.hooks.QuickBlock
import com.gpp.hooks.StatusDialog
import com.gpp.hooks.TimberLogging
import com.gpp.hooks.UnlimitedAlbums
import com.gpp.hooks.UnlimitedProfiles
import com.gpp.hooks.UnlockExplorer
import com.gpp.hooks.WebSocketAlive
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.reflect.KClass

class HookManager {
    private val hooks = ConcurrentHashMap<KClass<out Hook>, Hook>()

    fun registerHooks(init: Boolean = true) {
        runBlocking(Dispatchers.IO) {
            val hookList = listOf(
                AllowScreenshots(),
                AntiBlock(),
                AntiDetection(),
                BanManagement(),
                ChatIndicators(),
                ChatTerminal(),
                DisableAnalytics(),
                DisableBoosting(),
                DisableShuffle(),
                DisableUpdates(),
                EmptyCalls(),
                EnableUnlimited(),
                ExpiringMedia(),
                Favorites(),
                FeatureGranting(),
                LocalSavedPhrases(),
                LocationSpoofer(),
                NotificationAlerts(),
                OnlineIndicator(),
                ProfileDetails(),
                ProfileViews(),
                QuickBlock(),
                StatusDialog(),
                TimberLogging(),
                UnlimitedAlbums(),
                UnlimitedProfiles(),
                UnlockExplorer(),
                WebSocketAlive(),
            )

            hookList.forEach { hook ->
                // WebSocketAlive is opt-in (battery); default off
                val defaultEnabled = hook.hookName != "Keep Alive WebSocket"
                Config.initHookSettings(
                    hook.hookName, hook.hookDesc, defaultEnabled
                )
            }

            if (!init) return@runBlocking

            hooks.clear()
            hooks.putAll(hookList.associateBy { it::class })

            var enabled = 0
            var partial = 0
            var skipped = 0
            var disabled = 0
            var failed = 0
            val failedNames = mutableListOf<String>()

            hooks.values.forEach { hook ->
                if (!Config.isHookEnabled(hook.hookName)) {
                    Config.setHookRuntimeStatus(hook.hookName, STATUS_DISABLED, null)
                    AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_DISABLED)
                    Logger.i("Hook ${hook.hookName} is disabled.")
                    disabled++
                    return@forEach
                }

                val packStatus = packHookStatus(hook)
                when (packStatus?.status?.lowercase()) {
                    "skipped" -> {
                        // Morphe B RUNTIME_REMAP hooks may still init despite pack skip
                        // when the pack reason is obsolete — prefer SoftSkip inside init.
                        if (!shouldAttemptDespitePackSkip(hook)) {
                            val reason = packStatus.reason ?: "skipped by mapping pack"
                            Config.setHookRuntimeStatus(hook.hookName, STATUS_SKIPPED, reason)
                            AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_SKIPPED)
                            Logger.w("Skipping hook ${hook.hookName}: $reason")
                            skipped++
                            return@forEach
                        }
                    }
                }

                if (HookCircuitBreaker.isOpen(hook.hookName)) {
                    Config.setHookRuntimeStatus(
                        hook.hookName,
                        STATUS_SKIPPED,
                        "circuit open after repeated failures"
                    )
                    AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_SKIPPED)
                    Logger.w("Skipping hook ${hook.hookName}: circuit open")
                    skipped++
                    return@forEach
                }

                try {
                    hook.init()
                    HookCircuitBreaker.recordSuccess(hook.hookName)
                    when (packStatus?.status?.lowercase()) {
                        "partial" -> {
                            Config.setHookRuntimeStatus(
                                hook.hookName,
                                STATUS_PARTIAL,
                                packStatus.reason ?: "partial mapping"
                            )
                            AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_PARTIAL)
                            Logger.s("Initialized hook (partial): ${hook.hookName}")
                            partial++
                        }
                        else -> {
                            Config.setHookRuntimeStatus(hook.hookName, STATUS_ENABLED, null)
                            AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_ENABLED)
                            Logger.s("Initialized hook: ${hook.hookName}")
                            enabled++
                        }
                    }
                } catch (skip: SoftSkipException) {
                    Config.setHookRuntimeStatus(
                        hook.hookName,
                        STATUS_SKIPPED,
                        skip.message ?: "soft-skipped"
                    )
                    AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_SKIPPED)
                    Logger.w("Hook ${hook.hookName} soft-skipped: ${skip.message}")
                    skipped++
                } catch (t: Throwable) {
                    HookCircuitBreaker.recordFailure(hook.hookName)
                    Config.setHookRuntimeStatus(
                        hook.hookName,
                        STATUS_FAILED,
                        t.message ?: t.javaClass.simpleName
                    )
                    AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_FAILED)
                    Logger.e("Failed to initialize hook ${hook.hookName}: ${t.message}")
                    Logger.writeThrowable(t)
                    failed++
                    failedNames.add(hook.hookName)
                }
            }

            // #region agent log
            AgentDebugLog.log(
                hypothesisId = "H4",
                location = "HookManager.registerHooks",
                message = "hook_summary",
                data = mapOf(
                    "enabled" to enabled,
                    "partial" to partial,
                    "skipped" to skipped,
                    "disabled" to disabled,
                    "failed" to failed,
                    "failedNames" to failedNames.joinToString(","),
                    "total" to hooks.size,
                ),
            )
            // #endregion
        }
    }

    fun reloadHooks() {
        runBlocking(Dispatchers.IO) {
            cleanupAll()
            registerHooks()
            Logger.s("Reloaded hooks")
        }
    }

    /** Stop hook-owned work without re-registering (API 102 hot-reload drain). */
    fun cleanupAll() {
        hooks.values.forEach { hook -> runCatching { hook.cleanup() } }
        hooks.clear()
    }

    fun init() {
        registerHooks()
    }

    private fun packHookStatus(hook: Hook): MappingHookStatus? {
        val pack = MappingDictionary.current ?: return null
        val byName = pack.hooks[hook.hookName]
        if (byName != null) return byName
        val simple = hook::class.simpleName ?: return null
        return pack.hooks[simple]
    }

    /**
     * Hooks that Morphe B remaps at runtime should still attempt [Hook.init]
     * even if an older pack marks them skipped.
     */
    private fun shouldAttemptDespitePackSkip(hook: Hook): Boolean {
        return hook.hookName in setOf(
            "Video calls",
            "Chat terminal",
            "Favorites",
            "Keep Alive WebSocket",
            "Status Dialog",
        )
    }

    companion object {
        const val STATUS_ENABLED = "enabled"
        const val STATUS_DISABLED = "disabled"
        const val STATUS_SKIPPED = "skipped"
        const val STATUS_PARTIAL = "partial"
        const val STATUS_FAILED = "failed"
    }
}
