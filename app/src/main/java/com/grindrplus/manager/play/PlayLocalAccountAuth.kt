package com.grindrplus.manager.play

import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AuthHelper
import com.grindrplus.core.Logger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.Properties
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Local Google / microG Play token — Aurora Store personal-account path:
 *
 * 1. [AccountManager.getAuthToken] with `oauth2:https://www.googleapis.com/auth/googleplay`
 * 2. Spoof caller as Play Store via `overridePackage` + `overrideCertificate`
 * 3. **Stock GMS:** OAuth access token → [PlayAc2dm] AAS (same as Aurora WebView login)
 * 4. **microG:** AccountManager token often already usable as AAS — skip AC2DM when exchange fails
 * 5. [AuthHelper] `Token.AAS` → `generateToken` → Play AUTH for FDFE
 *
 * Why: anonymous dispenser accounts often get delivery **status 3** on dating/age-gated apps.
 */
object PlayLocalAccountAuth {

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    private const val PLAY_STORE_PACKAGE = "com.android.vending"
    private const val PLAY_AUTH_TOKEN_TYPE =
        "oauth2:https://www.googleapis.com/auth/googleplay"
    private const val PREFS = "play_local_auth"
    private const val PREF_AAS_PREFIX = "aas:"

    /**
     * Platform Android / Play Store signing cert (Base64), identical to Aurora Store's
     * `CertUtil.GOOGLE_PLAY_CERT` — required for AccountManager overrideCertificate.
     */
    private const val GOOGLE_PLAY_CERT =
        "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK"

    fun googleAccountEmails(context: Context): List<String> = try {
        AccountManager.get(context)
            .getAccountsByType(GOOGLE_ACCOUNT_TYPE)
            .map { it.name }
            .distinct()
    } catch (e: Exception) {
        Logger.w("Cannot list Google accounts: ${e.message}")
        emptyList()
    }

    fun findActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    /**
     * @return AuthData for the first on-device Google account that yields a Play session,
     * or null if none / user denies / GMS blocks override.
     */
    suspend fun tryBuildAuthData(
        context: Context,
        httpClient: PlayHttpClient,
        properties: Properties,
        activity: Activity? = findActivity(context),
        preferredEmail: String? = null,
    ): AuthData? {
        var emails = googleAccountEmails(context)
        // Android 8+: Google accounts are invisible until the user picks one for this app.
        if (emails.isEmpty() && activity != null) {
            Logger.i("No visible Google accounts — launching account picker for Play token")
            val picked = try {
                PlayAccountPickBridge.pick(activity)
            } catch (e: Exception) {
                Logger.w("Account picker failed: ${e.message}")
                null
            }
            if (!picked.isNullOrBlank()) {
                emails = listOf(picked) + googleAccountEmails(context).filter { it != picked }
            } else {
                Logger.i("Account picker cancelled or empty")
                return null
            }
        }
        if (emails.isEmpty()) {
            Logger.i("No on-device Google accounts for local Play token")
            return null
        }
        val ordered = buildList {
            preferredEmail?.takeIf { it in emails }?.let { add(it) }
            addAll(emails.filter { it != preferredEmail })
        }.distinct()
        var lastError: Exception? = null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        for (email in ordered) {
            // Cached AAS from a previous successful AC2DM (avoids re-consent).
            cachedAas(prefs, email)?.let { aas ->
                try {
                    Logger.i("Play local session from cached AAS for $email")
                    return buildWithAas(httpClient, email, aas, properties)
                } catch (e: Exception) {
                    Logger.w("Cached AAS invalid for $email: ${e.message}")
                    clearCachedAas(prefs, email)
                }
            }

            try {
                Logger.i("Requesting local Play oauth token for $email (AccountManager)...")
                val oauthToken = fetchPlayAuthToken(context, email, activity)
                val aasToken = resolveAasToken(email, oauthToken)
                val authData = buildWithAas(httpClient, email, aasToken, properties)
                cacheAas(prefs, email, aasToken)
                Logger.i("Play local session for $email (AccountManager→AAS→AUTH)")
                return authData
            } catch (e: Exception) {
                lastError = e
                Logger.w("Local Play token failed for $email: ${e.message}")
            }
        }
        Logger.w("Local Play auth unavailable (${lastError?.message ?: "no accounts"})")
        return null
    }

