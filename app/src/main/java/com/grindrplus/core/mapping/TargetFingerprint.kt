package com.grindrplus.core.mapping

import java.security.MessageDigest

/**
 * Runtime checks for [MappingSymbol.fingerprint].
 *
 * Pack fingerprints are primarily JADX search hints. This validator supports:
 * - `struct:<sha256-hex>` — structural hash of declared methods/fields/interfaces (prefix OK)
 * - `implements <Type>` — interface / superclass simple or FQ name
 * - `field <Type>` — declared field type name
 * - `TypeName(field=` — Kotlin data-class style marker → match [Class.getSimpleName]
 * - other strings — best-effort type-graph substring; unverifiable string-pool markers soft-pass
 */
object TargetFingerprint {
    data class Result(
        val matched: Boolean,
        val mode: Mode,
        val detail: String = "",
    ) {
        enum class Mode {
            NONE,
            STRUCT,
            IMPLEMENTS,
            FIELD,
            DATA_CLASS,
            SUBSTRING,
            UNSUPPORTED,
        }
    }

    fun matches(clazz: Class<*>, fingerprint: String?): Result {
        if (fingerprint.isNullOrBlank()) {
            return Result(matched = true, mode = Result.Mode.NONE)
        }

        val fp = fingerprint.trim()
        return when {
            fp.startsWith("struct:") -> {
                val expected = fp.removePrefix("struct:").trim().lowercase()
                if (expected.isEmpty()) {
                    return Result(matched = true, mode = Result.Mode.STRUCT, detail = "empty struct hash")
                }
                val actual = structuralHash(clazz)
                val ok = actual.startsWith(expected)
                Result(
                    matched = ok,
                    mode = Result.Mode.STRUCT,
                    detail = "expectedPrefix=$expected actual=${actual.take(16)}…",
                )
            }

            fp.startsWith("implements ") -> {
                val iface = fp.removePrefix("implements ").trim()
                val ok = collectTypes(clazz).any { typeMatches(it, iface) }
                Result(matched = ok, mode = Result.Mode.IMPLEMENTS, detail = iface)
            }

            fp.startsWith("field ") -> {
                val hint = fp.removePrefix("field ").trim()
                val ok = runCatching {
                    clazz.declaredFields.any { typeMatches(it.type, hint) }
                }.getOrDefault(false)
                Result(matched = ok, mode = Result.Mode.FIELD, detail = hint)
            }

            '(' in fp -> {
                val typeName = fp.substringBefore('(').trim()
                val ok = clazz.simpleName == typeName ||
                    clazz.name.endsWith(".$typeName") ||
                    clazz.name.endsWith(typeName)
                Result(matched = ok, mode = Result.Mode.DATA_CLASS, detail = typeName)
            }

            else -> {
                val hit = collectTypes(clazz).any { type ->
                    type.name.contains(fp) || type.simpleName.contains(fp)
                }
                if (hit) {
                    Result(matched = true, mode = Result.Mode.SUBSTRING, detail = fp)
                } else {
                    // String-pool markers (e.g. "grindr3/") need DEX parse — do not false-fail.
                    Result(
                        matched = true,
                        mode = Result.Mode.UNSUPPORTED,
                        detail = "advisory fingerprint not verified at runtime: $fp",
                    )
                }
            }
        }
    }

    /** SHA-256 hex of a stable structural descriptor for [clazz]. */
    fun structuralHash(clazz: Class<*>): String {
        val parts = buildList {
            runCatching {
                clazz.declaredMethods.sortedWith(compareBy({ it.name }, { it.parameterTypes.size }))
                    .forEach { m ->
                        add(
                            "m:${m.name}(${m.parameterTypes.joinToString(",") { it.name }})" +
                                m.returnType.name
                        )
                    }
            }
            runCatching {
                clazz.declaredFields.sortedBy { it.name }.forEach { f ->
                    add("f:${f.name}:${f.type.name}")
                }
            }
            clazz.interfaces.sortedBy { it.name }.forEach { add("i:${it.name}") }
            clazz.superclass?.let { add("s:${it.name}") }
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(parts.joinToString("\n").toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    private fun collectTypes(clazz: Class<*>): Sequence<Class<*>> = sequence {
        yield(clazz)
        var parent: Class<*>? = clazz.superclass
        while (parent != null && parent != Any::class.java) {
            yield(parent)
            parent = parent.superclass
        }
        for (iface in clazz.interfaces) {
            yield(iface)
        }
    }

    private fun typeMatches(type: Class<*>, hint: String): Boolean {
        if (hint.isEmpty()) return false
        return type.simpleName == hint ||
            type.name == hint ||
            type.name.endsWith(".$hint") ||
            type.name.contains(hint)
    }
}
