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
            // RadarUiModel(roamButton=…) — default-package dc9 (was missing on earlier 26.16.1 pass)
            const val RADAR_UI_MODEL = "dc9"
            const val FAB_UI_MODEL = "com.grindrapp.android.boost2.presentation.model.FabUiModel"
            const val RIGHT_NOW_MICROS_FAB_UI_MODEL =
                "com.grindrapp.android.rightnow.presentation.model.RightNowMicrosFabUiModel"
            const val BOOST_STATE_CLASS =
                "com.grindrapp.android.ui.drawer.model.MicrosDrawerItemState\$Unavailable"
            const val NAVBAR_CLASS =
                "com.grindrapp.android.home.presentation.model.HomeScreenBottomNavigationUiModel"
            const val SMALL_PERSISTENT_VECTOR =
                "kotlinx.collections.immutable.implementations.immutableList.SmallPersistentVector"

            // subscribeForBoostRedeem invoke-target not confidently re-resolved (BoostService exists; lambda drifted)
            const val SUBSCRIBE_FOR_BOOST_REDEEM = ""
            // ShowTapsAndViewedMeNotification handled via FlowCollector emit (h76), not invoke
            const val SHOW_TAPS_AND_VIEWED_ME_POPUP = "h76"
            const val SHOW_TAPS_AND_VIEWED_ME_POPUP_METHOD = "emit"
        }

        object AntiBlock {
            const val CHAT_DELETE_CONVERSATION_PLUGIN = "d32" // 'Deleting conversations'
            // Inbox delete path — dz2 still present with chat_read_receipt
            const val INBOX_FRAGMENT_V2_DELETE_CONVERSATIONS = "dz2"
            const val INDIVIDUAL_UNBLOCK_ACTIVITY_VIEW_MODEL =
                "io6" // DialogMessage(116) in L()
            const val CONVERSATION_DELETE_NOTIFICATION =
                "com.grindrapp.android.chat.model.ConversationDeleteNotification"
        }

        object ProfileDetails {
            // Blocked-profiles observer fingerprint not re-resolved — leave empty to skip risky hooks
            const val BLOCKED_PROFILES_OBSERVER = ""
            // Was ui.profileV2.g — RecyclerView ViewHolder with menu_actions is now r29
            const val PROFILE_VIEW_HOLDER = "r29"
            const val DISTANCE_UTILS = "com.grindrapp.android.utils.DistanceUtils"
            const val PROFILE_BAR_VIEW = "com.grindrapp.android.ui.profileV2.ProfileBarView"
            const val PROFILE_VIEW_STATE = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
            // ServerDrivenCascadeCacheState removed — CascadeProfileUiData is the cascade row model
            const val SERVER_DRIVEN_CASCADE_CACHED_STATE = ""
            const val SERVER_DRIVEN_CASCADE_CACHED_PROFILE =
                "com.grindrapp.android.persistence.model.serverdrivencascade.CascadeProfileUiData"
            const val CASCADE_PROFILE_UI_DATA =
                "com.grindrapp.android.persistence.model.serverdrivencascade.CascadeProfileUiData"
        }

        object ChatIndicators {
            const val CHAT_REST_SERVICE =
                "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
        }
    }
}
