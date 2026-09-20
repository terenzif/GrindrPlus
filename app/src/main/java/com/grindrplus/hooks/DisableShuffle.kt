package com.grindrplus.hooks

import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hookConstructor
import de.robv.android.xposed.XposedHelpers.setObjectField

// supported version: 26.16.1
// ShuffleUiState / browse.v$g fingerprints absent on 26.16.1 (Cascade V2 / no ShuffleUiState toString).
class DisableShuffle : Hook(
    "Disable shuffle",
    "Forcefully disable the shuffle feature"
) {
    // Left empty intentionally — do not invent class names.
    private val viewState = ""
    private val shuffleUiState = ""

    override fun init() {
        if (shuffleUiState.isEmpty() || viewState.isEmpty()) {
            logi(
                "Disable shuffle: skipped — ShuffleUiState/ViewState fingerprints not found in Grindr 26.16.1 DEX"
            )
            return
        }

        findClass(shuffleUiState).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "a", false)
            setObjectField(param.thisObject(), "b", false)
            setObjectField(param.thisObject(), "c", false)
            setObjectField(param.thisObject(), "d", false)
            setObjectField(param.thisObject(), "f", false)
            setObjectField(param.thisObject(), "g", false)
            setObjectField(param.thisObject(), "h", true)
            setObjectField(param.thisObject(), "i", true)
            setObjectField(param.thisObject(), "j", false)
        }

        findClass(viewState).hookConstructor(HookStage.AFTER) { param ->
            setObjectField(param.thisObject(), "b", false)
            setObjectField(param.thisObject(), "d", false)
        }
    }
}
