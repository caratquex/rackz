package com.rackz.stackmyrackz.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Primary local SQLite database for offline-first transaction storage. */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class RacksDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}
