package com.gpp.morphe.b

import java.io.File
import java.io.IOException

/**
 * Morphe B static applicator: mutates input APKs (base preferred) before Morphe A / LSPatch.
 *
 * Embeds `assets/grindrplus/morphe_b.json` listing applied / deferred patch ids.
 * Feature bytecode for skipped DEX sites is delivered via RUNTIME_REMAP hooks today;
 * [bytecodeBackend] is the ADR 0007 hook (default: dexlib2 rewrite + scan fallback).
 */
class MorpheBPatchEngine(
    private val catalog: List<MorpheBPatchDescriptor> = MorpheBCatalog.patches,
    private val bytecodeBackend: MorpheBytecodeBackend = DexlibBytecodeBackend(),
) {
    fun apply(inputApks: List<File>, print: Print): MorpheBApplyResult {
        val base = selectBaseApk(inputApks)

        print("Morphe B: applying static marker to ${base.name}")
        val applied = mutableListOf<String>()
        val deferred = mutableListOf<String>()

        catalog.forEach { desc ->
            when (desc.delivery) {
                MorpheBDelivery.STATIC_RESOURCE,
                MorpheBDelivery.RUNTIME_REMAP,
                -> applied += desc.id
                MorpheBDelivery.DEFERRED -> deferred += desc.id
            }
        }

        try {
            bytecodeBackend.applyBytecodePatches(base, print)
        } catch (t: Throwable) {
            print("Morphe B bytecode soft-fail: ${t.message}")
        }

        // Build JSON without org.json so JVM unit tests (no Android JSON mocks) pass.
        val patchesJson = catalog.joinToString(",\n") { d ->
            val hook = d.replacesHookName?.let { "\"$it\"" } ?: "null"
            """
            |    {
            |      "id": "${d.id}",
            |      "title": ${jsonString(d.title)},
            |      "delivery": "${d.delivery.name}",
            |      "replacesHookName": $hook,
            |      "notes": ${jsonString(d.notes)}
            |    }
            """.trimMargin()
        }
        val payload = """
            |{
            |  "schemaVersion": 1,
            |  "engine": "MorpheBPatchEngine",
            |  "applied": [${applied.joinToString(",") { "\"$it\"" }}],
            |  "deferred": [${deferred.joinToString(",") { "\"$it\"" }}],
            |  "patches": [
            |$patchesJson
            |  ]
            |}
        """.trimMargin()

        ApkAssetInjector.inject(base, "assets/grindrplus/morphe_b.json", payload.toByteArray(Charsets.UTF_8))
        print("Morphe B: marker written (applied=${applied.size}, deferred=${deferred.size})")
        return MorpheBApplyResult(applied = applied, deferred = deferred, baseApk = base)
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    companion object {
        /** Prefer true base APK; never treat "any .apk" as a match (avoids config splits). */
        fun selectBaseApk(inputApks: List<File>): File {
            inputApks.find { it.name == "base.apk" || it.name.startsWith("base.apk") }
                ?.let { return it }
            inputApks.find { it.name.contains("grindr-base", ignoreCase = true) }
                ?.let { return it }
            return inputApks.maxByOrNull { it.length() }
                ?: throw IOException("Morphe B: no input APK")
        }
    }
}

data class MorpheBApplyResult(
    val applied: List<String>,
    val deferred: List<String>,
    val baseApk: File,
)
