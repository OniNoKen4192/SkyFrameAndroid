package com.skyframe.data.geocoding

import kotlinx.serialization.Serializable

@Serializable
data class NominatimResult(
    val lat: String,
    val lon: String,
    val display_name: String? = null,
)
