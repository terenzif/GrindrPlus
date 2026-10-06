package com.grindrplus.manager.play

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.grindrplus.core.Logger
import org.json.JSONObject
import java.util.Locale
import java.util.Properties

/**
 * Anonymous Play session via Aurora OSS token dispenser (same approach as Aurora Store).
 * Does **not** launch the Aurora Store app — only reuses its Play/auth protocol.
 */
object PlayStoreSession {

    const val GRINDR_PACKAGE = "com.grindrapp.android"

    /** Public Aurora OSS dispenser (anonymous AUTH tokens). */
    const val DEFAULT_DISPENSER_URL = "https://auroraoss.com/api/auth"

    private const val DEVICE_ASSET = "play_device_px_9a.properties"

    fun loadDeviceProperties(context: Context): Properties {
        val props = Properties()
        context.assets.open(DEVICE_ASSET).use { props.load(it) }
        return props
    }

    /**
     * Soft-fails callers should wrap; throws on hard auth failure.
     */
    fun buildAnonymousAuth(
        context: Context,
        httpClient: PlayHttpClient,
        dispenserUrl: String = DEFAULT_DISPENSER_URL,
    ): AuthData {
        val properties = loadDeviceProperties(context)
        val body = propertiesToJson(properties).toByteArray(Charsets.UTF_8)

        val response = httpClient.postAuth(dispenserUrl, body)
        if (!response.isSuccessful) {
            throw IllegalStateException(
                "Play dispenser HTTP ${response.code}: ${response.errorString.ifBlank { "auth failed" }}"
            )
        }

        val json = JSONObject(String(response.responseBytes, Charsets.UTF_8))
        val email = json.optString("email").ifBlank {
            throw IllegalStateException("Play dispenser response missing email")
        }
        val token = json.optString("authToken").ifBlank {
            json.optString("auth")
        }.ifBlank {
            throw IllegalStateException("Play dispenser response missing authToken")
        }

        Logger.i("Play anonymous session for $email (dispenser)")

        return AuthHelper.using(httpClient).build(
            email = email,
            token = token,
            tokenType = AuthHelper.Token.AUTH,
            isAnonymous = true,
            properties = properties,
            locale = Locale.getDefault(),
        )
    }

    private fun propertiesToJson(properties: Properties): String {
        val obj = JSONObject()
        for (name in properties.stringPropertyNames()) {
            obj.put(name, properties.getProperty(name))
        }
        return obj.toString()
    }
}
