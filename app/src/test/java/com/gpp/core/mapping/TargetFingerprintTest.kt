package com.gpp.core.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetFingerprintTest {

    private interface UserSession

    private open class Base

    private class SessionImpl : Base(), UserSession {
        @JvmField
        val cascade: String = "x"
    }

    private data class Fail(val failValue: String)

    @Test
    fun matches_blankIsAlwaysOk() {
        val r = TargetFingerprint.matches(SessionImpl::class.java, null)
        assertTrue(r.matched)
        assertEquals(TargetFingerprint.Result.Mode.NONE, r.mode)
    }

    @Test
    fun matches_implements() {
        val ok = TargetFingerprint.matches(SessionImpl::class.java, "implements UserSession")
        assertTrue(ok.matched)
        assertEquals(TargetFingerprint.Result.Mode.IMPLEMENTS, ok.mode)

        val bad = TargetFingerprint.matches(SessionImpl::class.java, "implements MissingIface")
        assertFalse(bad.matched)
    }

    @Test
    fun matches_field() {
        val ok = TargetFingerprint.matches(SessionImpl::class.java, "field String")
        assertTrue(ok.matched)
        assertEquals(TargetFingerprint.Result.Mode.FIELD, ok.mode)
    }

    @Test
    fun matches_dataClassMarker() {
        val ok = TargetFingerprint.matches(Fail::class.java, "Fail(failValue=")
        assertTrue(ok.matched)
        assertEquals(TargetFingerprint.Result.Mode.DATA_CLASS, ok.mode)

        val bad = TargetFingerprint.matches(Fail::class.java, "Success(successValue=")
        assertFalse(bad.matched)
    }

    @Test
    fun matches_structPrefix() {
        val hash = TargetFingerprint.structuralHash(SessionImpl::class.java)
        val ok = TargetFingerprint.matches(SessionImpl::class.java, "struct:${hash.take(12)}")
        assertTrue(ok.matched)
        assertEquals(TargetFingerprint.Result.Mode.STRUCT, ok.mode)

        val bad = TargetFingerprint.matches(SessionImpl::class.java, "struct:deadbeefdead")
        assertFalse(bad.matched)
    }

    @Test
    fun matches_advisoryStringPoolSoftPasses() {
        val r = TargetFingerprint.matches(SessionImpl::class.java, "grindr3/")
        assertTrue(r.matched)
        assertEquals(TargetFingerprint.Result.Mode.UNSUPPORTED, r.mode)
    }
}
