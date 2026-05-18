package com.skyframe.data.nws

import com.skyframe.data.cache.WeatherCache
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.StationOverride
import com.skyframe.domain.WeatherError
import com.skyframe.domain.WeatherResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherNormalizerTest {

    // ---------- fixtures ----------

    private fun snapshot(
        primary: String = "KMKE",
        fallback: String = "KRAC",
        override: StationOverride = StationOverride.AUTO,
    ) = SettingsRepository.Snapshot(
        email = "test@example.com",
        lat = 42.8744,
        lon = -87.8633,
        locationName = "OAK CREEK WI",
        forecastOffice = "MKX",
        gridX = 88,
        gridY = 58,
        timezone = "America/Chicago",
        forecastZone = "WIZ066",
        stationPrimary = primary,
        stationFallback = fallback,
        stationOverride = override,
    )

    private fun fakePointsDto() = PointsDto(
        properties = PointsProperties(
            gridId = "MKX",
            gridX = 88,
            gridY = 58,
            timeZone = "America/Chicago",
            forecastZone = "https://api.weather.gov/zones/forecast/WIZ066",
            observationStations = "https://api.weather.gov/gridpoints/MKX/88,58/stations",
            relativeLocation = RelativeLocation(RelativeLocationProperties("Oak Creek", "WI")),
            astronomicalData = AstronomicalDataDto(
                sunrise = "2026-05-17T05:30:00-05:00",
                sunset = "2026-05-17T20:15:00-05:00",
            ),
        )
    )

    private fun fakeForecastDto() = ForecastDto(
        properties = ForecastProperties(
            generatedAt = "2026-05-17T14:00:00Z",
            periods = listOf(
                ForecastPeriodDto(
                    number = 1, name = "Today",
                    startTime = "2026-05-17T06:00:00-05:00",
                    endTime = "2026-05-17T18:00:00-05:00",
                    isDaytime = true, temperature = 75, temperatureUnit = "F",
                    windSpeed = "5 mph", windDirection = "S",
                    icon = "https://api.weather.gov/icons/land/day/few?size=medium",
                    shortForecast = "Sunny",
                ),
                ForecastPeriodDto(
                    number = 2, name = "Tonight",
                    startTime = "2026-05-17T18:00:00-05:00",
                    endTime = "2026-05-18T06:00:00-05:00",
                    isDaytime = false, temperature = 55, temperatureUnit = "F",
                    windSpeed = "5 mph", windDirection = "S",
                    icon = "https://api.weather.gov/icons/land/night/skc?size=medium",
                    shortForecast = "Clear",
                ),
            ),
        )
    )

    // Anchor observation timestamps to "now" so the >90min staleness check
    // produces the expected fresh/stale behavior regardless of when the test
    // runs (a hardcoded ISO timestamp goes stale the moment it's >90min old).
    private fun freshObservationDto(stationId: String = "KMKE") = ObservationDto(
        properties = ObservationProperties(
            station = "https://api.weather.gov/stations/$stationId",
            // 5 minutes ago - well within the 90-min freshness window
            timestamp = kotlinx.datetime.Clock.System.now()
                .minus(kotlin.time.Duration.parse("PT5M")).toString(),
            textDescription = "Sunny",
            icon = "https://api.weather.gov/icons/land/day/skc?size=medium",
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
            relativeHumidity = NumberMeasurementDto(value = 45.0, unitCode = "wmoUnit:percent"),
        )
    )

    private fun staleObservationDto(stationId: String = "KMKE") = ObservationDto(
        properties = ObservationProperties(
            station = "https://api.weather.gov/stations/$stationId",
            // 2 hours ago - exceeds the 90-min staleness threshold
            timestamp = kotlinx.datetime.Clock.System.now()
                .minus(kotlin.time.Duration.parse("PT2H")).toString(),
            temperature = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC"),
        )
    )

    private fun emptyAlertsDto() = AlertsDto(features = emptyList())
    private fun emptyObservationsListDto() = ObservationsListDto(features = emptyList())

    private fun mockSettings(snap: SettingsRepository.Snapshot): SettingsRepository {
        val s = mockk<SettingsRepository>()
        coEvery { s.snapshot() } returns snap
        return s
    }

    // ---------- tests ----------

    @Test
    fun `happy path with fresh primary station populates all fields`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val settings = mockSettings(snapshot())
        val normalizer = WeatherNormalizer(nws, settings, cache)

        val result = normalizer.load()

        assertEquals("KMKE", result.meta.stationId)
        assertEquals(StationOverride.AUTO, result.meta.stationOverride)
        assertNull(result.meta.error, "happy path should have no meta.error")
        assertEquals(0, result.alerts.size)
    }

    @Test
    fun `stale primary observation falls back to secondary and sets STATION_FALLBACK`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns staleObservationDto("KMKE")
        coEvery { nws.latestObservation("KRAC") } returns freshObservationDto("KRAC")
        coEvery { nws.recentObservations("KRAC", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        assertEquals("KRAC", result.meta.stationId)
        assertEquals(WeatherError.STATION_FALLBACK, result.meta.error)
    }

    @Test
    fun `force-secondary override skips primary entirely`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KRAC") } returns freshObservationDto("KRAC")
        coEvery { nws.recentObservations("KRAC", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(
            nws,
            mockSettings(snapshot(override = StationOverride.FORCE_SECONDARY)),
            cache,
        )

        val result = normalizer.load()

        assertEquals("KRAC", result.meta.stationId)
        // Verify primary was never called by checking the mock has no record of it.
        io.mockk.coVerify(exactly = 0) { nws.latestObservation("KMKE") }
    }

    @Test
    fun `alerts fetch failure becomes meta error PARTIAL with empty alerts`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } throws RuntimeException("503 from /alerts/active")
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        // Forecast still rendered; alerts gone; meta.error flagged.
        assertEquals(WeatherError.PARTIAL, result.meta.error)
        assertEquals(0, result.alerts.size)
        assertNotNull(result.current)
    }

    @Test
    fun `history fetch failure leaves trends as MISSING confidence`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } throws RuntimeException("503 from /observations")

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val result = normalizer.load()

        // History failure does NOT escalate to meta.error - graceful degradation.
        assertNull(result.meta.error)
        // All trends should be MISSING confidence since history is empty.
        assertEquals(com.skyframe.domain.TrendConfidence.MISSING, result.current.trends.temp.confidence)
        assertEquals(com.skyframe.domain.TrendConfidence.MISSING, result.current.trends.humidity.confidence)
    }

    @Test
    fun `second load within TTL returns cached response with cacheHit=true`() = runTest {
        val nws = mockk<NwsClient>()
        coEvery { nws.points(any(), any()) } returns fakePointsDto()
        coEvery { nws.forecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.hourlyForecast(any(), any(), any()) } returns fakeForecastDto()
        coEvery { nws.activeAlerts(any(), any()) } returns emptyAlertsDto()
        coEvery { nws.latestObservation("KMKE") } returns freshObservationDto("KMKE")
        coEvery { nws.recentObservations("KMKE", any()) } returns emptyObservationsListDto()

        val cache = WeatherCache<WeatherResponse>()
        val normalizer = WeatherNormalizer(nws, mockSettings(snapshot()), cache)

        val first = normalizer.load()
        val second = normalizer.load()  // Within TTL → cache hit

        assertTrue(!first.meta.cacheHit, "first load should be a fresh fetch")
        assertTrue(second.meta.cacheHit, "second load within TTL should be a cache hit")
        io.mockk.coVerify(exactly = 1) { nws.points(any(), any()) }
    }
}
