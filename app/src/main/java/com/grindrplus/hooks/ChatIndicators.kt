package com.grindrplus.hooks

import com.grindrplus.core.Obfuscation
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.RetrofitUtils
import com.grindrplus.utils.RetrofitUtils.RETROFIT_NAME
import com.grindrplus.utils.RetrofitUtils.createServiceProxy
import com.grindrplus.utils.hook

// supported version: 25.20.0
class ChatIndicators : Hook(
    "Chat indicators",
    "Don't show chat markers / indicators to others"
) {
    private val blacklistedPaths = setOf(
        "v4/chatstatus/typing"
    )

    override fun init() {
        val chatRestServiceClass = findClass(Obfuscation.G.ChatIndicators.CHAT_REST_SERVICE)

        val methodBlacklist = blacklistedPaths.mapNotNull {
            RetrofitUtils.findPOSTMethod(chatRestServiceClass, it)?.name
        }

        findClass(RETROFIT_NAME)
            .hook("create", HookStage.AFTER) { param ->
                val service = param.getResult()
                if (service != null && chatRestServiceClass.isAssignableFrom(service.javaClass)) {
                    param.setResult(createServiceProxy(
                        service,
                        chatRestServiceClass,
                        methodBlacklist.toTypedArray()
                    ))
                }
            }
    }
}
