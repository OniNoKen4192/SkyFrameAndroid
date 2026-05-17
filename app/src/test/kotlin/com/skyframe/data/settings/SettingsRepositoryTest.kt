package com.skyframe.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.skyframe.domain.StationOverride
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return SettingsRepository(dataStore)
    }

    @Test
    fun `snapshot returns defaults before any write`() = runTest {
        val repo = newRepo()
        val snap = repo.snapshot()
        assertEquals("", snap.email)
        assertEquals("", snap.forecastOffice)
        assertEquals(0.0, snap.lat)
        assertEquals(StationOverride.AUTO, snap.stationOverride)
        assertEquals(false, snap.isConfigured)
    }

    @Test
    fun `update persists location config and isConfigured becomes true`() = runTest {
        val repo = newRepo()
        repo.update {
            it.copy(
                email = "user@example.com",
                lat = 42.8744,
                lon = -87.8633,
                forecastOffice = "MKX",
                gridX = 88,
                gridY = 58,
                timezone = "America/Chicago",
                forecastZone = "WIZ066",
                stationPrimary = "KMKE",
                stationFallback = "KRAC",
                locationName = "OAK CREEK WI",
            )
        }
        val snap = repo.snapshot()
        assertEquals("user@example.com", snap.email)
        assertEquals(42.8744, snap.lat)
        assertEquals("KMKE", snap.stationPrimary)
        assertEquals(true, snap.isConfigured)
    }

    @Test
    fun `stationOverride toggles between AUTO and FORCE_SECONDARY`() = runTest {
        val repo = newRepo()
        repo.update { it.copy(stationOverride = StationOverride.FORCE_SECONDARY) }
        assertEquals(StationOverride.FORCE_SECONDARY, repo.snapshot().stationOverride)
        repo.update { it.copy(stationOverride = StationOverride.AUTO) }
        assertEquals(StationOverride.AUTO, repo.snapshot().stationOverride)
    }
}
