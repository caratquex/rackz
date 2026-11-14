package com.rackz.stackmyrackz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WealthDao {
    @Query("SELECT * FROM wealth_entries ORDER BY yearMonth ASC")
    fun observeAllEntries(): Flow<List<WealthEntryEntity>>

    @Query("SELECT * FROM wealth_entries WHERE yearMonth = :yearMonth LIMIT 1")
    fun observeEntryForMonth(yearMonth: String): Flow<WealthEntryEntity?>

    @Query("SELECT * FROM wealth_entries ORDER BY yearMonth ASC")
    suspend fun getAllEntriesSnapshot(): List<WealthEntryEntity>

    @Query("SELECT * FROM wealth_entries WHERE yearMonth = :yearMonth LIMIT 1")
    suspend fun getEntryForMonth(yearMonth: String): WealthEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WealthEntryEntity)
}
