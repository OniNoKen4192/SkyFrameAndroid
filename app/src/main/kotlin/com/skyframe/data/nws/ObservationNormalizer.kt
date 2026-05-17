package com.skyframe.data.nws

import com.skyframe.domain.ConditionTrends
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.IconCode
import com.skyframe.domain.Wind
import kotlinx.datetime.Instant

/**
 * Converts an NWS observation + recent observation history to the typed
 * CurrentConditions domain model. Trend computation uses the supplied
 * observation history (up to 6 recent observations per metric).
 */
object ObservationNormalizer {

    fun normalize(
        latest: ObservationDto,
        recentObservations: List<ObservationDto>,
        stationDistanceKm: Double,
        sunrise: Instant?,
        sunset: Instant?,
        precipOutlook: String,
        isDay: Boolean,
    ): CurrentConditions {
        val props = latest.properties

        val tempF = NormalizerHelpers.toFahrenheit(props.temperature)
            ?: 0.0  // server fallback; UI shows "--" when data is null
        val feelsLikeF = NormalizerHelpers.toFahrenheit(props.heatIndex)
            ?: NormalizerHelpers.toFahrenheit(props.windChill)
            ?: tempF

        val windSpeedMph = NormalizerHelpers.toMph(props.windSpeed) ?: 0.0
        val windDirDeg = NormalizerHelpers.toDegrees(props.windDirection) ?: 0.0

        return CurrentConditions(
            observedAt = Instant.parse(props.timestamp),
            stationId = props.station.substringAfterLast('/'),
            stationDistanceKm = stationDistanceKm,
            tempF = tempF,
            feelsLikeF = feelsLikeF,
            conditionText = props.textDescription.orEmpty(),
            iconCode = if (props.icon == null) IconCode.CLOUD else IconMapper.fromUrl(props.icon),
            precipOutlook = precipOutlook,
            humidityPct = NormalizerHelpers.toPercent(props.relativeHumidity),
            pressureInHg = NormalizerHelpers.toInHg(props.barometricPressure),
            visibilityMi = NormalizerHelpers.toMiles(props.visibility),
            dewpointF = NormalizerHelpers.toFahrenheit(props.dewpoint),
            wind = Wind(
                speedMph = windSpeedMph,
                directionDeg = windDirDeg,
                cardinal = NormalizerHelpers.cardinalFor(windDirDeg),
            ),
            trends = computeAllTrends(recentObservations),
            sunrise = sunrise ?: Instant.fromEpochSeconds(0),
            sunset = sunset ?: Instant.fromEpochSeconds(0),
        )
    }

    private fun computeAllTrends(history: List<ObservationDto>): ConditionTrends {
        val tempHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toFahrenheit(obs.properties.temperature)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val windHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toMph(obs.properties.windSpeed)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val humidityHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toPercent(obs.properties.relativeHumidity)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val pressureHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toInHg(obs.properties.barometricPressure)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val visibilityHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toMiles(obs.properties.visibility)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val dewpointHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toFahrenheit(obs.properties.dewpoint)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }

        return ConditionTrends(
            temp = TrendCalculator.compute(tempHistory),
            wind = TrendCalculator.compute(windHistory),
            humidity = TrendCalculator.compute(humidityHistory),
            pressure = TrendCalculator.compute(pressureHistory, steadyThreshold = 0.02),
            visibility = TrendCalculator.compute(visibilityHistory),
            dewpoint = TrendCalculator.compute(dewpointHistory),
        )
    }
}
