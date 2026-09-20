package com.grindrplus.core.mapping

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MappingDictionaryTest {

    @After
    fun tearDown() {
        MappingDictionary.clear()
    }

    @Test
    fun parsePack_readsSymbolsAndHooks() {
        val pack = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "26.16.1",
                  "versionCode": 179451,
                  "confidence": "static-jadx",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "lrc", "fingerprint": "grindr3/" },
                    "ProfileDetails.DISTANCE_UTILS": { "kind": "class", "name": "iq3", "method": "c" },
                    "DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM": { "kind": "class", "name": "" }
                  },
                  "hooks": {
                    "DisableBoosting": { "status": "partial" },
                    "DisableShuffle": { "status": "skipped", "reason": "absent" }
                  }
                }
                """.trimIndent()
            )
        )
        assertEquals(1, pack.schemaVersion)
        assertEquals(179451, pack.versionCode)
        assertEquals("lrc", pack.symbols["core.userAgent"]?.name)
        assertEquals("c", pack.symbols["ProfileDetails.DISTANCE_UTILS"]?.method)
        assertTrue(pack.symbols["DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM"]?.name?.isEmpty() == true)
        assertEquals("partial", pack.hooks["DisableBoosting"]?.status)
        assertEquals("absent", pack.hooks["DisableShuffle"]?.reason)
    }

    @Test
    fun resolve_prefersPackThenFallback() {
        assertEquals("fallback", MappingDictionary.resolve("core.userAgent", "fallback"))

        val pack = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "t",
                  "versionCode": 1,
                  "confidence": "test",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "fromPack" },
                    "skip.me": { "kind": "class", "name": "" },
                    "ProfileDetails.DISTANCE_UTILS": { "kind": "class", "name": "iq3", "method": "c" }
                  },
                  "hooks": {}
                }
                """.trimIndent()
            )
        )
        MappingDictionary.activate(pack)

        assertEquals("fromPack", MappingDictionary.resolve("core.userAgent", "fallback"))
        assertEquals("", MappingDictionary.resolve("skip.me", "fallback"))
        assertEquals("fallback", MappingDictionary.resolve("missing.key", "fallback"))
        assertEquals("c", MappingDictionary.methodName("ProfileDetails.DISTANCE_UTILS", "x"))
        assertNull(MappingDictionary.methodName("missing", null))
    }
}
