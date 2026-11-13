package com.rackz.stackmyrackz.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import java.time.Instant
import java.time.LocalDate

/** Offline-first transaction record persisted in Room. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: String = "MYR",
    val category: String,
    val remark: String?,
    val transactionDate: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncStatus: SyncStatus,
    val remoteRevision: String? = null,
    val isDeleted: Boolean = false
)
