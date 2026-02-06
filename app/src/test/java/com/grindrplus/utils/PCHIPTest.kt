package com.grindrplus.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalArgumentException

class PCHIPTest {

    @Test
    fun `test initialization with valid points`() {
        val points = listOf(
            0L to 0,
            10L to 100
        )
        val pchip = PCHIP(points)
        // No exception should be thrown
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test initialization with less than 2 points`() {
        PCHIP(listOf(0L to 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test initialization with non-ascending x`() {
        val points = listOf(
            10L to 0,
            0L to 100
        )
        PCHIP(points)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test initialization with decreasing y`() {
        val points = listOf(
            0L to 100,
            10L to 0
        )
        PCHIP(points)
    }

    @Test
    fun `test interpolate linear`() {
        val points = listOf(
            0L to 0,
            10L to 100
        )
        val pchip = PCHIP(points)

        assertEquals(0.0, pchip.interpolate(0.0), 1e-6)
        assertEquals(100.0, pchip.interpolate(10.0), 1e-6)
        assertEquals(50.0, pchip.interpolate(5.0), 1e-6)
    }

    @Test
    fun `test interpolate non-linear`() {
        // x: 0, 5, 10
        // y: 0, 25, 100
        val points = listOf(
            0L to 0,
            5L to 25,
            10L to 100
        )
        val pchip = PCHIP(points)

        // At x=2.5, y should be around 6.25 if it was exactly x^2, but PCHIP smoothes it.
        // It should definitely be between 0 and 25.
        val y = pchip.interpolate(2.5)
        assertTrue("Value $y should be between 0 and 25", y > 0 && y < 25)

        // Check monotonicity
        var prevY = -1.0
        for (i in 0..100) {
            val x = i / 10.0
            val curY = pchip.interpolate(x)
            assertTrue("Function must be monotonic at x=$x (prevY=$prevY, curY=$curY)", curY >= prevY - 1e-9)
            prevY = curY
        }
    }

    @Test
    fun `test invert within range`() {
        val points = listOf(
            0L to 0,
            10L to 100
        )
        val pchip = PCHIP(points)

        assertEquals(0.0, pchip.invert(0.0), 1e-6)
        assertEquals(10.0, pchip.invert(100.0), 1e-6)
        assertEquals(5.0, pchip.invert(50.0), 1e-6)
    }

    @Test
    fun `test invert below range`() {
        val points = listOf(
            10L to 100,
            20L to 200
        )
        val pchip = PCHIP(points)

        // Should return first x if y <= y[0]
        assertEquals(10.0, pchip.invert(50.0), 1e-6)
    }
}
