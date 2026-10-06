package com.gpp.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gpp.persistence.converters.DateConverter
import com.gpp.persistence.dao.AlbumDao
import com.gpp.persistence.dao.BlockEventDao
import com.gpp.persistence.dao.SavedPhraseDao
import com.gpp.persistence.dao.TeleportLocationDao
import com.gpp.persistence.model.AlbumContentEntity
import com.gpp.persistence.model.AlbumEntity
import com.gpp.persistence.model.BlockEventEntity
import com.gpp.persistence.model.SavedPhraseEntity
import com.gpp.persistence.model.TeleportLocationEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumContentEntity::class,
        TeleportLocationEntity::class,
        SavedPhraseEntity::class,
        BlockEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(DateConverter::class)
abstract class GPDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun teleportLocationDao(): TeleportLocationDao
    abstract fun savedPhraseDao(): SavedPhraseDao
    abstract fun blockEventDao(): BlockEventDao

    companion object {
        private const val DATABASE_NAME = "grindrplus.db"

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `block_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`profileId` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`eventType` TEXT NOT NULL, " +
                            "`timestamp` INTEGER NOT NULL, " +
                            "`packageName` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_block_events_timestamp` ON `block_events` (`timestamp`)"
                )
            }
        }

        fun create(context: Context): GPDatabase {
            return Room.databaseBuilder(context, GPDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration(false)
                .addMigrations(MIGRATION_5_6)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
