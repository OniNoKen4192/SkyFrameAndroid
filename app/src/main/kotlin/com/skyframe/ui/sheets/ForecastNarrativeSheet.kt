package com.skyframe.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.domain.DailyPeriod
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Renders a DailyPeriod's day + night detailed forecast strings, each under
 * an NWS-preserved period-name section header (THIS AFTERNOON, TONIGHT,
 * FRIDAY, FRIDAY NIGHT, etc.). Orphan halves (day-only at window end,
 * night-only at window start) just render the populated half.
 */
@Composable
fun ForecastNarrativeSheet(
    day: DailyPeriod,
    onDismiss: () -> Unit,
) {
    val accent = LocalHudAccent.current.accent
    HudBottomSheet(title = "FORECAST", onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            day.dayPeriodName?.let { name ->
                Text(
                    text = "┌ ${name.uppercase()} ┐",
                    color = accent,
                    style = HudType.sectionHeader,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                )
                day.dayDetailedForecast?.let { text ->
                    Text(
                        text = text,
                        color = HudColors.Foreground,
                        style = HudType.bodyMono,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
            day.nightPeriodName?.let { name ->
                Text(
                    text = "┌ ${name.uppercase()} ┐",
                    color = accent,
                    style = HudType.sectionHeader,
                    modifier = Modifier.padding(bottom = 4.dp, top = 4.dp),
                )
                day.nightDetailedForecast?.let { text ->
                    Text(
                        text = text,
                        color = HudColors.Foreground,
                        style = HudType.bodyMono,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                }
            }
        }
    }
}
