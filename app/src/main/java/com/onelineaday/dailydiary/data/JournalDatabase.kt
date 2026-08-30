package com.onelineaday.dailydiary.data

import android.content.Context
import androidx.room.*
import java.time.LocalDate

/**
 * Room database for storing journal entries
 */
@Database(
    entities = [JournalEntry::class, Journal::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao
    
    companion object {
        @Volatile
        private var INSTANCE: JournalDatabase? = null
        
        fun getDatabase(context: Context): JournalDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE journal_entries ADD COLUMN mediaUris TEXT NOT NULL DEFAULT '[]'")
                    }
                }
                
                val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE journal_entries ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                    }
                }
                
                val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
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
                
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JournalDatabase::class.java,
                    "journal_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
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
