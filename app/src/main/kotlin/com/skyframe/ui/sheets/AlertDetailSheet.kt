package com.skyframe.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.data.alerts.AlertDescriptionFormat
import com.skyframe.data.alerts.AlertDescriptionParagraph
import com.skyframe.domain.Alert
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import kotlinx.datetime.TimeZone

/**
 * Renders an NWS alert's full description as parsed paragraphs with
 * HAZARD/SOURCE/IMPACT prefixes tier-colored using the alert's OWN tier
 * color (not the dashboard's active accent).
 */
@Composable
fun AlertDetailSheet(
    alert: Alert,
    timezone: String,
    onDismiss: () -> Unit,
) {
    val tz = remember(timezone) {
        runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    }
    val tierColor = Color(alert.tier.baseColor)
    val paragraphs = remember(alert.description) {
        AlertDescriptionFormat.parseDescription(alert.description)
    }
    val meta = remember(alert, tz) { AlertDescriptionFormat.formatAlertMeta(alert, tz) }

    HudBottomSheet(title = "ALERT DETAIL", onDismissRequest = onDismiss) {
        // Event name in alert's tier color
        Text(
            text = alert.event.uppercase(),
            color = tierColor,
            style = HudType.titleBar,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )

        // Meta line
        Text(
            text = meta,
            color = HudColors.ForegroundDim,
            style = HudType.metaLabel,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Divider in alert's tier color at low alpha
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(tierColor.copy(alpha = 0.3f))
                .padding(bottom = 12.dp),
        )

        // Paragraphs scrollable
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            paragraphs.forEach { para ->
                when (para) {
                    is AlertDescriptionParagraph.Tagged -> {
                        Row(modifier = Modifier.padding(bottom = 12.dp)) {
                            Text(
                                text = "[${para.prefix.name}] ",
                                color = tierColor,
                                style = HudType.metaLabel,
                            )
                            Text(
                                text = para.text,
                                color = HudColors.Foreground,
                                style = HudType.bodyMono,
                            )
                        }
                    }
                    is AlertDescriptionParagraph.Plain -> {
                        Text(
                            text = para.text,
                            color = HudColors.Foreground,
                            style = HudType.bodyMono,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
