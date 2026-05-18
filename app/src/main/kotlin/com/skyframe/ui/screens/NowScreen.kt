package com.skyframe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.DailyPeriod
import com.skyframe.domain.TempUnit
import com.skyframe.domain.Units
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.HudHero
import com.skyframe.ui.widgets.HudMetricBar
import com.skyframe.viewmodel.DashboardUiState
import kotlin.math.roundToInt

@Composable
fun NowScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    onOpenForecast: (DailyPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current.accent
    val weather = state.weather

    Box(modifier = modifier.fillMaxSize()) {
        when (weather) {
            WeatherState.Idle, WeatherState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("FETCHING...", color = accent, style = HudType.titleBar)
                }
            }
            is WeatherState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ERROR: ${weather.message}", color = HudColors.Foreground, style = HudType.bodyMono)
                }
            }
            is WeatherState.Success -> {
                val today = weather.response.daily.firstOrNull()
                NowContent(
                    current = weather.response.current,
                    onOpenForecast = { today?.let(onOpenForecast) },
                )
            }
        }
    }
}

@Composable
private fun NowContent(current: CurrentConditions, onOpenForecast: () -> Unit) {
    val accent = LocalHudAccent.current.accent
    var tempUnit by remember { mutableStateOf(TempUnit.FAHRENHEIT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        HudHero(
            current = current,
            tempUnit = tempUnit,
            accent = accent,
            onToggleUnit = {
                tempUnit = if (tempUnit == TempUnit.FAHRENHEIT) TempUnit.CELSIUS else TempUnit.FAHRENHEIT
            },
            onOpenForecast = onOpenForecast,
        )

        HudMetricBar(
            label = "HUMIDITY",
            value = current.humidityPct?.roundToInt()?.let { "$it%" } ?: "--",
            trend = current.trends.humidity,
            accent = accent,
            fillFraction = (current.humidityPct ?: 0.0).toFloat() / 100f,
        )
        HudMetricBar(
            label = "WIND ${current.wind.cardinal}",
            value = "${current.wind.speedMph.roundToInt()} MPH",
            trend = current.trends.wind,
            accent = accent,
            fillFraction = (current.wind.speedMph / 40.0).toFloat(),
        )
        HudMetricBar(
            label = "PRESSURE",
            value = current.pressureInHg?.let { "%.2f\"".format(it) } ?: "--",
            trend = current.trends.pressure,
            accent = accent,
            // Normalize pressure 29.50..30.50 inHg to 0..1 (covers typical range)
            fillFraction = ((current.pressureInHg ?: 29.92) - 29.50).toFloat(),
        )
        HudMetricBar(
            label = "DEWPOINT",
            value = current.dewpointF?.let {
                val converted = Units.convertTempF(it, tempUnit).roundToInt()
                "$converted°"
            } ?: "--",
            trend = current.trends.dewpoint,
            accent = accent,
            // Dewpoint 0..80F mapped to 0..1
            fillFraction = ((current.dewpointF ?: 0.0) / 80.0).toFloat(),
        )
        HudMetricBar(
            label = "VISIBILITY",
            value = current.visibilityMi?.let { "%.1f mi".format(it) } ?: "--",
            trend = current.trends.visibility,
            accent = accent,
            // Visibility 0..10 mi mapped to 0..1
            fillFraction = ((current.visibilityMi ?: 0.0) / 10.0).toFloat(),
        )
    }
}
