package com.grindrplus.hooks

import com.grindrplus.commands.CommandHandler
import com.grindrplus.core.Config
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import org.json.JSONObject

// supported version: 26.16.1
// chatMessageMetaData fingerprint absent — Y9.k not present in 26.16.1 DEX.
class ChatTerminal : Hook(
    "Chat terminal",
    "Create a chat terminal to execute commands"
) {
    private val chatMessageHandler = "" // was Y9.k

    override fun init() {
        if (chatMessageHandler.isEmpty()) {
            logi(
                "Chat terminal: skipped — chatMessageMetaData handler not found in Grindr 26.16.1 DEX"
            )
            return
        }

        findClass(chatMessageHandler).hook("l", HookStage.BEFORE) { param ->
            val message = getObjectField(param.arg(0), "chatMessage")
            val content = getObjectField(message, "content")
            val sender = getObjectField(content, "sender") as String
            val recipient = getObjectField(content, "recipient") as String
            val messageBody = JSONObject(getObjectField(content, "body") as String)
            if (!messageBody.has("text")) return@hook
            val text = messageBody.getString("text")

            val commandPrefix = (Config.get("command_prefix", "/") as String)
            if (text.startsWith(commandPrefix)) {
                param.setResult(null)
                CommandHandler(sender, recipient).handle(text.substring(1))
            }
        }
    }
}
