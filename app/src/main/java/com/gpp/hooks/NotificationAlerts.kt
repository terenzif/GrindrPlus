package com.gpp.hooks

import com.gpp.core.mapping.MappingDictionary
import com.gpp.core.logi
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.SoftSkipException
import com.gpp.utils.hook

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
