package com.skyframe.ui.nav

/**
 * Top-level Compose Navigation route names. Two destinations:
 *  - DASHBOARD: the main weather UI (DashboardScaffold)
 *  - SETTINGS: the configuration form (SettingsScreen)
 *
 * Bottom-nav destinations (NOW/HOURLY/OUTLOOK) are NOT NavHost routes -
 * they're internal state within DashboardScaffold's Box-swap dispatcher.
 * See `DashboardDestination` for that.
 */
object NavRoutes {
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
}
