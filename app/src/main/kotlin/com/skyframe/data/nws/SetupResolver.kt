package com.skyframe.data.nws

import com.skyframe.data.geocoding.GeoCoordinates
import com.skyframe.data.geocoding.Geocoder
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedSetup(
    val lat: Double,
    val lon: Double,
    val forecastOffice: String,
    val gridX: Int,
    val gridY: Int,
    val timezone: String,
    val forecastZone: String,
    val primaryStation: String,
    val secondaryStation: String,
    val locationName: String,
)

class SetupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves a user-entered location (ZIP or "lat, lon") into the full
 * config needed to call NWS forecast endpoints. Port of
 * _reference/server/nws/setup.ts resolveSetup().
 */
@Singleton
class SetupResolver @Inject constructor(
    private val geocoder: Geocoder,
    private val nws: NwsClient,
) {
    private val zipRegex = Regex("""^\d{5}$""")
    private val latLonRegex = Regex("""^(-?\d+\.?\d*)[,\s]+(-?\d+\.?\d*)$""")

    suspend fun resolve(input: String): ResolvedSetup {
        val trimmed = input.trim()

        // 1. Parse input -> lat/lon
        val coords = when {
            zipRegex.matches(trimmed) -> geocoder.geocodeZip(trimmed)
            else -> {
                val match = latLonRegex.matchEntire(trimmed)
                    ?: throw SetupException("Enter a 5-digit ZIP code or lat,lon coordinates.")
                val lat = match.groupValues[1].toDouble()
                val lon = match.groupValues[2].toDouble()
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    throw SetupException("Coordinates out of valid range.")
                }
                GeoCoordinates(lat, lon)
            }
        }

        // 2. Call NWS /points for grid + zone + stations URL + relative location
        val points = try {
            nws.points(coords.lat, coords.lon)
        } catch (e: Exception) {
            throw SetupException("NWS /points lookup failed: ${e.message}", e)
        }
        val props = points.properties

        // 3. Strip the forecast zone to its terminal ID (e.g. WIZ066)
        val forecastZone = Regex("""([A-Z]{3}\d{3})$""")
            .find(props.forecastZone)?.groupValues?.get(1) ?: props.forecastZone

        // 4. Get nearby observation stations list
        val stationsList = try {
            nws.stationsList(props.observationStations)
        } catch (e: Exception) {
            throw SetupException("NWS /stations lookup failed: ${e.message}", e)
        }
        val stationIds = stationsList.features.map { it.properties.stationIdentifier }
        if (stationIds.isEmpty()) {
            throw SetupException("No observation stations found near this location.")
        }

        val city = props.relativeLocation.properties.city.uppercase()
        val state = props.relativeLocation.properties.state.uppercase()

        return ResolvedSetup(
            lat = coords.lat,
            lon = coords.lon,
            forecastOffice = props.gridId,
            gridX = props.gridX,
            gridY = props.gridY,
            timezone = props.timeZone,
            forecastZone = forecastZone,
            primaryStation = stationIds[0],
            secondaryStation = stationIds.getOrNull(1) ?: stationIds[0],
            locationName = "$city $state",
        )
    }
}
