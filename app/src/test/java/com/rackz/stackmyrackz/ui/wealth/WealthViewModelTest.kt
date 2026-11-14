package com.rackz.stackmyrackz.ui.wealth

import com.rackz.stackmyrackz.data.local.WealthEntryEntity
import com.rackz.stackmyrackz.data.preferences.InMemorySyncPreferencesRepository
import com.rackz.stackmyrackz.data.repository.WealthRepository
import com.rackz.stackmyrackz.data.sync.WealthReminderScheduler
import com.rackz.stackmyrackz.data.sync.computeNextReminderDelayMillis
import com.rackz.stackmyrackz.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
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
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class WealthViewModelTest {

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
    fun carryForwardTargets_applyToFutureUnrecordedMonthWithoutOverwritingPast() = runTest {
        val prefs = InMemorySyncPreferencesRepository()
        val repo = FakeWealthRepository(prefs)
        val viewModel = WealthViewModel(repo, prefs, RecordingReminderScheduler())
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        // Edit previous month with custom targets
        viewModel.onPreviousMonth()
        advanceUntilIdle()
        viewModel.onSaveMonthlyWealth(
            savingsSavedMinor = 120_000L,
            savingsTargetMinor = 150_000L,
            investmentSavedMinor = 60_000L,
            investmentTargetMinor = 80_000L,
            note = "Bonus allocation"
        )
        advanceUntilIdle()

        // Current month (no explicit entry yet) should carry forward prior month's targets
        viewModel.onResetToCurrentMonth()
        advanceUntilIdle()
        val currentState = viewModel.uiState.value
        assertEquals(150_000L, currentState.savingsTargetMinor)
        assertEquals(80_000L, currentState.investmentTargetMinor)
        assertEquals(0L, currentState.savingsSavedMinor)

        // Update current month with new targets; next month carries forward the new ones
        viewModel.onSaveMonthlyWealth(
            savingsSavedMinor = 100_000L,
            savingsTargetMinor = 200_000L,
            investmentSavedMinor = 100_000L,
            investmentTargetMinor = 100_000L,
            note = null
        )
        advanceUntilIdle()

        viewModel.onNextMonth()
        advanceUntilIdle()
        assertEquals(200_000L, viewModel.uiState.value.savingsTargetMinor)
        assertEquals(100_000L, viewModel.uiState.value.investmentTargetMinor)

        // Past month retains its own recorded targets
        viewModel.onPreviousMonth()
        viewModel.onPreviousMonth()
        advanceUntilIdle()
        assertEquals(150_000L, viewModel.uiState.value.savingsTargetMinor)
        assertEquals(80_000L, viewModel.uiState.value.investmentTargetMinor)

        collector.cancel()
    }

    @Test
    fun cumulativeBalances_sumContributionsAcrossMonths() = runTest {
        val prefs = InMemorySyncPreferencesRepository()
        val repo = FakeWealthRepository(prefs)
        val m2 = currentMonth.minusMonths(2)
        val m1 = currentMonth.minusMonths(1)

        repo.saveMonthlyWealth(m2, 100_000L, 100_000L, 50_000L, 50_000L, null)
        repo.saveMonthlyWealth(m1, 80_000L, 100_000L, 40_000L, 50_000L, null)
        repo.saveMonthlyWealth(currentMonth, 50_000L, 100_000L, 60_000L, 50_000L, null)

        val viewModel = WealthViewModel(repo, prefs, RecordingReminderScheduler())
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(230_000L, state.cumulativeSavingsMinor)
        assertEquals(150_000L, state.cumulativeInvestmentMinor)
        assertEquals(380_000L, state.cumulativeTotalMinor)
        assertEquals(50, state.savingsPercent)
        assertEquals(120, state.investmentPercent)
        assertEquals(73, state.combinedGoalPercent)
        collector.cancel()
    }

    @Test
    fun chartRangeFilter_slicesTimelineToSixMonthsOneYearOrAll() = runTest {
        val prefs = InMemorySyncPreferencesRepository()
        val repo = FakeWealthRepository(prefs)
        for (i in 0..14) {
            repo.saveMonthlyWealth(
                month = currentMonth.minusMonths(i.toLong()),
                savingsSavedMinor = 10_000L,
                savingsTargetMinor = 20_000L,
                investmentSavedMinor = 5_000L,
                investmentTargetMinor = 10_000L,
                note = null
            )
        }

        val viewModel = WealthViewModel(repo, prefs, RecordingReminderScheduler())
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        assertEquals(6, viewModel.uiState.value.chartSnapshots.size)

        viewModel.onChartRangeSelected(WealthChartRange.ONE_YEAR)
        advanceUntilIdle()
        assertEquals(12, viewModel.uiState.value.chartSnapshots.size)

        viewModel.onChartRangeSelected(WealthChartRange.ALL)
        advanceUntilIdle()
        assertEquals(15, viewModel.uiState.value.chartSnapshots.size)

        collector.cancel()
    }

    @Test
    fun reminderConfig_persistsAndInvokesScheduler() = runTest {
        val prefs = InMemorySyncPreferencesRepository()
        val repo = FakeWealthRepository(prefs)
        val scheduler = RecordingReminderScheduler()
        val viewModel = WealthViewModel(repo, prefs, scheduler)
        val collector = backgroundScope.launch { viewModel.uiState.collect() }
        advanceUntilIdle()

        viewModel.onSaveReminderConfig(
            enabled = true,
            frequency = "WEEKLY",
            dayOfMonth = 15,
            dayOfWeek = 5,
            hour = 9,
            minute = 30
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.reminderEnabled)
        assertEquals("WEEKLY", state.reminderFrequency)
        assertEquals(5, state.reminderDayOfWeek)
        assertEquals(9, state.reminderHour)
        assertEquals(30, state.reminderMinute)
        assertEquals(true, scheduler.lastEnabled)
        assertEquals("WEEKLY", scheduler.lastFrequency)

        viewModel.onSaveReminderConfig(
            enabled = false,
            frequency = "MONTHLY",
            dayOfMonth = 1,
            dayOfWeek = 1,
            hour = 20,
            minute = 0
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.reminderEnabled)
        assertEquals(false, scheduler.lastEnabled)
        collector.cancel()
    }

    @Test
    fun computeNextReminderDelayMillis_calculatesPositiveDurationForMonthlyAndWeekly() {
        val now = ZonedDateTime.of(2026, 11, 14, 21, 30, 0, 0, ZoneId.of("Asia/Kuala_Lumpur"))
        val monthlyDelay = computeNextReminderDelayMillis(
            now = now,
            frequency = "MONTHLY",
            dayOfMonth = 1,
            dayOfWeek = 1,
            hour = 20,
            minute = 0
        )
        // Nov 14 21:30 -> Dec 1 20:00 (~16d 22.5h)
        assertTrue(monthlyDelay > 16L * 24 * 3600 * 1000)

        val weeklyDelay = computeNextReminderDelayMillis(
            now = now, // Saturday (DayOfWeek 6)
            frequency = "WEEKLY",
            dayOfMonth = 1,
            dayOfWeek = 7, // Sunday 09:00
            hour = 9,
            minute = 0
        )
        // Saturday 21:30 -> Sunday 09:00 = 11.5 hours
        assertEquals(11L * 3600_000L + 30L * 60_000L, weeklyDelay)
    }
}

