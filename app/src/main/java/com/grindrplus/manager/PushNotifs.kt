package com.grindrplus.manager

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.text.HtmlCompat
import com.google.gson.JsonParser
import com.grindrplus.R
import com.grindrplus.core.Config
import com.grindrplus.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class GPlusMessage(
    val id: String,
    val content: String,
    val timestamp: Long
)

/**
 * Lightweight announcement feed for BridgeService push checks.
 * Not Telegram — fork-maintained JSON on GitHub. News UI uses Releases + wiki.
 */
const val CHANNEL_PING_URL =
    "https://raw.githubusercontent.com/terenzif/GrindrPlus/refs/heads/master/news.json"

val tgMessages = MutableStateFlow<List<GPlusMessage>>(listOf())

/** news.json historically carries Telegram-style HTML — strip for system notifications. */
fun plainTextFromNewsHtml(raw: String): String {
    val withoutPush = raw.replace("#push", "", ignoreCase = true)
    val spanned = HtmlCompat.fromHtml(withoutPush, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return spanned.toString()
        .replace('\u00A0', ' ')
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

suspend fun fetchNotifs(context: Context) = withContext(Dispatchers.IO) {
    try {
        val client = OkHttpClient.Builder()
            .callTimeout(30.seconds.toJavaDuration())
            .connectTimeout(10.seconds.toJavaDuration())
            .readTimeout(15.seconds.toJavaDuration())
            .build()

        val request = okhttp3.Request.Builder()
            .url(CHANNEL_PING_URL)
            .header(
                "User-Agent",
                "GrindrPlus/terenzif (Android; news-ping)"
            )
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logger.w("fetchNotifs soft-fail: HTTP ${response.code}")
                return@use
            }

            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                Logger.w("fetchNotifs soft-fail: empty body")
                return@use
            }

            tgMessages.value =
                JsonParser.parseString(body).asJsonArray
                    .map { it.asJsonObject }
                    .map { obj ->
                        GPlusMessage(
                            obj.get("message_id").asString,
                            obj.get("text").asString,
                            obj.get("date").asLong
                        )
                    }
                    .filterNot { it.content.isBlank() }
                    .sortedBy { it.id }.toList()

            val msg = tgMessages.value.lastOrNull() ?: return@use
            if (Config.get("last_push_id", "") != msg.id) {
                Config.put("last_push_id", msg.id)
                if (msg.content.contains("#push")) {
                    sendNotification(context, plainTextFromNewsHtml(msg.content))
                } else {
                    sendNotification(context)
                }
            }
        }
    } catch (e: Exception) {
        Logger.w("fetchNotifs soft-fail: ${e.message}")
    }
}

fun sendNotification(
    context: Context,
    msg: String = "GrindrPlus update — open News for wiki & Releases."
) {
    val nm = context.getSystemService(NotificationManager::class.java)

    val channel = android.app.NotificationChannel(
        "update_gplus",
        "GPlus Updates",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Notifications for GrindrPlus fork updates"
    }

    nm.createNotificationChannel(channel)

    val plain = plainTextFromNewsHtml(msg).ifBlank {
        "GrindrPlus update — open News for wiki & Releases."
    }

    NotificationCompat.Builder(context, "update_gplus").apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle("GrindrPlus News")
        setContentText(plain)
        setStyle(NotificationCompat.BigTextStyle().bigText(plain))
        setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        setAutoCancel(true)
        setPriority(NotificationCompat.PRIORITY_MAX)
    }.build().also { nm.notify(1, it) }
}
