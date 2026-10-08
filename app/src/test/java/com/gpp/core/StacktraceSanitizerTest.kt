package com.gpp.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StacktraceSanitizerTest {

    @Test
    fun sanitize_redactsJwtLikeTokens() {
        val input = "Authorization: Grindr3 eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.signature_tail_here"
        val out = StacktraceSanitizer.sanitize(input)
        assertFalse(out.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertTrue(out.contains("REDACTED"))
    }

    @Test
    fun sanitize_keepsModuleAndGrindrFrames() {
        val input = """
            java.lang.RuntimeException: boom
            	at com.gpp.core.http.Interceptor.invokeMethodSafe(Interceptor.kt:90)
            	at java.lang.reflect.Method.invoke(Method.java:571)
            	at kotlinx.coroutines.internal.DispatchedContinuation.resumeWith(DispatchedContinuation.kt:1)
            	at com.grindrapp.android.network.Foo.bar(Foo.kt:1)
        """.trimIndent()
        val out = StacktraceSanitizer.sanitize(input)
        assertTrue(out.contains("com.gpp.core.http.Interceptor"))
        assertTrue(out.contains("com.grindrapp.android.network.Foo"))
        assertTrue(out.contains("platform frames"))
        assertFalse(out.contains("DispatchedContinuation.resumeWith"))
    }
}
