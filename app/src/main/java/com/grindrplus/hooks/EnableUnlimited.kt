package com.grindrplus.hooks

import com.grindrplus.GrindrPlus
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook

class EnableUnlimited : Hook(
    "Enable unlimited",
    "Enable Grindr Unlimited features"
) {
    private val userSession = "aa.V" // search for 'com.grindrapp.android.storage.UserSessionImpl$1'
    private val subscribeToInterstitialsList = listOf(
        "n5.E\$a" // search for 'com.grindrapp.android.chat.presentation.ui.ChatActivityV2$subscribeToInterstitialAds$1$1$1'
    )
    override fun init() {
        val userSessionClass = findClass(userSession)

        userSessionClass.hook( // hasFeature()
            "y", HookStage.BEFORE // search for 'Intrinsics.checkNotNullParameter(feature, "feature")' in userSession
        ) { param ->
            val disallowedFeatures = setOf("DisableScreenshot")
            param.setResult(param.arg(0, String::class.java) !in disallowedFeatures)
        }

        userSessionClass.hook( // isNoXtraUpsell()
            "n", HookStage.BEFORE // search for '()) ? false : true;' in userSession
        ) { param ->
            param.setResult(true)
        }

        userSessionClass.hook( // isNoPlusUpsell()
            "G", HookStage.BEFORE // search for 'Role.PLUS, Role.FREE_PLUS' in userSession
        ) { param ->
            param.setResult(true)
        }

        userSessionClass.hook( // isFree()
            "z", HookStage.BEFORE // search for '.isEmpty();' in userSession
        ) { param ->
            param.setResult(false)
        }

        userSessionClass.hook( // isFreeXtra()
            "w", HookStage.BEFORE // search for 'Role.XTRA, Role.FREE_XTRA' in userSession
        ) { param ->
            param.setResult(false)
        }

        userSessionClass.hook( // isFreeUnlimited()
            "B", HookStage.BEFORE // search for 'Role.UNLIMITED, Role.FREE_UNLIMITED' in userSession
        ) { param ->
            param.setResult(true)
        }

        subscribeToInterstitialsList.forEach {
            findClass(it)
                .hook("emit", HookStage.BEFORE) { param ->
                    val modelName = param.arg<Any>(0)::class.java.name
                    if (!modelName.contains("NoInterstitialCreated")
                        && !modelName.contains("OnInterstitialDismissed")
                    ) {
                        param.setResult(null)
                    }
                }
        }
    }
}