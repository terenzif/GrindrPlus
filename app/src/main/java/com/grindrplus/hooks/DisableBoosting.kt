package com.grindrplus.hooks

import com.grindrplus.core.Logger
import com.grindrplus.core.Obfuscation
import com.grindrplus.core.loge
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setObjectField

// supported version: 26.16.1 (static JADX — field names use Kotlin properties)
class DisableBoosting : Hook(
    "Disable boosting",
    "Get rid of all upsells related to boosting"
) {
    override fun init() {
        runCatching {
            findClass(Obfuscation.G.DisableBoosting.DRAWER_PROFILE_UI_STATE)
                .hookConstructor(HookStage.AFTER) { param ->
                    val unavailable = newInstance(
                        findClass(Obfuscation.G.DisableBoosting.BOOST_STATE_CLASS)
                    )
                    setObjectField(param.thisObject(), "showBoostMeButton", false)
                    setObjectField(param.thisObject(), "boostButtonState", unavailable)
                    setObjectField(param.thisObject(), "roamButtonState", unavailable)
                    setObjectField(param.thisObject(), "showRNBoostCard", false)
                    setObjectField(param.thisObject(), "showDayPassItem", null)
                    setObjectField(param.thisObject(), "unlimitedWeeklySubscriptionItem", null)
                    setObjectField(param.thisObject(), "isRightNowAvailable", false)
                }
        }.onFailure {
            loge("DisableBoosting drawer state: ${it.message}")
            Logger.writeRaw(it.stackTraceToString())
        }

        val radar = Obfuscation.G.DisableBoosting.RADAR_UI_MODEL
        if (radar.isNotEmpty()) {
            runCatching {
                // dc9 RadarUiModel(roamButton=a, activeMicroSessionUi=b, …)
                findClass(radar).hookConstructor(HookStage.AFTER) { param ->
                    setObjectField(param.thisObject(), "a", null)
                    setObjectField(param.thisObject(), "b", null)
                }
            }.onFailure { loge("DisableBoosting radar: ${it.message}") }
        }

        runCatching {
            findClass(Obfuscation.G.DisableBoosting.FAB_UI_MODEL)
                .hookConstructor(HookStage.AFTER) { param ->
                    setObjectField(param.thisObject(), "isVisible", false)
                }
        }.onFailure { loge("DisableBoosting FAB: ${it.message}") }

        runCatching {
            findClass(Obfuscation.G.DisableBoosting.RIGHT_NOW_MICROS_FAB_UI_MODEL)
                .hookConstructor(HookStage.AFTER) { param ->
                    setObjectField(param.thisObject(), "isBoostFabVisible", false)
                    setObjectField(param.thisObject(), "isClickEnabled", false)
                    setObjectField(param.thisObject(), "isFabVisible", false)
                }
        }.onFailure { loge("DisableBoosting RightNow FAB: ${it.message}") }

        runCatching {
            val spvConstructor =
                findClass(Obfuscation.G.DisableBoosting.SMALL_PERSISTENT_VECTOR).constructors[0]

            findClass(Obfuscation.G.DisableBoosting.NAVBAR_CLASS)
                .hookConstructor(HookStage.BEFORE) { param ->
                    val routeList = param.args()[2] as List<*>
                    val newRouteArray =
                        routeList.filter { it?.javaClass?.simpleName != "Store" }.toTypedArray()
                    val newRouteList = spvConstructor.newInstance(newRouteArray)
                    param.setArg(2, newRouteList)
                }
        }.onFailure { loge("DisableBoosting navbar: ${it.message}") }

        if (Obfuscation.G.DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM.isNotEmpty()) {
            runCatching {
                findClass(Obfuscation.G.DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM)
                    .hook("invoke", HookStage.BEFORE) { param -> param.setResult(null) }
            }.onFailure { loge("DisableBoosting boost redeem: ${it.message}") }
        }

        // ShowTapsAndViewedMeNotification: HomeActivity FlowCollector h76.emit(ynb)
        val tapsPopup = Obfuscation.G.DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP
        if (tapsPopup.isNotEmpty()) {
            runCatching {
                findClass(tapsPopup).hook(
                    Obfuscation.G.DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP_METHOD,
                    HookStage.BEFORE
                ) { param ->
                    val event = param.args().firstOrNull() ?: return@hook
                    if (event.javaClass.name == "xnb") {
                        param.setResult(null)
                    }
                }
            }.onFailure { loge("DisableBoosting taps popup: ${it.message}") }
        }
    }
}
