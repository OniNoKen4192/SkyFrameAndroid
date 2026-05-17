package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StationOverride {
    @SerialName("auto") AUTO,
    @SerialName("force-secondary") FORCE_SECONDARY,
}

@Serializable
enum class WeatherError {
    @SerialName("rate_limited") RATE_LIMITED,
    @SerialName("upstream_malformed") UPSTREAM_MALFORMED,
    @SerialName("station_fallback") STATION_FALLBACK,
    @SerialName("partial") PARTIAL,
}

@Serializable
data class WeatherMeta(
    val fetchedAt: Instant,
    val nextRefreshAt: Instant,
    val cacheHit: Boolean,
    val stationId: String,
    val locationName: String,
    val stationOverride: StationOverride,
    val forecastGeneratedAt: Instant,
    val forecastOffice: String,
    val gridX: Int,
    val gridY: Int,
    val forecastZone: String,
    val error: WeatherError? = null,
)
