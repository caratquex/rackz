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

    suspend fun updateTransaction(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?,
        transactionDate: LocalDate
    ): TransactionEntity?

    suspend fun softDeleteTransaction(id: String)

    suspend fun restoreTransaction(id: String)

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
            remoteRevision = null,
            isDeleted = false
        )
        dao.insert(entity)
        return entity
    }

    override suspend fun updateTransaction(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?,
        transactionDate: LocalDate
    ): TransactionEntity? {
        require(amountMinor > 0L) { "Transaction amount must be greater than zero." }
        val existing = dao.getById(id) ?: return null
        val updated = existing.copy(
            type = type,
            amountMinor = amountMinor,
            category = category,
            remark = remark?.trim()?.takeIf { it.isNotEmpty() },
            transactionDate = transactionDate,
            updatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING
        )
        dao.update(updated)
        return updated
    }

    override suspend fun softDeleteTransaction(id: String) {
        val existing = dao.getById(id) ?: return
        if (!existing.isDeleted) {
            dao.update(
                existing.copy(
                    isDeleted = true,
                    updatedAt = Instant.now(),
                    syncStatus = SyncStatus.PENDING
                )
            )
        }
    }

    override suspend fun restoreTransaction(id: String) {
        val existing = dao.getById(id) ?: return
        if (existing.isDeleted) {
            dao.update(
                existing.copy(
                    isDeleted = false,
                    updatedAt = Instant.now(),
                    syncStatus = SyncStatus.PENDING
                )
            )
        }
    }

    override fun observeAllTransactions(): Flow<List<TransactionEntity>> = dao.observeAll()

    override fun observeTransactionsForMonth(month: YearMonth): Flow<List<TransactionEntity>> =
        dao.observeByMonth(month.atDay(1), month.atEndOfMonth())

    override fun observePendingSyncCount(): Flow<Int> = dao.observePendingCount()
}
