package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class TempUnit { FAHRENHEIT, CELSIUS }

@Serializable
enum class TrendDirection {
    @SerialName("up") UP,
    @SerialName("down") DOWN,
    @SerialName("steady") STEADY,
}

@Serializable
enum class TrendConfidence {
    @SerialName("ok") OK,
    @SerialName("missing") MISSING,
}

@Serializable
data class Trend(
    val direction: TrendDirection,
    val deltaPerHour: Double,
    val confidence: TrendConfidence,
)

/**
 * Pure unit conversions matching _reference/shared/units.ts behavior.
 *
 * Server-side normalization already emits temperatures in F, speeds in
 * mph, and pressures in inHg — these helpers exist for client-side
 * display unit toggles (NowScreen hero temp tap), not for raw NWS input.
 */
object Units {

    fun convertTempF(valueF: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.FAHRENHEIT -> valueF
        TempUnit.CELSIUS -> (valueF - 32.0) * 5.0 / 9.0
    }

    /** NWS observations expose wind speed in m/s; the normalizer converts to mph. */
    fun metersPerSecondToMph(mps: Double): Double = mps * 2.2369362921

    /** NWS observations expose pressure in Pa; the normalizer converts to inHg. */
    fun pascalsToInchesHg(pa: Double): Double = pa / 3386.389

    /**
     * Rescales a temperature trend when the user toggles F to C. Direction
     * and confidence are unit-agnostic; only deltaPerHour scales.
     */
    fun scaleTempTrend(trend: Trend, unit: TempUnit): Trend = when (unit) {
        TempUnit.FAHRENHEIT -> trend
        TempUnit.CELSIUS -> trend.copy(deltaPerHour = trend.deltaPerHour * 5.0 / 9.0)
    }
}
