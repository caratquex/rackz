package com.rackz.stackmyrackz.data.repository

import com.rackz.stackmyrackz.data.local.TransactionDao
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Contract for offline-first transaction storage and queries. */
interface TransactionRepository {
    suspend fun saveTransaction(
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?,
        transactionDate: LocalDate
    ): TransactionEntity

    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    fun observeTransactionsForMonth(month: YearMonth): Flow<List<TransactionEntity>>

    fun observePendingSyncCount(): Flow<Int>
}

/** Room-backed offline-first implementation of [TransactionRepository]. */
@Singleton
class OfflineFirstTransactionRepository @Inject constructor(
    private val dao: TransactionDao
) : TransactionRepository {

    override suspend fun saveTransaction(
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?,
        transactionDate: LocalDate
    ): TransactionEntity {
        require(amountMinor > 0L) { "Transaction amount must be greater than zero." }
        val now = Instant.now()
        val entity = TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            amountMinor = amountMinor,
            currency = "MYR",
            category = category,
            remark = remark?.trim()?.takeIf { it.isNotEmpty() },
            transactionDate = transactionDate,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            remoteRevision = null
        )
        dao.insert(entity)
        return entity
    }

    override fun observeAllTransactions(): Flow<List<TransactionEntity>> = dao.observeAll()

    override fun observeTransactionsForMonth(month: YearMonth): Flow<List<TransactionEntity>> =
        dao.observeByMonth(month.atDay(1), month.atEndOfMonth())

    override fun observePendingSyncCount(): Flow<Int> = dao.observePendingCount()
}
