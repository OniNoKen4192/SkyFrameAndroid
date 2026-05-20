package com.skyframe.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyframe.data.gps.GpsAutodetect
import com.skyframe.data.install.InstallSource
import com.skyframe.data.nws.SetupException
import com.skyframe.data.nws.SetupResolver
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.data.updates.UpdateCheckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val locationInput: String = "",
    val emailInput: String = "",
    val updateCheckEnabled: Boolean = false,
    val showUpdateCheckCheckbox: Boolean = false,
    val gpsState: GpsState = GpsState.Idle,
    val saveState: SettingsViewModel.SaveState = SettingsViewModel.SaveState.Idle,
    val isConfigured: Boolean = false,
)

sealed class GpsState {
    data object Idle : GpsState()
    data object Requesting : GpsState()
    data object Available : GpsState()
    data object NoLastKnown : GpsState()
    data object PermissionDenied : GpsState()
    data object PermissionDeniedPermanent : GpsState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val setupResolver: SetupResolver,
    private val updateCheck: UpdateCheckRepository,
    private val gpsAutodetect: GpsAutodetect,
    @ApplicationContext private val context: Context,
    private val notificationDispatcher: com.skyframe.notifications.NotificationDispatcher,
) : ViewModel() {

    // Test seam (Hilt ignores defaults on @Inject params, so this is set imperatively via the internal constructor).
    private var isFromPlayStoreOverride: Boolean? = null

    internal constructor(
        settings: SettingsRepository,
        setupResolver: SetupResolver,
        updateCheck: UpdateCheckRepository,
        gpsAutodetect: GpsAutodetect,
        context: Context,
        notificationDispatcher: com.skyframe.notifications.NotificationDispatcher,
        isFromPlayStoreOverride: Boolean?,
    ) : this(settings, setupResolver, updateCheck, gpsAutodetect, context, notificationDispatcher) {
        this.isFromPlayStoreOverride = isFromPlayStoreOverride
        // Re-run init with the override applied — viewModelScope.launch is async, so
        // the original init may not have populated showUpdateCheckCheckbox yet when
        // tests inspect state.value. Set synchronously here for predictable test behavior.
        _uiState.update { it.copy(showUpdateCheckCheckbox = !isFromPlayStore()) }
    }

    sealed class SaveState {
        data object Idle : SaveState()
        data object Resolving : SaveState()
        data object Saved : SaveState()
        data class Error(val message: String) : SaveState()
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val snap = settings.snapshot()
            // Show ZIP-equivalent in the LOCATION field when reopening after
            // first save: easiest representation is "lat, lon" since we don't
            // round-trip the original ZIP. Users can re-edit either way.
            val location = if (snap.lat != 0.0 && snap.lon != 0.0) {
                "${snap.lat}, ${snap.lon}"
            } else {
                ""
            }
            _uiState.update {
                it.copy(
                    locationInput = location,
                    emailInput = snap.email,
                    updateCheckEnabled = snap.updateCheckEnabled,
                    showUpdateCheckCheckbox = !isFromPlayStore(),
                    isConfigured = snap.isConfigured,
                )
            }
        }
    }

    fun onLocationChange(value: String) {
        _uiState.update { it.copy(locationInput = value) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(emailInput = value) }
    }

    fun onUpdateCheckToggle(enabled: Boolean) {
        _uiState.update { it.copy(updateCheckEnabled = enabled) }
        if (enabled) {
            viewModelScope.launch { updateCheck.maybeCheck() }
        }
    }

    /**
     * Debug-only: fires a synthetic top-tier (Tornado Warning) alert through
     * NotificationDispatcher after a short delay, so the tester can lock the
     * phone and verify the life_safety channel — 1050 Hz looping audio, DND
     * bypass, and the FullScreenAlertActivity lock-screen takeover. Only
     * invoked from a BuildConfig.DEBUG-gated button in SettingsScreen.
     */
    fun fireTestLifeSafetyAlert() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            val now = kotlinx.datetime.Clock.System.now()
            notificationDispatcher.notify(
                com.skyframe.domain.Alert(
                    id = "test-life-safety-${now.toEpochMilliseconds()}",
                    event = "Tornado Warning",
                    tier = com.skyframe.domain.AlertTier.TORNADO_WARNING,
                    severity = com.skyframe.domain.AlertSeverity.EXTREME,
                    headline = "TEST — Tornado Warning",
                    description = "This is a SkyFrame test alert. The National Weather " +
                        "Service has NOT issued a warning.\n\nHAZARD...Test tornado.\n\n" +
                        "SOURCE...SkyFrame debug trigger.\n\nIMPACT...None — this is a test.",
                    issuedAt = now,
                    effective = now,
                    expires = now.plus(kotlin.time.Duration.parse("PT30M")),
                    areaDesc = "Test County",
                ),
            )
        }
    }

    fun onGpsPermissionGranted() {
        viewModelScope.launch {
            _uiState.update { it.copy(gpsState = GpsState.Requesting) }
            val result = gpsAutodetect.getLastKnownLocation()
            when (result) {
                is GpsAutodetect.Result.Coordinates -> {
                    val formatted = String.format(
                        java.util.Locale.ROOT,
                        "%.4f, %.4f",
                        result.lat,
                        result.lon,
                    )
                    _uiState.update {
                        it.copy(locationInput = formatted, gpsState = GpsState.Available)
                    }
                }
                GpsAutodetect.Result.NoLastKnownLocation -> {
                    _uiState.update { it.copy(gpsState = GpsState.NoLastKnown) }
                }
                GpsAutodetect.Result.PermissionDenied -> {
                    _uiState.update { it.copy(gpsState = GpsState.PermissionDenied) }
                }
            }
        }
    }

    fun onGpsPermissionDenied(permanently: Boolean) {
        _uiState.update {
            it.copy(
                gpsState = if (permanently) GpsState.PermissionDeniedPermanent
                else GpsState.PermissionDenied,
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.locationInput.isBlank()) {
            _uiState.update {
                it.copy(saveState = SaveState.Error("LOCATION is required"))
            }
            return
        }
        if (state.emailInput.isBlank()) {
            _uiState.update {
                it.copy(saveState = SaveState.Error("EMAIL is required"))
            }
            return
        }

        _uiState.update { it.copy(saveState = SaveState.Resolving) }
        viewModelScope.launch {
            try {
                val resolved = setupResolver.resolve(state.locationInput)
                settings.update {
                    it.copy(
                        email = state.emailInput,
                        lat = resolved.lat,
                        lon = resolved.lon,
                        locationName = resolved.locationName,
                        forecastOffice = resolved.forecastOffice,
                        gridX = resolved.gridX,
                        gridY = resolved.gridY,
                        timezone = resolved.timezone,
                        forecastZone = resolved.forecastZone,
                        stationPrimary = resolved.primaryStation,
                        stationFallback = resolved.secondaryStation,
                        updateCheckEnabled = state.updateCheckEnabled,
                    )
                }
                _uiState.update { it.copy(saveState = SaveState.Saved, isConfigured = true) }
                onSaved()
            } catch (e: SetupException) {
                _uiState.update {
                    it.copy(saveState = SaveState.Error(e.message ?: "Couldn't resolve location"))
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saveState = SaveState.Error(e.message ?: "Save failed"))
                }
            }
        }
    }

    private fun isFromPlayStore(): Boolean =
        isFromPlayStoreOverride ?: InstallSource.isFromPlayStore(context)
}
