package com.gpp.morphe.b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class MorpheBCatalogTest {
    @Test
    fun catalogCoversSkippedFeatureHooks() {
        val names = MorpheBCatalog.patches.mapNotNull { it.replacesHookName }.toSet()
        assertTrue(names.contains("Chat terminal"))
        assertTrue(names.contains("Video calls"))
        assertTrue(names.contains("Disable shuffle"))
        assertTrue(names.contains("Notification Alerts"))
        assertTrue(names.contains("Favorites"))
    }

    @Test
    fun engineInjectsMarkerAsset() {
        val dir = createTempDir("morpheb")
        val apk = File(dir, "base.apk")
        // minimal zip with one entry
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF"))
            zos.write("Manifest-Version: 1.0\n".toByteArray())
            zos.closeEntry()
        }
        val result = MorpheBPatchEngine(
            bytecodeBackend = NoOpMorpheBytecodeBackend,
        ).apply(listOf(apk)) { _ -> }
        assertTrue(result.applied.isNotEmpty())
        assertTrue(result.deferred.isNotEmpty())
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry("assets/grindrplus/morphe_b.json")
            assertNotNull(entry)
            val text = zip.getInputStream(entry).bufferedReader().readText()
            assertTrue(text.contains("MorpheBPatchEngine"))
            assertTrue(text.contains("empty-calls"))
        }
        assertEquals(apk, result.baseApk)
    }

    @Test
    fun selectBaseApk_prefersNamedBaseOverFirstSplit() {
        val dir = createTempDir("morpheb-base")
        val config = File(dir, "config.xxhdpi.apk").also { it.writeText("tiny") }
        val base = File(dir, "base.apk").also { it.writeText("larger-base-content-here") }
        assertEquals(base, MorpheBPatchEngine.selectBaseApk(listOf(config, base)))
    }

    @Test
    fun fingerprintScanWritesReportOnHit() {
        val dir = createTempDir("morpheb-bc")
        val apk = File(dir, "base.apk")
        java.util.zip.ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(java.util.zip.ZipEntry("classes.dex"))
            // Not a real DEX — byte-scan dry-run only needs the needle present.
            zos.write("xxFavoritesFragmentyyfragment_favorite_recycler_viewzz".toByteArray())
            zos.closeEntry()
        }
        val logs = mutableListOf<String>()
        FingerprintScanBytecodeBackend().applyBytecodePatches(apk) { logs += it }
        ZipFile(apk).use { zip ->
            val entry = zip.getEntry(FingerprintScanBytecodeBackend.REPORT_ENTRY)
            assertNotNull(entry)
            val text = zip.getInputStream(entry).bufferedReader().readText()
            assertTrue(text.contains("FingerprintScanBytecodeBackend"))
            assertTrue(text.contains("\"status\": \"hit\""))
            assertTrue(text.contains("favorites-cascade"))
        }
        assertTrue(logs.any { it.contains("favorites-cascade") && it.contains("hit") })
    }
}
