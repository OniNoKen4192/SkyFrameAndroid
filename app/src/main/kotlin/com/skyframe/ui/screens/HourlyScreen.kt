package com.skyframe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
    // Show a consistent 12-hour window. The normalizer keeps 13 (12+), but the
    // chart and the icon/precip grids must agree on count or they desync.
    val shown = periods.take(12)

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

        // columnCentered = true so each line point sits above its column in the
        // weight(1f) grids below.
        HudChart(
            values = shown.map { it.tempF },
            accent = accent,
            modifier = Modifier.padding(vertical = 16.dp),
            height = 140.dp,
            columnCentered = true,
        )

        // Icon row - equal-width columns so they align with the chart points.
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            shown.forEach { p ->
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        p.hourLabel,
                        color = HudColors.ForegroundDim,
                        // Tighter than metricLabel so 4-char labels (10PM/12AM)
                        // fit a ~30dp column on one line instead of wrapping.
                        style = HudType.metricLabel.copy(fontSize = 9.sp, letterSpacing = 0.02.em),
                        maxLines = 1,
                        softWrap = false,
                    )
                    WxIcon(code = p.iconCode, tint = accent, size = 18.dp)
                    Text("${p.tempF.toInt()}°", color = HudColors.Foreground, style = HudType.metricValue)
                }
            }
        }

        // Precip bars - accent-only bars (no track), matching the web. At 0%
        // nothing renders; the % label shows only above 30%. Opacity tiers
        // mirror hud.css .bar.low/.med/.high.
        Text("PRECIP %", color = HudColors.ForegroundDim, style = HudType.sectionHeader, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.fillMaxWidth().height(48.dp).padding(vertical = 8.dp)) {
            shown.forEach { p ->
                val pct = p.precipProbPct
                val frac = (pct / 100f).coerceIn(0f, 1f)
                val barAlpha = when {
                    pct > 50 -> 0.9f
                    pct > 25 -> 0.55f
                    else -> 0.25f
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (pct > 0) {
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight(frac)
                                .background(accent.copy(alpha = barAlpha)),
                        )
                    }
                    if (pct > 30) {
                        Text(
                            "$pct%",
                            color = accent,
                            style = HudType.footerMono.copy(fontSize = 8.sp),
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }
}
