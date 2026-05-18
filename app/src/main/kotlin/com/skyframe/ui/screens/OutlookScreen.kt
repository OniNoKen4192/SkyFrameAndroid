package com.skyframe.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.DailyPeriod
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.HudRangeBar
import com.skyframe.ui.widgets.WxIcon
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun OutlookScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current.accent
    when (val w = state.weather) {
        is WeatherState.Success -> OutlookContent(w.response.daily, accent, onOpenForecast, modifier)
        is WeatherState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ERROR: ${w.message}", color = HudColors.Foreground, style = HudType.bodyMono)
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("FETCHING...", color = accent, style = HudType.titleBar)
        }
    }
}

@Composable
private fun OutlookContent(
    periods: List<DailyPeriod>,
    accent: Color,
    onOpenForecast: (DailyPeriod) -> Unit,
    modifier: Modifier,
) {
    val weekMin = periods.minOfOrNull { it.lowF } ?: 0
    val weekMax = periods.maxOfOrNull { it.highF } ?: 100

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("7-DAY OUTLOOK", color = HudColors.ForegroundDim, style = HudType.sectionHeader, modifier = Modifier.padding(bottom = 8.dp))

        periods.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = p.dayOfWeek,
                    color = accent,
                    style = HudType.metricValue,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .fillMaxWidth(0.20f)
                        .clickable { onOpenForecast(p) },
                )
                WxIcon(code = p.iconCode, tint = accent, size = 22.dp)
                Text(
                    text = "${p.lowF}° / ${p.highF}°",
                    color = HudColors.Foreground,
                    style = HudType.metricLabel,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HudRangeBar(
                    lowF = p.lowF,
                    highF = p.highF,
                    weekMinF = weekMin,
                    weekMaxF = weekMax,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (p.precipProbPct > 0) " ${p.precipProbPct}%" else "  --",
                    color = HudColors.ForegroundDim,
                    style = HudType.metricLabel,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
