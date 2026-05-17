package com.skyframe.domain

import kotlinx.serialization.Serializable

@Serializable
data class Wind(
    val speedMph: Double,
    val directionDeg: Double,
    val cardinal: String,
)
