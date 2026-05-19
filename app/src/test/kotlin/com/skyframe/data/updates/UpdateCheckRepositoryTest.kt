package com.skyframe.data.updates

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.skyframe.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UpdateCheckRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(
        nowProvider: () -> Instant,
        playStore: Boolean = false,
        checkboxEnabled: Boolean = true,
        releaseTag: String = "v0.3.0",
        releaseBody: String? = "release notes",
        releaseUrl: String = "https://github.com/foo/bar/releases/tag/v0.3.0",
        releaseClient: GithubReleaseClient? = null,
    ): UpdateCheckRepository {
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "uc.preferences_pb") })

        val settings = mockk<SettingsRepository>()
        coEvery { settings.snapshot() } returns SettingsRepository.Snapshot(
            updateCheckEnabled = checkboxEnabled,
        )

        val context = mockk<Context>(relaxed = true)

        val client = releaseClient ?: mockk<GithubReleaseClient>().also {
            coEvery { it.latestRelease() } returns GithubReleaseDto(
                tag_name = releaseTag,
                html_url = releaseUrl,
                body = releaseBody,
            )
        }

        return UpdateCheckRepository(
            context = context,
            dataStore = ds,
            settings = settings,
            releaseClient = client,
            now = nowProvider,
            currentVersion = "0.2.0",  // simulate installed v0.2.0
            isFromPlayStoreOverride = playStore,
        )
    }

    @Test
    fun `first check ever caches newer version`() = runTest {
        val now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now })

        repo.maybeCheck()

        val available = repo.currentAvailable()
        assertNotNull(available, "expected cached UpdateAvailable, got null")
        assertEquals("0.3.0", available!!.version)
    }

    @Test
    fun `second check within 24 hours is throttled`() = runTest {
        var now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now })

        repo.maybeCheck()  // first check populates cache
        // Force-clear cache so we can detect whether the second call re-populates
        repo.clearCachedUpdate()

        now = Instant.fromEpochSeconds(1_000_000 + 23 * 3600L)  // 23 hours later
        repo.maybeCheck()

        // If throttle worked, cache stays empty.
        assertNull(repo.currentAvailable())
    }

    @Test
    fun `check after 24 hours runs again`() = runTest {
        var now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now })

        repo.maybeCheck()
        repo.clearCachedUpdate()

        now = Instant.fromEpochSeconds(1_000_000 + 25 * 3600L)  // 25 hours later
        repo.maybeCheck()

        // Throttle elapsed, second check should re-populate.
        assertNotNull(repo.currentAvailable())
    }

    @Test
    fun `Play Store install skips check entirely`() = runTest {
        val now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now }, playStore = true)

        repo.maybeCheck()

        assertNull(repo.currentAvailable(), "Play Store install should never poll")
    }

    @Test
    fun `checkbox disabled skips check entirely`() = runTest {
        val now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now }, checkboxEnabled = false)

        repo.maybeCheck()

        assertNull(repo.currentAvailable())
    }

    @Test
    fun `same or older version clears cached state`() = runTest {
        var now = Instant.fromEpochSeconds(1_000_000)
        // First check: GitHub has 0.3.0, local is 0.2.0 → cache populated
        val client = mockk<GithubReleaseClient>()
        coEvery { client.latestRelease() } returns GithubReleaseDto("v0.3.0", "url", "notes")
        val repo = newRepo({ now }, releaseClient = client)
        repo.maybeCheck()
        assertNotNull(repo.currentAvailable())

        // Second check 25h later: GitHub now reports same version as installed
        // (user upgraded local app to match) → cache should clear
        coEvery { client.latestRelease() } returns GithubReleaseDto("v0.2.0", "url", "notes")
        now = Instant.fromEpochSeconds(1_000_000 + 25 * 3600L)
        repo.maybeCheck()

        assertNull(repo.currentAvailable(), "cache should clear when version is no longer newer")
    }

    @Test
    fun `network failure leaves cache unchanged`() = runTest {
        var now = Instant.fromEpochSeconds(1_000_000)
        val client = mockk<GithubReleaseClient>()
        coEvery { client.latestRelease() } returns GithubReleaseDto("v0.3.0", "url", "notes")
        val repo = newRepo({ now }, releaseClient = client)
        repo.maybeCheck()
        assertNotNull(repo.currentAvailable())

        // Next check throws — should NOT clear the existing cache
        coEvery { client.latestRelease() } throws RuntimeException("network down")
        now = Instant.fromEpochSeconds(1_000_000 + 25 * 3600L)
        repo.maybeCheck()

        // Cache from prior successful check still present
        assertNotNull(repo.currentAvailable(), "network failure must not clear existing cache")
    }

    @Test
    fun `clearCachedUpdate empties available state`() = runTest {
        val now = Instant.fromEpochSeconds(1_000_000)
        val repo = newRepo({ now })
        repo.maybeCheck()
        assertNotNull(repo.currentAvailable())

        repo.clearCachedUpdate()

        assertNull(repo.currentAvailable())
    }
}
