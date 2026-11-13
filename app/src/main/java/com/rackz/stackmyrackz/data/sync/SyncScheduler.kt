package com.rackz.stackmyrackz.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules background Google Sheets synchronization when network connectivity is available. */
interface SyncScheduler {
    fun enqueueImmediateSync()
    fun schedulePeriodicSync()
}

/** No-op scheduler for unit tests and Compose previews. */
object NoOpSyncScheduler : SyncScheduler {
    override fun enqueueImmediateSync() = Unit
    override fun schedulePeriodicSync() = Unit
}

/** WorkManager-backed implementation of [SyncScheduler]. */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : SyncScheduler {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30,
                TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SheetsSyncWorker>(
            6,
            TimeUnit.HOURS
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val IMMEDIATE_SYNC_WORK_NAME = "racks_sheets_immediate_sync"
        const val PERIODIC_SYNC_WORK_NAME = "racks_sheets_periodic_sync"
    }
}
