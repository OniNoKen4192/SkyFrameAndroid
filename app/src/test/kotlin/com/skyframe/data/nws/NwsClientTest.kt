package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NwsClientTest {

    private fun mockClient(responder: (String) -> String): Pair<HttpClient, MutableList<String>> {
        val capturedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            capturedUrls += req.url.toString()
            respond(
                content = ByteReadChannel(responder(req.url.toString())),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { header(HttpHeaders.UserAgent, "SkyFrameTest/0.1") }
        }
        return client to capturedUrls
    }

    @Test
    fun `points builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"gridId":"MKX","gridX":88,"gridY":58,"timeZone":"America/Chicago","forecastZone":"https://api.weather.gov/zones/forecast/WIZ066","observationStations":"https://api.weather.gov/gridpoints/MKX/88,58/stations","relativeLocation":{"properties":{"city":"Oak Creek","state":"WI"}}}}"""
        }
        val nws = NwsClient(client)
        nws.points(42.8744, -87.8633)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("/points/42.8744,-87.8633"), "Expected /points URL, got ${urls[0]}")
    }

    @Test
    fun `points URL uses dot decimal separator even on comma-locale JVM`() = runTest {
        // Regression: %.4f.format(d) used to honor JVM default Locale, producing
        // "42,8744,-87,8633" on de_DE which NWS rejects with 400.
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val (client, urls) = mockClient {
                """{"properties":{"gridId":"MKX","gridX":88,"gridY":58,"timeZone":"America/Chicago","forecastZone":"https://api.weather.gov/zones/forecast/WIZ066","observationStations":"https://api.weather.gov/gridpoints/MKX/88,58/stations","relativeLocation":{"properties":{"city":"Oak Creek","state":"WI"}}}}"""
            }
            val nws = NwsClient(client)
            nws.points(42.8744, -87.8633)
            assertTrue(urls[0].contains("/points/42.8744,-87.8633"), "Expected dot-decimal lat/lon, got ${urls[0]}")
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `latestObservation builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"station":"https://api.weather.gov/stations/KMKE","timestamp":"2026-05-16T12:00:00+00:00"}}"""
        }
        val nws = NwsClient(client)
        nws.latestObservation("KMKE")
        assertTrue(urls[0].contains("/stations/KMKE/observations/latest"))
    }

    @Test
    fun `activeAlerts builds expected URL`() = runTest {
        val (client, urls) = mockClient { """{"features":[]}""" }
        val nws = NwsClient(client)
        nws.activeAlerts(42.8744, -87.8633)
        assertTrue(urls[0].contains("/alerts/active?point=42.8744,-87.8633"))
    }

    @Test
    fun `forecast builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"generatedAt":"2026-05-16T12:00:00+00:00","periods":[]}}"""
        }
        val nws = NwsClient(client)
        nws.forecast("MKX", 88, 58)
        assertTrue(urls[0].contains("/gridpoints/MKX/88,58/forecast"))
        assertTrue(!urls[0].contains("/forecast/hourly"))
    }

    @Test
    fun `hourlyForecast builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"generatedAt":"2026-05-16T12:00:00+00:00","periods":[]}}"""
        }
        val nws = NwsClient(client)
        nws.hourlyForecast("MKX", 88, 58)
        assertTrue(urls[0].contains("/gridpoints/MKX/88,58/forecast/hourly"))
    }
}
