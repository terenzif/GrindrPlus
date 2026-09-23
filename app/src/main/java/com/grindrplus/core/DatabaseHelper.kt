package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.grindrplus.GrindrPlus

/**
 * Soft-fail access to Grindr's host `grindr_user*.db`.
 *
 * Never throws into hook / Binder threads: missing DB, lock, or I/O → empty / -1 / false + log.
 * Reads use [SQLiteDatabase.OPEN_READONLY] when possible; writes open READWRITE briefly.
 *
 * Host DB still belongs to Grindr — prefer API/HTTP paths for mutations when available (Track C).
 */
object DatabaseHelper {
    private var readDatabase: SQLiteDatabase? = null
    private var writeDatabase: SQLiteDatabase? = null
    private var databaseName: String? = null

    /** True when a `grindr_user*.db` file exists (may still be locked / unreadable). */
    @Synchronized
    fun isUserDatabasePresent(): Boolean = resolveUserDbName() != null

    @Synchronized
    private fun resolveUserDbName(): String? {
        return try {
            val context = GrindrPlus.context
            val databases = context.databaseList()
            databases.firstOrNull { it.contains("grindr_user") && it.endsWith(".db") }
        } catch (e: Exception) {
            Logger.w("DatabaseHelper: cannot list databases: ${e.message}")
            null
        }
    }

    @Synchronized
    private fun openRead(): SQLiteDatabase? {
        val name = resolveUserDbName()
        if (name == null) {
            Logger.w("DatabaseHelper: no grindr_user*.db yet (pre-login or renamed)")
            closeAllLocked()
            return null
        }

        if (readDatabase != null && databaseName == name && readDatabase!!.isOpen) {
            return readDatabase
        }

        closeAllLocked()
        databaseName = name
        return try {
            val path = GrindrPlus.context.getDatabasePath(name).absolutePath
            Logger.d("DatabaseHelper: opening READONLY $name")
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).also {
                readDatabase = it
            }
        } catch (e: Exception) {
            Logger.w("DatabaseHelper: READONLY open failed ($name): ${e.message}")
            null
        }
    }

    @Synchronized
    private fun openWrite(): SQLiteDatabase? {
        val name = resolveUserDbName()
        if (name == null) {
            Logger.w("DatabaseHelper: write skipped — no grindr_user*.db")
            return null
        }

        if (writeDatabase != null && databaseName == name && writeDatabase!!.isOpen) {
            return writeDatabase
        }

        // Drop read handle if switching modes on same file to avoid dual-handle surprises.
        if (databaseName != null && databaseName != name) {
            closeAllLocked()
        }
        databaseName = name

        writeDatabase?.takeIf { it.isOpen }?.close()
        writeDatabase = null

        return try {
            val path = GrindrPlus.context.getDatabasePath(name).absolutePath
            Logger.d("DatabaseHelper: opening READWRITE $name")
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).also {
                writeDatabase = it
            }
        } catch (e: Exception) {
            Logger.w("DatabaseHelper: READWRITE open failed ($name): ${e.message}")
            null
        }
    }

    @Synchronized
    private fun closeAllLocked() {
        try {
            readDatabase?.close()
        } catch (_: Exception) {
        }
        try {
            writeDatabase?.close()
        } catch (_: Exception) {
        }
        readDatabase = null
        writeDatabase = null
        databaseName = null
    }

    fun query(query: String, args: Array<String>? = null): List<Map<String, Any>> {
        val database = openRead() ?: return emptyList()
        return try {
            val cursor = database.rawQuery(query, args)
            val results = mutableListOf<Map<String, Any>>()
            try {
                if (cursor.moveToFirst()) {
                    val columnNames = cursor.columnNames
                    val columnIndices = columnNames.map { it to cursor.getColumnIndexOrThrow(it) }
                    do {
                        val row = mutableMapOf<String, Any>()
                        columnIndices.forEach { (column, index) ->
                            row[column] = when (cursor.getType(index)) {
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getInt(index)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getFloat(index)
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
                                Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                                Cursor.FIELD_TYPE_NULL -> "NULL"
                                else -> "UNKNOWN"
                            }
                        }
                        results.add(row)
                    } while (cursor.moveToNext())
                }
            } finally {
                cursor.close()
            }
            results
        } catch (e: Exception) {
            Logger.w("DatabaseHelper.query soft-fail: ${e.message}")
            emptyList()
        }
    }

    fun insert(table: String, values: ContentValues): Long {
        val database = openWrite() ?: return -1L
        return try {
            database.insert(table, null, values)
        } catch (e: Exception) {
            Logger.w("DatabaseHelper.insert soft-fail ($table): ${e.message}")
            -1L
        }
    }

    fun update(
        table: String,
        values: ContentValues,
        whereClause: String?,
        whereArgs: Array<String>?,
    ): Int {
        val database = openWrite() ?: return 0
        return try {
            database.update(table, values, whereClause, whereArgs)
        } catch (e: Exception) {
            Logger.w("DatabaseHelper.update soft-fail ($table): ${e.message}")
            0
        }
    }

    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int {
        val database = openWrite() ?: return 0
        return try {
            database.delete(table, whereClause, whereArgs)
        } catch (e: Exception) {
            Logger.w("DatabaseHelper.delete soft-fail ($table): ${e.message}")
            0
        }
    }

    fun getTables(): List<String> {
        val query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"
        return query(query).map { it["name"].toString() }
    }

    fun execute(sql: String): Boolean {
        val database = openWrite() ?: return false
        return try {
            database.execSQL(sql)
            true
        } catch (e: Exception) {
            Logger.w("DatabaseHelper.execute soft-fail: ${e.message}")
            false
        }
    }
}
