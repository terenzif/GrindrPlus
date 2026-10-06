package com.gpp.hooks

import com.gpp.core.Obfuscation
import com.gpp.utils.Hook
import com.gpp.utils.HookStage
import com.gpp.utils.RetrofitUtils
import com.gpp.utils.RetrofitUtils.RETROFIT_NAME
import com.gpp.utils.RetrofitUtils.createServiceProxy
import com.gpp.utils.hook

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
