package com.gpp.morphe.b

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PairIpStaticDecryptTest {

    @Test
    fun detectPairIp_falseWithoutMarkers() {
        val dir = createTempDir(prefix = "pairip-neg-")
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("assets/hello.txt"))
            zos.write("hi".toByteArray())
            zos.closeEntry()
        }
        assertFalse(PairIpStaticDecrypt.detectPairIp(apk))
    }

    @Test
    fun detectPairIp_trueWithPairipcore() {
        val dir = createTempDir(prefix = "pairip-pos-")
        val apk = File(dir, "base.apk")
        ZipOutputStream(apk.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(ByteArray(16) { 0x7f })
            zos.closeEntry()
        }
        assertTrue(PairIpStaticDecrypt.detectPairIp(apk))
    }

    @Test
    fun apply_splicesNativeWhenPackPresent() {
        val dir = createTempDir(prefix = "pairip-apply-")
        val base = File(dir, "base.apk")
        ZipOutputStream(base.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libpairipcore.so"))
            zos.write(byteArrayOf(1, 2, 3, 4))
            zos.closeEntry()
        }
        val arm64 = File(dir, "split_config.arm64_v8a.apk")
        ZipOutputStream(arm64.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("lib/arm64-v8a/libsqliteJni.so"))
            zos.write(ByteArray(32) { 0xAA.toByte() })
            zos.closeEntry()
        }
        val pack = File(dir, "pairip-decrypt/185656").also { it.mkdirs() }
        val decrypted = ByteArray(32) { 0x55 }
        File(pack, "libsqliteJni.so").writeBytes(decrypted)
        File(pack, "pack.json").writeText("""{"versionCode":185656,"libs":["libsqliteJni.so"]}""")

        val logs = mutableListOf<String>()
        val report = PairIpStaticDecrypt.apply(listOf(base, arm64)) { logs += it }
        assertTrue(report.present)
        assertTrue(report.packApplied)
        assertTrue(report.natives.contains("libsqliteJni.so"))

        // Re-read spliced entry
        java.util.zip.ZipFile(arm64).use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("lib/arm64-v8a/libsqliteJni.so")).readBytes()
            assertTrue(bytes.contentEquals(decrypted))
        }
        assertTrue(logs.any { it.contains("splice") })
    }
}
