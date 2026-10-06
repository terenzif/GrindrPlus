package com.grindrplus.hooks

import com.grindrplus.core.mapping.MappingDictionary
import com.grindrplus.core.logi
import com.grindrplus.utils.Hook
import com.grindrplus.utils.HookStage
import com.grindrplus.utils.SoftSkipException
import com.grindrplus.utils.hook
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Allow video calls on empty chats (26.16.1+).
 *
 * Old IndividualChatNavViewModel/isTalkBefore fingerprints are gone.
 * Gate surfaces as [VideoCallHasNotChattedException] and `hasChatted` checks.
 */
class EmptyCalls : Hook(
    "Video calls",
    "Allow video calls on empty chats"
) {
    private val exceptionClass = MappingDictionary.resolve(
        "EmptyCalls.VIDEO_CALL_HAS_NOT_CHATTED",
        "com.grindrapp.android.exception.VideoCallHasNotChattedException"
    )
    private val chatFragment = MappingDictionary.resolve(
        "EmptyCalls.CHAT_INDIVIDUAL_FRAGMENT",
        "com.grindrapp.android.chat.presentation.ui.individual.ChatIndividualFragment"
    )

    override fun init() {
        var hooked = 0
        try {
            val ex = findClass(exceptionClass)
            // Prefer replacing boolean predicates on the chat fragment that gate video call.
            hooked += hookBooleanGates(chatFragment)
            if (hooked == 0) {
                // Last resort: swallow exception construction sites by forcing a harmless type —
                // Xposed/Vector cannot cancel a throw from <init>; scan fragment methods that declare throws.
                hooked += hookThrowingMethods(chatFragment, ex)
            }
        } catch (t: Throwable) {
            throw SoftSkipException("Video calls: ${t.message}")
        }
        if (hooked == 0) {
            throw SoftSkipException(
                "Video calls: no hasChatted/boolean gate found on ChatIndividualFragment"
            )
        }
        logi("Video calls: hooked $hooked gate method(s)")
    }

    private fun hookBooleanGates(className: String): Int {
        val clazz = try {
            findClass(className)
        } catch (_: Throwable) {
            return 0
        }
        var n = 0
        clazz.declaredMethods.forEach { method ->
            if (Modifier.isAbstract(method.modifiers)) return@forEach
            if (!isBooleanGateCandidate(method)) return@forEach
            try {
                // Hook the exact Method — never all overloads of a short obfuscated name.
                method.hook(HookStage.BEFORE) { param ->
                    if (param.args().size == method.parameterTypes.size) {
                        param.setResult(true)
                    }
                }
                n++
            } catch (_: Throwable) {
                // overload mismatch — ignore
            }
        }
        return n
    }

    /** Require chat/video/call/talk signal; never blanket-hook short boolean getters. */
    private fun isBooleanGateCandidate(method: Method): Boolean {
        val ret = method.returnType
        val isBool = ret == Boolean::class.javaPrimitiveType || ret == java.lang.Boolean::class.java
        if (!isBool) return false
        val name = method.name
        return name.contains("chat", ignoreCase = true) ||
            name.contains("video", ignoreCase = true) ||
            name.contains("talk", ignoreCase = true) ||
            name.contains("call", ignoreCase = true) ||
            name == "N" // historic tip short name with known video-gate role
    }

    private fun hookThrowingMethods(className: String, exceptionClass: Class<*>): Int {
        val clazz = try {
            findClass(className)
        } catch (_: Throwable) {
            return 0
        }
        var n = 0
        clazz.declaredMethods.forEach { method ->
            val throwsEx = method.exceptionTypes.any { exceptionClass.isAssignableFrom(it) }
            val nameHint = method.name.contains("video", ignoreCase = true) ||
                method.name.contains("call", ignoreCase = true)
            if (!throwsEx && !nameHint) return@forEach
            try {
                clazz.hook(method.name, HookStage.BEFORE) { param ->
                    if (method.returnType == Void.TYPE) {
                        param.setResult(null)
                    } else if (method.returnType == Boolean::class.javaPrimitiveType ||
                        method.returnType == java.lang.Boolean::class.java
                    ) {
                        param.setResult(true)
                    }
                }
                n++
            } catch (_: Throwable) {
            }
        }
        return n
    }
}
