package com.grindrplus.hooks

import com.grindrplus.core.Config
import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import kotlin.time.Duration.Companion.minutes

// supported version: 26.16.1
class OnlineIndicator : Hook(
    "Online indicator",
    "Customize online indicator duration"
) {
    override fun init() {
        val savedDurationMinutes = Config.get("online_indicator", 3).toString().toInt()
        val savedDurationMillis = savedDurationMinutes.minutes.inWholeMilliseconds

        val utils = MappingDictionary.resolve("OnlineIndicator.utils", "aq7")
        val shouldShowMethod =
            MappingDictionary.resolve("OnlineIndicator.utils.shouldShowMethod", "u")
        if (utils.isEmpty() || shouldShowMethod.isEmpty()) {
            logi("OnlineIndicator: utils soft-skip (empty remap)")
        } else {
            runCatching {
                findClass(utils) // shouldShowOnlineIndicator() — was Vm.m0.a, now aq7.u
                    .hook(shouldShowMethod, HookStage.BEFORE) { param ->
                        val lastSeen = param.arg<Long>(0)
                        param.setResult(System.currentTimeMillis() - lastSeen <= savedDurationMillis)
                    }
            }.onFailure { loge("OnlineIndicator utils: ${it.message}") }
        }

        // Share FeatureGranting.isFeatureFlagEnabled (+ invokeMethod)
        val isFeatureFlagEnabled =
            MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled", "iv6")
        val invokeMethod =
            MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled.invokeMethod", "a")
        if (isFeatureFlagEnabled.isEmpty() || invokeMethod.isEmpty()) {
            logi("OnlineIndicator: isFeatureFlagEnabled soft-skip (empty remap)")
        } else {
            runCatching {
                findClass(isFeatureFlagEnabled)
                    .hook(invokeMethod, HookStage.BEFORE) { param ->
                        val a = param.args()[0]
                        val flagKey = a!!.javaClass.getMethod("getKey").invoke(a)

                        if (flagKey == "online-until-updates")
                            param.setResult(false)
                    }
            }.onFailure { loge("OnlineIndicator feature flag: ${it.message}") }
        }

        val thresholdMethod =
            MappingDictionary.resolve("OnlineIndicator.profileUtilsV2.thresholdMethod", "b")
        if (thresholdMethod.isEmpty()) {
            logi("OnlineIndicator: thresholdMethod soft-skip (empty remap)")
        } else {
            runCatching {
                findClass("com.grindrapp.android.utils.ProfileUtilsV2")
                    .hook(thresholdMethod, HookStage.BEFORE) { param ->
                        if (param.args().size > 1) {
                            val onlineUntilThreshold = param.arg<Long>(1)
                            if (onlineUntilThreshold == 600000L)
                                param.setArg(1, savedDurationMillis)
                        }
                    }
            }.onFailure { loge("OnlineIndicator ProfileUtilsV2: ${it.message}") }
        }
    }
}
