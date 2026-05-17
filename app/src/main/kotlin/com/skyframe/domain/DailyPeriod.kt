package com.skyframe.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DailyPeriod(
    val dateISO: LocalDate,
    val dayOfWeek: String,
    val dateLabel: String,
    val highF: Int,
    val lowF: Int,
    val iconCode: IconCode,
    val precipProbPct: Int,
    val shortDescription: String,
    val dayDetailedForecast: String?,
    val nightDetailedForecast: String?,
    val dayPeriodName: String?,
    val nightPeriodName: String?,
)
