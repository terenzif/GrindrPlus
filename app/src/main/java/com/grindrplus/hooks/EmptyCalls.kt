package com.grindrplus.hooks

import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook

// supported version: 26.16.1
// IndividualChatNavViewModel / isTalkBefore fingerprint absent from 26.16.1 DEX.
class EmptyCalls : Hook(
    "Video calls",
    "Allow video calls on empty chats"
) {
    private val individualChatNavViewModel = "" // was ma.c0

    override fun init() {
        if (individualChatNavViewModel.isEmpty()) {
            logi(
                "Video calls: skipped — IndividualChatNavViewModel/isTalkBefore not found in Grindr 26.16.1 DEX"
            )
            return
        }

        findClass(individualChatNavViewModel)
            .hook("N", HookStage.BEFORE) { param ->
                param.setResult(true)
            }
    }
}
