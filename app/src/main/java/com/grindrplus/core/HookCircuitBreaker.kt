package com.grindrplus.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple in-memory circuit breaker for hook init soft-fail.
 * Opens after [THRESHOLD] consecutive failures; closes on success.
 */
object HookCircuitBreaker {
    private const val THRESHOLD = 5

    private val failures = ConcurrentHashMap<String, AtomicInteger>()

    fun recordFailure(key: String) {
        failures.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordSuccess(key: String) {
        failures[key]?.set(0)
    }

    fun isOpen(key: String): Boolean {
        return (failures[key]?.get() ?: 0) >= THRESHOLD
    }
}
