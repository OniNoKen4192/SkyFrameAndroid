package com.skyframe.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.skyframe.domain.StationOverride
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    data class Snapshot(
        val email: String = "",
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val locationName: String = "",
        val forecastOffice: String = "",
        val gridX: Int = 0,
        val gridY: Int = 0,
        val timezone: String = "America/Chicago",
        val forecastZone: String = "",
        val stationPrimary: String = "",
        val stationFallback: String = "",
        val stationOverride: StationOverride = StationOverride.AUTO,
        val updateCheckEnabled: Boolean = false,
    ) {
        /** True when SetupResolver has populated the location config at least once. */
        val isConfigured: Boolean
            get() = forecastOffice.isNotBlank() && stationPrimary.isNotBlank() && lat != 0.0
    }

    val flow: Flow<Snapshot> = dataStore.data.map { prefs ->
        Snapshot(
            email = prefs[SettingsKeys.EMAIL] ?: "",
            lat = prefs[SettingsKeys.LAT] ?: 0.0,
            lon = prefs[SettingsKeys.LON] ?: 0.0,
            locationName = prefs[SettingsKeys.LOCATION_NAME] ?: "",
            forecastOffice = prefs[SettingsKeys.FORECAST_OFFICE] ?: "",
            gridX = prefs[SettingsKeys.GRID_X] ?: 0,
            gridY = prefs[SettingsKeys.GRID_Y] ?: 0,
            timezone = prefs[SettingsKeys.TIMEZONE] ?: "America/Chicago",
            forecastZone = prefs[SettingsKeys.FORECAST_ZONE] ?: "",
            stationPrimary = prefs[SettingsKeys.STATION_PRIMARY] ?: "",
            stationFallback = prefs[SettingsKeys.STATION_FALLBACK] ?: "",
            stationOverride = when (prefs[SettingsKeys.STATION_OVERRIDE]) {
                "force-secondary" -> StationOverride.FORCE_SECONDARY
                else -> StationOverride.AUTO
            },
            updateCheckEnabled = prefs[SettingsKeys.UPDATE_CHECK] == "true",
        )
    }

    suspend fun snapshot(): Snapshot = flow.first()

    suspend fun update(transform: (Snapshot) -> Snapshot) {
        val current = snapshot()
        val next = transform(current)
        dataStore.edit { prefs ->
            prefs[SettingsKeys.EMAIL] = next.email
            prefs[SettingsKeys.LAT] = next.lat
            prefs[SettingsKeys.LON] = next.lon
            prefs[SettingsKeys.LOCATION_NAME] = next.locationName
            prefs[SettingsKeys.FORECAST_OFFICE] = next.forecastOffice
            prefs[SettingsKeys.GRID_X] = next.gridX
            prefs[SettingsKeys.GRID_Y] = next.gridY
            prefs[SettingsKeys.TIMEZONE] = next.timezone
            prefs[SettingsKeys.FORECAST_ZONE] = next.forecastZone
            prefs[SettingsKeys.STATION_PRIMARY] = next.stationPrimary
            prefs[SettingsKeys.STATION_FALLBACK] = next.stationFallback
            prefs[SettingsKeys.STATION_OVERRIDE] = when (next.stationOverride) {
                StationOverride.AUTO -> "auto"
                StationOverride.FORCE_SECONDARY -> "force-secondary"
            }
            prefs[SettingsKeys.UPDATE_CHECK] = if (next.updateCheckEnabled) "true" else "false"
        }
    }
}
