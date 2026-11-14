package com.rackz.stackmyrackz.data.repository

import com.rackz.stackmyrackz.data.local.WealthDao
import com.rackz.stackmyrackz.data.local.WealthEntryEntity
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.sync.SyncScheduler
import com.rackz.stackmyrackz.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Contract for offline-first monthly savings and investment wealth snapshots and targets. */
interface WealthRepository {
    fun observeAllEntries(): Flow<List<WealthEntryEntity>>

    suspend fun saveMonthlyWealth(
        month: YearMonth,
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    )
}

/** Room + DataStore backed implementation of [WealthRepository]. */
@Singleton
class OfflineFirstWealthRepository @Inject constructor(
    private val wealthDao: WealthDao,
    private val syncPreferences: SyncPreferencesRepository,
    private val syncScheduler: SyncScheduler
) : WealthRepository {

    override fun observeAllEntries(): Flow<List<WealthEntryEntity>> =
        wealthDao.observeAllEntries()

    override suspend fun saveMonthlyWealth(
        month: YearMonth,
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    ) {
        val cleanSavingsSaved = savingsSavedMinor.coerceAtLeast(0L)
        val cleanSavingsTarget = savingsTargetMinor.coerceAtLeast(0L)
        val cleanInvestmentSaved = investmentSavedMinor.coerceAtLeast(0L)
        val cleanInvestmentTarget = investmentTargetMinor.coerceAtLeast(0L)
        val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }

        val entry = WealthEntryEntity(
            yearMonth = month.toString(),
            savingsSavedMinor = cleanSavingsSaved,
            savingsTargetMinor = cleanSavingsTarget,
            investmentSavedMinor = cleanInvestmentSaved,
            investmentTargetMinor = cleanInvestmentTarget,
            note = cleanNote,
            updatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING
        )
        wealthDao.upsert(entry)
        syncPreferences.setDefaultWealthTargets(
            savingsTargetMinor = cleanSavingsTarget,
            investmentTargetMinor = cleanInvestmentTarget
        )
        syncScheduler.enqueueImmediateSync()
    }
}
