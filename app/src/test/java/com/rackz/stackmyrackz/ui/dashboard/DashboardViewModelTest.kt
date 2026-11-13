package com.rackz.stackmyrackz.ui.dashboard

import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val currentMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur"))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun monthlyTotalsAndSavingsRate_computedFromActiveTransactions() = runTest {
        val repo = FakeDashboardTransactionRepository()
        repo.saveTransaction(TransactionType.INCOME, 500_000L, "Salary", null, currentMonth.atDay(1))
        repo.saveTransaction(TransactionType.EXPENSE, 150_000L, "Groceries & Food", "Supermarket", currentMonth.atDay(3))
        repo.saveTransaction(TransactionType.EXPENSE, 50_000L, "Transportation", "MRT", currentMonth.atDay(5))

        val viewModel = DashboardViewModel(repo)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(500_000L, state.incomeMinor)
        assertEquals(200_000L, state.expenseMinor)
        assertEquals(300_000L, state.netMinor)
        assertEquals(60, state.savingsRatePercent)
        collector.cancel()
    }

    @Test
    fun categoryBreakdown_groupsAndSortsExpensesDescending() = runTest {
        val repo = FakeDashboardTransactionRepository()
        repo.saveTransaction(TransactionType.EXPENSE, 2_000L, "Transportation", null, currentMonth.atDay(2))
        repo.saveTransaction(TransactionType.EXPENSE, 3_000L, "Groceries & Food", null, currentMonth.atDay(3))
        repo.saveTransaction(TransactionType.EXPENSE, 5_000L, "Groceries & Food", null, currentMonth.atDay(4))
        repo.saveTransaction(TransactionType.INCOME, 20_000L, "Salary", null, currentMonth.atDay(1))

        val viewModel = DashboardViewModel(repo)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val breakdown = viewModel.uiState.value.categoryBreakdown
        assertEquals(2, breakdown.size)
        assertEquals("Groceries & Food", breakdown[0].category)
        assertEquals(8_000L, breakdown[0].amountMinor)
        assertEquals(80, breakdown[0].percentage)
        assertEquals("Transportation", breakdown[1].category)
        assertEquals(2_000L, breakdown[1].amountMinor)
        assertEquals(20, breakdown[1].percentage)
        collector.cancel()
    }

    @Test
    fun monthNavigation_switchesObservedMonth() = runTest {
        val repo = FakeDashboardTransactionRepository()
        val prevMonth = currentMonth.minusMonths(1)
        repo.saveTransaction(TransactionType.EXPENSE, 1_500L, "Dining Out", null, currentMonth.atDay(5))
        repo.saveTransaction(TransactionType.EXPENSE, 9_000L, "Utilities & Bills", null, prevMonth.atDay(10))

        val viewModel = DashboardViewModel(repo)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(currentMonth, viewModel.uiState.value.currentMonth)
        assertEquals(1_500L, viewModel.uiState.value.expenseMinor)

        viewModel.onPreviousMonth()
        advanceUntilIdle()

        assertEquals(prevMonth, viewModel.uiState.value.currentMonth)
        assertEquals(9_000L, viewModel.uiState.value.expenseMinor)

        viewModel.onNextMonth()
        advanceUntilIdle()

        assertEquals(currentMonth, viewModel.uiState.value.currentMonth)
        assertEquals(1_500L, viewModel.uiState.value.expenseMinor)
        collector.cancel()
    }

    @Test
    fun typeAndCategoryFilters_filterTransactionsCorrectly() = runTest {
        val repo = FakeDashboardTransactionRepository()
        repo.saveTransaction(TransactionType.INCOME, 100_000L, "Salary", null, currentMonth.atDay(1))
        repo.saveTransaction(TransactionType.EXPENSE, 4_000L, "Groceries & Food", null, currentMonth.atDay(2))
        repo.saveTransaction(TransactionType.EXPENSE, 1_200L, "Transportation", null, currentMonth.atDay(3))

        val viewModel = DashboardViewModel(repo)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.filteredTransactions.size)

        viewModel.onTypeFilterSelected(HistoryTypeFilter.EXPENSE)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.filteredTransactions.size)

        viewModel.onCategoryFilterSelected("Transportation")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredTransactions.size)
        assertEquals("Transportation", viewModel.uiState.value.filteredTransactions.first().category)

        viewModel.onCategoryFilterSelected("Transportation")
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.selectedCategoryFilter)
        assertEquals(2, viewModel.uiState.value.filteredTransactions.size)
        collector.cancel()
    }

    @Test
    fun updateAndSoftDeleteWithUndo_updatesStateAndPreservesTombstone() = runTest {
        val repo = FakeDashboardTransactionRepository()
        val tx = repo.saveTransaction(TransactionType.EXPENSE, 2_500L, "Shopping", "Shirt", currentMonth.atDay(6))

        val viewModel = DashboardViewModel(repo)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        val events = mutableListOf<DashboardEvent>()
        val eventCollector = backgroundScope.launch { viewModel.events.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.onUpdateTransaction(
            id = tx.id,
            type = TransactionType.EXPENSE,
            amountMinor = 3_000L,
            category = "For Weiwei",
            remark = "Gift",
            date = currentMonth.atDay(7)
        )
        advanceUntilIdle()

        assertEquals(3_000L, viewModel.uiState.value.expenseMinor)
        assertEquals("For Weiwei", viewModel.uiState.value.transactions.first().category)
        assertEquals("Gift", viewModel.uiState.value.transactions.first().remark)

        viewModel.onDeleteTransaction(tx.id)
        advanceUntilIdle()

        assertEquals(0L, viewModel.uiState.value.expenseMinor)
        assertTrue(viewModel.uiState.value.transactions.isEmpty())
        assertTrue(repo.records.first().isDeleted)
        assertTrue(events.any { it is DashboardEvent.TransactionDeleted && it.id == tx.id })

        viewModel.onUndoDelete(tx.id)
        advanceUntilIdle()

        assertFalse(repo.records.first().isDeleted)
        assertEquals(3_000L, viewModel.uiState.value.expenseMinor)
        assertEquals(1, viewModel.uiState.value.transactions.size)

        eventCollector.cancel()
        collector.cancel()
    }
}

