package com.rackz.stackmyrackz.data.sync

import android.content.Intent
import com.rackz.stackmyrackz.data.local.TransactionDao
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.local.WealthDao
import com.rackz.stackmyrackz.data.local.WealthEntryEntity
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.remote.AuthTokenProvider
import com.rackz.stackmyrackz.data.remote.SheetTabInfo
import com.rackz.stackmyrackz.data.remote.SheetsApiException
import com.rackz.stackmyrackz.data.remote.SheetsRemoteDataSource
import com.rackz.stackmyrackz.data.remote.SpreadsheetMetadata
import com.rackz.stackmyrackz.data.remote.TokenResult
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of running a full Google Sheets synchronization pass. */
sealed interface SyncOutcome {
    data class Success(
        val uploadedCount: Int,
        val importedCount: Int,
        val deletedRemoteCount: Int,
        val conflictCount: Int
    ) : SyncOutcome

    data class ConsentRequired(val intent: Intent) : SyncOutcome
    data class NotConfigured(val reason: String) : SyncOutcome
    data class Error(val message: String, val retryable: Boolean) : SyncOutcome
}

private data class RemoteTabSnapshot(
    val month: YearMonth,
    val tab: SheetTabInfo,
    val rawRows: List<List<String>>,
    val parsedRows: MutableList<SheetLedgerRow>,
    val reservedRows: MutableSet<Int>
)

private data class RemoteRowMatch(
    val month: YearMonth,
    val tabTitle: String,
    val row: SheetLedgerRow
)

/**
 * Coordinates bidirectional offline-first reconciliation between Room and Google Sheets.
 * Preserves rows 1..9 summary formulas, deduplicates via UUIDs in column H, and flags
 * conflicting concurrent edits with [SyncStatus.CONFLICT].
 */
