package com.onelineaday.dailydiary.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JournalMigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        JournalDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration4To5PreservesEntryAndAddsAudioColumn() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO journals(
                    id,
                    name,
                    colorHex,
                    createdAt
                )
                VALUES(
                    'default',
                    'My Diary',
                    NULL,
                    1
                )
                """.trimIndent()
            )

            execSQL(
                """
                INSERT INTO journal_entries(
                    date,
                    journalId,
                    content,
                    mood,
                    photoUri,
                    mediaUris,
                    tags,
                    isPinned,
                    createdAt,
                    updatedAt
                )
                VALUES(
                    '2026-09-21',
                    'default',
                    'migration memory',
                    'HAPPY',
                    NULL,
                    '[]',
                    '[]',
                    0,
                    1,
                    1
                )
                """.trimIndent()
            )

            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            JournalDatabase.MIGRATION_4_5
        )

        migrated.query(
            """
            SELECT
                content,
                audioUri,
                journalId
            FROM journal_entries
            WHERE date = '2026-09-21'
            """.trimIndent()
        ).use { cursor ->
            require(cursor.moveToFirst())
            assertEquals("migration memory", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertEquals("default", cursor.getString(2))
        }

        migrated.close()
    }

    @Test
    fun migration1To5PreservesOldEntries() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                """
                INSERT INTO journal_entries(
                    date,
                    content,
                    mood,
                    photoUri,
                    createdAt,
                    updatedAt
                )
                VALUES(
                    '2026-01-01',
                    'v1 memory',
                    'EXCITED',
                    NULL,
                    100,
                    100
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            *JournalDatabase.ALL_MIGRATIONS
        )

        migrated.query(
            """
            SELECT content, mood, mediaUris, isPinned, journalId, audioUri
            FROM journal_entries
            WHERE date = '2026-01-01'
            """.trimIndent()
        ).use { cursor ->
            require(cursor.moveToFirst())
            assertEquals("v1 memory", cursor.getString(0))
            assertEquals("EXCITED", cursor.getString(1))
            assertEquals("[]", cursor.getString(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals("default", cursor.getString(4))
            assertNull(cursor.getString(5))
        }

        migrated.close()
    }
}

