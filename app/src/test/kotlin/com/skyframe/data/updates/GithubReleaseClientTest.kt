package com.skyframe.data.updates

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GithubReleaseClientTest {

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
    fun `latestRelease builds expected URL`() = runTest {
        val (client, urls) = mockClient("""{"tag_name":"v0.3.0","html_url":"https://github.com/foo/bar/releases/tag/v0.3.0","body":"release notes"}""")
        GithubReleaseClient(client).latestRelease()
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("/repos/OniNoKen4192/SkyFrameAndroid/releases/latest"),
            "Expected /repos/OniNoKen4192/SkyFrameAndroid/releases/latest, got ${urls[0]}")
    }

    @Test
    fun `latestRelease parses tag_name html_url body`() = runTest {
        val (client, _) = mockClient("""{"tag_name":"v0.3.0","html_url":"https://github.com/foo/bar/releases/tag/v0.3.0","body":"release notes"}""")
        val release = GithubReleaseClient(client).latestRelease()
        assertEquals("v0.3.0", release.tag_name)
        assertEquals("https://github.com/foo/bar/releases/tag/v0.3.0", release.html_url)
        assertEquals("release notes", release.body)
    }

    @Test
    fun `null body is preserved as null`() = runTest {
        val (client, _) = mockClient("""{"tag_name":"v0.3.0","html_url":"https://github.com/foo/bar/releases/tag/v0.3.0","body":null}""")
        val release = GithubReleaseClient(client).latestRelease()
        assertEquals(null, release.body)
    }
}
