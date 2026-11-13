package com.rackz.stackmyrackz.ui.home

import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.repository.NetworkMonitor
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.model.DefaultCategories
import com.rackz.stackmyrackz.model.SyncStatus
import com.rackz.stackmyrackz.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openAndDismissSheet_preservesEnteredAmount() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = HomeViewModel(repo, FakeNetworkMonitor(online = true))
        val collector = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.onDigitPressed('2')
        viewModel.onDigitPressed('5')
        viewModel.onOpenSheet()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSheetOpen)
        assertEquals(2500L, viewModel.uiState.value.amountInput.amountMinor)

        viewModel.onDismissSheet()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSheetOpen)
        assertEquals(2500L, viewModel.uiState.value.amountInput.amountMinor)
        collector.cancel()
    }

    @Test
    fun switchType_updatesCategoryList() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = HomeViewModel(repo, FakeNetworkMonitor(online = true))
        val collector = backgroundScope.launch { viewModel.uiState.collect() }

        viewModel.onTypeSelected(TransactionType.INCOME)
        advanceUntilIdle()

        assertEquals(TransactionType.INCOME, viewModel.uiState.value.transactionType)
        assertEquals(
            DefaultCategories.incomeCategories.first(),
            viewModel.uiState.value.selectedCategory
        )
        collector.cancel()
    }

    @Test
    fun saveTransaction_offline_savesPendingAndResetsKeypad() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = HomeViewModel(repo, FakeNetworkMonitor(online = false))
        val collector = backgroundScope.launch { viewModel.uiState.collect() }

        val emittedEvents = mutableListOf<HomeEvent>()
        val eventCollector = backgroundScope.launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.onDigitPressed('1')
        viewModel.onDigitPressed('8')
        viewModel.onDecimalPressed()
        viewModel.onDigitPressed('5')
        viewModel.onOpenSheet()
        viewModel.onCategorySelected("Groceries & Food")
        viewModel.onRemarkChanged("Nasi lemak")
        viewModel.onSaveTransaction()
        advanceUntilIdle()

        assertEquals(1, repo.saved.size)
        val saved = repo.saved.first()
        assertEquals(1850L, saved.amountMinor)
        assertEquals("Groceries & Food", saved.category)
        assertEquals("Nasi lemak", saved.remark)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)

        assertFalse(viewModel.uiState.value.isSheetOpen)
        assertEquals(0L, viewModel.uiState.value.amountInput.amountMinor)
        assertEquals("", viewModel.uiState.value.remark)
        assertEquals(1, viewModel.uiState.value.pendingSyncCount)

        assertEquals(
            listOf(HomeEvent.TransactionSaved("Saved offline · Waiting to sync")),
            emittedEvents
        )

        eventCollector.cancel()
        collector.cancel()
    }

    @Test
    fun saveTransaction_online_emitsTypeSavedMessage() = runTest {
        val repo = FakeTransactionRepository()
        val viewModel = HomeViewModel(repo, FakeNetworkMonitor(online = true))
        val collector = backgroundScope.launch { viewModel.uiState.collect() }

        val emittedEvents = mutableListOf<HomeEvent>()
        val eventCollector = backgroundScope.launch {
            viewModel.events.collect { emittedEvents.add(it) }
        }

        viewModel.onTypeSelected(TransactionType.INCOME)
        viewModel.onDigitPressed('5')
        viewModel.onDigitPressed('0')
        viewModel.onOpenSheet()
        viewModel.onSaveTransaction()
        advanceUntilIdle()

        assertEquals(
            listOf(HomeEvent.TransactionSaved("Income saved")),
            emittedEvents
        )
        assertEquals(1, repo.observePendingSyncCount().first())

        eventCollector.cancel()
        collector.cancel()
    }
}

private class FakeNetworkMonitor(private val online: Boolean) : NetworkMonitor {
    private val flow = MutableStateFlow(online)
    override fun isOnline(): Boolean = online
    override fun observeIsOnline(): Flow<Boolean> = flow
}

private class FakeTransactionRepository : TransactionRepository {
    val saved = mutableListOf<TransactionEntity>()
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
            remoteRevision = null
        )
        saved.add(entity)
        items.value = saved.toList()
        return entity
    }

    override fun observeAllTransactions(): Flow<List<TransactionEntity>> = items

    override fun observeTransactionsForMonth(month: YearMonth): Flow<List<TransactionEntity>> =
        items.map { list ->
            list.filter {
                !it.transactionDate.isBefore(month.atDay(1)) &&
                    !it.transactionDate.isAfter(month.atEndOfMonth())
            }
        }

    override fun observePendingSyncCount(): Flow<Int> =
        items.map { list -> list.count { it.syncStatus == SyncStatus.PENDING } }
}
