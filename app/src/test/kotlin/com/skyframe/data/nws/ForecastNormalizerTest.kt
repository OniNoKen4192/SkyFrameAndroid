package com.skyframe.data.nws

import com.skyframe.domain.IconCode
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ForecastNormalizerTest {

    @Test
    fun `hourly normalization filters past hours`() {
        val now = Instant.parse("2026-05-16T14:30:00Z")
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                hourly(1, "2026-05-16T13:00:00Z", "2026-05-16T14:00:00Z", true, 70, "rain", 80),
                hourly(2, "2026-05-16T14:00:00Z", "2026-05-16T15:00:00Z", true, 71, "few", 5),
                hourly(3, "2026-05-16T15:00:00Z", "2026-05-16T16:00:00Z", true, 72, "few", 5),
            ),
        ))
        val result = ForecastNormalizer.normalizeHourly(dto, now)
        // Period ending before 'now' is dropped
        assertEquals(2, result.size)
        assertEquals(71.0, result[0].tempF)
    }

    @Test
    fun `daily normalization pairs day and night periods`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                daily(1, "Today", "2026-05-16T06:00:00-05:00", "2026-05-16T18:00:00-05:00", true, 75, "Sunny", "few"),
                daily(2, "Tonight", "2026-05-16T18:00:00-05:00", "2026-05-17T06:00:00-05:00", false, 55, "Clear", "skc"),
                daily(3, "Sunday", "2026-05-17T06:00:00-05:00", "2026-05-17T18:00:00-05:00", true, 78, "Sunny", "few"),
                daily(4, "Sunday Night", "2026-05-17T18:00:00-05:00", "2026-05-18T06:00:00-05:00", false, 58, "Clear", "skc"),
            ),
        ))
        val result = ForecastNormalizer.normalizeDaily(dto)
        assertEquals(2, result.size)
        assertEquals(75, result[0].highF)
        assertEquals(55, result[0].lowF)
        assertEquals("Today", result[0].dayPeriodName)
        assertEquals("Tonight", result[0].nightPeriodName)
        assertEquals(78, result[1].highF)
        assertEquals("Sunday", result[1].dayPeriodName)
    }

    @Test
    fun `precipitation probability extracted from period`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                hourly(1, "2026-05-16T14:00:00Z", "2026-05-16T15:00:00Z", true, 70, "rain", 80),
            ),
        ))
        val result = ForecastNormalizer.normalizeHourly(dto, Instant.parse("2026-05-16T14:00:00Z"))
        assertEquals(80, result[0].precipProbPct)
    }

    @Test
    fun `daily upgrade icon promotes few-clouds to rain at high precip`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                daily(1, "Today", "2026-05-16T06:00:00-05:00", "2026-05-16T18:00:00-05:00", true, 75, "Chance Rain Showers", "few", precip = 60),
                daily(2, "Tonight", "2026-05-16T18:00:00-05:00", "2026-05-17T06:00:00-05:00", false, 55, "Clear", "skc", precip = 0),
            ),
        ))
        val result = ForecastNormalizer.normalizeDaily(dto)
        assertEquals(IconCode.RAIN, result[0].iconCode)
    }

    private fun hourly(num: Int, start: String, end: String, isDay: Boolean, temp: Int, code: String, precip: Int): ForecastPeriodDto =
        ForecastPeriodDto(
            number = num, name = "", startTime = start, endTime = end,
            isDaytime = isDay, temperature = temp, temperatureUnit = "F",
            windSpeed = "5 mph", windDirection = "S",
            icon = "https://api.weather.gov/icons/land/${if (isDay) "day" else "night"}/$code,${precip}?size=medium",
            shortForecast = "Test", detailedForecast = null,
            probabilityOfPrecipitation = ProbabilityOfPrecipitationDto(precip),
        )

    private fun daily(num: Int, name: String, start: String, end: String, isDay: Boolean, temp: Int, short: String, code: String, precip: Int = 5): ForecastPeriodDto =
        ForecastPeriodDto(
            number = num, name = name, startTime = start, endTime = end,
            isDaytime = isDay, temperature = temp, temperatureUnit = "F",
            windSpeed = "5 mph", windDirection = "S",
            icon = "https://api.weather.gov/icons/land/${if (isDay) "day" else "night"}/$code,${precip}?size=medium",
            shortForecast = short, detailedForecast = "$short detailed",
            probabilityOfPrecipitation = ProbabilityOfPrecipitationDto(precip),
        )
}
