package com.gpp.core

import com.gpp.BuildConfig
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
                assertEquals("com.gpp.morphe", BuildConfig.APPLICATION_ID)
                assertEquals("com.gpp.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            "alloy" -> {
                assertEquals(DeliveryChannel.ALLOY, DeliveryChannel.current)
                assertEquals(false, DeliveryChannel.current.showsInstallTab)
                assertEquals("com.gpp.alloy", BuildConfig.APPLICATION_ID)
                assertEquals("com.gpp.alloy", DeliveryChannel.current.bridgeHostPackage)
            }
            "embed" -> {
                assertEquals(DeliveryChannel.EMBED, DeliveryChannel.current)
                assertEquals("com.gpp.morphe.payload", BuildConfig.APPLICATION_ID)
                // Slim embed is not installed — bridge IPC targets Morphe Manager.
                assertEquals("com.gpp.morphe", DeliveryChannel.current.bridgeHostPackage)
            }
            else -> error("Unexpected DELIVERY_CHANNEL=${BuildConfig.DELIVERY_CHANNEL}")
        }
    }
}
