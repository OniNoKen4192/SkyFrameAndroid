package com.skyframe.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class UnitsTest {

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.01) {
        assert(abs(expected - actual) < epsilon) {
            "Expected $expected ± $epsilon but got $actual (diff ${abs(expected - actual)})"
        }
    }

    @Test
    fun `Fahrenheit to Fahrenheit is identity`() {
        assertClose(72.0, Units.convertTempF(72.0, TempUnit.FAHRENHEIT))
        assertClose(-40.0, Units.convertTempF(-40.0, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `Fahrenheit to Celsius uses 5_9 conversion`() {
        assertClose(0.0, Units.convertTempF(32.0, TempUnit.CELSIUS))
        assertClose(100.0, Units.convertTempF(212.0, TempUnit.CELSIUS))
        assertClose(-40.0, Units.convertTempF(-40.0, TempUnit.CELSIUS))
        assertClose(22.22, Units.convertTempF(72.0, TempUnit.CELSIUS))
    }

    @Test
    fun `meters per second to mph multiplies by 2_237`() {
        assertClose(22.37, Units.metersPerSecondToMph(10.0))
        assertClose(0.0, Units.metersPerSecondToMph(0.0))
        assertClose(33.55, Units.metersPerSecondToMph(15.0))
    }

    @Test
    fun `Pascals to inches of mercury divides by 3386_39`() {
        assertClose(29.92, Units.pascalsToInchesHg(101325.0))
        assertClose(0.0, Units.pascalsToInchesHg(0.0))
    }

    @Test
    fun `temperature trend rescaled to Celsius scales deltaPerHour by 5_9`() {
        val trendF = Trend(TrendDirection.UP, deltaPerHour = 9.0, confidence = TrendConfidence.OK)
        val trendC = Units.scaleTempTrend(trendF, TempUnit.CELSIUS)
        assertClose(5.0, trendC.deltaPerHour)
        assertEquals(TrendDirection.UP, trendC.direction)
        assertEquals(TrendConfidence.OK, trendC.confidence)
    }

    @Test
    fun `temperature trend in Fahrenheit is unchanged`() {
        val trend = Trend(TrendDirection.DOWN, deltaPerHour = -2.5, confidence = TrendConfidence.OK)
        assertEquals(trend, Units.scaleTempTrend(trend, TempUnit.FAHRENHEIT))
    }
}
