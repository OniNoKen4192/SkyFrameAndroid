package com.skyframe.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when the user taps DISMISS on a SkyFrame notification. Marks the
 * alert as acknowledged in AlertAcknowledgmentRepository (prevents
 * re-notification on subsequent polls) and clears the system notification.
 *
 * Bidirectional sync: in-app dismissal via DashboardViewModel.dismissAlert
 * does the equivalent in reverse (see Phase H).
 */
@AndroidEntryPoint
class DismissReceiver : BroadcastReceiver() {

    @Inject lateinit var acknowledgments: AlertAcknowledgmentRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra(NotificationExtras.ALERT_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationExtras.NOTIFICATION_ID, -1)

        scope.launch {
            acknowledgments.dismiss(alertId)
        }
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
