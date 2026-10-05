package com.grindrplus.core

/**
 * Redacts high-noise / sensitive fragments from stack traces before they hit bridge logs.
 *
 * Keeps GrindrPlus + Grindr frames; collapses long platform runs; strips JWT-like tokens.
 */
object StacktraceSanitizer {
    private val jwtLike =
        Regex("""(?i)\b(?:bearer\s+)?(?:grindr3\s+)?[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}""")
    private val authHeader = Regex("""(?i)(authorization\s*[:=]\s*)(\S+)""")
    private const val MAX_LINES = 48

    private val keepPackages = listOf(
        "com.grindrplus.",
        "com.grindrapp.android.",
    )

    private val collapsePrefixes = listOf(
        "java.",
        "javax.",
        "jdk.",
        "kotlin.",
        "kotlinx.",
        "android.",
        "androidx.",
        "dalvik.",
        "com.android.",
        "libcore.",
        "sun.",
        "org.junit.",
        "org.robolectric.",
    )

    fun sanitize(stacktrace: String): String {
        if (stacktrace.isBlank()) return stacktrace

        val redacted = authHeader.replace(jwtLike.replace(stacktrace, "***REDACTED_TOKEN***")) { m ->
            m.groupValues[1] + "***REDACTED***"
        }

        val out = ArrayList<String>(MAX_LINES + 4)
        var collapsed = 0

        fun flushCollapse() {
            if (collapsed > 0) {
                out.add("\t... $collapsed platform frames ...")
                collapsed = 0
            }
        }

        for (rawLine in redacted.lineSequence()) {
            if (out.size >= MAX_LINES) {
                out.add("\t... truncated ...")
                break
            }
            val line = rawLine.trimEnd()
            val atIndex = line.indexOf("at ")
            if (atIndex < 0) {
                flushCollapse()
                out.add(line)
                continue
            }
            val frame = line.substring(atIndex + 3)
            when {
                keepPackages.any { frame.startsWith(it) } -> {
                    flushCollapse()
                    out.add(line)
                }
                collapsePrefixes.any { frame.startsWith(it) } -> collapsed++
                else -> {
                    flushCollapse()
                    out.add(line)
                }
            }
        }
        flushCollapse()
        return out.joinToString("\n")
    }
}
