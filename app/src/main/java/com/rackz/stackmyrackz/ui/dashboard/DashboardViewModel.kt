package com.rackz.stackmyrackz.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackz.stackmyrackz.data.local.TransactionEntity
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class DashboardUiState(
    val currentMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur")),
    val incomeMinor: Long = 0L,
    val expenseMinor: Long = 0L,
    val transactions: List<TransactionEntity> = emptyList()
) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: TransactionRepository
) : ViewModel() {

    private val currentMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur"))

    val uiState: StateFlow<DashboardUiState> = repository.observeTransactionsForMonth(currentMonth)
        .map { list ->
            val income = list.filter { it.type == TransactionType.INCOME }.sumOf { it.amountMinor }
            val expense = list.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountMinor }
            DashboardUiState(
                currentMonth = currentMonth,
                incomeMinor = income,
                expenseMinor = expense,
                transactions = list
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(currentMonth = currentMonth)
        )
}
