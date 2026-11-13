package com.rackz.stackmyrackz.data.sync

import android.content.Intent
import com.rackz.stackmyrackz.data.local.TransactionDao
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.remote.AuthTokenProvider
import com.rackz.stackmyrackz.data.remote.SheetTabInfo
import com.rackz.stackmyrackz.data.remote.SheetsApiException
import com.rackz.stackmyrackz.data.remote.SheetsRemoteDataSource
import com.rackz.stackmyrackz.data.remote.TokenResult
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
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
    private val remoteDataSource: SheetsRemoteDataSource
) {

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
            val message = when (apiError.statusCode) {
                401, 403 -> "Spreadsheet access denied. Check account permissions."
                404 -> "Spreadsheet not found."
                else -> apiError.message ?: "Sync failed"
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
            if (
                local.id.startsWith("legacy-") &&
                local.syncStatus == SyncStatus.SYNCED &&
                !local.isDeleted &&
                YearMonth.from(local.transactionDate) in tabSnapshots.keys &&
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

        syncPreferences.recordSyncSuccess(System.currentTimeMillis())
        return SyncOutcome.Success(
            uploadedCount = uploadedCount,
            importedCount = importedCount,
            deletedRemoteCount = deletedRemoteCount,
            conflictCount = conflictCount
        )
    }

    private fun SyncStatus.needsUpload(): Boolean =
        this == SyncStatus.PENDING || this == SyncStatus.FAILED || this == SyncStatus.SYNCING
}
