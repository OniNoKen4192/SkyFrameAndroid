package com.skyframe.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Platform LocationManager wrapper. Used only for the "set my home location
 * once" use case in SettingsScreen's USE MY LOCATION button — no continuous
 * tracking, no FusedLocationProviderClient (Play Services dep).
 *
 * Caller MUST have FINE_LOCATION permission before calling getLastKnownLocation
 * (which uses @SuppressLint to silence the lint warning we can't satisfy without
 * making the API itself ugly).
 */
@Singleton
class GpsAutodetect @Inject constructor(@ApplicationContext private val context: Context) {

    sealed class Result {
        data class Coordinates(val lat: Double, val lon: Double) : Result()
        data object PermissionDenied : Result()
        data object NoLastKnownLocation : Result()
    }

    fun hasFineLocationPermission(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns the most recent fix from NETWORK provider (typically faster and
     * less battery), falling back to GPS provider. Each provider call wrapped
     * in runCatching since LocationManager.getLastKnownLocation can throw on
     * disabled providers in some OS versions.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Result {
        if (!hasFineLocationPermission()) return Result.PermissionDenied
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val fix = runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
        return fix?.let { Result.Coordinates(it.latitude, it.longitude) } ?: Result.NoLastKnownLocation
    }
}
