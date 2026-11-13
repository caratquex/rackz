package com.rackz.stackmyrackz.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.preferences.InMemorySyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.model.DefaultCategories
import com.rackz.stackmyrackz.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.round

data class CategorySpendItem(
    val category: String,
    val amountMinor: Long,
    val fraction: Float,
    val percentage: Int
)

enum class HistoryTypeFilter(val label: String) {
    ALL("All"),
    EXPENSE("Expense"),
    INCOME("Income")
}

sealed interface DashboardEvent {
    data class TransactionDeleted(val id: String) : DashboardEvent
    data class Message(val text: String) : DashboardEvent
}

data class DashboardUiState(
    val currentMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur")),
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val categoryBreakdown: List<CategorySpendItem> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val typeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val selectedCategoryFilter: String? = null,
    val isHistorySheetOpen: Boolean = false,
    val editingTransaction: TransactionEntity? = null,
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false,
    val expenseCategories: List<String> = DefaultCategories.expenseCategories,
    val incomeCategories: List<String> = DefaultCategories.incomeCategories
) {
    val netMinor: Long get() = incomeMinor - expenseMinor

    val savingsRatePercent: Int?
        get() = if (incomeMinor > 0L) {
            round(((incomeMinor - expenseMinor).toDouble() / incomeMinor.toDouble()) * 100.0).toInt()
        } else {
            null
        }

    val recentTransactions: List<TransactionEntity>
        get() = filteredTransactions.take(5)
}

