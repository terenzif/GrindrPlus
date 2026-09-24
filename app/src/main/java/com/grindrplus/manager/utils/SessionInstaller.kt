package com.grindrplus.manager.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

/**
 * Helper class for installing APK files using the PackageInstaller API
 */
class SessionInstaller {
    companion object {
        private const val TAG = "SessionInstaller"
        private const val ACTION_INSTALL_COMPLETE = "com.grindrplus.INSTALL_COMPLETE"
        private const val DEFAULT_BUFFER_SIZE = 8192
        /** User confirm dialog can sit forever if dismissed without a status callback. */
        private val INSTALL_TIMEOUT = 3.minutes
    }

    /**
     * Install multiple APK files (split APKs) using PackageInstaller
     *
     * @param context The application context
     * @param apks List of APK files to install
     * @param silent Whether to install silently (requires privileged permissions)
     * @param callback Optional callback to report success/failure
     * @return True if installation was successful, false otherwise
     */
    suspend fun installApks(
        context: Context,
        apks: List<File>,
        silent: Boolean = false,
        callback: ((success: Boolean, message: String) -> Unit)? = null,
        log: (String) -> Unit,
    ): Boolean = try {
        withTimeout(INSTALL_TIMEOUT) {
            installApksInternal(context, apks, silent, callback, log)
        }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        val message =
            "Installer timed out after ${INSTALL_TIMEOUT.inWholeMinutes} minutes " +
                "(confirm or dismiss the system install prompt, then tap Install again)"
        log("ERROR: $message")
        Timber.Forest.tag(TAG).e(message)
        callback?.invoke(false, message)
        throw IOException(message, e)
    }

