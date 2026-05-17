package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AlertSeverity {
    @SerialName("Extreme") EXTREME,
    @SerialName("Severe") SEVERE,
    @SerialName("Moderate") MODERATE,
    @SerialName("Minor") MINOR,
    @SerialName("Unknown") UNKNOWN,
}

@Serializable
data class Alert(
    val id: String,
    val event: String,
    val tier: AlertTier,
    val severity: AlertSeverity,
    val headline: String,
    val description: String,
    val issuedAt: Instant,
    val effective: Instant,
    val expires: Instant,
    val areaDesc: String,
)
