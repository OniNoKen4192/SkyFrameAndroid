package com.skyframe.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

data class GeoCoordinates(val lat: Double, val lon: Double)

class GeocodingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Wraps Nominatim (OpenStreetMap) for ZIP to lat/lon resolution. Port of
 * _reference/server/nws/setup.ts geocodeZip(). Nominatim is keyless and
 * free; their usage policy asks for a meaningful User-Agent (set by the
 * shared HttpClient at construction).
 */
@Singleton
class Geocoder @Inject constructor(private val http: HttpClient) {

    private val base = "https://nominatim.openstreetmap.org"

    suspend fun geocodeZip(zip: String): GeoCoordinates {
        val url = "$base/search?postalcode=$zip&country=US&format=json&limit=1"
        val response: HttpResponse = http.get(url)
        if (!response.status.isSuccess()) {
            throw GeocodingException("Nominatim returned ${response.status.value}")
        }
        val results: List<NominatimResult> = response.body()
        if (results.isEmpty()) {
            throw GeocodingException("No results for ZIP $zip")
        }
        val r = results.first()
        val lat = r.lat.toDoubleOrNull() ?: throw GeocodingException("Invalid lat from Nominatim: ${r.lat}")
        val lon = r.lon.toDoubleOrNull() ?: throw GeocodingException("Invalid lon from Nominatim: ${r.lon}")
        return GeoCoordinates(lat, lon)
    }
}
