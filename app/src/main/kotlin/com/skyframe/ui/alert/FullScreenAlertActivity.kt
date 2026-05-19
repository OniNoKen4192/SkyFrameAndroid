package com.skyframe.ui.alert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.skyframe.MainActivity
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.notifications.NotificationExtras
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Launched by NotificationDispatcher's setFullScreenIntent for life_safety
 * channel notifications. Shows the alert prominently on the lock screen.
 * setShowWhenLocked + setTurnScreenOn are declared in AndroidManifest so
 * the system wakes the screen even when locked.
 *
 * Renders edge-to-edge with a tier-color top stripe. Two CTAs: VIEW DETAILS
 * launches MainActivity routed to AlertDetailSheet; DISMISS acknowledges
 * the alert + cancels the notification + finishes the activity.
 *
 * Reads minimal data from intent extras to avoid touching the WeatherResponse
 * (which may be stale by the time the user sees the lock-screen takeover).
 * VIEW DETAILS hands off to MainActivity which resolves the full alert.
 */
@AndroidEntryPoint
class FullScreenAlertActivity : ComponentActivity() {

    @Inject lateinit var acknowledgments: AlertAcknowledgmentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        }

        val alertId = intent.getStringExtra(NotificationExtras.ALERT_ID).orEmpty()
        val notificationId = intent.getIntExtra(NotificationExtras.NOTIFICATION_ID, -1)
        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val tierColorArgb = intent.getLongExtra(EXTRA_TIER_COLOR, 0xFFFF4444L)

        setContent {
            FullScreenAlertContent(
                event = event,
                body = body,
                tierColor = Color(tierColorArgb),
                onViewDetails = {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(NotificationExtras.ALERT_ID, alertId)
                        },
                    )
                    finish()
                },
                onDismiss = {
                    lifecycleScope.launch { acknowledgments.dismiss(alertId) }
                    if (notificationId != -1) {
                        NotificationManagerCompat.from(this).cancel(notificationId)
                    }
                    finish()
                },
            )
        }
    }

    companion object {
        const val EXTRA_EVENT      = "com.skyframe.alert.EVENT"
        const val EXTRA_BODY       = "com.skyframe.alert.BODY"
        const val EXTRA_TIER_COLOR = "com.skyframe.alert.TIER_COLOR"
    }
}

@Composable
private fun FullScreenAlertContent(
    event: String,
    body: String,
    tierColor: Color,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(tierColor),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "⚠",
                color = tierColor,
                style = HudType.titleBar,
                fontSize = 56.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = event.uppercase(),
                color = tierColor,
                style = HudType.titleBar,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                color = HudColors.Foreground,
                style = HudType.bodyMono,
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = "[ VIEW DETAILS ]",
                color = tierColor,
                style = HudType.titleBar,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .border(BorderStroke(1.dp, tierColor))
                    .clickable(onClick = onViewDetails)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "[ DISMISS ]",
                color = HudColors.ForegroundDim,
                style = HudType.titleBar,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .border(BorderStroke(1.dp, HudColors.ForegroundDim))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}
