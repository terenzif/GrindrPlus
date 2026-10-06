package com.gpp.core

/**
 * Settings metadata for hooks without loading module hook implementations.
 * Keep in sync with [com.gpp.utils.HookManager] registration list.
 */
object HookCatalog {
    data class Entry(
        val name: String,
        val description: String,
        val defaultEnabled: Boolean = true,
    )

    val entries: List<Entry> = listOf(
        Entry("Allow screenshots", "Allow screenshots everywhere in the app"),
        Entry("Anti Block", "Notifies you when someone blocks or unblocks you"),
        Entry("Anti Detection", "Hides root, emulator, and environment detections"),
        Entry("Ban management", "Provides comprehensive ban management tools (detailed ban info, etc.)"),
        Entry("Chat indicators", "Don't show chat markers / indicators to others"),
        Entry("Chat terminal", "Create a chat terminal to execute commands"),
        Entry("Disable analytics", "Disable Grindr analytics (data collection)"),
        Entry("Disable boosting", "Get rid of all upsells related to boosting"),
        Entry("Disable shuffle", "Forcefully disable the shuffle feature"),
        Entry("Disable updates", "Disable forced updates"),
        Entry("Video calls", "Allow video calls on empty chats"),
        Entry("Enable unlimited", "Enable Grindr Unlimited features"),
        Entry("Expiring media", "Allow unlimited photo/video viewing and save media permanently"),
        Entry("Favorites", "Customize layout for the favorites tab"),
        Entry("Feature granting", "Grant all Grindr features"),
        Entry("Local saved phrases", "Save unlimited phrases locally"),
        Entry("Location spoofer", "Spoof your location"),
        Entry("Notification Alerts", "Disable all Grindr warnings related to notifications"),
        Entry("Online indicator", "Customize online indicator duration"),
        Entry("Profile details", "Add extra fields and details to profiles"),
        Entry("Profile views", "Don't let others know you viewed their profile"),
        Entry("Quick block", "Ability to block users quickly"),
        Entry("Status Dialog", "Check whether GrindrPlus is alive or not"),
        Entry("Timber Logging", "Forces Timber to log messages even if no tree is planted"),
        Entry("Unlimited albums", "Allow to be able to view unlimited albums"),
        Entry("Unlimited profiles", "Allow unlimited profiles"),
        Entry("Unlock Explorer", "Unlock all profiles in Explorer"),
        Entry(
            "Keep Alive WebSocket",
            "Prevents WebSocket disconnections when app goes to background. Causes battery drain, use with caution.",
            defaultEnabled = false,
        ),
    )

    suspend fun registerSettings() {
        entries.forEach { entry ->
            Config.initHookSettings(entry.name, entry.description, entry.defaultEnabled)
        }
    }
}
