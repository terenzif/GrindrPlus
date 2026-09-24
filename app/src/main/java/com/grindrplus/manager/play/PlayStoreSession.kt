package com.grindrplus.manager.play

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.grindrplus.BuildConfig
import com.grindrplus.core.Logger
import org.json.JSONObject
import java.util.Locale
import java.util.Properties

/**
 * Anonymous Play session via token dispenser + gplayapi (Aurora Store protocol).
 *
 * Does **not** launch the Aurora Store app. The public Aurora OSS dispenser is
 * behind Cloudflare and often returns **403** for non–Aurora-Store User-Agents —
 * we present as a store-like client for the auth POST only.
 *
 * Best-effort: 403/429 → fail soft with Custom Files guidance (no Grindr CDN).
 */
object PlayStoreSession {

    const val GRINDR_PACKAGE = "com.grindrapp.android"

    /** Public Aurora OSS dispenser (anonymous AUTH / AAS tokens). */
    const val DEFAULT_DISPENSER_URL = "https://auroraoss.com/api/auth"

    private const val DEVICE_ASSET = "play_device_px_9a.properties"

    /**
     * Cloudflare on auroraoss.com filters unknown UAs (seen as HTTP 403 on device).
     * Aurora Store itself sends `{applicationId}-{versionName}-{versionCode}`.
     */
    private fun dispenserUserAgents(): List<String> {
        val our = "com.grindrplus-${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}"
        return listOf(
            "com.aurora.store-4.7.4-76",
            "com.aurora.store-4.6.4-76",
            our,
        ).distinct()
    }

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

        var lastError: String? = null
        for (ua in dispenserUserAgents()) {
            httpClient.userAgent = ua
            val response = httpClient.postAuth(dispenserUrl, body)
            if (!response.isSuccessful) {
                val snippet = responseBodySnippet(response.responseBytes)
                lastError = "HTTP ${response.code}" +
                    (response.errorString.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "") +
                    (snippet?.let { " ($it)" } ?: "")
                Logger.w("Play dispenser auth failed with UA=$ua → $lastError")
                // Don't hammer on hard blocks
                if (response.code == 403 || response.code == 429 || response.code == 503) {
                    continue
                }
                continue
            }

            return parseAuthResponse(response.responseBytes, httpClient, properties)
        }

        throw IllegalStateException(
            "Play dispenser auth failed (${lastError ?: "unknown"}). " +
                "Cloudflare often blocks non-store clients (403) or rate-limits (429). " +
                "Use Custom Files with a local Grindr APK, or retry later."
        )
    }

    private fun parseAuthResponse(
        bytes: ByteArray,
        httpClient: PlayHttpClient,
        properties: Properties,
    ): AuthData {
        val json = JSONObject(String(bytes, Charsets.UTF_8))
        val email = json.optString("email").ifBlank {
            throw IllegalStateException("Play dispenser response missing email")
        }

        val authToken = json.optString("authToken").ifBlank { json.optString("auth") }
        val aasToken = json.optString("aasToken")

        val (token, tokenType) = when {
            authToken.isNotBlank() -> authToken to AuthHelper.Token.AUTH
            aasToken.isNotBlank() -> aasToken to AuthHelper.Token.AAS
            else -> throw IllegalStateException("Play dispenser response missing authToken/aasToken")
        }

        Logger.i("Play anonymous session for $email (token=${tokenType.name})")

        return AuthHelper.using(httpClient).build(
            email = email,
            token = token,
            tokenType = tokenType,
            isAnonymous = true,
            properties = properties,
            locale = Locale.getDefault(),
        )
    }

    private fun responseBodySnippet(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val text = String(bytes, Charsets.UTF_8).trim()
        if (text.isEmpty()) return null
        // Avoid logging tokens if CF somehow returned HTML + fragment
        if (text.contains("authToken", ignoreCase = true) ||
            text.contains("aasToken", ignoreCase = true)
        ) {
            return "json-ok-but-unexpected-status"
        }
        return text.take(120).replace('\n', ' ')
    }

    private fun propertiesToJson(properties: Properties): String {
        val obj = JSONObject()
        for (name in properties.stringPropertyNames()) {
            obj.put(name, properties.getProperty(name))
        }
        return obj.toString()
    }
}