    private suspend fun installApksInternal(
        context: Context,
        apks: List<File>,
        silent: Boolean,
        callback: ((success: Boolean, message: String) -> Unit)?,
        log: (String) -> Unit,
    ): Boolean = suspendCancellableCoroutine { continuation ->
        if (apks.isEmpty()) {
            val message = "No APK files provided."
            Timber.Forest.tag(TAG).e(message)
            callback?.invoke(false, message)
            continuation.resumeWithException(IOException(message))
            return@suspendCancellableCoroutine
        }

        val missingApks = apks.filter { !it.exists() || it.length() <= 0 }
        if (missingApks.isNotEmpty()) {
            val message =
                "Missing or empty APK files: ${missingApks.joinToString { it.absolutePath }}"
            Timber.Forest.tag(TAG).e(message)
            log("ERROR: $message")
            callback?.invoke(false, message)
            continuation.resumeWithException(IOException(message))
            return@suspendCancellableCoroutine
        }

        val packageInstaller = context.packageManager.packageInstaller

        val params =
            PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setInstallReason(PackageManager.INSTALL_REASON_USER)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST)
                    if (silent) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }
            }

        val sessionId = try {
            packageInstaller.createSession(params)
        } catch (e: IOException) {
            val message = "Failed to create install session: ${e.message}"
            Timber.Forest.tag(TAG).e(e, message)
            log("ERROR: $message")
            callback?.invoke(false, message)
            continuation.resumeWithException(e)
            return@suspendCancellableCoroutine
        }

        fun finishOnce(block: () -> Unit) {
            if (continuation.isActive) block()
        }

        val installCompleteReceiver = object : BroadcastReceiver() {
            @SuppressLint("UnsafeIntentLaunch")
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    val status = intent.getIntExtra(
                        PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE
                    )

                    val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        ?: "Unknown status"

                    Timber.Forest.tag(TAG).d("Installation status: $status, message: $message")
                    log("DEBUG: $message")

                    when (status) {
                        PackageInstaller.STATUS_SUCCESS -> {
                            finishOnce {
                                callback?.invoke(true, "Installation successful")
                                log("Installed!")
                                try {
                                    context.unregisterReceiver(this)
                                } catch (_: Exception) {
                                }
                                continuation.resume(true)
                            }
                        }

                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            Timber.Forest.tag(TAG).d("Installation requires user confirmation")
                            log(
                                "DEBUG: Waiting for system install confirmation " +
                                    "(approve update / uninstall conflict if prompted)"
                            )
                            val confirmationIntent =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    intent.getParcelableExtra(
                                        Intent.EXTRA_INTENT,
                                        Intent::class.java
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                                }
                            if (confirmationIntent != null) {
                                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivity(confirmationIntent)
                                } catch (e: Exception) {
                                    val errorMsg =
                                        "Failed to start installer activity: ${e.message}"
                                    log("ERROR: $errorMsg")
                                    Timber.Forest.tag(TAG).e(e, errorMsg)
                                    finishOnce {
                                        try {
                                            context.unregisterReceiver(this)
                                        } catch (_: Exception) {
                                        }
                                        callback?.invoke(false, errorMsg)
                                        continuation.resumeWithException(IOException(errorMsg))
                                    }
                                }
                            } else {
                                val errorMsg = "Missing confirmation intent"
                                log("ERROR: $errorMsg")
                                Timber.Forest.tag(TAG).e(errorMsg)
                                finishOnce {
                                    try {
                                        context.unregisterReceiver(this)
                                    } catch (_: Exception) {
                                    }
                                    callback?.invoke(false, errorMsg)
                                    continuation.resumeWithException(IOException(errorMsg))
                                }
                            }
                        }

                        PackageInstaller.STATUS_FAILURE,
                        PackageInstaller.STATUS_FAILURE_ABORTED,
                        PackageInstaller.STATUS_FAILURE_BLOCKED,
                        PackageInstaller.STATUS_FAILURE_CONFLICT,
                        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
                        PackageInstaller.STATUS_FAILURE_INVALID,
                        PackageInstaller.STATUS_FAILURE_STORAGE,
                            -> {
                            val errorMsg = "Installation failed: $message (code: $status)"
                            Timber.Forest.tag(TAG).e(errorMsg)
                            finishOnce {
                                try {
                                    context.unregisterReceiver(this)
                                } catch (_: Exception) {
                                }
                                callback?.invoke(false, errorMsg)
                                continuation.resumeWithException(IOException(errorMsg))
                            }
                        }

                        else -> {
                            val errorMsg = "Unknown status code: $status - $message"
                            Timber.Forest.tag(TAG).e(errorMsg)
                            finishOnce {
                                try {
                                    context.unregisterReceiver(this)
                                } catch (_: Exception) {
                                }
                                callback?.invoke(false, errorMsg)
                                continuation.resumeWithException(IOException(errorMsg))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.Forest.tag(TAG).e(e, "Error in broadcast receiver")
                    finishOnce {
                        try {
                            context.unregisterReceiver(this)
                        } catch (_: Exception) {
                        }
                        callback?.invoke(false, "Error processing installation result: ${e.message}")
                        continuation.resumeWithException(e)
                    }
                }
            }
        }

        continuation.invokeOnCancellation {
            try {
                packageInstaller.abandonSession(sessionId)
            } catch (_: Exception) {
            }
            try {
                context.unregisterReceiver(installCompleteReceiver)
            } catch (_: Exception) {
            }
        }

        try {
            val intent = Intent(ACTION_INSTALL_COMPLETE).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            ContextCompat.registerReceiver(
                context,
                installCompleteReceiver,
                IntentFilter(ACTION_INSTALL_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )

            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)

            packageInstaller.openSession(sessionId).use { session ->
                for (apk in apks) {
                    Timber.Forest.tag(TAG)
                        .d("Writing APK to session: ${apk.name} (${apk.length()} bytes)")

                    apk.inputStream().use { inputStream ->
                        session.openWrite(apk.name, 0, apk.length()).use { outputStream ->
                            inputStream.copyTo(outputStream, DEFAULT_BUFFER_SIZE)
                            session.fsync(outputStream)
                        }
                    }
                }

                Timber.Forest.tag(TAG).d("Committing installation session...")
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            try {
                packageInstaller.abandonSession(sessionId)
            } catch (_: Exception) {
            }

            try {
                context.unregisterReceiver(installCompleteReceiver)
            } catch (_: Exception) {
            }

            val message = "Installation failed: ${e.message}"
            Timber.Forest.tag(TAG).e(e, message)
            callback?.invoke(false, message)
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }
    }
}
