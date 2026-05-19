package com.skyframe.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * One-shot ExpeditedWorkRequest chained from AlertCheckWorker (or itself)
 * when a top-tier active alert (rank 1-4) is present.
 *
 * Re-runs the same AlertPoller logic, then chains another EscalationWorker
 * (2-min delay) while top-tier remains active. Stops when top-tier clears -
 * the next 15-min periodic resumes baseline cadence.
 *
 * OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST so quota exhaustion
 * degrades gracefully to a normal queued request instead of losing work.
 */
@HiltWorker
class EscalationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val poller: AlertPoller,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (val outcome = poller.poll()) {
        AlertPoller.Outcome.Skipped -> Result.success()
        AlertPoller.Outcome.Retry -> Result.retry()
        is AlertPoller.Outcome.Polled -> {
            if (outcome.hasTopTier) {
                enqueueNext(applicationContext)
            }
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "alert_check_escalation"

        fun enqueueNext(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
