package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the NWS REST API. URL construction matches the web
 * project's server/nws/client.ts exactly. The shared HttpClient is
 * Hilt-provided with the mandatory User-Agent header pre-installed.
 */
@Singleton
class NwsClient @Inject constructor(private val http: HttpClient) {

    private val base = "https://api.weather.gov"

    suspend fun points(lat: Double, lon: Double): PointsDto =
        http.get("$base/points/${fmt(lat)},${fmt(lon)}").body()

    suspend fun stationsList(url: String): StationsListDto =
        http.get(url).body()

    suspend fun forecast(office: String, x: Int, y: Int): ForecastDto =
        http.get("$base/gridpoints/$office/$x,$y/forecast").body()

    suspend fun hourlyForecast(office: String, x: Int, y: Int): ForecastDto =
        http.get("$base/gridpoints/$office/$x,$y/forecast/hourly").body()

    suspend fun latestObservation(stationId: String): ObservationDto =
        http.get("$base/stations/$stationId/observations/latest").body()

    suspend fun activeAlerts(lat: Double, lon: Double): AlertsDto =
        http.get("$base/alerts/active?point=${fmt(lat)},${fmt(lon)}").body()

    suspend fun recentObservations(stationId: String, limit: Int = 6): ObservationsListDto =
        http.get("$base/stations/$stationId/observations?limit=$limit").body()

    // Locale.ROOT keeps the decimal point as '.' regardless of the device locale.
    // Without this, locales like de_DE produce "42,8744,-87,8633" which NWS rejects.
    private fun fmt(d: Double): String = String.format(Locale.ROOT, "%.4f", d)
}
