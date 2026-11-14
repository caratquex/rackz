package com.rackz.stackmyrackz.ui.wealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackz.stackmyrackz.data.local.WealthEntryEntity
import com.rackz.stackmyrackz.data.preferences.InMemorySyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesRepository
import com.rackz.stackmyrackz.data.preferences.SyncPreferencesState
import com.rackz.stackmyrackz.data.repository.WealthRepository
import com.rackz.stackmyrackz.data.sync.NoOpWealthReminderScheduler
import com.rackz.stackmyrackz.data.sync.WealthReminderScheduler
import com.rackz.stackmyrackz.model.SyncStatus
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.round

enum class WealthChartRange(val label: String, val monthLimit: Int?) {
    SIX_MONTHS("6M", 6),
    ONE_YEAR("1Y", 12),
    ALL("All", null)
}

data class WealthMonthlySnapshot(
    val month: YearMonth,
    val savingsSavedMinor: Long,
    val savingsTargetMinor: Long,
    val investmentSavedMinor: Long,
    val investmentTargetMinor: Long,
    val cumulativeSavingsMinor: Long,
    val cumulativeInvestmentMinor: Long,
    val note: String?,
    val hasRecordedEntry: Boolean
) {
    val totalMonthlySavedMinor: Long get() = savingsSavedMinor + investmentSavedMinor
    val cumulativeTotalMinor: Long get() = cumulativeSavingsMinor + cumulativeInvestmentMinor
}

sealed interface WealthEvent {
    data class Message(val text: String) : WealthEvent
}