    private fun resolveAasToken(email: String, accountManagerToken: String): String {
        // Stock GMS: oauth access token must go through AC2DM (Aurora WebView path).
        if (PlayAc2dm.looksLikeOauthAccessToken(accountManagerToken)) {
            val aas = PlayAc2dm.exchangeOauthForAas(email, accountManagerToken)
                ?: throw IllegalStateException(
                    "AC2DM failed to mint AAS from OAuth token for $email"
                )
            return aas
        }
        // Prefer AC2DM when it works; otherwise treat AccountManager token as AAS (microG).
        PlayAc2dm.exchangeOauthForAas(email, accountManagerToken)?.let { return it }
        Logger.i("Using AccountManager token as AAS for $email (no AC2DM / microG-style)")
        return accountManagerToken
    }

    private fun buildWithAas(
        httpClient: PlayHttpClient,
        email: String,
        aasToken: String,
        properties: Properties,
    ): AuthData = AuthHelper.using(httpClient).build(
        email = email,
        token = aasToken,
        tokenType = AuthHelper.Token.AAS,
        isAnonymous = false,
        properties = properties,
        locale = Locale.US,
    )

    private fun cachedAas(prefs: SharedPreferences, email: String): String? =
        prefs.getString(PREF_AAS_PREFIX + email, null)?.takeIf { it.isNotBlank() }

    private fun cacheAas(prefs: SharedPreferences, email: String, aas: String) {
        prefs.edit().putString(PREF_AAS_PREFIX + email, aas).apply()
    }

    private fun clearCachedAas(prefs: SharedPreferences, email: String) {
        prefs.edit().remove(PREF_AAS_PREFIX + email).apply()
    }

    /**
     * Mirrors Aurora [GoogleAccountTokenProvider]: Activity overload lets GMS/microG show
     * consent and only then deliver [AccountManager.KEY_AUTHTOKEN] — do not treat KEY_INTENT
     * as a hard failure when an Activity was supplied.
     */
    private suspend fun fetchPlayAuthToken(
        context: Context,
        email: String,
        activity: Activity?,
        oldToken: String? = null,
    ): String = suspendCancellableCoroutine { continuation ->
        try {
            val am = AccountManager.get(context)
            if (oldToken != null) {
                am.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, oldToken)
            }
            val account = Account(email, GOOGLE_ACCOUNT_TYPE)
            val options = Bundle().apply {
                putString("overridePackage", PLAY_STORE_PACKAGE)
                putByteArray(
                    "overrideCertificate",
                    Base64.decode(GOOGLE_PLAY_CERT, Base64.DEFAULT),
                )
            }
            val handler = Handler(Looper.getMainLooper())
            val callback = android.accounts.AccountManagerCallback<Bundle> { future ->
                if (!continuation.isActive) return@AccountManagerCallback
                try {
                    val result = future.result
                    @Suppress("DEPRECATION")
                    val intent = result.getParcelable(AccountManager.KEY_INTENT)
                        as? android.content.Intent
                    val token = result.getString(AccountManager.KEY_AUTHTOKEN)
                    when {
                        !token.isNullOrBlank() -> continuation.resume(token)
                        intent != null && activity == null -> {
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "Google Play token needs an Activity for consent UI ($email)"
                                )
                            )
                        }
                        intent != null -> {
                            // Activity overload should have handled this; last resort.
                            Logger.w(
                                "Play token still has KEY_INTENT for $email — " +
                                    "approve the Google prompt, then retry Install"
                            )
                            activity!!.startActivity(intent)
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "User consent required for Google Play token ($email)"
                                )
                            )
                        }
                        else -> continuation.resumeWithException(
                            IllegalStateException("Auth token is null for $email")
                        )
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
            if (activity != null) {
                am.getAuthToken(
                    account,
                    PLAY_AUTH_TOKEN_TYPE,
                    options,
                    activity,
                    callback,
                    handler,
                )
            } else {
                am.getAuthToken(
                    account,
                    PLAY_AUTH_TOKEN_TYPE,
                    options,
                    true,
                    callback,
                    handler,
                )
            }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }
}
