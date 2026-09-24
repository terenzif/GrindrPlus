package com.grindrplus.manager.play

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.grindrplus.core.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

/**
 * One-shot Google account picker. On Android 8+ [AccountManager.getAccountsByType] is empty
 * until the user grants visibility — usually via [AccountManager.newChooseAccountIntent].
 */
class PlayGoogleAccountPickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val choose = AccountManager.newChooseAccountIntent(
            /* selectedAccount = */ null,
            /* allowableAccounts = */ null,
            /* allowableAccountTypes = */ arrayOf(GOOGLE_ACCOUNT_TYPE),
            /* descriptionOverrideText = */ "Select the Google account used for Play Store",
            /* authTokenType = */ PLAY_AUTH_TOKEN_TYPE,
            /* requiredFeatures = */ null,
            /* addAccountOptions = */ null,
        )
        @Suppress("DEPRECATION")
        startActivityForResult(choose, REQ_CHOOSE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQ_CHOOSE) {
            val email = if (resultCode == RESULT_OK) {
                data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            } else {
                null
            }
            Logger.i("Google account picker result: ${email ?: "(cancelled)"}")
            PlayAccountPickBridge.complete(email)
        }
        finish()
    }

    companion object {
        private const val REQ_CHOOSE = 4401
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val PLAY_AUTH_TOKEN_TYPE =
            "oauth2:https://www.googleapis.com/auth/googleplay"

        fun intent(context: Context): Intent =
            Intent(context, PlayGoogleAccountPickActivity::class.java).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
    }
}

internal object PlayAccountPickBridge {
    @Volatile
    private var pending: CompletableDeferred<String?>? = null

    suspend fun pick(activity: Activity): String? {
        pending?.cancel()
        val deferred = CompletableDeferred<String?>()
        pending = deferred
        activity.startActivity(PlayGoogleAccountPickActivity.intent(activity))
        return try {
            withTimeout(2.minutes) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            Logger.w("Google account picker timed out")
            complete(null)
            null
        }
    }

    fun complete(email: String?) {
        val d = pending
        pending = null
        d?.complete(email)
    }
}
