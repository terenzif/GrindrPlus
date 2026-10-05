package com.grindrplus.morphe.b

import com.grindrplus.manager.installation.Print
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Morphe B static applicator: mutates input APKs (base preferred) before Morphe A / LSPatch.
 *
 * Embeds `assets/grindrplus/morphe_b.json` listing applied / deferred patch ids.
 * Feature bytecode for skipped DEX sites is delivered via RUNTIME_REMAP hooks.
 */
class MorpheBPatchEngine(
    private val catalog: List<MorpheBPatchDescriptor> = MorpheBCatalog.patches,
) {
    fun apply(inputApks: List<File>, print: Print): MorpheBApplyResult {
        val base = inputApks.find {
            it.name == "base.apk" ||
                it.name.startsWith("base.apk") ||
                it.name.contains("grindr-base") ||
                it.name.endsWith(".apk")
        } ?: inputApks.firstOrNull()
            ?: throw IOException("Morphe B: no input APK")

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

        injectAsset(base, "assets/grindrplus/morphe_b.json", payload.toByteArray(Charsets.UTF_8))
        print("Morphe B: marker written (applied=${applied.size}, deferred=${deferred.size})")
        return MorpheBApplyResult(applied = applied, deferred = deferred, baseApk = base)
    }

    private fun injectAsset(apk: File, entryName: String, data: ByteArray) {
        val tmp = File(apk.parentFile, "${apk.name}.morpheb.tmp")
        ZipFile(apk).use { zip ->
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name == entryName || entry.isDirectory) continue
                    // Always recompress — avoids STORED CRC/size mismatches across ZipFile/ZOS.
                    zos.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                zos.putNextEntry(ZipEntry(entryName))
                zos.write(data)
                zos.closeEntry()
            }
        }
        if (!apk.delete()) {
            throw IOException("Morphe B: cannot replace ${apk.absolutePath}")
        }
        if (!tmp.renameTo(apk)) {
            tmp.copyTo(apk, overwrite = true)
            tmp.delete()
        }
    }

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

data class MorpheBApplyResult(
    val applied: List<String>,
    val deferred: List<String>,
    val baseApk: File,
)
