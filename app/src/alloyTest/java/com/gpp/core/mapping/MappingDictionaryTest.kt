package com.gpp.core.mapping

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
        assertEquals(2, pack.schemaVersion)
        assertEquals(179451, pack.versionCode)
        assertEquals("lrc", pack.symbols["core.userAgent"]?.name)
        assertEquals("c", pack.symbols["ProfileDetails.DISTANCE_UTILS"]?.method)
        assertTrue(pack.symbols["DisableBoosting.SUBSCRIBE_FOR_BOOST_REDEEM"]?.name?.isEmpty() == true)
        assertEquals("partial", pack.hooks["DisableBoosting"]?.status)
        assertEquals("absent", pack.hooks["DisableShuffle"]?.reason)
    }

    @Test
    fun parsePack_softMigratesSchemaV1ToV2() {
        val pack = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "versionName": "26.16.1",
                  "versionCode": 179451,
                  "confidence": "test",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "lrc" },
                    "retrofit.successValue": {
                      "kind": "field",
                      "name": "a",
                      "note": "owner r84"
                    },
                    "ProfileDetails.DISTANCE_UTILS": {
                      "kind": "method",
                      "name": "c",
                      "note": "owner iq3"
                    }
                  }
                }
                """.trimIndent()
            )
        )
        assertEquals(2, pack.schemaVersion)
        assertEquals("lrc", pack.symbols["core.userAgent"]?.name)
        assertEquals("field", pack.symbols["retrofit.successValue"]?.kind)
        assertEquals("a", pack.symbols["retrofit.successValue"]?.name)
        assertEquals("owner r84", pack.symbols["retrofit.successValue"]?.note)
        assertEquals("method", pack.symbols["ProfileDetails.DISTANCE_UTILS"]?.kind)
        assertEquals("c", pack.symbols["ProfileDetails.DISTANCE_UTILS"]?.name)
        assertNotNull(pack.hooks)
        assertTrue(pack.hooks.isEmpty())
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
        assertEquals("", MappingDictionary.resolve("missing.key", "fallback"))
        assertEquals("c", MappingDictionary.methodName("ProfileDetails.DISTANCE_UTILS", "x"))
        assertNull(MappingDictionary.methodName("missing", null))
    }

    @Test
    fun resolve_activePackMissingKeyReturnsEmpty_notFallback() {
        val pack = MappingDictionary.parsePack(
            JSONObject(
                """
                {
                  "schemaVersion": 2,
                  "versionName": "t",
                  "versionCode": 1,
                  "confidence": "test",
                  "generatedFrom": "test",
                  "symbols": {
                    "core.userAgent": { "kind": "class", "name": "fromPack" }
                  },
                  "hooks": {}
                }
                """.trimIndent()
            )
        )
        MappingDictionary.activate(pack)
        assertEquals("", MappingDictionary.resolve("not.in.pack", "compileTimeLiteral"))
        assertEquals("fromPack", MappingDictionary.resolve("core.userAgent", "compileTimeLiteral"))
    }

    @Test
    fun resolve_noActivePackUsesFallback() {
        assertNull(MappingDictionary.current)
        assertEquals("compileTimeLiteral", MappingDictionary.resolve("any.key", "compileTimeLiteral"))
    }

    @Test
    fun parseIndex_readsPackCatalog() {
        val index = MappingDictionary.parseIndex(
            JSONObject(
                """
                {
                  "schemaVersion": 1,
                  "packs": [
                    { "versionCode": 179451, "versionName": "26.16.1", "confidence": "high" },
                    { "versionCode": 174557, "versionName": "26.15.1", "confidence": "medium" }
                  ]
                }
                """.trimIndent()
            )
        )
        assertEquals(1, index.schemaVersion)
        assertEquals(2, index.packs.size)
        assertEquals(179451, index.packs[0].versionCode)
        assertEquals("26.16.1", index.packs[0].versionName)
        assertEquals("high", index.packs[0].confidence)
        assertEquals(174557, index.packs[1].versionCode)
    }

    @Test
    fun loadIndex_fallsBackToCacheWhenRemoteFails() {
        val cacheDir = createTempDirectory(prefix = "gp-map-index-cache").toFile()
        try {
            val json = """
                {
                  "schemaVersion": 1,
                  "packs": [
                    { "versionCode": 179451, "versionName": "26.16.1", "confidence": "high" }
                  ]
                }
            """.trimIndent()
            val file = File(File(cacheDir, "mapping-packs-cache"), "index.json")
            file.parentFile?.mkdirs()
            file.writeText(json)

            val index = MappingDictionary.loadIndex(
                cacheDir = cacheDir,
                remoteBaseUrl = "http://127.0.0.1:1/mapping-packs-unreachable",
            )
            assertNotNull(index)
            assertEquals(1, index!!.schemaVersion)
            assertEquals(179451, index.packs.single().versionCode)
            assertEquals("high", index.packs.single().confidence)
        } finally {
            cacheDir.deleteRecursively()
        }
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
    fun decodeAndActivate_rejectsFutureSchemaVersion() {
        val json = """
            {
              "schemaVersion": 3,
              "versionName": "t",
              "versionCode": 179451,
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

}
