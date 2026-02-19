package com.grindrplus.core

/**
 * Centralized location for all obfuscated class and method names.
 * This structure helps in managing updates when Grindr obfuscation changes.
 *
 * Mappings for Grindr version 25.20.0
 */
object Obfuscation {
    object G {
        object DisableBoosting {
            const val DRAWER_PROFILE_UI_STATE = "yl.e\$a" // 'DrawerProfileUiState(showBoostMeButton='
            const val RADAR_UI_MODEL = "ii.a\$a" // 'RadarUiModel(boostButton='
            const val FAB_UI_MODEL = "com.grindrapp.android.boost2.presentation.model.FabUiModel"
            const val RIGHT_NOW_MICROS_FAB_UI_MODEL = "com.grindrapp.android.rightnow.presentation.model.RightNowMicrosFabUiModel"
            const val BOOST_STATE_CLASS = "com.grindrapp.android.ui.drawer.model.MicrosDrawerItemState\$Unavailable"
            const val NAVBAR_CLASS = "com.grindrapp.android.home.presentation.model.HomeScreenBottomNavigationUiModel"
            const val SMALL_PERSISTENT_VECTOR = "kotlinx.collections.immutable.implementations.immutableList.SmallPersistentVector"

            // Popup methods
            const val SUBSCRIBE_FOR_BOOST_REDEEM = "Il.w0" // 'com.grindrapp.android.ui.home.HomeActivity$subscribeForBoostRedeem$1'
            // TODO: Find the obfuscated name for showTapsAndViewedMePopup in 25.20.0
            const val SHOW_TAPS_AND_VIEWED_ME_POPUP = ""
        }

        object AntiBlock {
            const val CHAT_DELETE_CONVERSATION_PLUGIN = "R9.c" // 'com.grindrapp.android.chat.ChatDeleteConversationPlugin'
            const val INBOX_FRAGMENT_V2_DELETE_CONVERSATIONS = "re.d" // '("chat_read_receipt", conversationId, null);'
            const val INDIVIDUAL_UNBLOCK_ACTIVITY_VIEW_MODEL = "bl.k" // 'SnackbarEvent.i.ERROR, R.string.unblock_individual_sync_blocks_failure'
            const val CONVERSATION_DELETE_NOTIFICATION = "com.grindrapp.android.chat.model.ConversationDeleteNotification"
        }

        object ProfileDetails {
            const val BLOCKED_PROFILES_OBSERVER = "Hm.f" // 'Intrinsics.checkNotNullParameter(dataList, "dataList");'
            const val PROFILE_VIEW_HOLDER = "bl.u\$c" // 'Intrinsics.checkNotNullParameter(individualUnblockActivityViewModel, "individualUnblockActivityViewModel");'
            const val DISTANCE_UTILS = "com.grindrapp.android.utils.DistanceUtils"
            const val PROFILE_BAR_VIEW = "com.grindrapp.android.ui.profileV2.ProfileBarView"
            const val PROFILE_VIEW_STATE = "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
            const val SERVER_DRIVEN_CASCADE_CACHED_STATE = "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCacheState"
            const val SERVER_DRIVEN_CASCADE_CACHED_PROFILE = "com.grindrapp.android.persistence.model.serverdrivencascade.ServerDrivenCascadeCachedProfile"
        }

        object ChatIndicators {
            const val CHAT_REST_SERVICE = "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
        }
    }
}
