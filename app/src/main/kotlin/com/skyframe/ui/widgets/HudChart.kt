package com.skyframe.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line chart with optional fill gradient. Mirrors the web's SVG line
 * chart in HourlyPanel.tsx: smooth polyline through (x, normalized y)
 * points with a subtle gradient fill below.
 *
 * @param values input series; first element drawn at left edge, last at right.
 * @param minOverride/maxOverride: optional fixed y-range; otherwise derived from values.
 */
@Composable
fun HudChart(
    values: List<Double>,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    minOverride: Double? = null,
    maxOverride: Double? = null,
    strokeWidth: Float = 3f,
) {
    if (values.size < 2) return
    val min = minOverride ?: values.min()
    val max = maxOverride ?: values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        val w = size.width
        val h = size.height
        val stepX = if (values.size > 1) w / (values.size - 1) else w

        val points = values.mapIndexed { i, v ->
            val x = i * stepX
            // Invert Y so larger value = higher on screen, with 8dp top/bottom padding
            val padding = 16f
            val y = padding + (h - 2 * padding) * (1f - ((v - min) / range).toFloat())
            Offset(x, y)
        }

        // Stroke path
        val strokePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = strokePath,
            color = accent,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Fill gradient under the line
        val fillPath = Path().apply {
            moveTo(points[0].x, h)
            for (p in points) lineTo(p.x, p.y)
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h,
            ),
        )
    }
}
