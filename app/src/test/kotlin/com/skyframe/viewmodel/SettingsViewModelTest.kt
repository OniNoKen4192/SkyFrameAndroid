package com.skyframe.viewmodel

import android.content.Context
import com.skyframe.data.gps.GpsAutodetect
import com.skyframe.data.nws.ResolvedSetup
import com.skyframe.data.nws.SetupException
import com.skyframe.data.nws.SetupResolver
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.data.updates.UpdateCheckRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @BeforeEach fun setMain() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterEach fun resetMain() { Dispatchers.resetMain() }

    private fun newViewModel(
        snapshot: SettingsRepository.Snapshot = SettingsRepository.Snapshot(),
        resolved: ResolvedSetup? = sampleResolved,
        resolveThrows: Throwable? = null,
        isFromPlayStore: Boolean = false,
    ): Triple<SettingsViewModel, SettingsRepository, UpdateCheckRepository> {
        val settings = mockk<SettingsRepository>(relaxed = true)
        coEvery { settings.snapshot() } returns snapshot

        val resolver = mockk<SetupResolver>()
        if (resolveThrows != null) {
            coEvery { resolver.resolve(any()) } throws resolveThrows
        } else {
            coEvery { resolver.resolve(any()) } returns resolved!!
        }

        val updateCheck = mockk<UpdateCheckRepository>(relaxed = true)
        coEvery { updateCheck.maybeCheck() } just Runs

        val gps = mockk<GpsAutodetect>()
        val context = mockk<Context>(relaxed = true)
        val notificationDispatcher = mockk<com.skyframe.notifications.NotificationDispatcher>(relaxed = true)

        return Triple(
            SettingsViewModel(
                settings = settings,
                setupResolver = resolver,
                updateCheck = updateCheck,
                gpsAutodetect = gps,
                context = context,
                notificationDispatcher = notificationDispatcher,
                isFromPlayStoreOverride = isFromPlayStore,
            ),
            settings,
            updateCheck,
        )
    }

    private val sampleResolved = ResolvedSetup(
        lat = 42.8744, lon = -87.8633,
        forecastOffice = "MKX", gridX = 88, gridY = 58,
        timezone = "America/Chicago", forecastZone = "WIZ066",
        primaryStation = "KMKE", secondaryStation = "KRAC",
        locationName = "OAK CREEK WI",
    )

    @Test
    fun `initial state hydrated from SettingsRepository snapshot`() = runTest {
        val (vm, _, _) = newViewModel(
            snapshot = SettingsRepository.Snapshot(
                email = "user@example.com",
                lat = 42.8744, lon = -87.8633,
                locationName = "OAK CREEK WI",
                forecastOffice = "MKX",
                stationPrimary = "KMKE",
                updateCheckEnabled = true,
            ),
        )
        val state = vm.uiState.value
        assertEquals("user@example.com", state.emailInput)
        assertEquals(true, state.updateCheckEnabled)
        assertEquals(true, state.isConfigured)
    }

    @Test
    fun `save success triggers onSaved callback and persists`() = runTest {
        val (vm, settings, _) = newViewModel()
        vm.onLocationChange("53154")
        vm.onEmailChange("user@example.com")

        var savedCalled = false
        vm.save(onSaved = { savedCalled = true })

        assertTrue(savedCalled, "expected onSaved callback to fire on successful save")
        assertTrue(vm.uiState.value.saveState is SettingsViewModel.SaveState.Saved)
        coVerify { settings.update(any()) }
    }

    @Test
    fun `save failure populates Error state and skips onSaved`() = runTest {
        val (vm, _, _) = newViewModel(resolveThrows = SetupException("NWS /points returned 404"))
        vm.onLocationChange("99999")
        vm.onEmailChange("user@example.com")

        var savedCalled = false
        vm.save(onSaved = { savedCalled = true })

        assertEquals(false, savedCalled)
        val state = vm.uiState.value.saveState
        assertTrue(state is SettingsViewModel.SaveState.Error)
        assertTrue((state as SettingsViewModel.SaveState.Error).message.contains("404"))
    }

    @Test
    fun `enabling update-check checkbox triggers maybeCheck immediately`() = runTest {
        val (vm, _, updateCheck) = newViewModel()
        vm.onUpdateCheckToggle(true)

        coVerify(atLeast = 1) { updateCheck.maybeCheck() }
    }

    @Test
    fun `Play Store install hides update check checkbox`() = runTest {
        val (vm, _, _) = newViewModel(isFromPlayStore = true)
        assertEquals(false, vm.uiState.value.showUpdateCheckCheckbox)
    }

    @Test
    fun `non-Play-Store install shows update check checkbox`() = runTest {
        val (vm, _, _) = newViewModel(isFromPlayStore = false)
        assertEquals(true, vm.uiState.value.showUpdateCheckCheckbox)
    }
}
