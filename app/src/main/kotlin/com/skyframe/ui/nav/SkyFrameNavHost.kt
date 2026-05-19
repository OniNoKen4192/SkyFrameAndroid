package com.skyframe.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.skyframe.data.nws.NwsClient
import com.skyframe.ui.screens.PermissionScreen
import com.skyframe.ui.screens.SettingsScreen
import com.skyframe.ui.shell.DashboardScaffold
import com.skyframe.viewmodel.DashboardViewModel
import com.skyframe.viewmodel.SettingsViewModel

/**
 * Top-level NavHost. Start destination decided by MainActivity from
 * SettingsRepository state. First-run users land on SETTINGS (force-completion),
 * then PERMISSIONS, then DASHBOARD; configured users land on DASHBOARD.
 */
@Composable
fun SkyFrameNavHost(
    startDestination: String,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    nwsClient: NwsClient,
    onPermissionsCompleted: () -> Unit,
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
                    // Returning users: pop back to the existing dashboard.
                    // First-run users (no dashboard on the back stack): advance
                    // to the permission cascade instead of going straight to
                    // the dashboard.
                    if (!navController.popBackStack(NavRoutes.DASHBOARD, inclusive = false)) {
                        navController.navigate(NavRoutes.PERMISSIONS) {
                            popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable(NavRoutes.PERMISSIONS) {
            PermissionScreen(
                onContinue = {
                    onPermissionsCompleted()
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.PERMISSIONS) { inclusive = true }
                    }
                },
            )
        }
    }
}
