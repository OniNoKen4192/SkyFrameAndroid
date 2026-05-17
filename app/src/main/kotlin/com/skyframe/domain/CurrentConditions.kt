package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CurrentConditions(
    val observedAt: Instant,
    val stationId: String,
    val stationDistanceKm: Double,
    val tempF: Double,
    val feelsLikeF: Double,
    val conditionText: String,
    val iconCode: IconCode,
    val precipOutlook: String,
    val humidityPct: Double?,
    val pressureInHg: Double?,
    val visibilityMi: Double?,
    val dewpointF: Double?,
    val wind: Wind,
    val trends: ConditionTrends,
    val sunrise: Instant,
    val sunset: Instant,
)

@Serializable
data class ConditionTrends(
    val temp: Trend,
    val wind: Trend,
    val humidity: Trend,
    val pressure: Trend,
    val visibility: Trend,
    val dewpoint: Trend,
)
