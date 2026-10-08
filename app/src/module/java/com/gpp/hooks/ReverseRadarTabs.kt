package com.gpp.hooks

import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.compat.XposedHelpers
import com.gpp.utils.hook
import com.gpp.utils.hookConstructor

class ReverseRadarTabs : Hook(
    "Reverse radar tabs",
    "Shows the received taps before profile views"
) {
    private val radarTabsClass = "m8.b"
    private val radarFragment = "com.grindrapp.android.radar.presentation.ui.RadarFragment"

    override fun init() {
        val radarTabs = XposedHelpers.callStaticMethod(
            findClass(radarTabsClass),
            "values",
        ) as Array<*>

        findClass("$radarFragment\$a")
            .hookConstructor(HookStage.BEFORE) { param ->
                val tabs = param.arg(2, List::class.java)!!
                val reversed = findClass("kotlinx.collections.immutable.ExtensionsKt")
                    .getMethod("toImmutableList", Iterable::class.java)
                    .invoke(null, tabs.reversed())
                param.setArg(2, reversed)
            }

        findClass("com.google.android.material.tabs.TabLayout")
            .hook("getTabAt", HookStage.BEFORE) { param ->
                if (!Thread.currentThread().stackTrace.any {
                        it.className.contains(radarFragment)
                    }
                ) {
                    return@hook
                }
                param.setArg(0, radarTabs.size - 1 - param.arg<Int>(0))
            }

        findClass("Ma.B")
            .hook("onChanged", HookStage.BEFORE) { param ->
                if (XposedHelpers.getIntField(param.thisObject(), "b") != 1) return@hook
                val position = param.arg<Number>(0).toInt()
                param.setArg(0, Integer.valueOf(radarTabs.size - 1 - position))
            }

        findClass("La.s0")
            .hookConstructor(HookStage.AFTER) { param ->
                val stateFlow = XposedHelpers.getObjectField(param.thisObject(), "J0")
                    ?: return@hookConstructor
                XposedHelpers.callMethod(
                    stateFlow,
                    "setValue",
                    Integer.valueOf(radarTabs.size - 1),
                )
            }
    }
}
