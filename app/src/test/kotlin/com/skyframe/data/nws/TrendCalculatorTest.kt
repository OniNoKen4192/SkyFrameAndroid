package com.skyframe.data.nws

import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrendCalculatorTest {

    private fun ts(hourOffset: Int): Instant =
        Instant.fromEpochSeconds(1_000_000L + hourOffset * 3600L)

    @Test
    fun `single observation yields steady missing-confidence trend`() {
        val obs = listOf(70.0 to ts(0))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
        assertEquals(TrendConfidence.MISSING, trend.confidence)
    }

    @Test
    fun `two observations rising at 1 degree per hour returns UP`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.UP, trend.direction)
        assertTrue(trend.deltaPerHour > 0.5, "Expected positive deltaPerHour, got ${trend.deltaPerHour}")
    }

    @Test
    fun `falling values return DOWN`() {
        val obs = listOf(70.0 to ts(0), 68.0 to ts(1), 66.0 to ts(2))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.DOWN, trend.direction)
        assertEquals(TrendConfidence.OK, trend.confidence)
    }

    @Test
    fun `flat values return STEADY`() {
        val obs = listOf(70.0 to ts(0), 70.0 to ts(1), 70.0 to ts(2), 70.0 to ts(3))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
    }

    @Test
    fun `small fluctuations under threshold return STEADY`() {
        val obs = listOf(70.0 to ts(0), 70.2 to ts(1), 70.1 to ts(2), 70.3 to ts(3))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
    }

    @Test
    fun `confidence is OK with 3+ observations`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1), 72.0 to ts(2))
        assertEquals(TrendConfidence.OK, TrendCalculator.compute(obs).confidence)
    }

    @Test
    fun `confidence is MISSING with fewer than 3 observations`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1))
        assertEquals(TrendConfidence.MISSING, TrendCalculator.compute(obs).confidence)
    }

    @Test
    fun `empty input returns steady missing`() {
        val trend = TrendCalculator.compute(emptyList())
        assertEquals(TrendDirection.STEADY, trend.direction)
        assertEquals(TrendConfidence.MISSING, trend.confidence)
        assertEquals(0.0, trend.deltaPerHour)
    }
}
