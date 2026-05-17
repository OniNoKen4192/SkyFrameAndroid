package com.skyframe.data.nws

import kotlinx.serialization.Serializable

// --------- /points/{lat},{lon} ---------

@Serializable
data class PointsDto(val properties: PointsProperties)

@Serializable
data class PointsProperties(
    val gridId: String,
    val gridX: Int,
    val gridY: Int,
    val timeZone: String,
    val forecastZone: String,
    val observationStations: String,
    val relativeLocation: RelativeLocation,
    val astronomicalData: AstronomicalDataDto? = null,
)

@Serializable
data class AstronomicalDataDto(
    val sunrise: String? = null,
    val sunset: String? = null,
)

@Serializable
data class RelativeLocation(val properties: RelativeLocationProperties)

@Serializable
data class RelativeLocationProperties(val city: String, val state: String)

// --------- /gridpoints/{office}/{x},{y}/forecast OR forecast/hourly ---------

@Serializable
data class ForecastDto(val properties: ForecastProperties)

@Serializable
data class ForecastProperties(
    val generatedAt: String,
    val periods: List<ForecastPeriodDto>,
)

@Serializable
data class ForecastPeriodDto(
    val number: Int,
    val name: String,
    val startTime: String,
    val endTime: String,
    val isDaytime: Boolean,
    val temperature: Int,
    val temperatureUnit: String,
    val windSpeed: String,
    val windDirection: String,
    val icon: String? = null,
    val shortForecast: String,
    val detailedForecast: String? = null,
    val probabilityOfPrecipitation: ProbabilityOfPrecipitationDto? = null,
)

@Serializable
data class ProbabilityOfPrecipitationDto(val value: Int? = null)

// --------- /stations/{id}/observations/latest ---------

@Serializable
data class ObservationDto(val properties: ObservationProperties)

@Serializable
data class ObservationProperties(
    val station: String,
    val timestamp: String,
    val textDescription: String? = null,
    val icon: String? = null,
    val temperature: NumberMeasurementDto? = null,
    val windSpeed: NumberMeasurementDto? = null,
    val windDirection: NumberMeasurementDto? = null,
    val windGust: NumberMeasurementDto? = null,
    val barometricPressure: NumberMeasurementDto? = null,
    val visibility: NumberMeasurementDto? = null,
    val relativeHumidity: NumberMeasurementDto? = null,
    val dewpoint: NumberMeasurementDto? = null,
    val heatIndex: NumberMeasurementDto? = null,
    val windChill: NumberMeasurementDto? = null,
)

/** NWS measurement values come as { value: Double?, unitCode: String }. */
@Serializable
data class NumberMeasurementDto(val value: Double? = null, val unitCode: String? = null)

// --------- /alerts/active?point={lat},{lon} ---------

@Serializable
data class AlertsDto(val features: List<AlertFeatureDto>)

@Serializable
data class AlertFeatureDto(val id: String, val properties: AlertProperties)

@Serializable
data class AlertProperties(
    val id: String,
    val event: String,
    val severity: String,
    val headline: String? = null,
    val description: String,
    /** NWS sometimes omits `sent`; AlertNormalizer falls back to `effective`. */
    val sent: String? = null,
    val effective: String,
    val expires: String,
    val areaDesc: String,
    val parameters: Map<String, List<String>> = emptyMap(),
)

// --------- /stations list (for setup) ---------

@Serializable
data class StationsListDto(val features: List<StationFeatureDto>)

@Serializable
data class StationFeatureDto(val properties: StationFeatureProperties)

@Serializable
data class StationFeatureProperties(val stationIdentifier: String)
