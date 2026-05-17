package com.skyframe.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Root theme. Provides:
 *  - Material 3 ColorScheme overridden to HUD colors (so incidentally-used
 *    Material widgets like ModalBottomSheet and Snackbar inherit HUD palette
 *    rather than the default purple).
 *  - LocalHudAccent at the supplied accent (default: cyan).
 *
 * Phase H wires the shell to pass the highest-severity-alert-derived accent.
 */
@Composable
fun HudTheme(
    accent: HudAccent = HudAccent.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accent.accent,
        onPrimary = HudColors.BackgroundBase,
        background = HudColors.BackgroundBase,
        onBackground = HudColors.Foreground,
        surface = HudColors.BackgroundPanel,
        onSurface = HudColors.Foreground,
        surfaceVariant = HudColors.BackgroundDeep,
        onSurfaceVariant = HudColors.ForegroundDim,
        outline = accent.darkStripe,
    )

    CompositionLocalProvider(LocalHudAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
