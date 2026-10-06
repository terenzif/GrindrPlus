package com.gpp.morphe.b

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Seam for MorpheApp-style `bytecodePatch` application (ADR 0007).
 * Default engine uses [DexlibBytecodeBackend]; [FingerprintScanBytecodeBackend] is the
 * invalid-DEX fallback and a dry-run test seam.
 */
fun interface MorpheBytecodeBackend {
    /**
     * Mutate [apk] in place (or return a replacement path written over [apk]).
     * Soft-fail: throw only for IO; fingerprint misses should log via [print] and return.
     */
    fun applyBytecodePatches(apk: File, print: Print)
}

/** Placeholder when bytecode scanning must be disabled. */
object NoOpMorpheBytecodeBackend : MorpheBytecodeBackend {
    override fun applyBytecodePatches(apk: File, print: Print) {
        print("Morphe B bytecode: no-op backend")
    }
}

/**
 * Dry-run STATIC bytecode pilot: scan DEX entries for [BytecodePilotCatalog] needles,
 * write `assets/grindrplus/bytecode_scan.json`, soft-miss without aborting install.
 *
 * Not a full MorpheApp `bytecodePatch` rewriter yet — proves the ADR 0007 seam end-to-end.
 */
class FingerprintScanBytecodeBackend(
    private val fingerprints: List<BytecodeFingerprint> = BytecodePilotCatalog.fingerprints,
) : MorpheBytecodeBackend {
    override fun applyBytecodePatches(apk: File, print: Print) {
        if (!apk.isFile) {
            print("Morphe B bytecode: apk missing, skip scan")
            return
        }

        val dexHits = mutableMapOf<String, MutableSet<String>>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!isDexEntry(entry.name)) continue
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                for (fp in fingerprints) {
                    val matched = fp.needles.filter { needle ->
                        needle.isNotEmpty() && indexOfAscii(bytes, needle) >= 0
                    }
                    if (matched.isNotEmpty()) {
                        dexHits.getOrPut(fp.id) { mutableSetOf() }.addAll(matched)
                    }
                }
            }
        }

        val results = fingerprints.map { fp ->
            val hits = dexHits[fp.id].orEmpty().sorted()
            val status = if (hits.isNotEmpty()) "hit" else "miss"
            print("Morphe B bytecode: ${fp.id} → $status${if (hits.isNotEmpty()) " (${hits.joinToString()})" else ""}")
            Triple(fp, status, hits)
        }

        val hitCount = results.count { it.second == "hit" }
        val payload = buildReportJson(results)
        ApkAssetInjector.inject(apk, REPORT_ENTRY, payload.toByteArray(Charsets.UTF_8))
        print("Morphe B bytecode: scan report written (hits=$hitCount/${fingerprints.size})")
    }

    private fun buildReportJson(
        results: List<Triple<BytecodeFingerprint, String, List<String>>>,
    ): String {
        val items = results.joinToString(",\n") { (fp, status, hits) ->
            val hitJson = hits.joinToString(",") { "\"${jsonEscape(it)}\"" }
            """
            |    {
            |      "id": "${jsonEscape(fp.id)}",
            |      "patchId": "${jsonEscape(fp.patchId)}",
            |      "status": "$status",
            |      "matchedNeedles": [$hitJson]
            |    }
            """.trimMargin()
        }
        return """
            |{
            |  "schemaVersion": 1,
            |  "backend": "FingerprintScanBytecodeBackend",
            |  "mode": "dry-run",
            |  "fingerprints": [
            |$items
            |  ]
            |}
        """.trimMargin()
    }

    companion object {
        const val REPORT_ENTRY = "assets/grindrplus/bytecode_scan.json"

        private fun isDexEntry(name: String): Boolean =
            name == "classes.dex" ||
                (name.startsWith("classes") && name.endsWith(".dex") && !name.contains('/'))

        /** ASCII / MUTF-8 subset search — enough for pilot needles. */
        private fun indexOfAscii(haystack: ByteArray, needle: String): Int {
            val n = needle.toByteArray(Charsets.UTF_8)
            if (n.isEmpty() || n.size > haystack.size) return -1
            outer@ for (i in 0..(haystack.size - n.size)) {
                for (j in n.indices) {
                    if (haystack[i + j] != n[j]) continue@outer
                }
                return i
            }
            return -1
        }

        private fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}

/** Shared APK asset rewrite used by Morphe B marker + bytecode scan. */
internal object ApkAssetInjector {
    fun inject(apk: File, entryName: String, data: ByteArray) {
        replaceEntries(apk, mapOf(entryName to data))
    }

    fun replaceEntries(apk: File, updates: Map<String, ByteArray>) {
        if (updates.isEmpty()) return
        val tmp = File(apk.parentFile, "${apk.name}.bytecode.tmp")
        ZipFile(apk).use { zip ->
            ZipOutputStream(tmp.outputStream().buffered()).use { zos ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name in updates) continue
                    zos.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
                for ((name, data) in updates) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
            }
        }
        if (!apk.delete()) {
            throw IOException("Morphe B bytecode: cannot replace ${apk.absolutePath}")
        }
        if (!tmp.renameTo(apk)) {
            tmp.copyTo(apk, overwrite = true)
            tmp.delete()
        }
    }
}
