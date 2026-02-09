package com.grindrplus.core

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {

    @Test
    fun testCalculateBMIMetric() {
        val weight = 70.0 // kg
        val height = 175.0 // cm
        val expected = 70.0 / (1.75 * 1.75)
        val result = Utils.calculateBMI(true, weight, height)
        assertEquals(expected, result, 1e-6)
    }

    @Test
    fun testCalculateBMIImperial() {
        val weight = 154.0 // lbs
        val height = 69.0 // inches
        val expected = 703 * 154.0 / (69.0 * 69.0)
        val result = Utils.calculateBMI(false, weight, height)
        assertEquals(expected, result, 1e-6)
    }

    @Test
    fun testCalculateBMIZeroHeight() {
        val weight = 70.0
        val height = 0.0
        val result = Utils.calculateBMI(true, weight, height)
        // Division by zero with Doubles results in Infinity
        assertEquals(Double.POSITIVE_INFINITY, result, 0.0)
    }

    @Test
    fun testCalculateBMIZeroWeight() {
        val weight = 0.0
        val height = 175.0
        val result = Utils.calculateBMI(true, weight, height)
        assertEquals(0.0, result, 1e-6)
    }
}
