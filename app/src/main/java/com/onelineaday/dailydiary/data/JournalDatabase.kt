package com.onelineaday.dailydiary.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDate

/**
 * Room database for storing journal entries
 */
@Database(
    entities = [JournalEntry::class, Journal::class],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    
    companion object {
        const val VERSION = 5

        @Volatile
        private var INSTANCE: JournalDatabase? = null
        
        fun getDatabase(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE journal_entries ADD COLUMN mediaUris TEXT NOT NULL DEFAULT '[]'")
                    }
                }
                
                val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE journal_entries ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                    }
                }
                
                val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // Create journals table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `journals` (
                                `id` TEXT NOT NULL,
                                `name` TEXT NOT NULL,
                                `colorHex` TEXT,
                                `createdAt` INTEGER NOT NULL,
                                PRIMARY KEY(`id`)
                            )
                        """)
                        
                        // Insert default journal
                        db.execSQL("INSERT OR IGNORE INTO `journals` (`id`, `name`, `createdAt`) VALUES ('default', 'My Diary', ${System.currentTimeMillis()})")
                        
                        // Recreate journal_entries with new composite primary key
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `journal_entries_new` (
                                `date` TEXT NOT NULL,
                                `journalId` TEXT NOT NULL DEFAULT 'default',
                                `content` TEXT NOT NULL,
                                `mood` TEXT NOT NULL,
                                `photoUri` TEXT,
                                `mediaUris` TEXT NOT NULL,
                                `tags` TEXT NOT NULL DEFAULT '[]',
                                `isPinned` INTEGER NOT NULL,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                PRIMARY KEY(`date`, `journalId`)
                            )
                        """)
                        
                        // Copy data
                        db.execSQL("""
                            INSERT INTO `journal_entries_new` (`date`, `journalId`, `content`, `mood`, `photoUri`, `mediaUris`, `tags`, `isPinned`, `createdAt`, `updatedAt`)
                            SELECT `date`, 'default', `content`, `mood`, `photoUri`, `mediaUris`, '[]', `isPinned`, `createdAt`, `updatedAt` FROM `journal_entries`
                        """)
                        
                        // Remove old table and rename new one
                        db.execSQL("DROP TABLE `journal_entries`")
                        db.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")
                    }
                }

                val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        // Ensure all existing entries have a corresponding journal row before adding FK
                        db.execSQL("""
                            INSERT OR IGNORE INTO journals(
                                id,
                                name,
                                colorHex,
                                createdAt
                            )
                            SELECT DISTINCT
                                journalId,
                                CASE
                                    WHEN journalId = 'default' THEN 'My Diary'
                                    ELSE 'Recovered Journal'
                                END,
                                NULL,
                                0
                            FROM journal_entries
                        """.trimIndent())

                        db.execSQL("""
                            INSERT OR IGNORE INTO journals(
                                id,
                                name,
                                colorHex,
                                createdAt
                            )
                            VALUES(
                                'default',
                                'My Diary',
                                NULL,
                                0
                            )
                        """.trimIndent())

                        // Recreate journal_entries with FK and index
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS journal_entries_new(
                                date TEXT NOT NULL,
                                journalId TEXT NOT NULL,
                                content TEXT NOT NULL,
                                mood TEXT NOT NULL,
                                photoUri TEXT,
                                mediaUris TEXT NOT NULL,
                                tags TEXT NOT NULL,
                                audioUri TEXT,
                                isPinned INTEGER NOT NULL,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                PRIMARY KEY(date, journalId),
                                FOREIGN KEY(journalId)
                                    REFERENCES journals(id)
                                    ON UPDATE NO ACTION
                                    ON DELETE RESTRICT
                            )
                        """.trimIndent())

                        db.execSQL("""
                            INSERT INTO journal_entries_new(
                                date,
                                journalId,
                                content,
                                mood,
                                photoUri,
                                mediaUris,
                                tags,
                                audioUri,
                                isPinned,
                                createdAt,
                                updatedAt
                            )
                            SELECT
                                date,
                                journalId,
                                content,
                                mood,
                                photoUri,
                                mediaUris,
                                tags,
                                NULL,
                                isPinned,
                                createdAt,
                                updatedAt
                            FROM journal_entries
                        """.trimIndent())

                        db.execSQL("DROP TABLE journal_entries")
                        db.execSQL("ALTER TABLE journal_entries_new RENAME TO journal_entries")

                        db.execSQL("""
                            CREATE INDEX IF NOT EXISTS index_journal_entries_journalId
                            ON journal_entries(journalId)
                        """.trimIndent())
                    }
                }
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JournalDatabase::class.java,
                    "journal_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaultJournal(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        seedDefaultJournal(db)
                    }
                })
                .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedDefaultJournal(db: SupportSQLiteDatabase) {
            db.execSQL("""
                INSERT OR IGNORE INTO journals(
                    id,
                    name,
                    colorHex,
                    createdAt
                )
                VALUES(
                    'default',
                    'My Diary',
                    NULL,
                    0
                )
            """.trimIndent())
        }
    }
}

/**
 * Type converters for Room to handle LocalDate and Mood
 */
class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.toString()
    }
    
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it) }
    }
    
    @TypeConverter
    fun fromMood(mood: Mood): String {
        return mood.name
    }
    
    @TypeConverter
    fun toMood(moodName: String): Mood {
        return Mood.valueOf(moodName)
    }

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return org.json.JSONArray(list).toString()
    }

    @TypeConverter
    fun toStringList(data: String): List<String> {
        val jsonArray = org.json.JSONArray(data)
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }
}