private data class DashboardControlState(
    val month: YearMonth,
    val typeFilter: HistoryTypeFilter = HistoryTypeFilter.ALL,
    val selectedCategoryFilter: String? = null,
    val isHistorySheetOpen: Boolean = false,
    val editingTransaction: TransactionEntity? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val syncPreferences: SyncPreferencesRepository
) : ViewModel() {

    constructor(repository: TransactionRepository) : this(
        repository = repository,
        syncPreferences = InMemorySyncPreferencesRepository()
    )

    private val localZone = ZoneId.of("Asia/Kuala_Lumpur")
    private val initialMonth = YearMonth.now(localZone)

    private val _selectedMonth = MutableStateFlow(initialMonth)
    private val _controls = MutableStateFlow(DashboardControlState(month = initialMonth))

    private val _events = MutableSharedFlow<DashboardEvent>()
    val events = _events.asSharedFlow()

    val uiState: StateFlow<DashboardUiState> = combine(
        _selectedMonth.flatMapLatest { month ->
            repository.observeTransactionsForMonth(month)
        },
        _controls,
        repository.observePendingSyncCount(),
        syncPreferences.state
    ) { transactions, controls, pendingSyncCount, prefs ->
        val sorted = transactions
            .filter { !it.isDeleted }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.transactionDate }
                    .thenByDescending { it.createdAt }
            )

        val income = sorted
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountMinor }
        val expense = sorted
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountMinor }

        val breakdown = if (expense > 0L) {
            sorted
                .filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, items) -> items.sumOf { it.amountMinor } }
                .entries
                .sortedByDescending { it.value }
                .map { (category, amount) ->
                    val ratio = amount.toDouble() / expense.toDouble()
                    CategorySpendItem(
                        category = category,
                        amountMinor = amount,
                        fraction = ratio.toFloat(),
                        percentage = round(ratio * 100.0).toInt()
                    )
                }
        } else {
            emptyList()
        }

        val typeFiltered = when (controls.typeFilter) {
            HistoryTypeFilter.ALL -> sorted
            HistoryTypeFilter.EXPENSE -> sorted.filter { it.type == TransactionType.EXPENSE }
            HistoryTypeFilter.INCOME -> sorted.filter { it.type == TransactionType.INCOME }
        }

        val availableCategories = typeFiltered
            .map { it.category }
            .distinct()

        val activeCategoryFilter = controls.selectedCategoryFilter
            ?.takeIf { it in availableCategories }

        val filtered = if (activeCategoryFilter != null) {
            typeFiltered.filter { it.category == activeCategoryFilter }
        } else {
            typeFiltered
        }

        DashboardUiState(
            currentMonth = controls.month,
            incomeMinor = income,
            expenseMinor = expense,
            categoryBreakdown = breakdown,
            transactions = sorted,
            filteredTransactions = filtered,
            availableCategories = availableCategories,
            typeFilter = controls.typeFilter,
            selectedCategoryFilter = activeCategoryFilter,
            isHistorySheetOpen = controls.isHistorySheetOpen,
            editingTransaction = controls.editingTransaction,
            pendingSyncCount = pendingSyncCount,
            isSyncing = prefs.isSyncing,
            hasSyncError = !prefs.lastSyncError.isNullOrBlank(),
            expenseCategories = prefs.expenseCategories,
            incomeCategories = prefs.incomeCategories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(currentMonth = initialMonth)
    )

    fun onPreviousMonth() {
        onSelectMonth(_selectedMonth.value.minusMonths(1))
    }

    fun onNextMonth() {
        onSelectMonth(_selectedMonth.value.plusMonths(1))
    }

    fun onResetToCurrentMonth() {
        onSelectMonth(YearMonth.now(localZone))
    }

    fun onSelectMonth(month: YearMonth) {
        _selectedMonth.value = month
        _controls.update {
            it.copy(
                month = month,
                selectedCategoryFilter = null
            )
        }
    }

    fun onTypeFilterSelected(filter: HistoryTypeFilter) {
        _controls.update {
            it.copy(
                typeFilter = filter,
                selectedCategoryFilter = null
            )
        }
    }

    fun onCategoryFilterSelected(category: String?) {
        _controls.update { current ->
            val next = if (category == null || current.selectedCategoryFilter == category) {
                null
            } else {
                category
            }
            current.copy(selectedCategoryFilter = next)
        }
    }

    fun onCategoryBreakdownClicked(category: String) {
        _controls.update { current ->
            val alreadySelected =
                current.typeFilter == HistoryTypeFilter.EXPENSE &&
                    current.selectedCategoryFilter == category
            if (alreadySelected) {
                current.copy(
                    typeFilter = HistoryTypeFilter.ALL,
                    selectedCategoryFilter = null
                )
            } else {
                current.copy(
                    typeFilter = HistoryTypeFilter.EXPENSE,
                    selectedCategoryFilter = category
                )
            }
        }
    }

    fun onOpenHistorySheet() {
        _controls.update { it.copy(isHistorySheetOpen = true) }
    }

    fun onDismissHistorySheet() {
        _controls.update { it.copy(isHistorySheetOpen = false) }
    }

    fun onTransactionClicked(transaction: TransactionEntity) {
        _controls.update { it.copy(editingTransaction = transaction) }
    }

    fun onDismissEditSheet() {
        _controls.update { it.copy(editingTransaction = null) }
    }

    fun onUpdateTransaction(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        category: String,
        remark: String,
        date: LocalDate
    ) {
        if (amountMinor <= 0L) return
        viewModelScope.launch {
            repository.updateTransaction(
                id = id,
                type = type,
                amountMinor = amountMinor,
                category = category,
                remark = remark,
                transactionDate = date
            )
            _controls.update { it.copy(editingTransaction = null) }
            _events.emit(DashboardEvent.Message("Transaction updated"))
        }
    }

    fun onDeleteTransaction(id: String) {
        viewModelScope.launch {
            repository.softDeleteTransaction(id)
            _controls.update { current ->
                if (current.editingTransaction?.id == id) {
                    current.copy(editingTransaction = null)
                } else {
                    current
                }
            }
            _events.emit(DashboardEvent.TransactionDeleted(id))
        }
    }

    fun onUndoDelete(id: String) {
        viewModelScope.launch {
            repository.restoreTransaction(id)
        }
    }
}
