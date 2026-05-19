package com.skyframe.data.updates

/**
 * Cached "newer release available" state from UpdateCheckRepository.
 * Persisted across launches in DataStore until either:
 *  - the user installs the new version (cache cleared by maybeCheck() when
 *    the next poll sees current >= cached version)
 *  - the user disables the update-check checkbox (cache cleared in
 *    SettingsViewModel)
 */
data class UpdateAvailable(
    val version: String,    // "0.3.0" (without 'v' prefix)
    val htmlUrl: String,    // GitHub release URL
    val body: String,       // release notes (markdown-ish from GitHub)
)
