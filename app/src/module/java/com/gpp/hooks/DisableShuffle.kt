package com.gpp.hooks

import com.gpp.core.mapping.MappingDictionary
import com.gpp.core.logi
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.SoftSkipException
import com.gpp.utils.hookConstructor
import com.gpp.utils.compat.XposedHelpers.setObjectField

/**
 * Force-disable shuffle. Cascade V2 (26.16.1) removed ShuffleUiState — soft-skip unless pack remaps.
 */
class DisableShuffle : Hook(
    "Disable shuffle",
    "Forcefully disable the shuffle feature"
) {
    private val viewState = MappingDictionary.resolve("DisableShuffle.VIEW_STATE", "")
    private val shuffleUiState = MappingDictionary.resolve("DisableShuffle.SHUFFLE_UI_STATE", "")

    override fun init() {
        if (shuffleUiState.isBlank() || viewState.isBlank()) {
            throw SoftSkipException("fingerprint absent on this DEX (ShuffleUiState / Cascade V2)")
        }

        findClass(shuffleUiState).hookConstructor(HookStage.AFTER) { param ->
            listOf("a", "b", "c", "d", "f", "g", "j").forEach {
                runCatching { setObjectField(param.thisObject(), it, false) }
            }
            listOf("h", "i").forEach {
                runCatching { setObjectField(param.thisObject(), it, true) }
            }
        }

        findClass(viewState).hookConstructor(HookStage.AFTER) { param ->
            runCatching { setObjectField(param.thisObject(), "b", false) }
            runCatching { setObjectField(param.thisObject(), "d", false) }
        }
        logi("Disable shuffle: hooked $shuffleUiState / $viewState")
    }
}
