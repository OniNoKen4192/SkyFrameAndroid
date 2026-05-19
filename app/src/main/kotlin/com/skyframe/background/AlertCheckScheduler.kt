package com.skyframe.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registers AlertCheckWorker as a PeriodicWorkRequest. Called from
 * SkyFrameApp.onCreate; KEEP policy ensures re-calls don't replace
 * the in-flight schedule.
 *
 * 15 minutes is Android's minimum periodic interval - lower cadences
 * require ExpeditedWorkRequest (see EscalationWorker).
 *
 * Constraints:
 *   - NetworkType.CONNECTED (no point polling NWS offline)
 *   - setRequiresBatteryNotLow(false): severe weather doesn't pause for
 *     low battery. Explicit because the default is also false but
 *     reading the code shouldn't require remembering defaults.
 */
object AlertCheckScheduler {

    const val UNIQUE_WORK_NAME = "alert_check_periodic"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