data class WealthUiState(
    val currentMonth: YearMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur")),
    val savingsSavedMinor: Long = 0L,
    val savingsTargetMinor: Long = 100_000L,
    val investmentSavedMinor: Long = 0L,
    val investmentTargetMinor: Long = 50_000L,
    val note: String = "",
    val hasCurrentMonthEntry: Boolean = false,
    val cumulativeSavingsMinor: Long = 0L,
    val cumulativeInvestmentMinor: Long = 0L,
    val chartRange: WealthChartRange = WealthChartRange.SIX_MONTHS,
    val chartSnapshots: List<WealthMonthlySnapshot> = emptyList(),
    val snapshotRows: List<WealthMonthlySnapshot> = emptyList(),
    val isEditSheetOpen: Boolean = false,
    val isReminderSheetOpen: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderFrequency: String = "MONTHLY",
    val reminderDayOfMonth: Int = 1,
    val reminderDayOfWeek: Int = 1,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val pendingSyncCount: Int = 0,
    val isSyncing: Boolean = false,
    val hasSyncError: Boolean = false
) {
    val cumulativeTotalMinor: Long get() = cumulativeSavingsMinor + cumulativeInvestmentMinor

    val savingsProgress: Float
        get() = if (savingsTargetMinor > 0L) {
            (savingsSavedMinor.toFloat() / savingsTargetMinor.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val savingsPercent: Int
        get() = if (savingsTargetMinor > 0L) {
            round((savingsSavedMinor.toDouble() / savingsTargetMinor.toDouble()) * 100.0).toInt()
        } else {
            0
        }

    val investmentProgress: Float
        get() = if (investmentTargetMinor > 0L) {
            (investmentSavedMinor.toFloat() / investmentTargetMinor.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val investmentPercent: Int
        get() = if (investmentTargetMinor > 0L) {
            round((investmentSavedMinor.toDouble() / investmentTargetMinor.toDouble()) * 100.0).toInt()
        } else {
            0
        }

    val combinedGoalPercent: Int?
        get() {
            val totalTarget = savingsTargetMinor + investmentTargetMinor
            if (totalTarget <= 0L) return null
            val totalSaved = savingsSavedMinor + investmentSavedMinor
            return round((totalSaved.toDouble() / totalTarget.toDouble()) * 100.0).toInt()
        }
}

private data class WealthControlState(
    val month: YearMonth = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur")),
    val chartRange: WealthChartRange = WealthChartRange.SIX_MONTHS,
    val isEditSheetOpen: Boolean = false,
    val isReminderSheetOpen: Boolean = false
)

@HiltViewModel
class WealthViewModel @Inject constructor(
    private val repository: WealthRepository,
    private val syncPreferences: SyncPreferencesRepository = InMemorySyncPreferencesRepository(),
    private val reminderScheduler: WealthReminderScheduler = NoOpWealthReminderScheduler
) : ViewModel() {

    private val controls = MutableStateFlow(WealthControlState())
    private val _events = MutableSharedFlow<WealthEvent>()
    val events = _events.asSharedFlow()

    private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

    val uiState: StateFlow<WealthUiState> = combine(
        controls,
        repository.observeAllEntries(),
        syncPreferences.state
    ) { ctrl, entries, prefs ->
        buildUiState(ctrl = ctrl, entries = entries, prefs = prefs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WealthUiState()
    )

    fun onPreviousMonth() {
        controls.update { it.copy(month = it.month.minusMonths(1)) }
    }

    fun onNextMonth() {
        controls.update { it.copy(month = it.month.plusMonths(1)) }
    }

    fun onResetToCurrentMonth() {
        controls.update {
            it.copy(month = YearMonth.now(ZoneId.of("Asia/Kuala_Lumpur")))
        }
    }

    fun onSelectMonth(month: YearMonth) {
        controls.update { it.copy(month = month) }
    }

    fun onChartRangeSelected(range: WealthChartRange) {
        controls.update { it.copy(chartRange = range) }
    }

    fun onOpenEditSheet(month: YearMonth? = null) {
        controls.update { current ->
            current.copy(
                month = month ?: current.month,
                isEditSheetOpen = true
            )
        }
    }

    fun onDismissEditSheet() {
        controls.update { it.copy(isEditSheetOpen = false) }
    }

    fun onOpenReminderSheet() {
        controls.update { it.copy(isReminderSheetOpen = true) }
    }

    fun onDismissReminderSheet() {
        controls.update { it.copy(isReminderSheetOpen = false) }
    }

    fun onSaveMonthlyWealth(
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    ) {
        val targetMonth = controls.value.month
        viewModelScope.launch {
            repository.saveMonthlyWealth(
                month = targetMonth,
                savingsSavedMinor = savingsSavedMinor,
                savingsTargetMinor = savingsTargetMinor,
                investmentSavedMinor = investmentSavedMinor,
                investmentTargetMinor = investmentTargetMinor,
                note = note
            )
            controls.update { it.copy(isEditSheetOpen = false) }
            _events.emit(WealthEvent.Message("Saved ${targetMonth.format(monthFormatter)}"))
        }
    }

    fun onSaveReminderConfig(
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) {
        val cleanFreq = if (frequency == "WEEKLY") "WEEKLY" else "MONTHLY"
        val safeDom = dayOfMonth.coerceIn(1, 28)
        val safeDow = dayOfWeek.coerceIn(1, 7)
        val safeHour = hour.coerceIn(0, 23)
        val safeMinute = minute.coerceIn(0, 59)

        viewModelScope.launch {
            syncPreferences.updateWealthReminderConfig(
                enabled = enabled,
                frequency = cleanFreq,
                dayOfMonth = safeDom,
                dayOfWeek = safeDow,
                hour = safeHour,
                minute = safeMinute
            )
            reminderScheduler.scheduleOrCancel(
                enabled = enabled,
                frequency = cleanFreq,
                dayOfMonth = safeDom,
                dayOfWeek = safeDow,
                hour = safeHour,
                minute = safeMinute
            )
            controls.update { it.copy(isReminderSheetOpen = false) }
            val text = if (enabled) {
                "Wealth reminder scheduled"
            } else {
                "Wealth reminder turned off"
            }
            _events.emit(WealthEvent.Message(text))
        }
    }

    private fun buildUiState(
        ctrl: WealthControlState,
        entries: List<WealthEntryEntity>,
        prefs: SyncPreferencesState
    ): WealthUiState {
        val parsedEntries = entries.mapNotNull { entry ->
            runCatching { YearMonth.parse(entry.yearMonth) }.getOrNull()?.let { ym -> ym to entry }
        }.toMap()

        val sortedRecordedMonths = parsedEntries.keys.sorted()
        val earliestTimelineMonth = minOf(
            sortedRecordedMonths.firstOrNull() ?: ctrl.month.minusMonths(5),
            ctrl.month.minusMonths(5)
        )
        val latestTimelineMonth = maxOf(
            sortedRecordedMonths.lastOrNull() ?: ctrl.month,
            ctrl.month
        )

        var runningCumulativeSavings = 0L
        var runningCumulativeInvestment = 0L
        var lastKnownSavingsTarget = prefs.defaultSavingsTargetMinor
        var lastKnownInvestmentTarget = prefs.defaultInvestmentTargetMinor

        val timeline = mutableListOf<WealthMonthlySnapshot>()
        var cursor = earliestTimelineMonth
        while (!cursor.isAfter(latestTimelineMonth)) {
            val entry = parsedEntries[cursor]
            val savingsSaved = entry?.savingsSavedMinor ?: 0L
            val investmentSaved = entry?.investmentSavedMinor ?: 0L
            val savingsTarget = entry?.savingsTargetMinor ?: lastKnownSavingsTarget
            val investmentTarget = entry?.investmentTargetMinor ?: lastKnownInvestmentTarget

            if (entry != null) {
                lastKnownSavingsTarget = entry.savingsTargetMinor
                lastKnownInvestmentTarget = entry.investmentTargetMinor
            }

            runningCumulativeSavings += savingsSaved
            runningCumulativeInvestment += investmentSaved

            timeline.add(
                WealthMonthlySnapshot(
                    month = cursor,
                    savingsSavedMinor = savingsSaved,
                    savingsTargetMinor = savingsTarget,
                    investmentSavedMinor = investmentSaved,
                    investmentTargetMinor = investmentTarget,
                    cumulativeSavingsMinor = runningCumulativeSavings,
                    cumulativeInvestmentMinor = runningCumulativeInvestment,
                    note = entry?.note,
                    hasRecordedEntry = entry != null
                )
            )
            cursor = cursor.plusMonths(1)
        }

        val currentSnapshot = timeline.firstOrNull { it.month == ctrl.month }
        val chartBase = timeline.filter { !it.month.isAfter(latestTimelineMonth) }
        val chartSnapshots = when (val limit = ctrl.chartRange.monthLimit) {
            null -> chartBase
            else -> chartBase.takeLast(limit)
        }

        val firstMeaningfulMonth = sortedRecordedMonths.firstOrNull() ?: ctrl.month
        val snapshotRows = timeline
            .filter { !it.month.isBefore(firstMeaningfulMonth) }
            .sortedByDescending { it.month }

        val pendingWealthCount = entries.count { it.syncStatus != SyncStatus.SYNCED }

        return WealthUiState(
            currentMonth = ctrl.month,
            savingsSavedMinor = currentSnapshot?.savingsSavedMinor ?: 0L,
            savingsTargetMinor = currentSnapshot?.savingsTargetMinor ?: prefs.defaultSavingsTargetMinor,
            investmentSavedMinor = currentSnapshot?.investmentSavedMinor ?: 0L,
            investmentTargetMinor = currentSnapshot?.investmentTargetMinor ?: prefs.defaultInvestmentTargetMinor,
            note = currentSnapshot?.note.orEmpty(),
            hasCurrentMonthEntry = currentSnapshot?.hasRecordedEntry ?: false,
            cumulativeSavingsMinor = currentSnapshot?.cumulativeSavingsMinor ?: 0L,
            cumulativeInvestmentMinor = currentSnapshot?.cumulativeInvestmentMinor ?: 0L,
            chartRange = ctrl.chartRange,
            chartSnapshots = chartSnapshots,
            snapshotRows = snapshotRows,
            isEditSheetOpen = ctrl.isEditSheetOpen,
            isReminderSheetOpen = ctrl.isReminderSheetOpen,
            reminderEnabled = prefs.wealthReminderEnabled,
            reminderFrequency = prefs.wealthReminderFrequency,
            reminderDayOfMonth = prefs.wealthReminderDayOfMonth,
            reminderDayOfWeek = prefs.wealthReminderDayOfWeek,
            reminderHour = prefs.wealthReminderHour,
            reminderMinute = prefs.wealthReminderMinute,
            pendingSyncCount = pendingWealthCount,
            isSyncing = prefs.isSyncing,
            hasSyncError = prefs.lastSyncError != null
        )
    }
}
