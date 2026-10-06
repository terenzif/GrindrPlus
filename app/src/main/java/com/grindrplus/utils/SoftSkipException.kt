package com.grindrplus.utils

/**
 * Thrown from [Hook.init] to signal an intentional soft-skip (not a failure).
 * [HookManager] records runtime status `skipped` instead of `failed` / `enabled`.
 */
class SoftSkipException(message: String) : Exception(message)
