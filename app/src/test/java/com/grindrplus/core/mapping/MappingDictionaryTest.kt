package com.grindrplus.core.mapping

import java.io.File
import kotlin.io.path.createTempDirectory
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    @Test
    fun decodeAndActivate_softFailsOnMalformedJson() {
        assertNull(MappingDictionary.decodeAndActivate("{ not json", expectedVersionCode = 179451))
        assertNull(MappingDictionary.current)
        assertEquals("literal", MappingDictionary.resolve("core.userAgent", "literal"))
    }

    @Test
    fun decodeAndActivate_softFailsOnBadSymbolShape() {
        val json = """
            {
              "schemaVersion": 1,
              "versionName": "t",
              "versionCode": 179451,
              "confidence": "test",
              "generatedFrom": "test",
              "symbols": {
                "core.userAgent": "not-an-object"
              },
              "hooks": {}
            }
        """.trimIndent()
        assertNull(MappingDictionary.decodeAndActivate(json, expectedVersionCode = 179451))
        assertNull(MappingDictionary.current)
        assertEquals("literal", MappingDictionary.resolve("core.userAgent", "literal"))
    }

    @Test
    fun decodeAndActivate_rejectsVersionCodeMismatch() {
        val json = """
            {
              "schemaVersion": 1,
              "versionName": "t",
              "versionCode": 999,
              "confidence": "test",
              "generatedFrom": "test",
              "symbols": {
                "core.userAgent": { "kind": "class", "name": "fromPack" }
              },
              "hooks": {}
            }
        """.trimIndent()
        assertNull(MappingDictionary.decodeAndActivate(json, expectedVersionCode = 179451))
        assertNull(MappingDictionary.current)
        assertEquals("literal", MappingDictionary.resolve("core.userAgent", "literal"))
    }

    @Test
    fun decodeAndActivate_activatesWhenVersionMatches() {
        val json = """
            {
              "schemaVersion": 1,
              "versionName": "26.16.1",
              "versionCode": 179451,
              "confidence": "test",
              "generatedFrom": "test",
              "symbols": {
                "core.userAgent": { "kind": "class", "name": "lrc" }
              },
              "hooks": {}
            }
        """.trimIndent()
        val pack = MappingDictionary.decodeAndActivate(json, expectedVersionCode = 179451)
        assertNotNull(pack)
        assertEquals(179451, MappingDictionary.current?.versionCode)
        assertEquals("lrc", MappingDictionary.resolve("core.userAgent", "fallback"))
    }

    @Test
    fun packSchema_resolvesAddSavedPhraseAndBannedArgs() {
        val json = """
            {
              "schemaVersion": 1,
              "versionName": "26.16.1",
              "versionCode": 179451,
              "confidence": "test",
              "generatedFrom": "test",
              "symbols": {
                "core.userAgent": { "kind": "class", "name": "lrc" },
                "core.deviceInfo": { "kind": "class", "name": "wh3" },
                "BanManagement.bannedArgs": { "kind": "class", "name": "ak0" },
                "LocalSavedPhrases.AddSavedPhraseResponse": {
                  "kind": "class",
                  "name": "com.grindrapp.android.chat.data.datasource.api.model.AddSavedPhraseResponse"
                }
              },
              "hooks": {
                "LocalSavedPhrases": { "status": "mapped" }
              }
            }
            """.trimIndent()
        val pack = MappingDictionary.decodeAndActivate(json, expectedVersionCode = 179451)
        assertNotNull(pack)
        assertEquals(
            "com.grindrapp.android.chat.data.datasource.api.model.AddSavedPhraseResponse",
            MappingDictionary.resolve("LocalSavedPhrases.AddSavedPhraseResponse", "legacy")
        )
        assertEquals("ak0", MappingDictionary.resolve("BanManagement.bannedArgs", ""))
        assertEquals("mapped", MappingDictionary.current?.hooks?.get("LocalSavedPhrases")?.status)
    }

    @Test
    fun loadForVersion_usesValidCacheWhenRemoteDisabled() {
        val cacheDir = createTempDirectory(prefix = "gp-map-cache-ok").toFile()
        try {
            val versionCode = 179451
            val json = """
                {
                  "schemaVersion": 1,
                  "versionName": "26.16.1",
                  "versionCode": 179451,
                  "confidence": "test",
                  "generatedFrom": "cache-test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "fromCache" }
                  },
                  "hooks": {}
                }
            """.trimIndent()
            val file = File(File(cacheDir, "mapping-packs-cache"), "$versionCode.json")
            file.parentFile?.mkdirs()
            file.writeText(json)

            val pack = MappingDictionary.loadForVersion(
                modulePath = "/nonexistent/module.apk",
                versionCode = versionCode,
                cacheDir = cacheDir,
                fetchRemote = false
            )
            assertNotNull(pack)
            assertEquals(179451, MappingDictionary.current?.versionCode)
            assertEquals("fromCache", MappingDictionary.resolve("core.userAgent", "literal"))
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun loadForVersion_softFailsMalformedCacheAndKeepsLiterals() {
        val cacheDir = createTempDirectory(prefix = "gp-map-cache-bad").toFile()
        try {
            val versionCode = 179451
            val file = File(File(cacheDir, "mapping-packs-cache"), "$versionCode.json")
            file.parentFile?.mkdirs()
            file.writeText("{ not valid json")

            val pack = MappingDictionary.loadForVersion(
                modulePath = "/nonexistent/module.apk",
                versionCode = versionCode,
                cacheDir = cacheDir,
                fetchRemote = false
            )
            assertNull(pack)
            assertNull(MappingDictionary.current)
            assertEquals("literal", MappingDictionary.resolve("core.userAgent", "literal"))
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun loadForVersion_softFailsVersionMismatchInCache() {
        val cacheDir = createTempDirectory(prefix = "gp-map-cache-mismatch").toFile()
        try {
            val versionCode = 179451
            val json = """
                {
                  "schemaVersion": 1,
                  "versionName": "t",
                  "versionCode": 999,
                  "confidence": "test",
                  "generatedFrom": "cache-test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "wrong" }
                  },
                  "hooks": {}
                }
            """.trimIndent()
            val file = File(File(cacheDir, "mapping-packs-cache"), "$versionCode.json")
            file.parentFile?.mkdirs()
            file.writeText(json)

            val pack = MappingDictionary.loadForVersion(
                modulePath = "/nonexistent/module.apk",
                versionCode = versionCode,
                cacheDir = cacheDir,
                fetchRemote = false
            )
            assertNull(pack)
            assertNull(MappingDictionary.current)
            assertEquals("literal", MappingDictionary.resolve("core.userAgent", "literal"))
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    @Test
    fun hasCompleteCore_requiresAllFiveSymbols() {
        assertTrue(!MappingDictionary.hasCompleteCore(null))

        val incomplete = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "t",
                  "versionCode": 1,
                  "confidence": "test",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "a" },
                    "core.userSession": { "kind": "class", "name": "b" },
                    "core.deviceInfo": { "kind": "class", "name": "" }
                  },
                  "hooks": {}
                }
                """.trimIndent()
            )
        )
        assertTrue(!MappingDictionary.hasCompleteCore(incomplete))
        assertTrue(
            MappingDictionary.missingCoreKeys(incomplete).containsAll(
                listOf(
                    "core.deviceInfo",
                    "core.grindrLocationProvider",
                    "core.serverDrivenCascadeRepo",
                )
            )
        )

        val complete = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "t",
                  "versionCode": 1,
                  "confidence": "test",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "a" },
                    "core.userSession": { "kind": "class", "name": "b" },
                    "core.deviceInfo": { "kind": "class", "name": "c" },
                    "core.grindrLocationProvider": { "kind": "class", "name": "d" },
                    "core.serverDrivenCascadeRepo": { "kind": "class", "name": "e" }
                  },
                  "hooks": {}
                }
                """.trimIndent()
            )
        )
        assertTrue(MappingDictionary.hasCompleteCore(complete))
        assertTrue(MappingDictionary.missingCoreKeys(complete).isEmpty())
    }

}
