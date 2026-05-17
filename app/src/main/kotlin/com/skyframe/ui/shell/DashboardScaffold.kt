package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyframe.domain.StationOverride
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudAccent
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudTheme
import com.skyframe.ui.nav.DashboardDestination
import com.skyframe.ui.screens.HourlyScreen
import com.skyframe.ui.screens.NowScreen
import com.skyframe.ui.screens.OutlookScreen
import com.skyframe.viewmodel.DashboardUiState
import com.skyframe.viewmodel.DashboardViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DashboardScaffold(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(DashboardDestination.NOW) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        viewModel.onResume()
    }

    val accent = computeAccent(ui)

    HudTheme(accent = accent) {
        Column(modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase)) {
            // AlertBanner — Phase L; conditional, slot reserved

            TopBar(
                locationName = ui.locationName,
                timezone = ui.timezone,
                isOnline = ui.weather !is WeatherState.Error,
                onLocationClick = onNavigateToSettings,
                onMenuClick = onNavigateToSettings,
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.HOURLY -> HourlyScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.OUTLOOK -> OutlookScreen(state = ui, onRefresh = viewModel::refresh)
                }
            }

            Footer(
                stationId = (ui.weather as? WeatherState.Success)?.response?.meta?.stationId.orEmpty(),
                stationOverride = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
                lastFetchedLabel = formatFetchedLabel(ui.weather, ui.timezone),
                nextRefreshLabel = formatRefreshLabel(ui.weather),
                onStationClick = {},  // Plan 2 wires StationOverrideSheet
            )
            HudBottomNavBar(selected = selected, onSelect = { selected = it })
        }
    }
}

private fun computeAccent(ui: DashboardUiState): HudAccent {
    val top = ui.visibleAlerts.firstOrNull() ?: return HudAccent.Default
    return HudAccent.fromTier(top.tier)
}

private fun formatFetchedLabel(state: WeatherState, timezone: String): String {
    val success = state as? WeatherState.Success ?: return "WAITING..."
    val tz = runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    val ldt = success.response.meta.fetchedAt.toLocalDateTime(tz)
    val h = ldt.hour.toString().padStart(2, '0')
    val m = ldt.minute.toString().padStart(2, '0')
    val s = ldt.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}

private fun formatRefreshLabel(state: WeatherState): String {
    val success = state as? WeatherState.Success ?: return "T-???"
    val secondsLeft = (success.response.meta.nextRefreshAt.epochSeconds - Clock.System.now().epochSeconds).coerceAtLeast(0)
    return "T-${secondsLeft}s"
}
