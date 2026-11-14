package com.rackz.stackmyrackz.data.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules or cancels configurable local check-in notifications for Wealth goals. */
interface WealthReminderScheduler {
    fun scheduleOrCancel(
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    )
}

/** No-op reminder scheduler for unit tests and Compose previews. */
object NoOpWealthReminderScheduler : WealthReminderScheduler {
    override fun scheduleOrCancel(
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) = Unit
}

/** Calculates milliseconds from [now] until the next configured Monthly or Weekly reminder time. */
fun computeNextReminderDelayMillis(
    now: ZonedDateTime,
    frequency: String,
    dayOfMonth: Int,
    dayOfWeek: Int,
    hour: Int,
    minute: Int
): Long {
    val targetTime = LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    val nextTrigger = if (frequency == "WEEKLY") {
        val targetDow = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        val sameOrNext = now
            .with(TemporalAdjusters.nextOrSame(targetDow))
            .with(targetTime)
            .withSecond(0)
            .withNano(0)
        if (sameOrNext.isAfter(now)) {
            sameOrNext
        } else {
            sameOrNext.plusWeeks(1)
        }
    } else {
        val safeDay = dayOfMonth.coerceIn(1, 28)
        val thisMonth = now
            .withDayOfMonth(safeDay)
            .with(targetTime)
            .withSecond(0)
            .withNano(0)
        if (thisMonth.isAfter(now)) {
            thisMonth
        } else {
            thisMonth.plusMonths(1).withDayOfMonth(safeDay)
        }
    }

    return Duration.between(now, nextTrigger).toMillis().coerceAtLeast(1_000L)
}

/** WorkManager-backed implementation of [WealthReminderScheduler]. */
@Singleton
class WorkManagerWealthReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : WealthReminderScheduler {

    override fun scheduleOrCancel(
        enabled: Boolean,
        frequency: String,
        dayOfMonth: Int,
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val delayMillis = computeNextReminderDelayMillis(
            now = ZonedDateTime.now(ZoneId.systemDefault()),
            frequency = frequency,
            dayOfMonth = dayOfMonth,
            dayOfWeek = dayOfWeek,
            hour = hour,
            minute = minute
        )

        val request = OneTimeWorkRequestBuilder<WealthReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val WORK_NAME = "racks_wealth_reminder"
    }
}
