package com.gpp.manager.vector

import android.content.Context
import android.content.Intent
import com.gpp.BuildConfig
import com.gpp.core.Constants.GRINDR_PACKAGE_NAME
import com.gpp.core.LogSource
import com.gpp.core.Logger
import java.util.concurrent.TimeUnit

/**
 * Enable/disable this Alloy module for Grindr via Vector CLI (rooted).
 *
 * Hot reload (libxposed API 102) replaces module *code* in an already-injected process;
 * it does **not** enable/disable scope. For app-scoped Grindr, force-stop + next launch
 * applies the new enable state (no device reboot).
 *
 * CLI shape (Vector ≥3043): `modules enable|disable`, `scope add`.
 */
object VectorModToggle {
    private val CLI_CANDIDATES = listOf(
        "/data/adb/lspd/cli",
        "/data/adb/vector/cli",
    )

    data class Result(
        val ok: Boolean,
        val message: String,
    )

    fun resolveCli(): String? =
        CLI_CANDIDATES.firstOrNull { path ->
            try {
                runSu("test -x $path && echo OK").stdout.trim() == "OK"
            } catch (_: Exception) {
                false
            }
        }

    fun isCliAvailable(): Boolean = resolveCli() != null

    /**
     * @param enable true to enable module + ensure Grindr in scope; false to disable module
     */
    fun setModdingEnabled(context: Context, enable: Boolean, userId: Int = 0): Result {
        val cli = resolveCli()
            ?: return Result(
                false,
                "Vector CLI not found. Open Vector and enable GrindMod Alloy for Grindr manually.",
            )

        val modulePkg = BuildConfig.APPLICATION_ID
        val commands = if (enable) {
            listOf(
                "$cli modules enable $modulePkg",
                "$cli scope add $modulePkg $GRINDR_PACKAGE_NAME/$userId",
            )
        } else {
            listOf("$cli modules disable $modulePkg")
        }

        for (cmd in commands) {
            val r = runSu(cmd)
            if (r.exitCode != 0) {
                Logger.w(
                    "Vector CLI failed ($cmd): ${r.stderr.ifBlank { r.stdout }}",
                    LogSource.MANAGER,
                )
                return Result(
                    false,
                    "Vector CLI error. Use Vector Manager instead. " +
                        "(${r.stderr.ifBlank { r.stdout }.take(120)})",
                )
            }
        }

        forceStopGrindr()
        if (enable) {
            launchGrindr(context)
        }

        return Result(
            true,
            if (enable) {
                "Modding enabled — Grindr restarted with hooks."
            } else {
                "Modding disabled — open Grindr for stock."
            },
        )
    }

    fun forceStopGrindr() {
        runSu("am force-stop $GRINDR_PACKAGE_NAME")
    }

    fun launchGrindr(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(GRINDR_PACKAGE_NAME)
                ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.w("Could not launch Grindr: ${e.message}", LogSource.MANAGER)
        }
    }

    private data class SuResult(val exitCode: Int, val stdout: String, val stderr: String)

    private fun runSu(command: String): SuResult {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(false)
            .start()
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val finished = process.waitFor(45, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return SuResult(-1, stdout, "timeout")
        }
        return SuResult(process.exitValue(), stdout, stderr)
    }
}
