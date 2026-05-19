package com.skyframe.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.skyframe.R

/**
 * Creates the five notification channels and two groups SkyFrame uses.
 * Idempotent — Android no-ops on re-creation with the same channel/group ID.
 * Called from SkyFrameApp.onCreate.
 *
 * Channel → tier mapping (see ALERT_TIERS.md):
 *   life_safety     ranks 1-4    (tornado-*, tstorm-destructive)
 *   severe_weather  rank 5       (severe-warning)
 *   watches         ranks 6-8    (blizzard, winter-storm, flood)
 *   advisories      ranks 9-13   (heat, special-weather-statement, watch,
 *                                  advisory-high, advisory)
 *   app_updates     synthetic update alerts only
 */
object NotificationChannels {

    // Channel IDs - public so NotificationDispatcher can reference them.
    const val LIFE_SAFETY     = "life_safety"
    const val SEVERE_WEATHER  = "severe_weather"
    const val WATCHES         = "watches"
    const val ADVISORIES      = "advisories"
    const val APP_UPDATES     = "app_updates"

    // Group IDs.
    private const val GROUP_WEATHER = "weather_alerts"
    private const val GROUP_SYSTEM  = "system"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_WEATHER, "Weather alerts"),
        )
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_SYSTEM, "App updates"),
        )

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val lifeSafetySound = soundUri(context, R.raw.notification_life_safety)
        val severeSound     = soundUri(context, R.raw.notification_severe)

        nm.createNotificationChannel(
            NotificationChannel(LIFE_SAFETY, "Life-safety alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tornado warnings, destructive thunderstorm warnings. Bypasses Do Not Disturb."
                group = GROUP_WEATHER
                setSound(lifeSafetySound, audioAttrs)
                setBypassDnd(true)
                enableLights(true)
                lightColor = 0xFFFF4444.toInt()
                enableVibration(true)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(SEVERE_WEATHER, "Severe weather", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Severe thunderstorm warnings."
                group = GROUP_WEATHER
                setSound(severeSound, audioAttrs)
                enableVibration(true)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(WATCHES, "Watches", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Blizzard, winter storm, flood warnings."
                group = GROUP_WEATHER
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(ADVISORIES, "Advisories", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Heat advisories, special weather statements, lower-tier alerts."
                group = GROUP_WEATHER
                setSound(null, null)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(APP_UPDATES, "App updates", NotificationManager.IMPORTANCE_MIN).apply {
                description = "New SkyFrame release available on GitHub."
                group = GROUP_SYSTEM
                setSound(null, null)
            },
        )
    }

    private fun soundUri(context: Context, @androidx.annotation.RawRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")
}
