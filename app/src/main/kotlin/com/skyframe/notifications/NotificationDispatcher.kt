package com.skyframe.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.skyframe.MainActivity
import com.skyframe.R
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertTier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds + posts a single Alert as a system notification, routed to the
 * channel implied by alert.tier.rank. Tap PendingIntent launches MainActivity
 * with EXTRA_ALERT_ID; DISMISS action PendingIntent broadcasts to
 * DismissReceiver.
 *
 * No unit test - thin wrapper around NotificationCompat.Builder +
 * NotificationManagerCompat. Manual smoke test verifies tier->channel
 * routing, tap behavior, and DISMISS action.
 *
 * Life-safety notifications (ranks 1-4) set setFullScreenIntent targeting
 * FullScreenAlertActivity - that part wires in Phase G.
 */
@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notify(alert: Alert) {
        val channelId = channelFor(alert.tier)
        val notificationId = NotificationIds.forAlertId(alert.id)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)  // TODO Phase J: dedicated monochrome icon
            .setColor(alert.tier.baseColor.toInt())
            .setContentTitle("⚠ ${alert.event.uppercase()}")
            .setContentText(formatBody(alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(longBody(alert)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(alert.id, notificationId))
            .addAction(
                NotificationCompat.Action.Builder(
                    /* icon = */ 0,
                    /* title = */ "DISMISS",
                    /* intent = */ dismissIntent(alert.id, notificationId),
                ).build(),
            )

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun channelFor(tier: AlertTier): String = when (tier.rank) {
        in 1..4    -> NotificationChannels.LIFE_SAFETY
        5          -> NotificationChannels.SEVERE_WEATHER
        in 6..8    -> NotificationChannels.WATCHES
        else       -> NotificationChannels.ADVISORIES  // ranks 9-13
    }

    private fun formatBody(alert: Alert): String {
        val tz = TimeZone.currentSystemDefault()
        val expires = alert.expires.toLocalDateTime(tz)
        val hh = expires.hour.toString().padStart(2, '0')
        val mm = expires.minute.toString().padStart(2, '0')
        val area = if (alert.areaDesc.isNotBlank()) " · ${alert.areaDesc}" else ""
        return "Until $hh:$mm$area"
    }

    private fun longBody(alert: Alert): String {
        val short = formatBody(alert)
        val firstLine = alert.description.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: alert.headline
        return "$short\n$firstLine"
    }

    private fun tapIntent(alertId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationExtras.ALERT_ID, alertId)
            putExtra(NotificationExtras.NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(alertId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, DismissReceiver::class.java).apply {
            putExtra(NotificationExtras.ALERT_ID, alertId)
            putExtra(NotificationExtras.NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
