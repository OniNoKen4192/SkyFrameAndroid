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
import com.skyframe.ui.shell.DashboardScaffold
import com.skyframe.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var setupResolver: SetupResolver
    @Inject lateinit var nwsClient: com.skyframe.data.nws.NwsClient

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeDebugSeed()
        setContent {
            DashboardScaffold(
                viewModel = viewModel,
                nwsClient = nwsClient,
                onNavigateToSettings = {
                    Toast.makeText(this, "Settings: lands in Plan 3", Toast.LENGTH_SHORT).show()
                },
            )
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
                viewModel.refresh()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Debug seed failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}
