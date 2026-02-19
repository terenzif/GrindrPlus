package com.grindrplus.hooks

import com.grindrplus.core.Obfuscation
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.newInstance
import de.robv.android.xposed.XposedHelpers.setObjectField

// supported version: 25.20.0
class DisableBoosting : Hook(
    "Disable boosting",
    "Get rid of all upsells related to boosting"
) {
    override fun init() {
        findClass(Obfuscation.G.DisableBoosting.DRAWER_PROFILE_UI_STATE).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "a", false) // showBoostMeButton
            setObjectField(
                param.thisObject(),
                "e",
                newInstance(findClass(Obfuscation.G.DisableBoosting.BOOST_STATE_CLASS))
            ) // boostButtonState
            setObjectField(
                param.thisObject(),
                "f",
                newInstance(findClass(Obfuscation.G.DisableBoosting.BOOST_STATE_CLASS))
            ) // roamButtonState
            setObjectField(param.thisObject(), "c", false) // showRNBoostCard
            setObjectField(param.thisObject(), "i", null) // showDayPassItem
            setObjectField(param.thisObject(), "j", null) // unlimitedWeeklySubscriptionItem
            setObjectField(param.thisObject(), "u", false) // isRightNowAvailable
			setObjectField(param.thisObject(), "w", false) // showMegaBoost
        }

        findClass(Obfuscation.G.DisableBoosting.RADAR_UI_MODEL).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "a", null) // boostButton
            setObjectField(param.thisObject(), "b", null) // roamButton
        }

        findClass(Obfuscation.G.DisableBoosting.FAB_UI_MODEL).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "isVisible", false) // isVisible
        }

        findClass(Obfuscation.G.DisableBoosting.RIGHT_NOW_MICROS_FAB_UI_MODEL).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "isBoostFabVisible", false) // isBoostFabVisible
            setObjectField(param.thisObject(), "isClickEnabled", false) // isClickEnabled
            setObjectField(param.thisObject(), "isFabVisible", false) // isFabVisible
        }

		val spvConstructor = findClass(Obfuscation.G.DisableBoosting.SMALL_PERSISTENT_VECTOR).constructors[0]

		findClass(Obfuscation.G.DisableBoosting.NAVBAR_CLASS).hookConstructor(HookStage.BEFORE) { param ->
			val routeList = param.args()[2] as List<*>
			spvConstructor
			val newRouteArray =	routeList.filter { it?.javaClass?.simpleName != "Store" }.toTypedArray()
			val newRouteList = spvConstructor.newInstance(newRouteArray)

			param.setArg(2, newRouteList)
		}

        // the two anonymous functions that get called to invoke the annoying tooltip
        // respectively: showRadarTooltip.<anonymous> and showTapsAndViewedMePopup
        // search for:
        //   ???     - 'com.grindrapp.android.ui.home.HomeActivity$showTapsAndViewedMePopup$1$1'
        //   ???     - 'com.grindrapp.android.ui.home.HomeActivity.showTapsAndViewedMePopup.<anonymous> (HomeActivity.kt'
        //   ???     - 'com.grindrapp.android.ui.home.HomeActivity.showTapsAndViewedMePopup.<anonymous>.<anonymous> (HomeActivity.kt'
		//   "Il.w0" - 'com.grindrapp.android.ui.home.HomeActivity$subscribeForBoostRedeem$1'
		// TODO find the showTapsAndViewedMePopup in 25.20.0
        val popupMethods = mutableListOf(Obfuscation.G.DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM)
        if (Obfuscation.G.DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP.isNotEmpty()) {
            popupMethods.add(Obfuscation.G.DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP)
        }

        popupMethods.forEach {
            findClass(it).hook("invoke", HookStage.BEFORE) { param ->
                param.setResult(null)
            }
        }
    }
}
