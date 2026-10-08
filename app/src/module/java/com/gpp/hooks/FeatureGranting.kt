package com.gpp.hooks

import com.gpp.GrindrPlus
import com.gpp.core.Config
import com.gpp.core.logi
import com.gpp.core.mapping.MappingDictionary
import com.gpp.ui.Utils
import com.gpp.utils.Feature
import com.gpp.utils.FeatureManager
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.hook
import com.gpp.utils.hookConstructor
import com.gpp.utils.compat.XposedHelpers
import com.gpp.utils.compat.XposedHelpers.callMethod
import com.gpp.utils.compat.XposedHelpers.getObjectField

class FeatureGranting : Hook(
    "Feature granting",
    "Grant all Grindr features"
) {
    private val upsellsV8Model = "com.grindrapp.android.model.UpsellsV8"
    private val insertsModel = "com.grindrapp.android.model.Inserts"
    private val featureModel = "com.grindrapp.android.usersession.model.Feature"
    private val tapModel = "com.grindrapp.android.taps.model.Tap"
    private val tapInboxModel = "com.grindrapp.android.taps.data.model.TapsInboxEntity"
    private val featureManager = FeatureManager()

    override fun init() {
        initFeatures()

        val isFeatureFlagEnabled =
            MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled", "iv6")
        val invokeMethod =
            MappingDictionary.resolve("FeatureGranting.isFeatureFlagEnabled.invokeMethod", "a")
        if (isFeatureFlagEnabled.isEmpty() || invokeMethod.isEmpty()) {
            logi("FeatureGranting: isFeatureFlagEnabled soft-skip (empty remap)")
        } else {
            // search for 'Assignment.Flag'
            findClass(isFeatureFlagEnabled).hook(invokeMethod, HookStage.BEFORE) { param ->
                val flagKey = callMethod(param.args()[0], "toString") as String
                if (featureManager.isManaged(flagKey)) {
                    param.setResult(featureManager.isEnabled(flagKey))
                }
            }
        }

        findClass(featureModel).hook("isGranted", HookStage.BEFORE) { param ->
            val disallowedFeatures = setOf("DisableScreenshot")
            val feature = callMethod(param.thisObject(), "toString") as String
            param.setResult(feature !in disallowedFeatures)
        }

        val settingDistanceVisibilityViewModel = MappingDictionary.resolve(
            "FeatureGranting.settingDistanceVisibilityViewModel",
            "n5b"
        )
        if (settingDistanceVisibilityViewModel.isEmpty()) {
            logi("FeatureGranting: settingDistanceVisibilityViewModel soft-skip (empty remap)")
        } else {
            findClass(settingDistanceVisibilityViewModel)
                .hookConstructor(HookStage.BEFORE) { param ->
                    // n5b(int distanceVisibility, boolean hidePreciseDistance, Set loading)
                    if (param.args().size >= 2) {
                        param.setArg(1, false) // hidePreciseDistance
                    }
                }
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

        listOf(tapModel, tapInboxModel).forEach { model ->
            findClass(model).hook("isViewable", HookStage.BEFORE) { param ->
                param.setResult(true)
            }
        }

        val alertParamsField =
            MappingDictionary.resolve("FeatureGranting.alertParamsField", "P")
        if (alertParamsField.isEmpty()) {
            logi("FeatureGranting: alertParamsField soft-skip (empty remap)")
        } else {
            val boostAlertStringId = Utils.getId(
                "incognito_while_boosting_confilct_warning_message",
                "string",
                GrindrPlus.context
            )

            val boostAlertString = GrindrPlus.context.resources.getString(boostAlertStringId)

            findClass("androidx.appcompat.app.AlertDialog\$Builder")
                .hook("show", HookStage.BEFORE) { param ->
                    val builder = param.thisObject()
                    // search for 'AlertController.AlertParams' in androidx.appcompat.app.AlertDialog
                    val alertParams = getObjectField(builder, alertParamsField)
                    val messageString = getObjectField(alertParams, "mMessage")

                    if (messageString.equals(boostAlertString)) {
                        val dialog = callMethod(builder, "create")
                        val positiveButtonListener =
                            getObjectField(alertParams, "mPositiveButtonListener")

                        val positiveButtonId = XposedHelpers.getStaticIntField(
                            findClass("android.content.DialogInterface"),
                            "BUTTON_POSITIVE"
                        )

                        callMethod(positiveButtonListener, "onClick", dialog, positiveButtonId)

                        param.setResult(dialog)
                    }
                }
        }
    }

    private fun initFeatures() {
        featureManager.add(Feature("PasswordComplexity", false))
        featureManager.add(Feature("TimedBans", false))
        featureManager.add(Feature("GenderFlag", true))
        featureManager.add(Feature("ForceApplovinOptOut", true))
        featureManager.add(Feature("RewardedAdViewedMeFeatureFlag", false))
        featureManager.add(Feature("ChatInterstitialFeatureFlag", false))
        featureManager.add(Feature("SideDrawerDeeplinkKillSwitch", true))
        featureManager.add(Feature("SponsoredRoamKillSwitch", true))
        featureManager.add(Feature("UnifiedProfileAvatarFeatureFlag", true))
        featureManager.add(Feature("ApproximateDistanceFeatureFlag", false))
        featureManager.add(Feature("DoxyPEP", true))
        featureManager.add(Feature("CascadeRewriteFeatureFlag", false))
        featureManager.add(Feature("AdsLogs", false))
        featureManager.add(Feature("NonChatEnvironmentAdBannerFeatureFlag", false))
        featureManager.add(Feature("PersistentAdBannerFeatureFlag", false))
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
        featureManager.add(Feature("CookieTap", Config.get("enable_cookie_tap", false, true) as Boolean))
        featureManager.add(Feature("VipFlag", Config.get("enable_vip_flag", false, true) as Boolean))
        featureManager.add(Feature("PositionFilter", true))
        featureManager.add(Feature("AgeFilter", true))
        featureManager.add(Feature("BanterFeatureGate", false))
        featureManager.add(Feature("TakenOnGrindrWatermarkFlag", false))
        featureManager.add(Feature("gender-filter", true))
        featureManager.add(Feature("enable-chat-summaries", true))
        featureManager.add(Feature("enable-mutual-taps-no-paywall", !(Config.get("enable_interest_section", true, true) as Boolean)))
    }
}
