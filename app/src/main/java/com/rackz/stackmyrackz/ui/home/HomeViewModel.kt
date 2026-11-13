package com.rackz.stackmyrackz.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackz.stackmyrackz.data.preferences.InMemorySyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.repository.NetworkMonitor
import com.rackz.stackmyrackz.data.repository.TransactionRepository
import com.rackz.stackmyrackz.domain.AmountInputEngine
import com.rackz.stackmyrackz.domain.AmountInputState
import com.rackz.stackmyrackz.domain.CalcOperator
import com.rackz.stackmyrackz.model.DefaultCategories
import com.rackz.stackmyrackz.model.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val transactionType: TransactionType = TransactionType.EXPENSE,
    val amountInput: AmountInputState = AmountInputState(),
    val isSheetOpen: Boolean = false,
    val selectedCategory: String? = null,
    val remark: String = "",
    val selectedDate: LocalDate = LocalDate.now(ZoneId.of("Asia/Kuala_Lumpur")),
    val isDatePickerOpen: Boolean = false,
    val isSaving: Boolean = false,
    val pendingSyncCount: Int = 0,
    val expenseCategories: List<String> = DefaultCategories.expenseCategories,
    val incomeCategories: List<String> = DefaultCategories.incomeCategories
) {
    val availableCategories: List<String>
        get() = when (transactionType) {
            TransactionType.EXPENSE -> expenseCategories.ifEmpty { DefaultCategories.expenseCategories }
            TransactionType.INCOME -> incomeCategories.ifEmpty { DefaultCategories.incomeCategories }
        }

    val canSave: Boolean
        get() = amountInput.canContinue && selectedCategory != null && !isSaving
}

sealed interface HomeEvent {
    data class TransactionSaved(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val networkMonitor: NetworkMonitor,
    private val syncPreferences: SyncPreferencesRepository
) : ViewModel() {

    constructor(
        repository: TransactionRepository,
        networkMonitor: NetworkMonitor
    ) : this(repository, networkMonitor, InMemorySyncPreferencesRepository())

    private val localZone = ZoneId.of("Asia/Kuala_Lumpur")
    private val _state = MutableStateFlow(
        HomeUiState(
            selectedCategory = DefaultCategories.forType(TransactionType.EXPENSE).first(),
            selectedDate = LocalDate.now(localZone)
        )
    )

    val uiState: StateFlow<HomeUiState> = combine(
        _state,
        repository.observePendingSyncCount(),
        syncPreferences.state
    ) { state, pendingCount, prefs ->
        state.copy(
            pendingSyncCount = pendingCount,
            expenseCategories = prefs.expenseCategories,
            incomeCategories = prefs.incomeCategories
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = _state.value
    )

    private val _events = MutableSharedFlow<HomeEvent>()
    val events = _events.asSharedFlow()

    fun onTypeSelected(type: TransactionType) {
        _state.update { current ->
            if (current.transactionType == type) {
                current
            } else {
                val categories = when (type) {
                    TransactionType.EXPENSE -> uiState.value.expenseCategories
                    TransactionType.INCOME -> uiState.value.incomeCategories
                }
                current.copy(
                    transactionType = type,
                    selectedCategory = categories.firstOrNull()
                        ?: DefaultCategories.forType(type).firstOrNull()
                )
            }
        }
    }

    fun onDigitPressed(digit: Char) {
        _state.update { it.copy(amountInput = AmountInputEngine.onDigit(it.amountInput, digit)) }
    }

    fun onDecimalPressed() {
        _state.update { it.copy(amountInput = AmountInputEngine.onDecimalPoint(it.amountInput)) }
    }

    fun onBackspacePressed() {
        _state.update { it.copy(amountInput = AmountInputEngine.onBackspace(it.amountInput)) }
    }

    fun onClearPressed() {
        _state.update {
            it.copy(
                amountInput = AmountInputEngine.reset(
                    keepCalculatorMode = true,
                    currentMode = it.amountInput.isCalculatorMode
                )
            )
        }
    }

    fun onOperatorPressed(operator: CalcOperator) {
        _state.update {
            it.copy(amountInput = AmountInputEngine.onOperator(it.amountInput, operator))
        }
    }

    fun onEqualsPressed() {
        _state.update { it.copy(amountInput = AmountInputEngine.onEquals(it.amountInput)) }
    }

    fun onToggleCalculatorMode() {
        _state.update {
            it.copy(amountInput = AmountInputEngine.onToggleCalculatorMode(it.amountInput))
        }
    }

    fun onOpenSheet() {
        _state.update { current ->
            if (!current.amountInput.canContinue) return@update current
            val evaluated = if (current.amountInput.expressionPreview != null) {
                AmountInputEngine.onEquals(current.amountInput)
            } else {
                current.amountInput
            }
            if (!evaluated.canContinue) {
                current.copy(amountInput = evaluated)
            } else {
                current.copy(
                    amountInput = evaluated,
                    isSheetOpen = true,
                    selectedCategory = current.selectedCategory
                        ?: DefaultCategories.forType(current.transactionType).firstOrNull()
                )
            }
        }
    }

    fun onDismissSheet() {
        _state.update { it.copy(isSheetOpen = false, isDatePickerOpen = false) }
    }

    fun onCategorySelected(category: String) {
        _state.update { it.copy(selectedCategory = category) }
    }

    fun onRemarkChanged(remark: String) {
        _state.update { it.copy(remark = remark) }
    }

    fun onOpenDatePicker() {
        _state.update { it.copy(isDatePickerOpen = true) }
    }

    fun onDismissDatePicker() {
        _state.update { it.copy(isDatePickerOpen = false) }
    }

    fun onDateSelected(date: LocalDate) {
        _state.update { it.copy(selectedDate = date, isDatePickerOpen = false) }
    }

    fun onSaveTransaction() {
        val snapshot = _state.value
        val category = snapshot.selectedCategory ?: return
        if (!snapshot.amountInput.canContinue || snapshot.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                repository.saveTransaction(
                    type = snapshot.transactionType,
                    amountMinor = snapshot.amountInput.amountMinor,
                    category = category,
                    remark = snapshot.remark,
                    transactionDate = snapshot.selectedDate
                )

                val message = if (networkMonitor.isOnline()) {
                    "${snapshot.transactionType.label} saved"
                } else {
                    "Saved offline · Waiting to sync"
                }

                _state.update { current ->
                    current.copy(
                        amountInput = AmountInputEngine.reset(
                            keepCalculatorMode = true,
                            currentMode = current.amountInput.isCalculatorMode
                        ),
                        isSheetOpen = false,
                        isDatePickerOpen = false,
                        remark = "",
                        selectedDate = LocalDate.now(localZone),
                        isSaving = false
                    )
                }
                _events.emit(HomeEvent.TransactionSaved(message))
            } catch (_: Throwable) {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
