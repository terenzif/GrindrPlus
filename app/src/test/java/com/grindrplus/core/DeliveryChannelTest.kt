package com.grindrplus.core

import com.grindrplus.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryChannelTest {
    @Test
    fun buildConfig_matchesMorpheFlavorDefaults() {
        // This class runs under testMorpheDebugUnitTest / testAlloyDebugUnitTest.
        when (BuildConfig.DELIVERY_CHANNEL) {
            "morphe" -> {
                assertEquals(DeliveryChannel.MORPHE, DeliveryChannel.current)
                assertTrue(DeliveryChannel.current.showsInstallTab)
                assertEquals("com.grindrplus.morphe", BuildConfig.APPLICATION_ID)
                assertEquals("com.grindrplus.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            "alloy" -> {
                assertEquals(DeliveryChannel.ALLOY, DeliveryChannel.current)
                assertEquals(false, DeliveryChannel.current.showsInstallTab)
                assertEquals("com.grindrplus.alloy", BuildConfig.APPLICATION_ID)
                assertEquals("com.grindrplus.alloy", DeliveryChannel.current.bridgeHostPackage)
            }
            "embed" -> {
                assertEquals(DeliveryChannel.EMBED, DeliveryChannel.current)
                assertEquals("com.grindrplus.morphe.payload", BuildConfig.APPLICATION_ID)
                // Slim embed is not installed — bridge IPC targets Morphe Manager.
                assertEquals("com.grindrplus.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            else -> error("Unexpected DELIVERY_CHANNEL=${BuildConfig.DELIVERY_CHANNEL}")
        }
    }
}
