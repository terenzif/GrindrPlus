package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import kotlin.time.Duration.Companion.minutes

// supported version: 26.16.1
class OnlineIndicator : Hook(
    "Online indicator",
    "Customize online indicator duration"
) {
    val utils = "aq7" // search for '<= 600000' / shouldShowOnlineIndicator
    val isFeatureFlagEnabled = "iv6" // implements IsFeatureFlagEnabled

    override fun init() {
        val savedDurationMinutes = Config.get("online_indicator", 3).toString().toInt()
        val savedDurationMillis = savedDurationMinutes.minutes.inWholeMilliseconds

        runCatching {
            findClass(utils) // shouldShowOnlineIndicator() — was Vm.m0.a, now aq7.u
                .hook("u", HookStage.BEFORE) { param ->
                    val lastSeen = param.arg<Long>(0)
                    param.setResult(System.currentTimeMillis() - lastSeen <= savedDurationMillis)
                }
        }.onFailure { loge("OnlineIndicator utils: ${it.message}") }

        runCatching {
            findClass(isFeatureFlagEnabled)
                .hook("a", HookStage.BEFORE) { param ->
                    val a = param.args()[0]
                    val flagKey = a!!.javaClass.getMethod("getKey").invoke(a)

                    if (flagKey == "online-until-updates")
                        param.setResult(false)
                }
        }.onFailure { loge("OnlineIndicator feature flag: ${it.message}") }

        runCatching {
            findClass("com.grindrapp.android.utils.ProfileUtilsV2")
                .hook("b", HookStage.BEFORE) { param ->
                    if (param.args().size > 1) {
                        val onlineUntilThreshold = param.arg<Long>(1)
                        if (onlineUntilThreshold == 600000L)
                            param.setArg(1, savedDurationMillis)
                    }
                }
        }.onFailure { loge("OnlineIndicator ProfileUtilsV2: ${it.message}") }
    }
}
