package com.gpp.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

class TaskScheduler(private val scope: CoroutineScope) {
    private val runningJobs = ConcurrentHashMap<String, Job>()

    fun periodic(
        name: String,
        intervalMs: Long,
        action: suspend () -> Unit
    ): Job {
        val maxDelayMs = min(intervalMs * 16, 5 * 60 * 1000L)
        val job = scope.launch {
            var delayMs = intervalMs
            while (true) {
                try {
                    action()
                    delayMs = intervalMs
                    delay(delayMs)
                } catch (e: Exception) {
                    Logger.e("$name failed: ${e.message}", LogSource.MODULE)
                    Logger.writeThrowable(e)
                    delay(delayMs)
                    delayMs = min(delayMs * 2, maxDelayMs)
                }
            }
        }
        runningJobs[name] = job
        return job
    }

    fun once(name: String, action: suspend () -> Unit): Job {
        val job = scope.launch {
            try {
                action()
            } catch (e: Exception) {
                Logger.e("$name failed: ${e.message}", LogSource.MODULE)
                Logger.writeThrowable(e)
            } finally {
                runningJobs.remove(name)
            }
        }
        runningJobs[name] = job
        return job
    }

    fun withRetry(
        name: String,
        retries: Int = 3,
        delayMs: Long = 1000,
        action: suspend () -> Unit
    ): Job {
        val job = scope.launch {
            try {
                repeat(retries) { attempt ->
                    try {
                        action()
                        return@launch
                    } catch (e: Exception) {
                        if (attempt == retries - 1) {
                            Logger.e("$name failed after $retries attempts", LogSource.MODULE)
                            Logger.writeThrowable(e)
                            throw e
                        } else {
                            Logger.w("$name retry ${attempt+1}/$retries", LogSource.MODULE)
                            delay(delayMs)
                        }
                    }
                }
            } finally {
                runningJobs.remove(name)
            }
        }
        runningJobs[name] = job
        return job
    }

    fun isTaskRunning(name: String): Boolean {
        return runningJobs[name]?.isActive == true
    }

    fun cancelTask(name: String) {
        runningJobs[name]?.cancel()
        runningJobs.remove(name)
    }

    fun cancelAllTasks() {
        runningJobs.values.forEach { it.cancel() }
        runningJobs.clear()
    }
}
