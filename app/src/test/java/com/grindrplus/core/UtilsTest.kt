package com.grindrplus.core

import org.junit.Test
import org.junit.Assert.*

class UtilsTest {

    @Test
    fun testW2nMetric() {
        assertEquals(70.0, Utils.w2n(true, "70kg"), 0.01)
        assertEquals(70.0, Utils.w2n(true, "70 kg"), 0.01)
        assertEquals(70.0, Utils.w2n(true, "70"), 0.01)
        assertEquals(70.5, Utils.w2n(true, "70.5kg"), 0.01)
    }

    @Test
    fun testW2nImperial() {
        assertEquals(154.0, Utils.w2n(false, "154lbs"), 0.01)
        assertEquals(154.0, Utils.w2n(false, "154 lbs"), 0.01)
        assertEquals(154.0, Utils.w2n(false, "154"), 0.01)
        assertEquals(154.5, Utils.w2n(false, "154.5lbs"), 0.01)
    }

    @Test
    fun testH2nMetric() {
        assertEquals(180.0, Utils.h2n(true, "180cm"), 0.01)
        assertEquals(180.0, Utils.h2n(true, "180 cm"), 0.01)
        assertEquals(180.0, Utils.h2n(true, "180"), 0.01)
        assertEquals(180.5, Utils.h2n(true, "180.5cm"), 0.01)
    }

    @Test
    fun testH2nImperial() {
        assertEquals(71.0, Utils.h2n(false, "5'11\""), 0.01)
        assertEquals(72.0, Utils.h2n(false, "6'0\""), 0.01)
        assertEquals(71.0, Utils.h2n(false, "5' 11\""), 0.01)
        assertEquals(71.0, Utils.h2n(false, "5'11"), 0.01)
    }

    @Test
    fun testH2nImperialEdgeCases() {
        assertEquals(72.0, Utils.h2n(false, "6'"), 0.01)
        assertEquals(71.0, Utils.h2n(false, "71"), 0.01)
        assertEquals(0.0, Utils.h2n(false, "invalid"), 0.01)
    }

    @Test
    fun testW2nEdgeCases() {
        assertEquals(0.0, Utils.w2n(true, "invalid"), 0.01)
        assertEquals(70.0, Utils.w2n(true, "70kg (approx)"), 0.01)
        assertEquals(-70.0, Utils.w2n(true, "-70kg"), 0.01)
    }

    @Test
    fun testCalculateBMIMetric() {
        assertEquals(22.86, Utils.calculateBMI(true, 70.0, 175.0), 0.01)
    }

    @Test
    fun testCalculateBMIImperial() {
        assertEquals(21.48, Utils.calculateBMI(false, 154.0, 71.0), 0.01)
    }
}
