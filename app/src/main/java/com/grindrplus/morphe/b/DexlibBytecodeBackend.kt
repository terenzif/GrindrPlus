package com.grindrplus.morphe.b

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11x
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max

/**
 * MorpheApp-shaped `bytecodePatch` executor inside the orchestrator (ADR 0007).
 *
 * Uses the same dexlib2 writer as [app.morphe.patcher] / patches-template, without
 * shipping a separate Morphe Manager or pulling the desktop Patcher (apktool, etc.).
 * Feature instruction recipes stay fail-soft; the default patch injects a marker class
 * so install produces a real DEX mutation. Invalid DEX falls back to the dry-run scan.
 */
class DexlibBytecodeBackend(
    private val fingerprints: List<BytecodeFingerprint> = BytecodePilotCatalog.fingerprints,
    private val patches: List<MorpheBytecodePatch> = MorpheBytecodePatches.defaults,
    private val scanFallback: MorpheBytecodeBackend = FingerprintScanBytecodeBackend(),
) : MorpheBytecodeBackend {
    override fun applyBytecodePatches(apk: File, print: Print) {
        if (!apk.isFile) {
            print("Morphe B bytecode: apk missing, skip rewrite")
            return
        }

        val dexBytes = linkedMapOf<String, ByteArray>()
        ZipFile(apk).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!isDexEntry(entry.name)) continue
                dexBytes[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
            }
        }
        if (dexBytes.isEmpty()) {
            print("Morphe B bytecode: no classes*.dex, skip rewrite")
            scanFallback.applyBytecodePatches(apk, print)
            return
        }

        val parsed = mutableListOf<ParsedDex>()
        for ((name, bytes) in dexBytes) {
            val dex = try {
                DexBackedDexFile.fromInputStream(Opcodes.getDefault(), ByteArrayInputStream(bytes))
            } catch (t: Throwable) {
                print("Morphe B bytecode: DEX parse failed ($name), scan fallback")
                scanFallback.applyBytecodePatches(apk, print)
                return
            }
            parsed += ParsedDex(name, dex.opcodes, dex.classes.toMutableList())
        }

        val located = linkedMapOf<String, MutableSet<String>>()
        for (dex in parsed) {
            for (fp in fingerprints) {
                val hits = mutableSetOf<String>()
                for (cls in dex.classes) {
                    val needles = fp.needles.filter { needle ->
                        needle.isNotEmpty() && classUsesString(cls, needle)
                    }
                    if (needles.isNotEmpty()) {
                        hits += cls.type
                        located.getOrPut(fp.id) { mutableSetOf() }.add(cls.type)
                    }
                }
                if (hits.isNotEmpty()) {
                    print("Morphe B bytecode: ${fp.id} located ${hits.joinToString()}")
                }
            }
        }

        val host = parsed.first()
        val ctx = DexPatchContext(host)
        val patchResults = mutableListOf<Pair<String, String>>()
        for (patch in patches) {
            try {
                patch.execute(ctx)
                patchResults += patch.name to "applied"
                print("Morphe B bytecode: patch ${patch.name} applied")
            } catch (t: Throwable) {
                patchResults += patch.name to "miss"
                print("Morphe B bytecode: patch ${patch.name} soft-fail: ${t.message}")
            }
        }

        val updates = mutableMapOf<String, ByteArray>()
        for (dex in parsed) {
            updates[dex.name] = writeDex(dex)
        }
        val report = buildReportJson(located, patchResults)
        updates[FingerprintScanBytecodeBackend.REPORT_ENTRY] = report.toByteArray(Charsets.UTF_8)
        ApkAssetInjector.replaceEntries(apk, updates)
        print("Morphe B bytecode: rewrite complete (mode=rewrite, patches=${patchResults.size})")
    }

    private fun buildReportJson(
        located: Map<String, Set<String>>,
        patchResults: List<Pair<String, String>>,
    ): String {
        val fps = fingerprints.joinToString(",\n") { fp ->
            val classes = located[fp.id].orEmpty().sorted()
            val status = if (classes.isNotEmpty()) "hit" else "miss"
            val classJson = classes.joinToString(",") { "\"${jsonEscape(it)}\"" }
            """
            |    {
            |      "id": "${jsonEscape(fp.id)}",
            |      "patchId": "${jsonEscape(fp.patchId)}",
            |      "status": "$status",
            |      "classes": [$classJson]
            |    }
            """.trimMargin()
        }
        val patchJson = patchResults.joinToString(",\n") { (name, status) ->
            """
            |    {
            |      "name": "${jsonEscape(name)}",
            |      "status": "$status"
            |    }
            """.trimMargin()
        }
        return """
            |{
            |  "schemaVersion": 2,
            |  "backend": "DexlibBytecodeBackend",
            |  "mode": "rewrite",
            |  "fingerprints": [
            |$fps
            |  ],
            |  "patches": [
            |$patchJson
            |  ]
            |}
        """.trimMargin()
    }

    companion object {
        const val MARKER_TYPE = "Lcom/grindrplus/morphe/b/BytecodeApplied;"
        const val MARKER_VALUE = "morphe-b"

        fun isDexEntry(name: String): Boolean =
            name == "classes.dex" ||
                (name.startsWith("classes") && name.endsWith(".dex") && !name.contains('/'))

        fun jsonEscape(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        fun classUsesString(cls: ClassDef, needle: String): Boolean {
            for (method in cls.methods) {
                val impl = method.implementation ?: continue
                for (insn in impl.instructions) {
                    if (insn !is ReferenceInstruction) continue
                    val ref = insn.reference
                    if (ref is StringReference && ref.string == needle) return true
                }
            }
            return false
        }

        fun writeDex(dex: ParsedDex): ByteArray {
            val file = object : DexFile {
                override fun getClasses() = dex.classes.toSet()
                override fun getOpcodes() = dex.opcodes
            }
            val store = MemoryDataStore()
            DexPool.writeTo(store, file)
            return store.data
        }

        fun markerClass(): ClassDef {
            val method = ImmutableMethod(
                MARKER_TYPE,
                "id",
                emptyList(),
                "Ljava/lang/String;",
                AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
                emptySet(),
                emptySet(),
                MutableMethodImplementation(1).also { impl ->
                    impl.addInstruction(
                        BuilderInstruction21c(Opcode.CONST_STRING, 0, ImmutableStringReference(MARKER_VALUE)),
                    )
                    impl.addInstruction(BuilderInstruction11x(Opcode.RETURN_OBJECT, 0))
                },
            )
            return ImmutableClassDef(
                MARKER_TYPE,
                AccessFlags.PUBLIC.value,
                "Ljava/lang/Object;",
                emptyList(),
                null,
                emptySet(),
                emptyList(),
                emptyList(),
                listOf(method),
                emptyList(),
            )
        }
    }
}

