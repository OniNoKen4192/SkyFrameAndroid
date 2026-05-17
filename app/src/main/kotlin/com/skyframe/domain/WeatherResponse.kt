package com.skyframe.domain

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val current: CurrentConditions,
    val hourly: List<HourlyPeriod>,
    val daily: List<DailyPeriod>,
    val alerts: List<Alert>,
    val meta: WeatherMeta,
)
