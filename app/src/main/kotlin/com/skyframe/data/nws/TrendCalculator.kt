package com.skyframe.data.nws

import com.skyframe.domain.Trend
import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import kotlinx.datetime.Instant

/**
 * Linear-regression trend over up to 6 (value, timestamp) observations.
 * Ported from _reference/server/nws/trends.ts.
 *
 * Algorithm: ordinary least-squares slope. Confidence is OK when there
 * are 3 or more observations, MISSING otherwise. Direction is UP/DOWN
 * when |slope| exceeds steadyThreshold, STEADY when within.
 */
object TrendCalculator {

    fun compute(
        observations: List<Pair<Double, Instant>>,
        steadyThreshold: Double = 0.5,
    ): Trend {
        if (observations.isEmpty()) {
            return Trend(TrendDirection.STEADY, 0.0, TrendConfidence.MISSING)
        }
        if (observations.size == 1) {
            return Trend(TrendDirection.STEADY, 0.0, TrendConfidence.MISSING)
        }

        // Convert timestamps to hours-since-first-observation.
        val first = observations.first().second
        val points = observations.map { (v, t) ->
            val hoursElapsed = (t.epochSeconds - first.epochSeconds).toDouble() / 3600.0
            hoursElapsed to v
        }

        // OLS slope: sum((x - mean_x)(y - mean_y)) / sum((x - mean_x)^2)
        val xMean = points.sumOf { it.first } / points.size
        val yMean = points.sumOf { it.second } / points.size
        val numerator = points.sumOf { (x, y) -> (x - xMean) * (y - yMean) }
        val denominator = points.sumOf { (x, _) -> (x - xMean) * (x - xMean) }
        val slope = if (denominator == 0.0) 0.0 else numerator / denominator

        val direction = when {
            slope > steadyThreshold -> TrendDirection.UP
            slope < -steadyThreshold -> TrendDirection.DOWN
            else -> TrendDirection.STEADY
        }
        val confidence = if (observations.size >= 3) TrendConfidence.OK else TrendConfidence.MISSING

        return Trend(direction, slope, confidence)
    }
}
