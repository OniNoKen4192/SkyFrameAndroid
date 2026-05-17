package com.skyframe.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal range bar for the 7-day outlook. The bar's position and
 * length encode (lowF..highF) relative to the global (weekMin..weekMax)
 * axis. Accent color, subtle glow at the high end.
 */
@Composable
fun HudRangeBar(
    lowF: Int,
    highF: Int,
    weekMinF: Int,
    weekMaxF: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val weekRange = (weekMaxF - weekMinF).coerceAtLeast(1)
    val barStartFraction = (lowF - weekMinF).toFloat() / weekRange
    val barEndFraction = (highF - weekMinF).toFloat() / weekRange

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val x0 = barStartFraction * w
        val x1 = barEndFraction * w

        // Track
        drawRoundRect(
            color = accent.copy(alpha = 0.15f),
            topLeft = Offset(0f, h / 4),
            size = Size(w, h / 2),
            cornerRadius = CornerRadius(h / 4),
        )

        // Active fill
        drawRoundRect(
            color = accent,
            topLeft = Offset(x0, h / 4),
            size = Size(x1 - x0, h / 2),
            cornerRadius = CornerRadius(h / 4),
        )
    }
}
