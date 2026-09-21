package com.grindrplus.core

import com.grindrplus.core.mapping.MappingDictionary

/**
 * Obfuscated class / method names for hooks.
 *
 * When a mapping pack is loaded for the installed Grindr [versionCode], values come from
 * [MappingDictionary]. Otherwise (or for keys not yet in the pack) the compile-time fallbacks
 * for 26.16.1 (179451) are used. Empty string still means "skip this hook site".
 */
object Obfuscation {
    object G {
        object DisableBoosting {
            val DRAWER_PROFILE_UI_STATE: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.DRAWER_PROFILE_UI_STATE",
                    "com.grindrapp.android.ui.drawer.model.DrawerProfileUiState"
                )
            val RADAR_UI_MODEL: String
                get() = MappingDictionary.resolve("DisableBoosting.RADAR_UI_MODEL", "dc9")
            val FAB_UI_MODEL: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.FAB_UI_MODEL",
                    "com.grindrapp.android.boost2.presentation.model.FabUiModel"
                )
            val RIGHT_NOW_MICROS_FAB_UI_MODEL: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.RIGHT_NOW_MICROS_FAB_UI_MODEL",
                    "com.grindrapp.android.rightnow.presentation.model.RightNowMicrosFabUiModel"
                )
            val BOOST_STATE_CLASS: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.BOOST_STATE_CLASS",
                    "com.grindrapp.android.ui.drawer.model.MicrosDrawerItemState\$Unavailable"
                )
            val NAVBAR_CLASS: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.NAVBAR_CLASS",
                    "com.grindrapp.android.home.presentation.model.HomeScreenBottomNavigationUiModel"
                )
            val SMALL_PERSISTENT_VECTOR: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.SMALL_PERSISTENT_VECTOR",
                    "kotlinx.collections.immutable.implementations.immutableList.SmallPersistentVector"
                )
            val SUBSCRIBE_FOR_BOOST_REDEEM: String
                get() = MappingDictionary.resolve("DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM", "")
            val SHOW_TAPS_AND_VIEWED_ME_POPUP: String
                get() = MappingDictionary.resolve("DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP", "h76")
            val SHOW_TAPS_AND_VIEWED_ME_POPUP_METHOD: String
                get() = MappingDictionary.resolve(
                    "DisableBoosting.SHOW_TAPS_AND_VIEWED_ME_POPUP_METHOD",
                    "emit"
                )
        }

        object AntiBlock {
            val CHAT_DELETE_CONVERSATION_PLUGIN: String
                get() = MappingDictionary.resolve("AntiBlock.CHAT_DELETE_CONVERSATION_PLUGIN", "d32")
            val INBOX_FRAGMENT_V2_DELETE_CONVERSATIONS: String
                get() = MappingDictionary.resolve(
                    "AntiBlock.INBOX_FRAGMENT_V2_DELETE_CONVERSATIONS",
                    "dz2"
                )
            val INDIVIDUAL_UNBLOCK_ACTIVITY_VIEW_MODEL: String
                get() = MappingDictionary.resolve(
                    "AntiBlock.INDIVIDUAL_UNBLOCK_ACTIVITY_VIEW_MODEL",
                    "io6"
                )
            val CONVERSATION_DELETE_NOTIFICATION: String
                get() = MappingDictionary.resolve(
                    "AntiBlock.CONVERSATION_DELETE_NOTIFICATION",
                    "com.grindrapp.android.chat.model.ConversationDeleteNotification"
                )
        }

        object ProfileDetails {
            val BLOCKED_PROFILES_OBSERVER: String
                get() = MappingDictionary.resolve("ProfileDetails.BLOCKED_PROFILES_OBSERVER", "")
            val PROFILE_VIEW_HOLDER: String
                get() = MappingDictionary.resolve("ProfileDetails.PROFILE_VIEW_HOLDER", "r29")
            val DISTANCE_UTILS: String
                get() = MappingDictionary.resolve("ProfileDetails.DISTANCE_UTILS", "iq3")
            /** Method on [DISTANCE_UTILS]; pack field `method`, fallback `c` on 26.16.1. */
            val DISTANCE_UTILS_METHOD: String
                get() = MappingDictionary.methodName("ProfileDetails.DISTANCE_UTILS", "c") ?: "c"
            val PROFILE_BAR_VIEW: String
                get() = MappingDictionary.resolve(
                    "ProfileDetails.PROFILE_BAR_VIEW",
                    "com.grindrapp.android.ui.profileV2.ProfileBarView"
                )
            val PROFILE_VIEW_STATE: String
                get() = MappingDictionary.resolve(
                    "ProfileDetails.PROFILE_VIEW_STATE",
                    "com.grindrapp.android.ui.profileV2.model.ProfileViewState"
                )
            val SERVER_DRIVEN_CASCADE_CACHED_STATE: String
                get() = MappingDictionary.resolve(
                    "ProfileDetails.SERVER_DRIVEN_CASCADE_CACHED_STATE",
                    ""
                )
            val SERVER_DRIVEN_CASCADE_CACHED_PROFILE: String
                get() = MappingDictionary.resolve(
                    "ProfileDetails.SERVER_DRIVEN_CASCADE_CACHED_PROFILE",
                    "com.grindrapp.android.persistence.model.serverdrivencascade.CascadeProfileUiData"
                )
            val CASCADE_PROFILE_UI_DATA: String
                get() = MappingDictionary.resolve(
                    "ProfileDetails.CASCADE_PROFILE_UI_DATA",
                    "com.grindrapp.android.persistence.model.serverdrivencascade.CascadeProfileUiData"
                )
        }

        object ChatIndicators {
            val CHAT_REST_SERVICE: String
                get() = MappingDictionary.resolve(
                    "ChatIndicators.CHAT_REST_SERVICE",
                    "com.grindrapp.android.chat.data.datasource.api.service.ChatRestService"
                )
        }
    }
}
