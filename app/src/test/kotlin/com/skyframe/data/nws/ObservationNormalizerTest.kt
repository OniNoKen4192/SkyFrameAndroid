package com.skyframe.data.nws

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ObservationNormalizerTest {

    private fun assertClose(expected: Double, actual: Double?, epsilon: Double = 0.01) {
        require(actual != null) { "Expected $expected ± $epsilon but got null" }
        assert(abs(expected - actual) < epsilon) { "Expected $expected ± $epsilon but got $actual" }
    }

    @Test
    fun `convert NWS celsius temperature to fahrenheit`() {
        val measurement = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC")
        assertClose(71.6, NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `degF stays degF`() {
        val measurement = NumberMeasurementDto(value = 72.0, unitCode = "wmoUnit:degF")
        assertClose(72.0, NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `null value returns null`() {
        val measurement = NumberMeasurementDto(value = null, unitCode = "wmoUnit:degC")
        assertNull(NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `unknown unit code returns null`() {
        val measurement = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:K")
        assertNull(NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `km_h-1 to mph conversion`() {
        val measurement = NumberMeasurementDto(value = 16.0934, unitCode = "wmoUnit:km_h-1")
        assertClose(10.0, NormalizerHelpers.toMph(measurement))
    }

    @Test
    fun `m_s-1 to mph conversion`() {
        val measurement = NumberMeasurementDto(value = 10.0, unitCode = "wmoUnit:m_s-1")
        assertClose(22.37, NormalizerHelpers.toMph(measurement))
    }

    @Test
    fun `Pa to inHg conversion`() {
        val measurement = NumberMeasurementDto(value = 101325.0, unitCode = "wmoUnit:Pa")
        assertClose(29.92, NormalizerHelpers.toInHg(measurement))
    }

    @Test
    fun `meters to miles conversion`() {
        val measurement = NumberMeasurementDto(value = 16093.44, unitCode = "wmoUnit:m")
        assertClose(10.0, NormalizerHelpers.toMiles(measurement))
    }

    @Test
    fun `cardinal for N`() {
        assertEquals("N", NormalizerHelpers.cardinalFor(0.0))
        assertEquals("N", NormalizerHelpers.cardinalFor(360.0))
    }

    @Test
    fun `cardinal for cardinal points`() {
        assertEquals("E", NormalizerHelpers.cardinalFor(90.0))
        assertEquals("S", NormalizerHelpers.cardinalFor(180.0))
        assertEquals("W", NormalizerHelpers.cardinalFor(270.0))
        assertEquals("NE", NormalizerHelpers.cardinalFor(45.0))
        assertEquals("SSE", NormalizerHelpers.cardinalFor(157.5))
    }

    @Test
    fun `observation older than 90 minutes is stale`() {
        assertEquals(true, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 91 * 60 * 1000L,
            temperatureF = 70.0,
        ))
    }

    @Test
    fun `observation with null temperature is stale`() {
        assertEquals(true, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 1000L,
            temperatureF = null,
        ))
    }

    @Test
    fun `fresh observation with temperature is not stale`() {
        assertEquals(false, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 60 * 60 * 1000L,
            temperatureF = 70.0,
        ))
    }
}
