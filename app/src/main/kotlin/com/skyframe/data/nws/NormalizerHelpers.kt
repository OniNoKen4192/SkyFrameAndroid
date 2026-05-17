package com.skyframe.data.nws

import com.skyframe.domain.Units
import kotlin.math.roundToInt

internal object NormalizerHelpers {

    /**
     * Converts an NWS measurement value to the requested target unit based
     * on the unitCode. Returns null when the input is null or unrecognized.
     *
     * Supported unitCodes (subset NWS actually returns):
     *   wmoUnit:degC, wmoUnit:degF
     *   wmoUnit:km_h-1, wmoUnit:m_s-1
     *   wmoUnit:Pa
     *   wmoUnit:m
     *   wmoUnit:percent
     *   wmoUnit:degree_(angle)
     */
    fun toFahrenheit(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:degC" -> v * 9.0 / 5.0 + 32.0
            "wmoUnit:degF" -> v
            else -> null
        }
    }

    fun toMph(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:km_h-1" -> v * 0.6213711922
            "wmoUnit:m_s-1" -> Units.metersPerSecondToMph(v)
            else -> null
        }
    }

    fun toInHg(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:Pa" -> Units.pascalsToInchesHg(v)
            else -> null
        }
    }

    fun toMiles(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:m" -> v / 1609.344
            else -> null
        }
    }

    fun toPercent(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:percent" -> v
            else -> null
        }
    }

    fun toDegrees(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:degree_(angle)" -> v
            else -> null
        }
    }

    /** Compass direction to 16-point cardinal. */
    fun cardinalFor(deg: Double): String {
        if (deg.isNaN()) return ""
        val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        val idx = ((deg / 22.5).roundToInt() % 16 + 16) % 16
        return dirs[idx]
    }

    /**
     * Heuristic: an observation is stale if older than 90 minutes or has
     * null core fields (temperature). Triggers station fallback.
     */
    fun isObservationStale(
        timestampEpochMs: Long,
        nowEpochMs: Long,
        temperatureF: Double?,
    ): Boolean {
        val ageMs = nowEpochMs - timestampEpochMs
        if (ageMs > 90 * 60 * 1000L) return true
        if (temperatureF == null) return true
        return false
    }
}
