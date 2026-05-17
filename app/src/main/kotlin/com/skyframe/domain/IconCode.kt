package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IconCode {
    @SerialName("sun") SUN,
    @SerialName("moon") MOON,
    @SerialName("partly-day") PARTLY_DAY,
    @SerialName("partly-night") PARTLY_NIGHT,
    @SerialName("cloud") CLOUD,
    @SerialName("rain") RAIN,
    @SerialName("snow") SNOW,
    @SerialName("thunder") THUNDER,
    @SerialName("fog") FOG,
}
