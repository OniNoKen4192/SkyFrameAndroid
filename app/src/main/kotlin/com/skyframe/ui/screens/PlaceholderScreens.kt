package com.skyframe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun NowScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("NOW SCREEN — Phase I", modifier)
}

@Composable
fun HourlyScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("HOURLY SCREEN — Phase J", modifier)
}

@Composable
fun OutlookScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("OUTLOOK SCREEN — Phase K", modifier)
}

@Composable
private fun PlaceholderContent(label: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, color = LocalHudAccent.current.accent, style = HudType.titleBar)
    }
}
