package com.grindrplus.utils

import com.grindrplus.core.AnonymousTelemetry
import com.grindrplus.core.Config
import com.grindrplus.core.HookCircuitBreaker
import com.grindrplus.core.Logger
import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.core.mapping.MappingHookStatus
import com.grindrplus.hooks.AllowScreenshots
import com.grindrplus.hooks.AntiBlock
import com.grindrplus.hooks.AntiDetection
import com.grindrplus.hooks.BanManagement
import com.grindrplus.hooks.ChatIndicators
import com.grindrplus.hooks.ChatTerminal
import com.grindrplus.hooks.DisableAnalytics
import com.grindrplus.hooks.DisableBoosting
import com.grindrplus.hooks.DisableShuffle
import com.grindrplus.hooks.DisableUpdates
import com.grindrplus.hooks.EmptyCalls
import com.grindrplus.hooks.EnableUnlimited
import com.grindrplus.hooks.ExpiringMedia
import com.grindrplus.hooks.Favorites
import com.grindrplus.hooks.FeatureGranting
import com.grindrplus.hooks.LocalSavedPhrases
import com.grindrplus.hooks.LocationSpoofer
import com.grindrplus.hooks.NotificationAlerts
import com.grindrplus.hooks.OnlineIndicator
import com.grindrplus.hooks.ProfileDetails
import com.grindrplus.hooks.ProfileViews
import com.grindrplus.hooks.QuickBlock
import com.grindrplus.hooks.StatusDialog
import com.grindrplus.hooks.TimberLogging
import com.grindrplus.hooks.UnlimitedAlbums
import com.grindrplus.hooks.UnlimitedProfiles
import com.grindrplus.hooks.UnlockExplorer
import com.grindrplus.hooks.WebSocketAlive
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

            hooks.values.forEach { hook ->
                if (!Config.isHookEnabled(hook.hookName)) {
                    Config.setHookRuntimeStatus(hook.hookName, STATUS_DISABLED, null)
                    AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_DISABLED)
                    Logger.i("Hook ${hook.hookName} is disabled.")
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
                        }
                        else -> {
                            Config.setHookRuntimeStatus(hook.hookName, STATUS_ENABLED, null)
                            AnonymousTelemetry.recordHookStatus(hook.hookName, STATUS_ENABLED)
                            Logger.s("Initialized hook: ${hook.hookName}")
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
                }
            }
        }
    }

    fun reloadHooks() {
        runBlocking(Dispatchers.IO) {
            hooks.values.forEach { hook -> hook.cleanup() }
            hooks.clear()
            registerHooks()
            Logger.s("Reloaded hooks")
        }
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
