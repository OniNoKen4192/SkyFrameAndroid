package com.skyframe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.skyframe.domain.HourlyPeriod
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.ForecastButton
import com.skyframe.ui.widgets.HudChart
import com.skyframe.ui.widgets.WxIcon
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun HourlyScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current.accent
    when (val weather = state.weather) {
        is WeatherState.Success -> {
            val today = weather.response.daily.firstOrNull()
            HourlyContent(
                periods = weather.response.hourly,
                accent = accent,
                onOpenForecast = { today?.let(onOpenForecast) },
                modifier = modifier,
            )
        }
        is WeatherState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ERROR: ${weather.message}", color = HudColors.Foreground, style = HudType.bodyMono)
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("FETCHING...", color = accent, style = HudType.titleBar)
        }
    }
}

@Composable
private fun HourlyContent(
    periods: List<HourlyPeriod>,
    accent: Color,
    onOpenForecast: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("NEXT 12H", color = HudColors.ForegroundDim, style = HudType.sectionHeader)
            ForecastButton(onClick = onOpenForecast)
        }

        HudChart(
            values = periods.map { it.tempF },
            accent = accent,
            modifier = Modifier.padding(vertical = 16.dp),
            height = 140.dp,
        )

        // Icon row
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            periods.forEach { p ->
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.hourLabel, color = HudColors.ForegroundDim, style = HudType.metricLabel)
                    WxIcon(code = p.iconCode, tint = accent, size = 20.dp)
                    Text("${p.tempF.toInt()}°", color = HudColors.Foreground, style = HudType.metricValue)
                }
            }
        }

        // Precip bars
        Text("PRECIP %", color = HudColors.ForegroundDim, style = HudType.sectionHeader, modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            periods.forEach { p ->
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(HudColors.BackgroundDeep),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(p.precipProbPct / 100f)
                                .fillMaxWidth()
                                .background(accent.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter),
                        )
                    }
                    Text("${p.precipProbPct}", color = HudColors.ForegroundDim, style = HudType.footerMono)
                }
            }
        }
    }
}
