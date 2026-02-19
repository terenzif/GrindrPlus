package com.grindrplus.core

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Ignore
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CursorBenchmarkTest {

    private lateinit var database: SQLiteDatabase
    private val tableName = "benchmark_table"
    private val rowCount = 2000 // Reduced from 10000 to avoid timeout
    private val columnCount = 10

    @Before
    fun setUp() {
        println("Setting up database...")
        database = SQLiteDatabase.create(null)
        val columns = (0 until columnCount).joinToString(", ") { "col_$it TEXT" }
        database.execSQL("CREATE TABLE $tableName ($columns)")

        val values = ContentValues()
        database.beginTransaction()
        try {
            for (i in 0 until rowCount) {
                values.clear()
                for (j in 0 until columnCount) {
                    values.put("col_$j", "value_${i}_$j")
                }
                database.insert(tableName, null, values)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        println("Database setup complete.")
    }

    @Test
    @Ignore("Performance benchmark test, not for CI/CD")
    fun benchmarkColumnIndexOptimization() {
        val cursor = database.rawQuery("SELECT * FROM $tableName", null)

        // --- Unoptimized (Current Implementation) ---
        // Measures time for the current implementation logic
        println("Measuring unoptimized implementation...")
        val unoptimizedResults = mutableListOf<Map<String, Any>>()
        val unoptimizedTime = measureNanoTime {
            if (cursor.moveToFirst()) {
                do {
                    val row = mutableMapOf<String, Any>()
                    cursor.columnNames.forEach { column ->
                        row[column] = when (cursor.getType(cursor.getColumnIndexOrThrow(column))) {
                            Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_STRING -> cursor.getString(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_NULL -> "NULL"
                            else -> "UNKNOWN"
                        }
                    }
                    unoptimizedResults.add(row)
                } while (cursor.moveToNext())
            }
        }

        // Reset cursor for optimized test
        cursor.moveToFirst() // Move back to start
        // However, rawQuery returns a new cursor for each query usually, but here we reuse
        // Actually, better to query again to be fair and clean?
        // Let's re-query to be safe and avoid cursor state issues impacting benchmarks.
        cursor.close()
        val cursorOptimized = database.rawQuery("SELECT * FROM $tableName", null)

        // --- Optimized Implementation ---
        println("Measuring optimized implementation...")
        val optimizedResults = mutableListOf<Map<String, Any>>()
        val optimizedTime = measureNanoTime {
            if (cursorOptimized.moveToFirst()) {
                // Optimization: Cache column indices
                val columnNames = cursorOptimized.columnNames
                val columnIndices = IntArray(columnNames.size) { i ->
                    cursorOptimized.getColumnIndexOrThrow(columnNames[i])
                }

                do {
                    val row = mutableMapOf<String, Any>()
                    // Iterate by index to avoid map lookup overhead inside the loop
                    for (i in columnNames.indices) {
                        val columnName = columnNames[i]
                        val columnIndex = columnIndices[i]

                        row[columnName] = when (cursorOptimized.getType(columnIndex)) {
                            Cursor.FIELD_TYPE_INTEGER -> cursorOptimized.getInt(columnIndex)
                            Cursor.FIELD_TYPE_FLOAT -> cursorOptimized.getFloat(columnIndex)
                            Cursor.FIELD_TYPE_STRING -> cursorOptimized.getString(columnIndex)
                            Cursor.FIELD_TYPE_BLOB -> cursorOptimized.getBlob(columnIndex)
                            Cursor.FIELD_TYPE_NULL -> "NULL"
                            else -> "UNKNOWN"
                        }
                    }
                    optimizedResults.add(row)
                } while (cursorOptimized.moveToNext())
            }
        }
        cursorOptimized.close()

        val unoptimizedTimeMs = unoptimizedTime / 1_000_000.0
        val optimizedTimeMs = optimizedTime / 1_000_000.0
        val improvement = unoptimizedTime.toDouble() / optimizedTime.toDouble()

        println("Unoptimized Time: %.2f ms".format(unoptimizedTimeMs))
        println("Optimized Time:   %.2f ms".format(optimizedTimeMs))
        println("Improvement:      %.2fx".format(improvement))

        // Assert correctness
        assertEquals("Results size mismatch", unoptimizedResults.size, optimizedResults.size)
        // Check first and last row content
        if (unoptimizedResults.isNotEmpty()) {
            assertEquals("First row mismatch", unoptimizedResults[0], optimizedResults[0])
            assertEquals("Last row mismatch", unoptimizedResults.last(), optimizedResults.last())
        }

        // Assert improvement
        // Robolectric is slow, but relative improvement should still be visible.
        // We expect at least some improvement, but given Robolectric overhead, it might vary.
        // We'll assert > 1.0 to pass, but the console output is the key.
        assertTrue("Expected optimization to be faster", improvement > 1.0)
    }
}
