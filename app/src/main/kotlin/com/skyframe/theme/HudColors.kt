package com.skyframe.theme

import androidx.compose.ui.graphics.Color

/**
 * Static HUD palette. Tier-driven accent colors are NOT here — those flow
 * through [LocalHudAccent] so they can change with the highest-severity
 * visible alert.
 */
object HudColors {
    val BackgroundDeep   = Color(0xFF050A10)  // recessed bands (title bar interior)
    val BackgroundBase   = Color(0xFF0A1018)  // main background
    val BackgroundPanel  = Color(0xFF0E1620)  // panel surfaces
    val Foreground       = Color(0xFFC6ECFF)  // body text
    val ForegroundDim    = Color(0xFF7A96A8)  // labels, footer text

    /** Default base-cyan accent — used when no alert is overriding. */
    val DefaultAccent    = Color(0xFF22D3EE)
}
