package com.grindrplus.hooks

import com.grindrplus.commands.CommandHandler
import com.grindrplus.core.Config
import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.SoftSkipException
import com.grindrplus.utils.hook
import de.robv.android.xposed.XposedHelpers.getObjectField
import org.json.JSONObject

/**
 * Chat command terminal.
 *
 * 26.16.1: historic `Y9.k` / chatMessageMetaData absent.
 * Tries pack-resolved inbound processor, then soft-skips.
 */
class ChatTerminal : Hook(
    "Chat terminal",
    "Create a chat terminal to execute commands"
) {
    private val chatMessageHandler = MappingDictionary.resolve(
        "ChatTerminal.HANDLER",
        "" // intentionally empty default — must come from pack
    )
    private val handlerMethod = MappingDictionary.resolve(
        "ChatTerminal.HANDLER_METHOD",
        "c"
    )

    override fun init() {
        if (chatMessageHandler.isBlank()) {
            throw SoftSkipException(
                "chatMessageMetaData absent — set ChatTerminal.HANDLER in mapping pack"
            )
        }
        try {
            findClass(chatMessageHandler).hook(handlerMethod, HookStage.BEFORE) { param ->
                if (param.args().isEmpty()) return@hook
                val message = try {
                    getObjectField(param.arg(0), "chatMessage")
                } catch (_: Throwable) {
                    param.arg(0)
                }
                val content = try {
                    getObjectField(message, "content")
                } catch (_: Throwable) {
                    return@hook
                }
                val sender = getObjectField(content, "sender") as? String ?: return@hook
                val recipient = getObjectField(content, "recipient") as? String ?: return@hook
                val bodyRaw = getObjectField(content, "body") as? String ?: return@hook
                val messageBody = JSONObject(bodyRaw)
                if (!messageBody.has("text")) return@hook
                val text = messageBody.getString("text")
                val commandPrefix = (Config.get("command_prefix", "/") as String)
                if (text.startsWith(commandPrefix)) {
                    param.setResult(null)
                    CommandHandler(sender, recipient).handle(text.substring(1))
                }
            }
            logi("Chat terminal: hooked $chatMessageHandler.$handlerMethod")
        } catch (t: Throwable) {
            throw SoftSkipException("Chat terminal: ${t.message}")
        }
    }
}
