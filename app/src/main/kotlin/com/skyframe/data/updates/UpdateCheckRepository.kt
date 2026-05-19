package com.skyframe.data.updates

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.skyframe.BuildConfig
import com.skyframe.data.install.InstallSource
import com.skyframe.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 24h-throttled foreground-only GitHub release poller. Gated on:
 *  - Install source must NOT be Play Store (Play handles updates for those users)
 *  - User's "Check GitHub for updates" checkbox must be enabled
 *  - At least 24h since last successful check
 *
 * Fire-and-forget: failures (network down, GitHub 503, parse error) swallowed
 * via runCatching; cache remains in whatever state it was before the failure.
 *
 * Cached UpdateAvailable persists in DataStore so the synthetic update alert
 * shows up immediately on launch (before the next poll) when one is queued.
 *
 * `now`, `currentVersion`, and `isFromPlayStoreOverride` are test seams
 * available only via the internal secondary constructor — the @Inject primary
 * constructor exposes only the Hilt-injectable dependencies (Dagger ignores
 * default values on @Inject params, so seams must be set imperatively).
 */
@Singleton
class UpdateCheckRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val settings: SettingsRepository,
    private val releaseClient: GithubReleaseClient,
) {
    private var now: () -> Instant = { Clock.System.now() }
    private var currentVersion: String = BuildConfig.VERSION_NAME
    private var isFromPlayStoreOverride: Boolean? = null

    internal constructor(
        context: Context,
        dataStore: DataStore<Preferences>,
        settings: SettingsRepository,
        releaseClient: GithubReleaseClient,
        now: () -> Instant,
        currentVersion: String,
        isFromPlayStoreOverride: Boolean?,
    ) : this(context, dataStore, settings, releaseClient) {
        this.now = now
        this.currentVersion = currentVersion
        this.isFromPlayStoreOverride = isFromPlayStoreOverride
    }

    private val lastCheckedKey = longPreferencesKey("update_check_last_at")
    private val cachedVersionKey = stringPreferencesKey("update_check_version")
    private val cachedUrlKey = stringPreferencesKey("update_check_url")
    private val cachedBodyKey = stringPreferencesKey("update_check_body")

    val available: Flow<UpdateAvailable?> = dataStore.data.map { prefs ->
        val v = prefs[cachedVersionKey]
        val u = prefs[cachedUrlKey]
        val b = prefs[cachedBodyKey]
        if (v != null && u != null && b != null) UpdateAvailable(v, u, b) else null
    }

    suspend fun currentAvailable(): UpdateAvailable? = available.first()

    suspend fun maybeCheck() {
        if (isFromPlayStore()) return
        if (!settings.snapshot().updateCheckEnabled) return

        val lastCheckedMs = dataStore.data.first()[lastCheckedKey] ?: 0L
        val nowMs = now().toEpochMilliseconds()
        if (nowMs - lastCheckedMs < 24L * 60L * 60L * 1000L) return

        runCatching {
            val release = releaseClient.latestRelease()
            val latest = release.tag_name
            dataStore.edit { prefs ->
                prefs[lastCheckedKey] = nowMs
                if (VersionCompare.isNewer(latest, currentVersion)) {
                    prefs[cachedVersionKey] = latest.removePrefix("v")
                    prefs[cachedUrlKey] = release.html_url
                    prefs[cachedBodyKey] = release.body.orEmpty()
                } else {
                    prefs.remove(cachedVersionKey)
                    prefs.remove(cachedUrlKey)
                    prefs.remove(cachedBodyKey)
                }
            }
        }
    }

    suspend fun clearCachedUpdate() {
        dataStore.edit {
            it.remove(cachedVersionKey)
            it.remove(cachedUrlKey)
            it.remove(cachedBodyKey)
        }
    }

    private fun isFromPlayStore(): Boolean =
        isFromPlayStoreOverride ?: InstallSource.isFromPlayStore(context)
}
