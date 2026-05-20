package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.skyframe.ui.sheets.AlertDetailSheet
import com.skyframe.ui.sheets.SheetState
import com.skyframe.viewmodel.DashboardUiState
import com.skyframe.viewmodel.DashboardViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DashboardScaffold(
    viewModel: DashboardViewModel,
    nwsClient: com.skyframe.data.nws.NwsClient,
    onNavigateToSettings: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingAlertId by viewModel.pendingAlertDetailId.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(DashboardDestination.NOW) }
    var sheetState by remember { mutableStateOf<SheetState>(SheetState.None) }

    // Notification-tap deep-link: when MainActivity sets a pending alert id,
    // open AlertDetailSheet for it once the alert is present in weather data.
    // Re-runs when weather arrives after the tap (cold launch from notification).
    LaunchedEffect(pendingAlertId, ui.weather) {
        val id = pendingAlertId ?: return@LaunchedEffect
        val alert = (ui.weather as? WeatherState.Success)
            ?.response?.alerts?.firstOrNull { it.id == id }
        if (alert != null) {
            sheetState = SheetState.AlertDetail(alert)
            viewModel.consumePendingAlertDetail()
        }
    }

    // 1Hz ticker so the Footer's T-Xs countdown actually decrements. Without
    // this, formatRefreshLabel reads Clock.System.now() only at composition
    // time, freezing the value between weather state emissions (every ~90s).
    var nowTick by remember { mutableStateOf(Clock.System.now().epochSeconds) }
    LaunchedEffect(Unit) {
        while (true) {
            nowTick = Clock.System.now().epochSeconds
            kotlinx.coroutines.delay(1000)
        }
    }

    // Polling lifecycle is owned by MainActivity (onResume/onPause). Don't also
    // wire it here - that would call onResume() twice on every resume.

    val accent = computeAccent(ui)

    HudTheme(accent = accent) {
        // Paint the HUD background edge-to-edge (under the transparent system
        // bars), then inset the actual content within the status + navigation
        // bars so nothing is occluded. targetSdk 35 forces edge-to-edge on
        // Android 15, so consuming insets here is mandatory, not optional.
        Box(modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
            ) {

            AlertBanner(
                alerts = ui.visibleAlerts,
                onDismiss = { id -> viewModel.dismissAlert(id) },
                onAlertClick = { alert -> sheetState = SheetState.AlertDetail(alert) },
            )

            TopBar(
                locationName = ui.locationName,
                timezone = ui.timezone,
                isOnline = ui.weather !is WeatherState.Error,
                onLocationClick = onNavigateToSettings,
                onMenuClick = onNavigateToSettings,
            )

            Box(modifier = Modifier.weight(1f)) {
                val openForecast: (com.skyframe.domain.DailyPeriod) -> Unit = { day ->
                    sheetState = SheetState.Forecast(day)
                }
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                    DashboardDestination.HOURLY -> HourlyScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                    DashboardDestination.OUTLOOK -> OutlookScreen(
                        state = ui, onRefresh = viewModel::refresh, onOpenForecast = openForecast,
                    )
                }
            }

            Footer(
                stationId = (ui.weather as? WeatherState.Success)?.response?.meta?.stationId.orEmpty(),
                stationOverride = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
                lastFetchedLabel = formatFetchedLabel(ui.weather, ui.timezone),
                nextRefreshLabel = formatRefreshLabel(ui.weather, nowTick),
                onStationClick = { sheetState = SheetState.StationOverride },
            )
            HudBottomNavBar(selected = selected, onSelect = { selected = it })
            }
        }

        // Sheet dispatch — only one sheet open at a time per SheetState sealed class.
        when (val s = sheetState) {
            SheetState.None -> Unit
            is SheetState.AlertDetail -> AlertDetailSheet(
                alert = s.alert,
                timezone = ui.timezone,
                onDismiss = { sheetState = SheetState.None },
            )
            is SheetState.Forecast -> com.skyframe.ui.sheets.ForecastNarrativeSheet(
                day = s.day,
                onDismiss = { sheetState = SheetState.None },
            )
            SheetState.StationOverride -> {
                val success = ui.weather as? WeatherState.Success
                val currentMode = success?.response?.meta?.stationOverride ?: StationOverride.AUTO
                com.skyframe.ui.sheets.StationOverrideSheet(
                    currentMode = currentMode,
                    primaryStationId = ui.primaryStationId,
                    secondaryStationId = ui.secondaryStationId,
                    timezone = ui.timezone,
                    client = nwsClient,
                    onApply = { mode ->
                        viewModel.applyStationOverride(mode)
                        sheetState = SheetState.None
                    },
                    onDismiss = { sheetState = SheetState.None },
                )
            }
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

private fun formatRefreshLabel(state: WeatherState, nowSeconds: Long): String {
    val success = state as? WeatherState.Success ?: return "T-???"
    val secondsLeft = (success.response.meta.nextRefreshAt.epochSeconds - nowSeconds).coerceAtLeast(0)
    return "T-${secondsLeft}s"
}
