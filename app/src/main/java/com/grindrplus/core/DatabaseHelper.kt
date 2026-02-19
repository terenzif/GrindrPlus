package com.grindrplus.core

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.grindrplus.GrindrPlus

object DatabaseHelper {
    private var database: SQLiteDatabase? = null
    private var databaseName: String? = null

    @Synchronized
    private fun getDatabase(): SQLiteDatabase {
        val context = GrindrPlus.context
        val databases = context.databaseList()
        val grindrUserDb = databases.firstOrNull {
            it.contains("grindr_user") && it.endsWith(".db") }
            ?: throw IllegalStateException("No Grindr user database found!").also {
                Logger.apply {
                    e(it.message!!)
                    writeRaw("Available databases:\n" +
                            "${databases.joinToString("\n") { "  $it" }}\n")
                }
            }

        if (database != null && databaseName == grindrUserDb && database!!.isOpen) {
            return database!!
        }

        database?.close()
        databaseName = grindrUserDb
        database = context.openOrCreateDatabase(grindrUserDb.also {
            Logger.d("Using database: $it") }, Context.MODE_PRIVATE, null)

        return database!!
    }

    fun query(query: String, args: Array<String>? = null): List<Map<String, Any>> {
        val database = getDatabase()
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

        return results
    }

    fun insert(table: String, values: ContentValues): Long {
        return getDatabase().insert(table, null, values)
    }

    fun update(table: String, values: ContentValues, whereClause: String?, whereArgs: Array<String>?): Int {
        return getDatabase().update(table, values, whereClause, whereArgs)
    }

    fun delete(table: String, whereClause: String?, whereArgs: Array<String>?): Int {
        return getDatabase().delete(table, whereClause, whereArgs)
    }

    fun getTables(): List<String> {
        val query = "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name;"
        return query(query).map { it["name"].toString() }
    }

    fun execute(sql: String) {
        getDatabase().execSQL(sql)
    }
}