@Singleton
class SheetsSyncEngine @Inject constructor(
    private val dao: TransactionDao,
    private val syncPreferences: SyncPreferencesRepository,
    private val authTokenProvider: AuthTokenProvider,
    private val remoteDataSource: SheetsRemoteDataSource,
    private val wealthDao: WealthDao? = null
) {

    constructor(
        dao: TransactionDao,
        syncPreferences: SyncPreferencesRepository,
        authTokenProvider: AuthTokenProvider,
        remoteDataSource: SheetsRemoteDataSource
    ) : this(
        dao = dao,
        syncPreferences = syncPreferences,
        authTokenProvider = authTokenProvider,
        remoteDataSource = remoteDataSource,
        wealthDao = null
    )

    private val syncMutex = Mutex()

    suspend fun syncAll(): SyncOutcome = syncMutex.withLock {
        val prefs = syncPreferences.getSnapshot()
        val spreadsheetId = prefs.spreadsheetId.trim()
        if (spreadsheetId.isEmpty()) {
            val reason = "Missing Spreadsheet ID"
            syncPreferences.recordSyncError(reason)
            return@withLock SyncOutcome.NotConfigured(reason)
        }

        val initialToken = when (val result = authTokenProvider.getAccessToken()) {
            is TokenResult.Success -> result.accessToken
            is TokenResult.ConsentRequired -> {
                syncPreferences.recordSyncError("Google authorization required")
                return@withLock SyncOutcome.ConsentRequired(result.intent)
            }
            is TokenResult.NotConfigured -> {
                syncPreferences.recordSyncError(null)
                return@withLock SyncOutcome.NotConfigured(result.reason)
            }
            is TokenResult.Error -> {
                syncPreferences.recordSyncError(result.message)
                return@withLock SyncOutcome.Error(result.message, result.retryable)
            }
        }

        syncPreferences.setSyncInProgress(true)

        return@withLock try {
            runWithAuthRetry(initialToken) { token ->
                performSyncPass(spreadsheetId = spreadsheetId, accessToken = token)
            }
        } catch (apiError: SheetsApiException) {
            val detail = apiError.message?.takeIf { it.isNotBlank() }
            val message = when (apiError.statusCode) {
                401 -> detail ?: "Authentication expired. Reconnect or refresh token."
                403 -> detail ?: "Spreadsheet access denied. Check account permissions."
                404 -> "Spreadsheet not found."
                else -> detail ?: "Sync failed"
            }
            syncPreferences.recordSyncError(message)
            SyncOutcome.Error(message = message, retryable = apiError.retryable)
        } catch (t: Throwable) {
            val message = t.message ?: "Unexpected sync failure"
            syncPreferences.recordSyncError(message)
            SyncOutcome.Error(message = message, retryable = true)
        }
    }

    private suspend fun runWithAuthRetry(
        initialToken: String,
        block: suspend (String) -> SyncOutcome
    ): SyncOutcome {
        return try {
            block(initialToken)
        } catch (unauthorized: SheetsApiException) {
            if (unauthorized.statusCode != 401) throw unauthorized
            authTokenProvider.invalidateToken(initialToken)
            when (val refreshed = authTokenProvider.getAccessToken()) {
                is TokenResult.Success -> block(refreshed.accessToken)
                is TokenResult.ConsentRequired -> {
                    syncPreferences.recordSyncError("Google authorization required")
                    SyncOutcome.ConsentRequired(refreshed.intent)
                }
                is TokenResult.NotConfigured -> {
                    syncPreferences.recordSyncError(null)
                    SyncOutcome.NotConfigured(refreshed.reason)
                }
                is TokenResult.Error -> {
                    syncPreferences.recordSyncError(refreshed.message)
                    SyncOutcome.Error(refreshed.message, refreshed.retryable)
                }
            }
        }
    }

    private suspend fun performSyncPass(
        spreadsheetId: String,
        accessToken: String
    ): SyncOutcome {
        var metadata = remoteDataSource.getSpreadsheetMetadata(spreadsheetId, accessToken)
        val localSnapshot = dao.getAllTransactionsSnapshot()

        val activeUnsyncedMonths = localSnapshot
            .asSequence()
            .filter { !it.isDeleted && it.syncStatus.needsUpload() }
            .map { YearMonth.from(it.transactionDate) }
            .toSet()

        val existingMonthTabs = metadata.sheets.mapNotNull { tab ->
            SheetLedgerMapper.parseMonthFromTabTitle(tab.title)?.let { month -> month to tab }
        }.toMap()

        val missingMonths = activeUnsyncedMonths.filter { month ->
            SheetLedgerMapper.resolveExistingTabName(month, metadata.sheets.map { it.title }) == null
        }.sorted()

        if (missingMonths.isNotEmpty()) {
            val templateTab = existingMonthTabs.entries
                .maxByOrNull { it.key }
                ?.value
                ?: throw SheetsApiException(
                    statusCode = 400,
                    message = "No monthly template tab found in spreadsheet.",
                    retryable = false
                )

            missingMonths.forEach { month ->
                val newTitle = SheetLedgerMapper.formatMonthTabName(month)
                remoteDataSource.duplicateTemplateTabForMonth(
                    spreadsheetId = spreadsheetId,
                    sourceSheetId = templateTab.sheetId,
                    newTabTitle = newTitle,
                    accessToken = accessToken
                )
            }
            metadata = remoteDataSource.getSpreadsheetMetadata(spreadsheetId, accessToken)
        }

        val tabSnapshots = linkedMapOf<YearMonth, RemoteTabSnapshot>()
        val remoteById = linkedMapOf<String, RemoteRowMatch>()

        metadata.sheets.forEach { tab ->
            val month = SheetLedgerMapper.parseMonthFromTabTitle(tab.title) ?: return@forEach
            if (tabSnapshots.containsKey(month)) return@forEach

            val rawRows = remoteDataSource.getLedgerRows(spreadsheetId, tab.title, accessToken)
            val parsedRows = SheetLedgerMapper.parseSheetRows(month, rawRows)
            val snapshot = RemoteTabSnapshot(
                month = month,
                tab = tab,
                rawRows = rawRows,
                parsedRows = parsedRows.toMutableList(),
                reservedRows = parsedRows.map { it.rowNumber }.toMutableSet()
            )
            tabSnapshots[month] = snapshot
            parsedRows.forEach { row ->
                remoteById[row.id] = RemoteRowMatch(
                    month = month,
                    tabTitle = tab.title,
                    row = row
                )
            }
        }

        var uploadedCount = 0
        var deletedRemoteCount = 0
        var conflictCount = 0
        var importedCount = 0

        val now = Instant.now()
        val deletedIdsThisPass = mutableSetOf<String>()

        // 1. Push unsynced local additions, edits, and tombstone deletions.
        val unsynced = localSnapshot.filter { it.syncStatus.needsUpload() || it.isDeleted }
        unsynced.forEach { local ->
            val remoteMatch = remoteById[local.id]

            if (local.isDeleted) {
                if (remoteMatch != null) {
                    remoteDataSource.clearLedgerRow(
                        spreadsheetId = spreadsheetId,
                        tabTitle = remoteMatch.tabTitle,
                        rowNumber = remoteMatch.row.rowNumber,
                        accessToken = accessToken
                    )
                    deletedRemoteCount++
                    remoteById.remove(local.id)
                    tabSnapshots[remoteMatch.month]?.reservedRows?.remove(remoteMatch.row.rowNumber)
                    tabSnapshots[remoteMatch.month]?.parsedRows?.removeAll { it.id == local.id }
                }
                deletedIdsThisPass.add(local.id)
                dao.hardDeleteById(local.id)
                return@forEach
            }

            val targetMonth = YearMonth.from(local.transactionDate)
            val targetTabSnapshot = tabSnapshots[targetMonth]
                ?: run {
                    dao.update(local.copy(syncStatus = SyncStatus.FAILED, updatedAt = now))
                    return@forEach
                }

            val localRevision = SheetLedgerMapper.computeRevision(local)

            if (remoteMatch != null) {
                val remoteModifiedSinceLastSync =
                    local.remoteRevision != null &&
                        remoteMatch.row.revision != local.remoteRevision &&
                        localRevision != remoteMatch.row.revision

                if (remoteModifiedSinceLastSync) {
                    dao.update(local.copy(syncStatus = SyncStatus.CONFLICT, updatedAt = now))
                    conflictCount++
                    return@forEach
                }

                if (remoteMatch.month == targetMonth) {
                    val values = SheetLedgerMapper.toSheetRowValues(local, remoteMatch.row.notes)
                    remoteDataSource.updateLedgerRow(
                        spreadsheetId = spreadsheetId,
                        tabTitle = remoteMatch.tabTitle,
                        rowNumber = remoteMatch.row.rowNumber,
                        rowValues = values,
                        accessToken = accessToken
                    )
                    dao.update(
                        local.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteRevision = localRevision
                        )
                    )
                    uploadedCount++
                } else {
                    val newRowNumber = SheetLedgerMapper.findNextAvailableRowNumber(
                        rawRows = targetTabSnapshot.rawRows,
                        reservedRows = targetTabSnapshot.reservedRows
                    )
                    targetTabSnapshot.reservedRows.add(newRowNumber)
                    val values = SheetLedgerMapper.toSheetRowValues(local, remoteMatch.row.notes)
                    remoteDataSource.updateLedgerRow(
                        spreadsheetId = spreadsheetId,
                        tabTitle = targetTabSnapshot.tab.title,
                        rowNumber = newRowNumber,
                        rowValues = values,
                        accessToken = accessToken
                    )
                    remoteDataSource.clearLedgerRow(
                        spreadsheetId = spreadsheetId,
                        tabTitle = remoteMatch.tabTitle,
                        rowNumber = remoteMatch.row.rowNumber,
                        accessToken = accessToken
                    )
                    tabSnapshots[remoteMatch.month]?.reservedRows?.remove(remoteMatch.row.rowNumber)
                    tabSnapshots[remoteMatch.month]?.parsedRows?.removeAll { it.id == local.id }
                    dao.update(
                        local.copy(
                            syncStatus = SyncStatus.SYNCED,
                            remoteRevision = localRevision
                        )
                    )
                    uploadedCount++
                }
            } else {
                val rowNumber = SheetLedgerMapper.findNextAvailableRowNumber(
                    rawRows = targetTabSnapshot.rawRows,
                    reservedRows = targetTabSnapshot.reservedRows
                )
                targetTabSnapshot.reservedRows.add(rowNumber)
                val values = SheetLedgerMapper.toSheetRowValues(local, existingNotes = null)
                remoteDataSource.updateLedgerRow(
                    spreadsheetId = spreadsheetId,
                    tabTitle = targetTabSnapshot.tab.title,
                    rowNumber = rowNumber,
                    rowValues = values,
                    accessToken = accessToken
                )
                dao.update(
                    local.copy(
                        syncStatus = SyncStatus.SYNCED,
                        remoteRevision = localRevision
                    )
                )
                uploadedCount++
            }
        }

        // 2. Pull remote transactions (legacy + multi-device) without clobbering unsynced local rows.
        val refreshedLocal = dao.getAllTransactionsSnapshot()
        val localById = refreshedLocal.associateBy { it.id }

        tabSnapshots.values.forEach { snapshot ->
            snapshot.parsedRows.forEach { remoteRow ->
                if (remoteRow.id in deletedIdsThisPass) return@forEach
                val existingLocal = localById[remoteRow.id]
                if (existingLocal == null) {
                    val timestamp = remoteRow.updatedAt ?: now
                    dao.insert(
                        TransactionEntity(
                            id = remoteRow.id,
                            type = remoteRow.type,
                            amountMinor = remoteRow.amountMinor,
                            currency = "MYR",
                            category = remoteRow.category,
                            remark = remoteRow.remark,
                            transactionDate = remoteRow.transactionDate,
                            createdAt = timestamp,
                            updatedAt = timestamp,
                            syncStatus = SyncStatus.SYNCED,
                            remoteRevision = remoteRow.revision,
                            isDeleted = false
                        )
                    )
                    importedCount++
                } else if (
                    !existingLocal.isDeleted &&
                    existingLocal.syncStatus == SyncStatus.SYNCED &&
                    existingLocal.remoteRevision != remoteRow.revision
                ) {
                    dao.update(
                        existingLocal.copy(
                            type = remoteRow.type,
                            amountMinor = remoteRow.amountMinor,
                            category = remoteRow.category,
                            remark = remoteRow.remark,
                            transactionDate = remoteRow.transactionDate,
                            updatedAt = remoteRow.updatedAt ?: now,
                            remoteRevision = remoteRow.revision
                        )
                    )
                    importedCount++
                }
            }
        }

        // Remove stale synced legacy rows that no longer exist in freshly scanned tabs.
        val activeRemoteIds = remoteById.keys
        refreshedLocal.forEach { local ->
            val relevantMonth = SheetLedgerMapper.parseLegacyTabMonth(local.id)
                ?: YearMonth.from(local.transactionDate)
            if (
                local.id.startsWith("legacy-") &&
                local.syncStatus == SyncStatus.SYNCED &&
                !local.isDeleted &&
                relevantMonth in tabSnapshots.keys &&
                local.id !in activeRemoteIds
            ) {
                dao.hardDeleteById(local.id)
            }
        }

        // 3. Extract observed Expense and Income categories from the workbook.
        val finalRows = dao.getAllTransactionsSnapshot().filter { !it.isDeleted }
        val discoveredExpense = finalRows
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .map { it.category }
            .distinct()
            .toList()
        val discoveredIncome = finalRows
            .asSequence()
            .filter { it.type == TransactionType.INCOME }
            .map { it.category }
            .distinct()
            .toList()
        if (discoveredExpense.isNotEmpty() || discoveredIncome.isNotEmpty()) {
            syncPreferences.updateCategories(
                expenseCategories = discoveredExpense,
                incomeCategories = discoveredIncome
            )
        }

        // 4. Reconcile monthly Wealth snapshots with the dedicated "Wealth" tab.
        val (wealthUploaded, wealthImported) = reconcileWealthTab(
            spreadsheetId = spreadsheetId,
            accessToken = accessToken,
            metadata = metadata,
            now = now
        )
        uploadedCount += wealthUploaded
        importedCount += wealthImported

        syncPreferences.recordSyncSuccess(System.currentTimeMillis())
        return SyncOutcome.Success(
            uploadedCount = uploadedCount,
            importedCount = importedCount,
            deletedRemoteCount = deletedRemoteCount,
            conflictCount = conflictCount
        )
    }

    private suspend fun reconcileWealthTab(
        spreadsheetId: String,
        accessToken: String,
        metadata: SpreadsheetMetadata,
        now: Instant
    ): Pair<Int, Int> {
        val dao = wealthDao ?: return 0 to 0
        val localEntries = dao.getAllEntriesSnapshot()
        val hasWealthTab = metadata.sheets.any { it.title.equals(WEALTH_TAB_TITLE, ignoreCase = true) }
        if (!hasWealthTab && localEntries.isEmpty()) {
            return 0 to 0
        }
        if (!hasWealthTab) {
            remoteDataSource.ensureWealthTabExists(spreadsheetId, accessToken)
        }

        val remoteRawRows = remoteDataSource.getWealthRows(spreadsheetId, accessToken)
        val remoteByMonth = linkedMapOf<String, WealthEntryEntity>()
        remoteRawRows.forEach { row ->
            parseWealthSheetRow(row, now)?.let { parsed ->
                remoteByMonth[parsed.yearMonth] = parsed
            }
        }

        val localByMonth = localEntries.associateBy { it.yearMonth }
        val allMonths = (localByMonth.keys + remoteByMonth.keys).toSortedSet()
        if (allMonths.isEmpty()) return 0 to 0

        var imported = 0
        var needUpload = false
        val mergedList = mutableListOf<WealthEntryEntity>()

        allMonths.forEach { monthKey ->
            val local = localByMonth[monthKey]
            val remote = remoteByMonth[monthKey]
            when {
                local == null && remote != null -> {
                    val synced = remote.copy(syncStatus = SyncStatus.SYNCED)
                    dao.upsert(synced)
                    mergedList.add(synced)
                    imported++
                }
                local != null && remote == null -> {
                    mergedList.add(local)
                    needUpload = true
                }
                local != null && remote != null -> {
                    val remoteNewer = remote.updatedAt.isAfter(local.updatedAt)
                    val sameData =
                        local.savingsSavedMinor == remote.savingsSavedMinor &&
                            local.savingsTargetMinor == remote.savingsTargetMinor &&
                            local.investmentSavedMinor == remote.investmentSavedMinor &&
                            local.investmentTargetMinor == remote.investmentTargetMinor &&
                            local.note == remote.note

                    if (remoteNewer || (local.syncStatus == SyncStatus.SYNCED && !sameData)) {
                        val synced = remote.copy(syncStatus = SyncStatus.SYNCED)
                        if (!sameData || local.syncStatus != SyncStatus.SYNCED) {
                            dao.upsert(synced)
                            imported++
                        }
                        mergedList.add(synced)
                    } else {
                        mergedList.add(local)
                        if (!sameData || local.syncStatus.needsUpload()) {
                            needUpload = true
                        }
                    }
                }
            }
        }

        val sortedMerged = mergedList.sortedBy { it.yearMonth }
        var uploaded = 0
        if (needUpload) {
            val serializedRows = sortedMerged.map { entry ->
                listOf(
                    "'${entry.yearMonth}",
                    SheetLedgerMapper.formatMajorAmount(entry.savingsSavedMinor),
                    SheetLedgerMapper.formatMajorAmount(entry.savingsTargetMinor),
                    SheetLedgerMapper.formatMajorAmount(entry.investmentSavedMinor),
                    SheetLedgerMapper.formatMajorAmount(entry.investmentTargetMinor),
                    entry.note.orEmpty(),
                    "'${entry.updatedAt}"
                )
            }
            remoteDataSource.updateWealthRows(spreadsheetId, serializedRows, accessToken)
            sortedMerged.forEach { entry ->
                if (entry.syncStatus != SyncStatus.SYNCED) {
                    dao.upsert(entry.copy(syncStatus = SyncStatus.SYNCED))
                    uploaded++
                }
            }
        } else {
            sortedMerged.forEach { entry ->
                if (entry.syncStatus != SyncStatus.SYNCED) {
                    dao.upsert(entry.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }

        return uploaded to imported
    }

    private fun parseWealthSheetRow(
        row: List<String>,
        fallbackTimestamp: Instant
    ): WealthEntryEntity? {
        val yearMonth = parseWealthYearMonth(row.getOrElse(0) { "" }) ?: return null
        val savingsSaved = parseWealthAmountMinor(row.getOrElse(1) { "" }) ?: 0L
        val savingsTarget = parseWealthAmountMinor(row.getOrElse(2) { "" }) ?: 100_000L
        val investmentSaved = parseWealthAmountMinor(row.getOrElse(3) { "" }) ?: 0L
        val investmentTarget = parseWealthAmountMinor(row.getOrElse(4) { "" }) ?: 50_000L
        val note = row.getOrElse(5) { "" }.trim().takeIf { it.isNotEmpty() }
        val updatedAt = row.getOrElse(6) { "" }.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { raw -> runCatching { Instant.parse(raw) }.getOrNull() }
            ?: fallbackTimestamp

        return WealthEntryEntity(
            yearMonth = yearMonth.toString(),
            savingsSavedMinor = savingsSaved,
            savingsTargetMinor = savingsTarget,
            investmentSavedMinor = investmentSaved,
            investmentTargetMinor = investmentTarget,
            note = note,
            updatedAt = updatedAt,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun parseWealthYearMonth(raw: String): YearMonth? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        runCatching { return YearMonth.parse(trimmed) }
        SheetLedgerMapper.parseMonthFromTabTitle(trimmed)?.let { return it }
        val serial = trimmed.toDoubleOrNull()
        if (serial != null && serial >= 20_000.0 && serial <= 100_000.0) {
            val epoch = LocalDate.of(1899, 12, 30)
            return YearMonth.from(epoch.plusDays(serial.toLong()))
        }
        return null
    }

    private fun parseWealthAmountMinor(raw: String): Long? {
        val cleaned = raw
            .replace("RM", "", ignoreCase = true)
            .replace("MYR", "", ignoreCase = true)
            .replace("$", "")
            .replace(",", "")
            .trim()
        if (cleaned.isEmpty()) return null
        val decimal = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return null
        if (decimal < BigDecimal.ZERO) return null
        return decimal.movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    private fun SyncStatus.needsUpload(): Boolean =
        this == SyncStatus.PENDING || this == SyncStatus.FAILED || this == SyncStatus.SYNCING

    private companion object {
        const val WEALTH_TAB_TITLE = "Wealth"
    }
}
