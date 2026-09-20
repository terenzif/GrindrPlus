package com.grindrplus.core

/**
 * Centralized location for all obfuscated class and method names.
 * This structure helps in managing updates when Grindr obfuscation changes.
 *
 * Mappings for Grindr version 26.16.1 (versionCode 179451).
 * Confidence: static JADX only — device confirmation required before claiming hooks work.
 *
 * Note: many R8 classes now live in the default package (DEX `Llrc;` → findClass `"lrc"`).
 * JADX sources show them under `defpackage/` for Java validity only.
 */
object Obfuscation {
    object G {
        object DisableBoosting {
            // Unobfuscated on 26.16.1
            const val DRAWER_PROFILE_UI_STATE =
                "com.grindrapp.android.ui.drawer.model.DrawerProfileUiState" // 'DrawerProfileUiState(showBoostMeButton='
            // RadarUiModel(boostButton=) fingerprint not found — needs device/JADX follow-up
            const val RADAR_UI_MODEL = ""
            const val FAB_UI_MODEL = "com.grindrapp.android.boost2.presentation.model.FabUiModel"
            const val RIGHT_NOW_MICROS_FAB_UI_MODEL =
                "com.grindrapp.android.rightnow.presentation.model.RightNowMicrosFabUiModel"
            const val BOOST_STATE_CLASS =
                "com.grindrapp.android.ui.drawer.model.MicrosDrawerItemState\$Unavailable"
            const val NAVBAR_CLASS =
                "com.grindrapp.android.home.presentation.model.HomeScreenBottomNavigationUiModel"
            const val SMALL_PERSISTENT_VECTOR =
                "kotlinx.collections.immutable.implementations.immutableList.SmallPersistentVector"

            // Popup methods — subscribeForBoostRedeem fingerprint not found on 26.16.1
            const val SUBSCRIBE_FOR_BOOST_REDEEM = ""
            const val SHOW_TAPS_AND_VIEWED_ME_POPUP = ""
        }

        object AntiBlock {
            const val CHAT_DELETE_CONVERSATION_PLUGIN = "d32" // 'Deleting conversations'
            // Inbox delete path fingerprint drifted — confirm on device
            const val INBOX_FRAGMENT_V2_DELETE_CONVERSATIONS = "dz2" // '("chat_read_receipt", …, null);' approx
            const val INDIVIDUAL_UNBLOCK_ACTIVITY_VIEW_MODEL =
                "io6" // 'R.string.unblock_individual_sync_blocks_failure'
            const val CONVERSATION_DELETE_NOTIFICATION =
                "com.grindrapp.android.chat.model.ConversationDeleteNotification"
        }

        object ProfileDetails {
            // Blocked-profiles observer fingerprint not re-resolved — leave empty to skip risky hooks
            const val BLOCKED_PROFILES_OBSERVER = ""
            const val PROFILE_VIEW_HOLDER = ""
            const val DISTANCE_UTILS = "com.grindrapp.android.utils.DistanceUtils"
            const val PROFILE_BAR_VIEW = "com.grindrapp.android.ui.profileV2.ProfileBarView"
            const val PROFILE_VIEW_STATE = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
            const val SERVER_DRIVEN_CASCADE_CACHED_STATE =
                "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
            const val SERVER_DRIVEN_CASCADE_CACHED_PROFILE =
                "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"
        }

        object ChatIndicators {
            const val CHAT_REST_SERVICE =
                "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
        }
    }
}
