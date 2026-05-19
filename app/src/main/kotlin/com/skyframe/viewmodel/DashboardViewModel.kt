package com.skyframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.Alert
import com.skyframe.repository.WeatherRepository
import com.skyframe.repository.WeatherState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the dashboard shell. Combines weather data + dismissed
 * acknowledgments + settings into one consumable stream so Composables
 * don't have to manage multiple sources.
 */
data class DashboardUiState(
    val weather: WeatherState,
    val dismissedAlertIds: Set<String>,
    val isConfigured: Boolean,
    val locationName: String,
    val timezone: String,
    val primaryStationId: String,
    val secondaryStationId: String,
) {
    val visibleAlerts: List<Alert>
        get() = when (weather) {
            is WeatherState.Success -> weather.response.alerts.filterNot { it.id in dismissedAlertIds }
            else -> emptyList()
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        weatherRepository.state,
        acknowledgments.flow,
        settings.flow,
    ) { weather, dismissed, cfg ->
        // Prune dismissed set against currently-active alert IDs so stale
        // dismissals don't accumulate.
        if (weather is WeatherState.Success) {
            val activeIds = weather.response.alerts.map { it.id }.toSet()
            val stale = dismissed - activeIds
            if (stale.isNotEmpty()) {
                viewModelScope.launch { acknowledgments.pruneTo(activeIds) }
            }
        }
        DashboardUiState(
            weather = weather,
            dismissedAlertIds = dismissed,
            isConfigured = cfg.isConfigured,
            locationName = cfg.locationName,
            timezone = cfg.timezone,
            primaryStationId = cfg.stationPrimary,
            secondaryStationId = cfg.stationFallback,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(
            WeatherState.Idle, emptySet(), false, "", "America/Chicago", "", "",
        ),
    )

    // Set by MainActivity when launched via a notification tap carrying
    // EXTRA_ALERT_ID. DashboardScaffold observes it, opens AlertDetailSheet
    // for the matching alert (once weather data is present), then consumes it.
    private val _pendingAlertDetailId = MutableStateFlow<String?>(null)
    val pendingAlertDetailId: StateFlow<String?> = _pendingAlertDetailId.asStateFlow()

    fun openAlertDetail(alertId: String) {
        _pendingAlertDetailId.value = alertId
    }

    fun consumePendingAlertDetail() {
        _pendingAlertDetailId.value = null
    }

    fun onResume() = weatherRepository.startPolling()
    fun onPause() = weatherRepository.stopPolling()
    fun refresh() = weatherRepository.refresh()
    fun dismissAlert(id: String) {
        viewModelScope.launch { acknowledgments.dismiss(id) }
    }

    fun applyStationOverride(mode: com.skyframe.domain.StationOverride) {
        viewModelScope.launch {
            settings.update { it.copy(stationOverride = mode) }
            // Immediate refresh so the UI reflects the new station without
            // waiting for the next 90s poll cycle.
            weatherRepository.refresh()
        }
    }
}
