package com.rackz.stackmyrackz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Primary local SQLite database for offline-first transaction and wealth storage. */
@Database(
    entities = [TransactionEntity::class, WealthEntryEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class RacksDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao

    abstract fun wealthDao(): WealthDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `wealth_entries` (
                        `yearMonth` TEXT NOT NULL,
                        `savingsSavedMinor` INTEGER NOT NULL,
                        `savingsTargetMinor` INTEGER NOT NULL,
                        `investmentSavedMinor` INTEGER NOT NULL,
                        `investmentTargetMinor` INTEGER NOT NULL,
                        `note` TEXT,
                        `updatedAt` INTEGER NOT NULL,
                        `syncStatus` TEXT NOT NULL,
                        PRIMARY KEY(`yearMonth`)
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
