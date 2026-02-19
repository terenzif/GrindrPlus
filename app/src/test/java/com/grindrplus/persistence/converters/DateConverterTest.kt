package com.grindrplus.persistence.converters

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DateConverterTest {

    private val converter = DateConverter()

    @Test
    fun fromTimestamp_validFormat() {
        val input = "2023-10-27T10:30:45.123Z"
        val expected = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2023, Calendar.OCTOBER, 27, 10, 30, 45)
            set(Calendar.MILLISECOND, 123)
        }.time

        val actual = converter.fromTimestamp(input)
        assertNotNull(actual)
        // Check time within 1000ms just in case of slight diff, but exact match expected
        assertEquals(expected.time, actual?.time)
    }

    @Test
    fun fromTimestamp_nullInput() {
        assertNull(converter.fromTimestamp(null))
    }

    @Test
    fun fromTimestamp_invalidFormat() {
        // Should catch Exception and return null
        assertNull(converter.fromTimestamp("invalid-date-string"))
        assertNull(converter.fromTimestamp("2023-10-27")) // Missing time/tz
    }

    @Test
    fun dateToTimestamp_validDate() {
        val date = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(2023, Calendar.OCTOBER, 27, 10, 30, 45)
            set(Calendar.MILLISECOND, 123)
        }.time
        val expected = "2023-10-27T10:30:45.123Z"

        assertEquals(expected, converter.dateToTimestamp(date))
    }

    @Test
    fun dateToTimestamp_nullInput() {
        assertNull(converter.dateToTimestamp(null))
    }

    @Test
    fun verifyUtcHandling() {
        // 1698402645123L is 2023-10-27T10:30:45.123Z
        val date = Date(1698402645123L)
        val expected = "2023-10-27T10:30:45.123Z"
        assertEquals(expected, converter.dateToTimestamp(date))
    }

    @Test
    fun testConcurrency() {
        val numThreads = 10
        val iterations = 100
        val startLatch = CountDownLatch(1)
        val completionLatch = CountDownLatch(numThreads)
        val errors = AtomicInteger(0)

        for (i in 0 until numThreads) {
            Thread {
                try {
                    startLatch.await()
                    for (j in 0 until iterations) {
                        try {
                            val timestamp = "2023-10-27T10:30:45.123Z"
                            // DateConverter uses a static SimpleDateFormat which is not thread-safe.
                            // This test is expected to fail if run concurrently without synchronization.
                            val date = converter.fromTimestamp(timestamp)

                            if (date == null) {
                                errors.incrementAndGet() // Parsing failed (swallowed exception)
                                continue
                            }

                            val backToString = converter.dateToTimestamp(date)
                            if (timestamp != backToString) {
                                errors.incrementAndGet() // Formatting mismatch or wrong parsing
                            }
                        } catch (e: Exception) {
                            errors.incrementAndGet()
                        }
                    }
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    completionLatch.countDown()
                }
            }.start()
        }

        startLatch.countDown() // Start all threads
        val completed = completionLatch.await(10, TimeUnit.SECONDS)
        assertTrue("Test timed out", completed)

        // Assert that there were NO errors.
        // If the code is buggy (thread-unsafe), this assertion will fail.
        assertEquals("Concurrency errors detected. SimpleDateFormat is not thread-safe!", 0, errors.get())
    }
}
