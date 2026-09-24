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
 * Play session for gplayapi — Aurora Store protocol without launching Aurora Store.
 *
 * Order (same idea as Aurora’s Google vs Anonymous login):
 * 1. **Local** Google account via [AccountManager] ([PlayLocalAccountAuth]) when present
 * 2. Else **anonymous** token dispenser (`https://auroraoss.com/api/auth`)
 *
 * Dispenser: Cloudflare often **403**s non–store UAs — we rotate store-like agents.
 * Dating apps: anonymous delivery often **status 3**; local account is the reliable path.
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
     * Prefer on-device Google Play token; fall back to anonymous dispenser.
     * May show a system consent UI the first time (approve, then retry Install).
     */
    suspend fun buildPreferredAuth(
        context: Context,
        httpClient: PlayHttpClient,
        preferLocal: Boolean = true,
        dispenserUrl: String = DEFAULT_DISPENSER_URL,
    ): AuthData {
        val properties = loadDeviceProperties(context)
        if (preferLocal) {
            val emails = PlayLocalAccountAuth.googleAccountEmails(context)
            if (emails.isNotEmpty()) {
                Logger.i(
                    "Trying local Play token for ${emails.size} Google account(s): " +
                        emails.joinToString()
                )
            }
            val local = PlayLocalAccountAuth.tryBuildAuthData(
                context = context,
                httpClient = httpClient,
                properties = properties,
                activity = PlayLocalAccountAuth.findActivity(context),
            )
            if (local != null) return local
            if (emails.isNotEmpty()) {
                Logger.w(
                    "Local Play auth failed for on-device account(s) — " +
                        "falling back to anonymous dispenser (dating apps often status-3)"
                )
            }
        }
        return buildAnonymousAuth(context, httpClient, dispenserUrl, properties)
    }

    /**
     * Soft-fails callers should wrap; throws on hard auth failure.
     */
    fun buildAnonymousAuth(
        context: Context,
        httpClient: PlayHttpClient,
        dispenserUrl: String = DEFAULT_DISPENSER_URL,
        properties: Properties = loadDeviceProperties(context),
    ): AuthData {
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

        // Locale must match the spoofed device config (bundled Pixel props are US).
        // Using the phone's locale with a US Pixel spoof is a common cause of delivery
        // status 3 ("App not purchased / unavailable in your country") for free apps —
        // same constraint Aurora Store enforces via matchesActiveSpoof().
        return AuthHelper.using(httpClient).build(
            email = email,
            token = token,
            tokenType = tokenType,
            isAnonymous = true,
            properties = properties,
            locale = Locale.US,
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
