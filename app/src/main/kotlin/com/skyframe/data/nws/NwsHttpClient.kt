package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object NwsHttpClient {
    /**
     * @param userAgentProvider invoked at every request so the configured
     *   email is always current. NWS requires the User-Agent header to identify
     *   the app and a contact email; baking it once at client construction
     *   meant the header stayed at the pre-onboarding placeholder until
     *   process restart.
     */
    fun create(userAgentProvider: () -> String): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            })
        }
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, userAgentProvider())
            header(HttpHeaders.Accept, "application/geo+json,application/ld+json,application/json;q=0.9")
        }
    }
}
