package com.skyframe.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.skyframe.domain.AlertTier

/**
 * Active accent color for HUD chrome. Equivalent to the web's
 * --accent, --accent-rgb, and --accent-glow-* CSS custom properties.
 *
 * Glow colors are derived from the base accent at fixed alpha values
 * matching hud.css text-shadow rules.
 */
@Immutable
data class HudAccent(
    val accent: Color,
    val darkStripe: Color,
    val glowSoft: Color,     // base at 0.15 alpha
    val glowMedium: Color,   // base at 0.30 alpha
    val glowStrong: Color,   // base at 0.50 alpha
) {
    companion object {
        /** Base-cyan accent used when no alert is overriding. */
        val Default: HudAccent = fromColors(
            base = HudColors.DefaultAccent,
            dark = Color(0xFF008E82),  // matches advisory tier dark variant
        )

        fun fromTier(tier: AlertTier): HudAccent = fromColors(
            base = Color(tier.baseColor),
            dark = Color(tier.darkColor),
        )

        private fun fromColors(base: Color, dark: Color): HudAccent = HudAccent(
            accent = base,
            darkStripe = dark,
            glowSoft = base.copy(alpha = 0.15f),
            glowMedium = base.copy(alpha = 0.30f),
            glowStrong = base.copy(alpha = 0.50f),
        )
    }
}

/**
 * Tree-scoped accent. Read via LocalHudAccent.current in any Composable
 * that needs accent-derived color. Shell-level state provides the active
 * value based on the highest-severity visible alert.
 */
val LocalHudAccent = compositionLocalOf { HudAccent.Default }