private class FakeWealthRepository(
    private val prefs: InMemorySyncPreferencesRepository
) : WealthRepository {
    private val entries = MutableStateFlow<List<WealthEntryEntity>>(emptyList())

    override fun observeAllEntries(): Flow<List<WealthEntryEntity>> = entries

    override suspend fun saveMonthlyWealth(
        month: YearMonth,
        savingsSavedMinor: Long,
        savingsTargetMinor: Long,
        investmentSavedMinor: Long,
        investmentTargetMinor: Long,
        note: String?
    ) {
        val key = month.toString()
        val updated = WealthEntryEntity(
            yearMonth = key,
            savingsSavedMinor = savingsSavedMinor,
            savingsTargetMinor = savingsTargetMinor,
            investmentSavedMinor = investmentSavedMinor,
            investmentTargetMinor = investmentTargetMinor,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = Instant.now(),
            syncStatus = SyncStatus.PENDING
        )
        entries.update { current ->
            (current.filterNot { it.yearMonth == key } + updated).sortedBy { it.yearMonth }
        }
        prefs.setDefaultWealthTargets(savingsTargetMinor, investmentTargetMinor)
    }
}

private class RecordingReminderScheduler : WealthReminderScheduler {
    var lastEnabled: Boolean? = null
    var lastFrequency: String? = null

    override fun scheduleOrCancel(
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) {
        lastEnabled = enabled
        lastFrequency = frequency
    }
}
