package com.rackz.stackmyrackz.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Background WorkManager worker executing [SheetsSyncEngine.syncAll]. */
@HiltWorker
class SheetsSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncEngine: SheetsSyncEngine
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return when (val outcome = syncEngine.syncAll()) {
            is SyncOutcome.Success -> Result.success()
            is SyncOutcome.NotConfigured,
            is SyncOutcome.ConsentRequired -> Result.success()
            is SyncOutcome.Error -> if (outcome.retryable) Result.retry() else Result.failure()
        }
    }
}
