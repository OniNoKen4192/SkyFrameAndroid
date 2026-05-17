package com.skyframe.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeocoderTest {

    private fun mockClient(content: String, status: HttpStatusCode = HttpStatusCode.OK): Pair<HttpClient, MutableList<String>> {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            urls += req.url.toString()
            respond(
                content = ByteReadChannel(content),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return client to urls
    }

    @Test
    fun `geocodeZip returns first result's lat lon`() = runTest {
        val (client, _) = mockClient("""[{"lat":"42.8744","lon":"-87.8633","display_name":"Oak Creek, WI, USA"}]""")
        val result = Geocoder(client).geocodeZip("53154")
        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
    }

    @Test
    fun `geocodeZip URL includes country=US and limit=1`() = runTest {
        val (client, urls) = mockClient("""[{"lat":"42.8744","lon":"-87.8633"}]""")
        Geocoder(client).geocodeZip("53154")
        val url = urls[0]
        assertTrue(url.contains("postalcode=53154"))
        assertTrue(url.contains("country=US"))
        assertTrue(url.contains("limit=1"))
        assertTrue(url.contains("format=json"))
    }

    @Test
    fun `empty results throws GeocodingException`() = runTest {
        val (client, _) = mockClient("[]")
        val exc = assertThrows(GeocodingException::class.java) {
            kotlinx.coroutines.runBlocking { Geocoder(client).geocodeZip("00000") }
        }
        assertTrue(exc.message!!.contains("No results"))
    }

    @Test
    fun `non-200 response throws GeocodingException`() = runTest {
        val (client, _) = mockClient("Service Unavailable", status = HttpStatusCode.ServiceUnavailable)
        assertThrows(GeocodingException::class.java) {
            kotlinx.coroutines.runBlocking { Geocoder(client).geocodeZip("53154") }
        }
    }
}
