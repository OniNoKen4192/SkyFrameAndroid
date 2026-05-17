package com.skyframe.ui.nav

enum class DashboardDestination(val route: String, val label: String, val glyph: String) {
    NOW("now", "NOW", "◉"),
    HOURLY("hourly", "HOURLY", "░"),
    OUTLOOK("outlook", "OUTLOOK", "█"),
}
