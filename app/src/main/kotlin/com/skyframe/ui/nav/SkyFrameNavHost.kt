package com.skyframe.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skyframe.data.nws.NwsClient
import com.skyframe.ui.screens.SettingsScreen
import com.skyframe.ui.shell.DashboardScaffold
import com.skyframe.viewmodel.DashboardViewModel
import com.skyframe.viewmodel.SettingsViewModel

/**
 * Top-level NavHost. Start destination decided by MainActivity based on
 * SettingsRepository.isConfigured. First-run users land on SETTINGS (in
 * force-completion mode); configured users land on DASHBOARD.
 */
@Composable
fun SkyFrameNavHost(
    startDestination: String,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    nwsClient: NwsClient,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(NavRoutes.DASHBOARD) {
            DashboardScaffold(
                viewModel = dashboardViewModel,
                nwsClient = nwsClient,
                onNavigateToSettings = { navController.navigate(NavRoutes.SETTINGS) },
            )
        }
        composable(NavRoutes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onSaved = {
                    // popBackStack returns to dashboard. If first-run (dashboard
                    // was never on the back stack), navigate explicitly and clear settings.
                    if (!navController.popBackStack(NavRoutes.DASHBOARD, inclusive = false)) {
                        navController.navigate(NavRoutes.DASHBOARD) {
                            popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                        }
                    }
                },
            )
        }
    }
}