data class ParsedDex(
    val name: String,
    val opcodes: Opcodes,
    val classes: MutableList<ClassDef>,
)

/** Mirrors MorpheApp `bytecodePatch { execute { ... } }` without the desktop Patcher. */
data class MorpheBytecodePatch(
    val name: String,
    val execute: (DexPatchContext) -> Unit,
)

object MorpheBytecodePatches {
    val marker = MorpheBytecodePatch("morphe-b-dex-marker") { ctx ->
        ctx.injectMarkerClass()
    }

    val defaults: List<MorpheBytecodePatch> = listOf(marker)
}

class DexPatchContext(private val dex: ParsedDex) {
    fun injectMarkerClass() {
        if (dex.classes.any { it.type == DexlibBytecodeBackend.MARKER_TYPE }) return
        dex.classes += DexlibBytecodeBackend.markerClass()
    }

    fun findMethodsUsingString(needle: String): List<MethodRef> {
        val out = mutableListOf<MethodRef>()
        for (cls in dex.classes) {
            for (method in cls.methods) {
                if (methodUsesString(method, needle)) {
                    out += MethodRef(cls.type, method)
                }
            }
        }
        return out
    }

    fun prependReturnTrue(ref: MethodRef) {
        if (ref.method.returnType != "Z") {
            throw IllegalArgumentException("${ref.classType}->${ref.method.name} is not boolean")
        }
        val impl = ref.method.implementation
            ?: throw IllegalArgumentException("${ref.method.name} has no implementation")
        val registers = max(impl.registerCount, 1)
        val mutable = MutableMethodImplementation(registers)
        mutable.addInstruction(BuilderInstruction11n(Opcode.CONST_4, 0, 1))
        mutable.addInstruction(BuilderInstruction11x(Opcode.RETURN, 0))
        val rewritten = ImmutableMethod(
            ref.method.definingClass,
            ref.method.name,
            ref.method.parameters,
            ref.method.returnType,
            ref.method.accessFlags,
            ref.method.annotations,
            ref.method.hiddenApiRestrictions,
            mutable,
        )
        replaceMethod(ref.classType, ref.method, rewritten)
    }

    private fun replaceMethod(classType: String, original: Method, replacement: Method) {
        val idx = dex.classes.indexOfFirst { it.type == classType }
        if (idx < 0) throw IllegalStateException("class $classType missing")
        val cls = dex.classes[idx]
        val same = { m: Method ->
            m.name == original.name &&
                m.parameterTypes == original.parameterTypes &&
                m.returnType == original.returnType
        }
        dex.classes[idx] = ImmutableClassDef(
            cls.type,
            cls.accessFlags,
            cls.superclass,
            cls.interfaces,
            cls.sourceFile,
            cls.annotations,
            cls.staticFields,
            cls.instanceFields,
            cls.directMethods.map { if (same(it)) replacement else it },
            cls.virtualMethods.map { if (same(it)) replacement else it },
        )
    }

    private fun methodUsesString(method: Method, needle: String): Boolean {
        val impl = method.implementation ?: return false
        for (insn in impl.instructions) {
            if (insn !is ReferenceInstruction) continue
            val ref = insn.reference
            if (ref is StringReference && ref.string == needle) return true
        }
        return false
    }
}

data class MethodRef(val classType: String, val method: Method)
