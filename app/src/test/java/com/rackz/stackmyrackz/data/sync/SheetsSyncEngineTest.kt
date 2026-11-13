package com.rackz.stackmyrackz.data.sync

import com.rackz.stackmyrackz.data.local.TransactionDao
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.preferences.InMemorySyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesState
import com.rackz.stackmyrackz.data.remote.AuthTokenProvider
import com.rackz.stackmyrackz.data.remote.SheetTabInfo
import com.rackz.stackmyrackz.data.remote.SheetsRemoteDataSource
import com.rackz.stackmyrackz.data.remote.SpreadsheetMetadata
import com.rackz.stackmyrackz.data.remote.TokenResult
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SheetsSyncEngineTest {

    @Test
    fun syncAll_uploadsPendingTransactionToNextEmptyRowAndMarksSynced() = runTest {
        val dao = FakeSyncDao()
        val remote = FakeSheetsRemoteDataSource(
            tabs = mutableListOf(
                SheetTabInfo(1, "Notes", 0),
                SheetTabInfo(2, "Nov 2026", 1)
            ),
            tabRows = mutableMapOf(
                "Nov 2026" to mutableListOf(
                    mutableListOf("1/11", "Expense", "Transportation", "Grab", "15.00")
                )
            )
        )
        val prefs = InMemorySyncPreferencesRepository(
            SyncPreferencesState(spreadsheetId = "sheet-1", accountEmail = "user@gmail.com")
        )
        val engine = SheetsSyncEngine(dao, prefs, FakeAuthTokenProvider(), remote)

        val localTx = TransactionEntity(
            id = "uuid-new-1",
            type = TransactionType.EXPENSE,
            amountMinor = 1850L,
            category = "Groceries & Food",
            remark = "Nasi lemak",
            transactionDate = LocalDate.of(2026, 11, 13),
            createdAt = Instant.parse("2026-11-13T08:00:00Z"),
            updatedAt = Instant.parse("2026-11-13T08:00:00Z"),
            syncStatus = SyncStatus.PENDING
        )
        dao.insert(localTx)

        val outcome = engine.syncAll() as SyncOutcome.Success

        assertEquals(1, outcome.uploadedCount)
        assertEquals(1, outcome.importedCount) // legacy row imported
        assertEquals(0, outcome.conflictCount)

        val syncedLocal = dao.getById("uuid-new-1")!!
        assertEquals(SyncStatus.SYNCED, syncedLocal.syncStatus)
        assertNotNull(syncedLocal.remoteRevision)

        // Legacy row at index 0 (row 10), uploaded row at index 1 (row 11)
        val sheetNovRows = remote.tabRows["Nov 2026"]!!
        assertEquals(2, sheetNovRows.size)
        assertEquals("uuid-new-1", sheetNovRows[1][6])
        assertEquals("18.50", sheetNovRows[1][4])
        assertEquals("Racks", sheetNovRows[1][8])
    }

    @Test
    fun syncAll_retryWithExistingRemoteUuid_updatesInPlaceWithoutDuplicates() = runTest {
        val dao = FakeSyncDao()
        val remote = FakeSheetsRemoteDataSource(
            tabs = mutableListOf(SheetTabInfo(2, "Nov 2026", 0)),
            tabRows = mutableMapOf(
                "Nov 2026" to mutableListOf(
                    mutableListOf(
                        "2026-11-13",
                        "Expense",
                        "Groceries & Food",
                        "Old coffee",
                        "10.00",
                        "existing note",
                        "uuid-retry-1",
                        "2026-11-13T08:00:00Z",
                        "Racks"
                    )
                )
            )
        )
        val prefs = InMemorySyncPreferencesRepository(
            SyncPreferencesState(spreadsheetId = "sheet-1", accountEmail = "user@gmail.com")
        )
        val engine = SheetsSyncEngine(dao, prefs, FakeAuthTokenProvider(), remote)

        // Simulates a retry after a local edit or interrupted sync without remoteRevision set
        dao.insert(
            TransactionEntity(
                id = "uuid-retry-1",
                type = TransactionType.EXPENSE,
                amountMinor = 1250L,
                category = "Groceries & Food",
                remark = "Updated coffee",
                transactionDate = LocalDate.of(2026, 11, 13),
                createdAt = Instant.parse("2026-11-13T08:00:00Z"),
                updatedAt = Instant.parse("2026-11-13T08:05:00Z"),
                syncStatus = SyncStatus.PENDING,
                remoteRevision = null
            )
        )

        val outcome = engine.syncAll() as SyncOutcome.Success

        assertEquals(1, outcome.uploadedCount)
        assertEquals(1, remote.tabRows["Nov 2026"]!!.size)
        assertEquals("12.50", remote.tabRows["Nov 2026"]!![0][4])
        assertEquals("existing note", remote.tabRows["Nov 2026"]!![0][5])
    }

    @Test
    fun syncAll_detectsConflictWhenBothLocalAndRemoteChanged() = runTest {
        val dao = FakeSyncDao()
        val originalRevision = SheetLedgerMapper.computeRevision(
            transactionDate = LocalDate.of(2026, 11, 13),
            type = TransactionType.EXPENSE,
            amountMinor = 1000L,
            category = "Shopping",
            remark = "Shirt"
        )
        val remote = FakeSheetsRemoteDataSource(
            tabs = mutableListOf(SheetTabInfo(2, "Nov 2026", 0)),
            tabRows = mutableMapOf(
                "Nov 2026" to mutableListOf(
                    mutableListOf(
                        "2026-11-13",
                        "Expense",
                        "Shopping",
                        "Shirt edited on sheet",
                        "12.00",
                        "",
                        "uuid-conflict-1",
                        "2026-11-13T09:00:00Z",
                        "Racks"
                    )
                )
            )
        )
        val prefs = InMemorySyncPreferencesRepository(
            SyncPreferencesState(spreadsheetId = "sheet-1", accountEmail = "user@gmail.com")
        )
        val engine = SheetsSyncEngine(dao, prefs, FakeAuthTokenProvider(), remote)

        dao.insert(
            TransactionEntity(
                id = "uuid-conflict-1",
                type = TransactionType.EXPENSE,
                amountMinor = 1500L,
                category = "Shopping",
                remark = "Shirt edited locally",
                transactionDate = LocalDate.of(2026, 11, 13),
                createdAt = Instant.parse("2026-11-13T08:00:00Z"),
                updatedAt = Instant.parse("2026-11-13T09:05:00Z"),
                syncStatus = SyncStatus.PENDING,
                remoteRevision = originalRevision
            )
        )

        val outcome = engine.syncAll() as SyncOutcome.Success

        assertEquals(1, outcome.conflictCount)
        assertEquals(0, outcome.uploadedCount)
        assertEquals(SyncStatus.CONFLICT, dao.getById("uuid-conflict-1")!!.syncStatus)
        // Remote sheet value was NOT overwritten
        assertEquals("12.00", remote.tabRows["Nov 2026"]!![0][4])
    }

    @Test
    fun syncAll_tombstoneDeletesRemoteRowAndRemovesLocalTombstone() = runTest {
        val dao = FakeSyncDao()
        val remote = FakeSheetsRemoteDataSource(
            tabs = mutableListOf(SheetTabInfo(2, "Nov 2026", 0)),
            tabRows = mutableMapOf(
                "Nov 2026" to mutableListOf(
                    mutableListOf(
                        "2026-11-13",
                        "Expense",
                        "Dining Out",
                        "Mamak",
                        "9.00",
                        "",
                        "uuid-del-1",
                        "2026-11-13T08:00:00Z",
                        "Racks"
                    )
                )
            )
        )
        val prefs = InMemorySyncPreferencesRepository(
            SyncPreferencesState(spreadsheetId = "sheet-1", accountEmail = "user@gmail.com")
        )
        val engine = SheetsSyncEngine(dao, prefs, FakeAuthTokenProvider(), remote)

        dao.insert(
            TransactionEntity(
                id = "uuid-del-1",
                type = TransactionType.EXPENSE,
                amountMinor = 900L,
                category = "Dining Out",
                remark = "Mamak",
                transactionDate = LocalDate.of(2026, 11, 13),
                createdAt = Instant.parse("2026-11-13T08:00:00Z"),
                updatedAt = Instant.parse("2026-11-13T08:10:00Z"),
                syncStatus = SyncStatus.PENDING,
                isDeleted = true
            )
        )

        val outcome = engine.syncAll() as SyncOutcome.Success

        assertEquals(1, outcome.deletedRemoteCount)
        assertNull(dao.getById("uuid-del-1"))
        assertTrue(remote.tabRows["Nov 2026"]!![0].all { it.isBlank() })
    }

    @Test
    fun syncAll_missingFutureMonthTab_duplicatesLatestMonthlyTemplate() = runTest {
        val dao = FakeSyncDao()
        val remote = FakeSheetsRemoteDataSource(
            tabs = mutableListOf(
                SheetTabInfo(10, "Nov 2026", 0),
                SheetTabInfo(11, "Dec 2026", 1)
            ),
            tabRows = mutableMapOf(
                "Nov 2026" to mutableListOf(),
                "Dec 2026" to mutableListOf(
                    mutableListOf("2026-12-01", "Expense", "Utilities & Bills", "Internet", "129.00")
                )
            )
        )
        val prefs = InMemorySyncPreferencesRepository(
            SyncPreferencesState(spreadsheetId = "sheet-1", accountEmail = "user@gmail.com")
        )
        val engine = SheetsSyncEngine(dao, prefs, FakeAuthTokenProvider(), remote)

        dao.insert(
            TransactionEntity(
                id = "uuid-jan-1",
                type = TransactionType.INCOME,
                amountMinor = 400_000L,
                category = "Salary",
                remark = "Jan salary",
                transactionDate = LocalDate.of(2027, 1, 5),
                createdAt = Instant.parse("2027-01-05T08:00:00Z"),
                updatedAt = Instant.parse("2027-01-05T08:00:00Z"),
                syncStatus = SyncStatus.PENDING
            )
        )

        val outcome = engine.syncAll() as SyncOutcome.Success

        assertEquals(1, outcome.uploadedCount)
        assertTrue(remote.tabs.any { it.title == "Jan 2027" })
        assertEquals(11, remote.duplicatedFromSheetId) // Dec 2026 was used as template
        assertFalse(remote.tabRows["Jan 2027"]!!.isEmpty())
        assertEquals("uuid-jan-1", remote.tabRows["Jan 2027"]!![0][6])
    }
}

