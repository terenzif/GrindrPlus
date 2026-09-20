package com.grindrplus.hooks

import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook

// supported version: 26.16.1
// notification_reminder_time fingerprint absent from 26.16.1 DEX (ue.e gone).
class NotificationAlerts : Hook(
    "Notification Alerts",
    "Disable all Grindr warnings related to notifications"
) {
    private val notificationManager = "" // was ue.e

    override fun init() {
        if (notificationManager.isEmpty()) {
            logi(
                "Notification Alerts: skipped — notification_reminder_time fingerprint not found in Grindr 26.16.1 DEX"
            )
            return
        }

        findClass(notificationManager)
            .hook("a", HookStage.BEFORE) { param ->
                param.setResult(null)
            }
    }
}
