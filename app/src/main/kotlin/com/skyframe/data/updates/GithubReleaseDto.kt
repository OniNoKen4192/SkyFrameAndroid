package com.skyframe.data.updates

import kotlinx.serialization.Serializable

/**
 * Subset of the GitHub /repos/{owner}/{repo}/releases/latest response we care
 * about. Field names match GitHub's JSON exactly so kotlinx.serialization can
 * deserialize without translation maps. body may be null for releases with no
 * description.
 */
@Serializable
data class GithubReleaseDto(
    val tag_name: String,
    val html_url: String,
    val body: String? = null,
)
