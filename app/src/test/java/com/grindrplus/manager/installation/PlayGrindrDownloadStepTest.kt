package com.grindrplus.manager.installation

import com.grindrplus.manager.installation.steps.PlayGrindrDownloadStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PlayGrindrDownloadStepTest {

    @Test
    fun resolveDownloadVersionCode_prefersTargetOverTip() {
        assertEquals(
            179451L,
            PlayGrindrDownloadStep.resolveDownloadVersionCode(
                tipVersionCode = 181424L,
                preferredVersionCode = 179451L,
            ),
        )
    }

    @Test
    fun resolveDownloadVersionCode_usesTipWhenNoTarget() {
        assertEquals(
            181424L,
            PlayGrindrDownloadStep.resolveDownloadVersionCode(
                tipVersionCode = 181424L,
                preferredVersionCode = 0L,
            ),
        )
    }

    @Test
    fun resolveDownloadVersionCode_rejectsInvalid() {
        assertThrows(IOException::class.java) {
            PlayGrindrDownloadStep.resolveDownloadVersionCode(
                tipVersionCode = 0L,
                preferredVersionCode = 0L,
            )
        }
    }
}
