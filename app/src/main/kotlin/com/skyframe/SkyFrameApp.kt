package com.skyframe

import android.app.Application
import androidx.work.Configuration
import com.skyframe.background.SkyFrameWorkerFactory
import com.skyframe.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkyFrameApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactoryHolder: SkyFrameWorkerFactory

    // WorkManager's on-demand initializer reads this; required for @HiltWorker
    // resolution. AlertCheckScheduler.schedulePeriodic() (Phase D) enqueues
    // the actual periodic work.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactoryHolder.hiltFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        // AlertCheckScheduler.schedulePeriodic(this) wires in Phase D
    }
}
