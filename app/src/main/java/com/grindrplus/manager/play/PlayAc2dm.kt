package com.grindrplus.manager.play

import com.grindrplus.core.Logger
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * OAuth → AAS exchange — port of Aurora Store [AC2DMTask].
 *
 * Stock GMS [AccountManager] returns an OAuth2 access token for
 * `oauth2:https://www.googleapis.com/auth/googleplay`. [AuthHelper] expects an **AAS**
 * master token when `Token.AAS` is used (`generateToken` → Play AUTH). Aurora’s WebView
 * Google login always runs this exchange; microG AccountManager tokens often already work
 * as AAS and can skip it.
 */
object PlayAc2dm {

    private const val TOKEN_AUTH_URL = "https://android.clients.google.com/auth"
    private const val BUILD_VERSION_SDK = 28
    private const val PLAY_SERVICES_VERSION_CODE = 19629032
    /** SHA-1 of the platform Google Play Services signing cert (Aurora / GMS callerSig). */
    private const val GMS_CALLER_SIG = "38918a453d07199354f8b19af05ec6562ced5788"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * @return AAS master token, or null if Google rejected / network failed.
     */
    fun exchangeOauthForAas(email: String, oAuthToken: String): String? {
        val locale = Locale.US
        val form = FormBody.Builder()
            .add("lang", locale.toString().replace('_', '-'))
            .add("google_play_services_version", PLAY_SERVICES_VERSION_CODE.toString())
            .add("sdk_version", BUILD_VERSION_SDK.toString())
            .add("device_country", locale.country.lowercase(Locale.US))
            .add("Email", email)
            .add("service", "ac2dm")
            .add("get_accountid", "1")
            .add("ACCESS_TOKEN", "1")
            .add("callerPkg", "com.google.android.gms")
            .add("add_account", "1")
            .add("Token", oAuthToken)
            .add("callerSig", GMS_CALLER_SIG)
            .add("droidguard_results", "null")
            .build()

        val request = Request.Builder()
            .url(TOKEN_AUTH_URL)
            .header("app", "com.google.android.gms")
            .header("User-Agent", "")
            .post(form)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Logger.w("AC2DM HTTP ${response.code}: ${body.take(120)}")
                    return null
                }
                val map = parseResponse(body)
                val error = map["Error"] ?: map["error"]
                if (!error.isNullOrBlank()) {
                    Logger.w("AC2DM Error=$error for $email")
                    return null
                }
                val aas = map["Token"]
                if (aas.isNullOrBlank()) {
                    Logger.w("AC2DM response missing Token for $email")
                    null
                } else {
                    Logger.i("AC2DM AAS minted for $email")
                    aas
                }
            }
        } catch (e: Exception) {
            Logger.w("AC2DM exchange failed: ${e.message}")
            null
        }
    }

    /** True when the AccountManager string looks like an OAuth2 access token (needs AC2DM). */
    fun looksLikeOauthAccessToken(token: String): Boolean =
        token.startsWith("ya29.", ignoreCase = false) ||
            token.startsWith("1//", ignoreCase = false)

    private fun parseResponse(response: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (line in response.split('\n', '\r')) {
            if (line.isBlank()) continue
            val idx = line.indexOf('=')
            if (idx <= 0) continue
            out[line.substring(0, idx)] = line.substring(idx + 1)
        }
        return out
    }
}