private class FakeAuthTokenProvider : AuthTokenProvider {
    override suspend fun getAccessToken(): TokenResult = TokenResult.Success("fake-token")
    override suspend fun invalidateToken(accessToken: String) = Unit
}

private class FakeSheetsRemoteDataSource(
    val tabs: MutableList<SheetTabInfo>,
    val tabRows: MutableMap<String, MutableList<MutableList<String>>>
) : SheetsRemoteDataSource {

    var duplicatedFromSheetId: Int? = null
    private var nextSheetId = 100

    override suspend fun getSpreadsheetMetadata(
        spreadsheetId: String,
        accessToken: String
    ): SpreadsheetMetadata = SpreadsheetMetadata(
        spreadsheetId = spreadsheetId,
        title = "Money Tracker 2026",
        sheets = tabs.toList()
    )

    override suspend fun getLedgerRows(
        spreadsheetId: String,
        tabTitle: String,
        accessToken: String
    ): List<List<String>> = tabRows[tabTitle]?.map { it.toList() }.orEmpty()

    override suspend fun updateLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        rowValues: List<String>,
        accessToken: String
    ) {
        val rows = tabRows.getOrPut(tabTitle) { mutableListOf() }
        val index = rowNumber - SheetLedgerMapper.FIRST_LEDGER_ROW
        while (rows.size <= index) {
            rows.add(mutableListOf())
        }
        rows[index] = rowValues.toMutableList()
    }

    override suspend fun clearLedgerRow(
        spreadsheetId: String,
        tabTitle: String,
        rowNumber: Int,
        accessToken: String
    ) {
        val rows = tabRows[tabTitle] ?: return
        val index = rowNumber - SheetLedgerMapper.FIRST_LEDGER_ROW
        if (index in rows.indices) {
            rows[index] = MutableList(9) { "" }
        }
    }

    override suspend fun duplicateTemplateTabForMonth(
        spreadsheetId: String,
        sourceSheetId: Int,
        newTabTitle: String,
        accessToken: String
    ) {
        duplicatedFromSheetId = sourceSheetId
        tabs.add(SheetTabInfo(nextSheetId++, newTabTitle, tabs.size))
        tabRows[newTabTitle] = mutableListOf()
    }
}

