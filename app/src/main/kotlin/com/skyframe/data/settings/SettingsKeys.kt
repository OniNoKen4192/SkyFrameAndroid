package com.skyframe.data.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsKeys {
    val EMAIL              = stringPreferencesKey("email")
    val LAT                = doublePreferencesKey("lat")
    val LON                = doublePreferencesKey("lon")
    val LOCATION_NAME      = stringPreferencesKey("location_name")
    val FORECAST_OFFICE    = stringPreferencesKey("forecast_office")
    val GRID_X             = intPreferencesKey("grid_x")
    val GRID_Y             = intPreferencesKey("grid_y")
    val TIMEZONE           = stringPreferencesKey("timezone")
    val FORECAST_ZONE      = stringPreferencesKey("forecast_zone")
    val STATION_PRIMARY    = stringPreferencesKey("station_primary")
    val STATION_FALLBACK   = stringPreferencesKey("station_fallback")
    val STATION_OVERRIDE   = stringPreferencesKey("station_override")
    val UPDATE_CHECK       = stringPreferencesKey("update_check")  // "true" / "false"
    val PERMISSIONS_PROMPTED_AT = longPreferencesKey("permissions_prompted_at")
}
