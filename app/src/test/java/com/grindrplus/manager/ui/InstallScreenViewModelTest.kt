package com.grindrplus.manager.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InstallScreenViewModelTest {

    @Test
    fun parseMappingIndex_objectWithPacks() {
        val json = """
            {
              "schemaVersion": 1,
              "packs": [
                {"versionCode": 179451, "versionName": "26.16.1"},
                {"versionCode": 181424, "versionName": "26.17.0"}
              ]
            }
        """.trimIndent()
        val packs = InstallScreenViewModel.parseMappingIndex(json)
        assertEquals(2, packs.size)
        assertEquals(181424L, packs[0].versionCode)
        assertEquals("26.17.0", packs[0].versionName)
        assertEquals(179451L, packs[1].versionCode)
    }

    @Test
    fun parseMappingIndex_arrayForm() {
        val json = """[{"versionCode":147239,"versionName":"25.20.0"}]"""
        val packs = InstallScreenViewModel.parseMappingIndex(json)
        assertEquals(1, packs.size)
        assertEquals(147239L, packs[0].versionCode)
    }

    @Test
    fun parseLatestModUrl_picksLexicographicLatest() {
        val json = """
            {
              "v4.7.1-26.15.1": ["", "https://example/old.apk"],
              "v4.7.2-26.16.1": ["", "https://example/new.apk"]
            }
        """.trimIndent()
        assertEquals("https://example/new.apk", InstallScreenViewModel.parseLatestModUrl(json))
    }

    @Test
    fun displayLabel_marksInstalled() {
        assertEquals(
            "26.17.0 (181424) • installed",
            InstallScreenViewModel.displayLabel("26.17.0", 181424L, true),
        )
        assertEquals(
            "26.16.1 (179451)",
            InstallScreenViewModel.displayLabel("26.16.1", 179451L, false),
        )
    }

    @Test
    fun installKey_usesVersionName() {
        val data = Data(
            modVer = "26.17.0 (181424) • installed",
            grindrUrl = "",
            modUrl = "https://example/mod.apk",
            versionCode = 181424L,
            versionName = "26.17.0",
            isInstalled = true,
        )
        assertEquals("26.17.0", data.installKey)
        assertTrue(data.isInstalled)
    }
}
