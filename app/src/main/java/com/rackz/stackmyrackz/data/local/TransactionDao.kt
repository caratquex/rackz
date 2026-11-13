package com.rackz.stackmyrackz.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Room DAO for offline-first transaction persistence and queries. */
@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY transactionDate DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions " +
            "WHERE transactionDate >= :startDate AND transactionDate <= :endDate " +
            "ORDER BY transactionDate DESC, createdAt DESC"
    )
    fun observeByMonth(startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE syncStatus IN ('PENDING', 'FAILED', 'SYNCING')")
    fun observePendingCount(): Flow<Int>
}
