package com.skyframe.data.nws

import com.skyframe.data.geocoding.GeoCoordinates
import com.skyframe.data.geocoding.Geocoder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SetupResolverTest {

    @Test
    fun `parses ZIP input via geocoder`() = runTest {
        val geo = mockk<Geocoder>()
        coEvery { geo.geocodeZip("53154") } returns GeoCoordinates(42.8744, -87.8633)
        val nws = mockk<NwsClient>()
        coEvery { nws.points(42.8744, -87.8633) } returns fakePointsDto()
        coEvery { nws.stationsList(any()) } returns StationsListDto(
            features = listOf(
                StationFeatureDto(StationFeatureProperties("KMKE")),
                StationFeatureDto(StationFeatureProperties("KRAC")),
            )
        )

        val result = SetupResolver(geo, nws).resolve("53154")

        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
        assertEquals("MKX", result.forecastOffice)
        assertEquals(88, result.gridX)
        assertEquals(58, result.gridY)
        assertEquals("America/Chicago", result.timezone)
        assertEquals("WIZ066", result.forecastZone)
        assertEquals("KMKE", result.primaryStation)
        assertEquals("KRAC", result.secondaryStation)
        assertEquals("OAK CREEK WI", result.locationName)
    }

    @Test
    fun `parses lat lon input directly without geocoding`() = runTest {
        val geo = mockk<Geocoder>()
        val nws = mockk<NwsClient>()
        coEvery { nws.points(42.8744, -87.8633) } returns fakePointsDto()
        coEvery { nws.stationsList(any()) } returns StationsListDto(
            features = listOf(StationFeatureDto(StationFeatureProperties("KMKE")))
        )

        val result = SetupResolver(geo, nws).resolve("42.8744, -87.8633")

        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
        assertEquals("KMKE", result.primaryStation)
        // Only one station available, so secondary falls back to primary
        assertEquals("KMKE", result.secondaryStation)
    }

    @Test
    fun `rejects invalid input`() {
        val geo = mockk<Geocoder>()
        val nws = mockk<NwsClient>()
        assertThrows(SetupException::class.java) {
            kotlinx.coroutines.runBlocking { SetupResolver(geo, nws).resolve("not-a-valid-input") }
        }
    }

    private fun fakePointsDto() = PointsDto(
        properties = PointsProperties(
            gridId = "MKX",
            gridX = 88,
            gridY = 58,
            timeZone = "America/Chicago",
            forecastZone = "https://api.weather.gov/zones/forecast/WIZ066",
            observationStations = "https://api.weather.gov/gridpoints/MKX/88,58/stations",
            relativeLocation = RelativeLocation(
                properties = RelativeLocationProperties("Oak Creek", "WI")
            ),
            astronomicalData = null,
        )
    )
}
