package com.grindrplus.hooks

import com.grindrplus.core.loge
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook

// supported version: 26.16.1
class DisableAnalytics : Hook(
    "Disable analytics",
    "Disable Grindr analytics (data collection)"
) {
    override fun init() {
        // Braze
        runCatching {
            findClass("com.braze.Braze\$Companion")
                .hook("setOutboundNetworkRequestsOffline", HookStage.BEFORE) { param ->
                    param.setArg(0, true)
                }
        }.onFailure { loge("DisableAnalytics Braze: ${it.message}") }

        // Digital Turbine
        runCatching {
            findClass("com.fyber.inneractive.sdk.network.i")
                .hook("a", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
        }.onFailure { loge("DisableAnalytics Fyber: ${it.message}") }

        // Google Analytics
        runCatching {
            findClass("com.google.firebase.analytics.FirebaseAnalytics")
                .hook("setAnalyticsCollectionEnabled", HookStage.BEFORE) { param ->
                    param.setArg(0, false)
                }
        }.onFailure { loge("DisableAnalytics FirebaseAnalytics: ${it.message}") }

        // Google Crashlytics
        runCatching {
            findClass("com.google.firebase.crashlytics.FirebaseCrashlytics")
                .hook("setCrashlyticsCollectionEnabled", HookStage.BEFORE) { param ->
                    param.setArg(0, false)
                }
        }.onFailure { loge("DisableAnalytics Crashlytics: ${it.message}") }

        // Ironsource mediation ServerURL removed from 26.16.1 DEX (only adqualitysdk remains)
        runCatching {
            findClass("com.ironsource.mediationsdk.server.ServerURL")
                .hook("getRequestURL", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
        }.onFailure {
            logi("DisableAnalytics IronSource ServerURL: skipped (${it.message})")
        }

        // Liftoff (Vungle)
        runCatching {
            findClass("com.vungle.ads.internal.network.VungleApiClient")
                .hook("config", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
        }.onFailure { loge("DisableAnalytics Vungle: ${it.message}") }

        // Unity
        runCatching {
            findClass("com.unity3d.services.ads.UnityAdsImplementation")
                .hook("getInstance", HookStage.BEFORE) { param ->
                    param.setResult(null)
                }
        }.onFailure { loge("DisableAnalytics Unity: ${it.message}") }
    }
}
