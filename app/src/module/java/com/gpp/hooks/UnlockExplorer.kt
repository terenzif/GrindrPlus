package com.gpp.hooks

import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.hook

class UnlockExplorer : Hook(
    "Unlock Explorer",
    "Unlock all profiles in Explorer"
) {
    override fun init() {
        findClass("com.grindrapp.android.ui.profileV2.model.ProfileViewState")
            .hook("getShouldLockQuickbar", HookStage.BEFORE) { param ->
                param.setResult(false)
            }
    }
    // Alternative methods
    // getNumOfFreeExploreChatsRemaining
    // numOfFreeExploreChatsRemaining
}