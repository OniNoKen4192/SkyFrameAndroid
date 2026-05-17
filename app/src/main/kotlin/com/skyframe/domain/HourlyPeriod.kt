package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HourlyPeriod(
    val startTime: Instant,
    val hourLabel: String,
    val tempF: Double,
    val iconCode: IconCode,
    val precipProbPct: Int,
    val wind: Wind,
    val shortDescription: String,
)
