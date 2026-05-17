package com.skyframe.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a soft glow halo around the modified content matching the web's
 * text-shadow: 0 0 8px rgba(accent, 0.5) effect.
 *
 * On Android 12+ uses RenderEffect.createBlurEffect (hardware-accelerated
 * Skia blur). On API 26-30 falls back to no-op — the underlying text still
 * renders but without glow. Compose blur fallback for pre-31 is non-trivial
 * and ~5% of 2026 devices fall in that range; we accept the reduced effect.
 *
 * @param color reserved for future use (currently the blur picks up content's
 *   own color); kept in the signature so callers can wire the accent.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.hudTextGlow(
    color: Color,
    radius: Dp = 8.dp,
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = with(LocalDensity.current) { radius.toPx() }
        Modifier.graphicsLayer {
            renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Decal)
        }
    } else {
        Modifier
    }
}
