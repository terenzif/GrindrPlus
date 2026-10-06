package com.grindrplus.hooks

import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.SoftSkipException
import com.grindrplus.utils.hook

/**
 * Disable Grindr notification-reminder warnings.
 * 26.16.1: `notification_reminder_time` / historic `ue.e` absent.
 */
class NotificationAlerts : Hook(
    "Notification Alerts",
    "Disable all Grindr warnings related to notifications"
) {
    private val notificationManager = MappingDictionary.resolve(
        "NotificationAlerts.MANAGER",
        ""
    )
    private val managerMethod = MappingDictionary.resolve(
        "NotificationAlerts.MANAGER_METHOD",
        "a"
    )

    override fun init() {
        if (notificationManager.isBlank()) {
            throw SoftSkipException("notification_reminder_time absent")
        }
        findClass(notificationManager).hook(managerMethod, HookStage.BEFORE) { param ->
            param.setResult(null)
        }
        logi("Notification Alerts: hooked $notificationManager.$managerMethod")
    }
}
