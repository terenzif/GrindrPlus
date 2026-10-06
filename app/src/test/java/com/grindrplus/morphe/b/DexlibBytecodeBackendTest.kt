package com.grindrplus.morphe.b

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction11n
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.writer.io.MemoryDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DexlibBytecodeBackendTest {
    @Test
    fun prependsReturnTrueOnStringFingerprint() {
        val dir = createTempDir("morpheb-dexlib")
        val apk = File(dir, "base.apk")
        writeApkWithDex(apk, fixtureDexReturningFalse())

        val patch = MorpheBytecodePatch("example-gate") { ctx ->
            ctx.findMethodsUsingString("showBannerAds").forEach { ctx.prependReturnTrue(it) }
        }
        DexlibBytecodeBackend(patches = listOf(patch)).applyBytecodePatches(apk) { }

        val dex = loadFirstDex(apk)
        val method = dex.classes
            .first { it.type == "Lcom/example/AdLoader;" }
            .methods
            .first { it.name == "showAds" }
        val first = method.implementation!!.instructions.first() as Instruction11n
        assertEquals(Opcode.CONST_4, first.opcode)
        assertEquals(1, first.narrowLiteral)
    }

    @Test
    fun injectsMarkerClassAndRewriteReport() {
        val dir = createTempDir("morpheb-marker")
        val apk = File(dir, "base.apk")
        writeApkWithDex(apk, fixtureDexReturningFalse())

        val logs = mutableListOf<String>()
        DexlibBytecodeBackend().applyBytecodePatches(apk) { logs += it }

        val dex = loadFirstDex(apk)
        assertTrue(dex.classes.any { it.type == DexlibBytecodeBackend.MARKER_TYPE })
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(FingerprintScanBytecodeBackend.REPORT_ENTRY)
            assertNotNull(entry)
            val text = zip.getInputStream(entry).bufferedReader().readText()
            assertTrue(text.contains("DexlibBytecodeBackend"))
            assertTrue(text.contains("\"mode\": \"rewrite\""))
            assertTrue(text.contains("morphe-b-dex-marker"))
        }
        assertTrue(logs.any { it.contains("rewrite") })
    }

    @Test
    fun invalidDexFallsBackToByteScan() {
        val dir = createTempDir("morpheb-fallback")
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zos.write("xxFavoritesFragmentyyFavoritesHeaderDatazz".toByteArray())
            zos.closeEntry()
        }
        DexlibBytecodeBackend().applyBytecodePatches(apk) { }
        ZipFile(apk).use { zip ->
            val text = zip.getInputStream(zip.getEntry(FingerprintScanBytecodeBackend.REPORT_ENTRY))
                .bufferedReader().readText()
            assertTrue(text.contains("FingerprintScanBytecodeBackend"))
            assertTrue(text.contains("\"status\": \"hit\""))
            assertFalse(text.contains("\"mode\": \"rewrite\""))
        }
    }

    private fun writeApkWithDex(apk: File, dex: ByteArray) {
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            zos.write(dex)
            zos.closeEntry()
        }
    }

    private fun loadFirstDex(apk: File): DexFile {
        ZipFile(apk).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("classes.dex")).readBytes()
            return DexBackedDexFile.fromInputStream(
                Opcodes.getDefault(),
                ByteArrayInputStream(bytes),
            )
        }
    }

    private fun fixtureDexReturningFalse(): ByteArray {
        val method = ImmutableMethod(
            "Lcom/example/AdLoader;",
            "showAds",
            emptyList(),
            "Z",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            ImmutableMethodImplementation(
                1,
                listOf(
                    ImmutableInstruction21c(
                        Opcode.CONST_STRING,
                        0,
                        ImmutableStringReference("showBannerAds"),
                    ),
                    ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
                    ImmutableInstruction11x(Opcode.RETURN, 0),
                ),
                emptyList(),
                emptyList(),
            ),
        )
        val classDef = ImmutableClassDef(
            "Lcom/example/AdLoader;",
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
        val dexFile = object : DexFile {
            override fun getClasses() = setOf(classDef)
            override fun getOpcodes() = Opcodes.getDefault()
        }
        val store = MemoryDataStore()
        DexPool.writeTo(store, dexFile)
        return store.data
    }
}
