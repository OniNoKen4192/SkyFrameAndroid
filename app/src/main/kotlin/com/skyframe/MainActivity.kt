package com.skyframe

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.skyframe.data.nws.SetupResolver
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.data.updates.UpdateCheckRepository
import com.skyframe.theme.HudTheme
import com.skyframe.ui.nav.NavRoutes
import com.skyframe.ui.nav.SkyFrameNavHost
import com.skyframe.viewmodel.DashboardViewModel
import com.skyframe.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var setupResolver: SetupResolver
    @Inject lateinit var nwsClient: com.skyframe.data.nws.NwsClient
    @Inject lateinit var updateCheckRepository: UpdateCheckRepository

    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeDebugSeed()

        // Decide start destination at first composition. runBlocking is OK here -
        // it's a one-shot onCreate read of local DataStore (sub-ms), not a
        // continuous coroutine.
        val startDestination = if (runBlocking { settingsRepository.snapshot().isConfigured }) {
            NavRoutes.DASHBOARD
        } else {
            NavRoutes.SETTINGS
        }

        setContent {
            HudTheme {
                SkyFrameNavHost(
                    startDestination = startDestination,
                    dashboardViewModel = dashboardViewModel,
                    settingsViewModel = settingsViewModel,
                    nwsClient = nwsClient,
                )
            }
        }
    }

    private fun maybeDebugSeed() {
        val zip = BuildConfig.DEBUG_SEED_ZIP
        val email = BuildConfig.DEBUG_SEED_EMAIL
        if (zip.isBlank() || email.isBlank()) return
        lifecycleScope.launch {
            val current = settingsRepository.snapshot()
            if (current.isConfigured) return@launch
            try {
                val resolved = setupResolver.resolve(zip)
                settingsRepository.update {
                    it.copy(
                        email = email,
                        lat = resolved.lat,
                        lon = resolved.lon,
                        locationName = resolved.locationName,
                        forecastOffice = resolved.forecastOffice,
                        gridX = resolved.gridX,
                        gridY = resolved.gridY,
                        timezone = resolved.timezone,
                        forecastZone = resolved.forecastZone,
                        stationPrimary = resolved.primaryStation,
                        stationFallback = resolved.secondaryStation,
                    )
                }
                dashboardViewModel.refresh()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Debug seed failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.onResume()
        // Fire-and-forget GitHub update check (24h-throttled + Play-Store gated
        // + checkbox gated inside UpdateCheckRepository - safe to call freely).
        lifecycleScope.launch { updateCheckRepository.maybeCheck() }
    }

    override fun onPause() {
        super.onPause()
        dashboardViewModel.onPause()
    }
}
