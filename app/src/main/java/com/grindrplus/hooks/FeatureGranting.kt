package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.utils.Feature
import com.grindrplus.utils.FeatureManager
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.callMethod

class FeatureGranting : Hook(
    "Feature granting",
    "Grant all Grindr features"
) {
    private val isFeatureFlagEnabled = "O8.b" // search for 'implements IsFeatureFlagEnabled {'
    private val upsellsV8Model = "com.grindrapp.android.model.UpsellsV8"
    private val insertsModel = "com.grindrapp.android.model.Inserts"
    private val expiringAlbumsExperiment = "B3.a" // search for '("SeeAlbumOptions", 1, "see-album-options")'
    private val favoritesExperiment = "com.grindrapp.android.favoritesv2.domain.experiment.FavoritesV2Experiment" // search for 'public final class FavoritesV2Experiment'
    private val settingDistanceVisibilityViewModel =
        "com.grindrapp.android.ui.settings.distance.a\$e" // search for 'UiState(distanceVisibility='
    private val featureManager = FeatureManager()

    override fun init() {
        initFeatures()

        findClass(isFeatureFlagEnabled).hook("a", HookStage.BEFORE) { param ->
            val flagKey = callMethod(param.args()[0], "toString") as String
            if (featureManager.isManaged(flagKey)) {
                GrindrPlus.logger.debug("Returning ${featureManager.isEnabled(flagKey)} for $flagKey")
                param.setResult(featureManager.isEnabled(flagKey))
            } else {
                GrindrPlus.logger.debug("Unhandled feature flag $flagKey")
            }
        }

        findClass(settingDistanceVisibilityViewModel)
            .hookConstructor(HookStage.BEFORE) { param ->
                param.setArg(4, false) // hidePreciseDistance
            }

        listOf(upsellsV8Model, insertsModel).forEach { model ->
            findClass(model)
                .hook("getMpuFree", HookStage.BEFORE) { param ->
                    param.setResult(0)
                }

            findClass(model)
                .hook("getMpuXtra", HookStage.BEFORE) { param ->
                    param.setResult(0)
                }
        }

        findClass(expiringAlbumsExperiment)
            .hook("b", HookStage.BEFORE) { param ->
                param.setResult(false)
            }

        findClass(favoritesExperiment)
            .hook("e", HookStage.BEFORE) { param ->
                param.setResult(false) // I'm afraid this will get deprecated soon
            }
    }

    private fun initFeatures() {
        featureManager.add(Feature("PasswordComplexity", false))
        featureManager.add(Feature("TimedBans", false))
        featureManager.add(Feature("ChatInterstitialFeatureFlag", false))
        featureManager.add(Feature("SideDrawerDeeplinkKillSwitch", true))
        featureManager.add(Feature("SponsoredRoamKillSwitch", true))
        featureManager.add(Feature("UnifiedProfileAvatarFeatureFlag", true))
        featureManager.add(Feature("ApproximateDistanceFeatureFlag", false))
        featureManager.add(Feature("DoxyPEP", true))
        featureManager.add(Feature("CascadeRewriteFeatureFlag", false))
        featureManager.add(Feature("AdsLogs", false))
        featureManager.add(Feature("ClientTelemetryTracking", false))
        featureManager.add(Feature("LTOAds", false))
        featureManager.add(Feature("SponsorProfileAds", false))
        featureManager.add(Feature("ConversationAds", false))
        featureManager.add(Feature("InboxNativeAds", false))
        featureManager.add(Feature("ReportingLagTime", false))
        featureManager.add(Feature("MrecNewFlow", false))
        featureManager.add(Feature("RunningOnEmulatorFeatureFlag", false))
        featureManager.add(Feature("BannerNewFlow", false))
        featureManager.add(Feature("CalendarUi", true))
        featureManager.add(Feature("CookieTap", false))
        featureManager.add(Feature("TakenOnGrindrWatermarkFlag", false))
    }
}