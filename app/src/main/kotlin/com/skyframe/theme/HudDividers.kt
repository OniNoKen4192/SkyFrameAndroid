package com.skyframe.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp

/**
 * Dashed accent divider on the top and/or bottom edge, matching the web HUD's
 * `1px dashed rgba(accent, 0.18)` region separators (hud.css .hud-topbar /
 * .hud-footer / .alert-banner).
 *
 * The shell regions are transparent over the single body background and
 * delineated by these dashes — NOT by a darker background band. Using a
 * darker band (BackgroundDeep) is what caused the header/footer-vs-body
 * color mismatch.
 */
fun Modifier.hudDashedBorder(
    accent: Color,
    top: Boolean = false,
    bottom: Boolean = false,
    alpha: Float = 0.18f,
): Modifier = this.drawBehind {
    val color = accent.copy(alpha = alpha)
    val sw = 1.dp.toPx()
    val effect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()), 0f)
    if (top) {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), sw, pathEffect = effect)
    }
    if (bottom) {
        drawLine(color, Offset(0f, size.height), Offset(size.width, size.height), sw, pathEffect = effect)
    }
}