private class FakeDashboardTransactionRepository : TransactionRepository {
    val records = mutableListOf<TransactionEntity>()
    private val items = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun saveTransaction(
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String?,
        transactionDate: LocalDate
    ): TransactionEntity {
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
        records.add(entity)
        items.value = records.toList()
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
        val index = records.indexOfFirst { it.id == id }
        if (index == -1) return null
        val updated = records[index].copy(
            type = type,
            amountMinor = amountMinor,
            category = category,
            remark = remark?.trim()?.takeIf { it.isNotEmpty() },
            transactionDate = transactionDate,
            updatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING
        )
        records[index] = updated
        items.value = records.toList()
        return updated
    }

    override suspend fun softDeleteTransaction(id: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index == -1) return
        records[index] = records[index].copy(isDeleted = true, updatedAt = Instant.now())
        items.value = records.toList()
    }

    override suspend fun restoreTransaction(id: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index == -1) return
        records[index] = records[index].copy(isDeleted = false, updatedAt = Instant.now())
        items.value = records.toList()
    }

    override fun observeAllTransactions(): Flow<List<TransactionEntity>> =
        items.map { list -> list.filter { !it.isDeleted } }

    override fun observeTransactionsForMonth(month: YearMonth): Flow<List<TransactionEntity>> =
        items.map { list ->
            list.filter {
                !it.isDeleted &&
                    !it.transactionDate.isBefore(month.atDay(1)) &&
                    !it.transactionDate.isAfter(month.atEndOfMonth())
            }
        }

    override fun observePendingSyncCount(): Flow<Int> =
        items.map { list -> list.count { !it.isDeleted && it.syncStatus == SyncStatus.PENDING } }
}
