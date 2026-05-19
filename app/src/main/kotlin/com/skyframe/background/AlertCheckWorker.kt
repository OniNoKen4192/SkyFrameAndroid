package com.skyframe.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background poll worker registered by AlertCheckScheduler. A thin
 * shell over AlertPoller (which holds all the testable logic). Maps the
 * poll Outcome to a WorkManager Result and chains an EscalationWorker when a
 * top-tier alert is active.
 */
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val poller: AlertPoller,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (val outcome = poller.poll()) {
        AlertPoller.Outcome.Skipped -> Result.success()
        AlertPoller.Outcome.Retry -> Result.retry()
        is AlertPoller.Outcome.Polled -> {
            if (outcome.hasTopTier) {
                EscalationWorker.enqueueNext(applicationContext)
            }
            Result.success()
        }
    }
}
