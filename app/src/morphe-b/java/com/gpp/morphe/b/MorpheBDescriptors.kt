package com.gpp.morphe.b

/**
 * Morphe B static / hybrid feature-parity patches (post Ver.5 green).
 * See [docs/adr/0004-morphe-b.md] and [docs/morphe-b.md].
 */
enum class MorpheBDelivery {
    /** Applied as APK resource/manifest/asset mutation before LSPatch. */
    STATIC_RESOURCE,

    /** Served by a remapped runtime hook (HookManager); static marker only. */
    RUNTIME_REMAP,

    /** Feature surface gone or no safe target — Settings stay skipped/inactive. */
    DEFERRED,
}

data class MorpheBPatchDescriptor(
    val id: String,
    val title: String,
    val replacesHookName: String?,
    val delivery: MorpheBDelivery,
    val notes: String = "",
)

object MorpheBCatalog {
    val patches: List<MorpheBPatchDescriptor> = listOf(
        MorpheBPatchDescriptor(
            id = "chat-terminal",
            title = "Chat terminal",
            replacesHookName = "Chat terminal",
            delivery = MorpheBDelivery.RUNTIME_REMAP,
            notes = "Inbound path via ProcessIncomingMessageWrapper; soft-skip if handler absent",
        ),
        MorpheBPatchDescriptor(
            id = "empty-calls",
            title = "Video calls",
            replacesHookName = "Video calls",
            delivery = MorpheBDelivery.RUNTIME_REMAP,
            notes = "Gate: VideoCallHasNotChattedException / hasChatted checks",
        ),
        MorpheBPatchDescriptor(
            id = "disable-shuffle",
            title = "Disable shuffle",
            replacesHookName = "Disable shuffle",
            delivery = MorpheBDelivery.DEFERRED,
            notes = "ShuffleUiState removed in Cascade V2 on 26.16.1",
        ),
        MorpheBPatchDescriptor(
            id = "notification-alerts",
            title = "Notification Alerts",
            replacesHookName = "Notification Alerts",
            delivery = MorpheBDelivery.DEFERRED,
            notes = "notification_reminder_time fingerprint absent",
        ),
        MorpheBPatchDescriptor(
            id = "favorites",
            title = "Favorites",
            replacesHookName = "Favorites",
            delivery = MorpheBDelivery.RUNTIME_REMAP,
            notes = "FavoritesFragment gone; Cascade V2 favorites item soft-hook",
        ),
        MorpheBPatchDescriptor(
            id = "websocket-alive",
            title = "Keep Alive WebSocket",
            replacesHookName = "Keep Alive WebSocket",
            delivery = MorpheBDelivery.RUNTIME_REMAP,
            notes = "Client com.grindrapp.android.network.websocket.a + factory jcd",
        ),
        MorpheBPatchDescriptor(
            id = "status-dialog",
            title = "Status Dialog",
            replacesHookName = "Status Dialog",
            delivery = MorpheBDelivery.RUNTIME_REMAP,
            notes = "Material TabLayout\$TabView present",
        ),
        MorpheBPatchDescriptor(
            id = "morphe-b-marker",
            title = "Morphe B applied marker",
            replacesHookName = null,
            delivery = MorpheBDelivery.STATIC_RESOURCE,
            notes = "Writes assets/grindrplus/morphe_b.json into base APK",
        ),
    )

    fun byHookName(hookName: String): MorpheBPatchDescriptor? =
        patches.firstOrNull { it.replacesHookName == hookName }
}
