package com.rackz.stackmyrackz.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rackz.stackmyrackz.model.SyncStatus
import java.time.Instant

/** Monthly savings and investment contribution and target snapshot keyed by YYYY-MM. */
@Entity(tableName = "wealth_entries")
data class WealthEntryEntity(
    @PrimaryKey
    val yearMonth: String,
    val savingsSavedMinor: Long = 0L,
    val savingsTargetMinor: Long = 100_000L,
    val investmentSavedMinor: Long = 0L,
    val investmentTargetMinor: Long = 50_000L,
    val note: String? = null,
    val updatedAt: Instant = Instant.now(),
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
