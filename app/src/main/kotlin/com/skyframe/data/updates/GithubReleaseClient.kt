package com.skyframe.data.updates

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around GitHub's /repos/{owner}/{repo}/releases/latest endpoint.
 * Reuses the project's shared Ktor HttpClient (which carries the NWS-specific
 * User-Agent header — GitHub doesn't require any specific UA but accepts it
 * as good-citizen identification of the requesting app).
 *
 * Unauthenticated GitHub API allows 60 requests/hour, far above the once-per-
 * 24h throttle UpdateCheckRepository enforces.
 */
@Singleton
class GithubReleaseClient @Inject constructor(private val http: HttpClient) {

    private val url = "https://api.github.com/repos/OniNoKen4192/SkyFrameAndroid/releases/latest"

    suspend fun latestRelease(): GithubReleaseDto = http.get(url).body()
}