private class FakeSyncDao : TransactionDao {
    private val map = linkedMapOf<String, TransactionEntity>()
    private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun insert(transaction: TransactionEntity) {
        map[transaction.id] = transaction
        emit()
    }

    override suspend fun update(transaction: TransactionEntity) {
        map[transaction.id] = transaction
        emit()
    }

    override suspend fun getById(id: String): TransactionEntity? = map[id]

    override fun observeAll(): Flow<List<TransactionEntity>> =
        flow.map { list -> list.filter { !it.isDeleted } }

    override fun observeByMonth(
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<TransactionEntity>> =
        flow.map { list ->
            list.filter {
                !it.isDeleted &&
                    !it.transactionDate.isBefore(startDate) &&
                    !it.transactionDate.isAfter(endDate)
            }
        }

    override fun observePendingCount(): Flow<Int> =
        flow.map { list ->
            list.count {
                it.syncStatus == SyncStatus.PENDING ||
                    it.syncStatus == SyncStatus.FAILED ||
                    it.syncStatus == SyncStatus.SYNCING
            }
        }

    override suspend fun getUnsyncedTransactions(): List<TransactionEntity> =
        map.values.filter {
            it.syncStatus == SyncStatus.PENDING ||
                it.syncStatus == SyncStatus.FAILED ||
                it.syncStatus == SyncStatus.SYNCING
        }

    override suspend fun getAllTransactionsSnapshot(): List<TransactionEntity> = map.values.toList()

    override suspend fun hardDeleteById(id: String) {
        map.remove(id)
        emit()
    }

    private fun emit() {
        flow.value = map.values.toList()
    }
}
