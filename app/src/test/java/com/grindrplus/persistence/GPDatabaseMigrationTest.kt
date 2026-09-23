package com.grindrplus.persistence

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Static gate for GPDatabase migrations (pre-5.0 Track B).
 * Validates 5→6 adds [block_events] without wiping album/phrase/teleport tables.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class GPDatabaseMigrationTest {

    private val testDbName = "gpdatabase-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GPDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    @Throws(IOException::class)
    fun migrate5To6_preservesTablesAndAddsBlockEvents() {
        helper.createDatabase(testDbName, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `AlbumEntity` (
                    `id` INTEGER NOT NULL,
                    `albumName` TEXT,
                    `createdAt` TEXT NOT NULL,
                    `profileId` INTEGER NOT NULL,
                    `updatedAt` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `index_AlbumEntity_profileId` ON `AlbumEntity` (`profileId`)"
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `AlbumContentEntity` (
                    `id` INTEGER NOT NULL,
                    `albumId` INTEGER NOT NULL,
                    `contentType` TEXT,
                    `coverUrl` TEXT,
                    `thumbUrl` TEXT,
                    `url` TEXT,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`albumId`) REFERENCES `AlbumEntity`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                "CREATE INDEX IF NOT EXISTS `index_AlbumContentEntity_albumId` ON `AlbumContentEntity` (`albumId`)"
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `TeleportLocationEntity` (
                    `name` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    PRIMARY KEY(`name`)
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `SavedPhraseEntity` (
                    `phraseId` INTEGER NOT NULL,
                    `text` TEXT NOT NULL,
                    `frequency` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    PRIMARY KEY(`phraseId`)
                )
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO `AlbumEntity` (`id`,`albumName`,`createdAt`,`profileId`,`updatedAt`) " +
                    "VALUES (1,'keep','2024-01-01',42,'2024-01-01')"
            )
            execSQL(
                "INSERT INTO `SavedPhraseEntity` (`phraseId`,`text`,`frequency`,`timestamp`) " +
                    "VALUES (7,'hello',1,100)"
            )
            close()
        }

        helper.runMigrationsAndValidate(testDbName, 6, true, GPDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT `albumName` FROM `AlbumEntity` WHERE `id` = 1").use { c ->
                check(c.moveToFirst()) { "Album row lost during 5→6" }
                check(c.getString(0) == "keep") { "Album data corrupted" }
            }
            db.query("SELECT `text` FROM `SavedPhraseEntity` WHERE `phraseId` = 7").use { c ->
                check(c.moveToFirst()) { "Phrase row lost during 5→6" }
                check(c.getString(0) == "hello") { "Phrase data corrupted" }
            }
            db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='block_events'"
            ).use { c ->
                check(c.moveToFirst()) { "block_events table missing after 5→6" }
            }
        }
    }
}
