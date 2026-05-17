package com.skyframe.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.Trend
import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun HudMetricBar(
    label: String,
    value: String,
    trend: Trend?,
    accent: Color,
    fillFraction: Float,  // 0.0..1.0 - bar fill proportion
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = HudColors.ForegroundDim,
            style = HudType.metricLabel,
            modifier = Modifier.padding(end = 8.dp),
        )

        // Bar fill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(HudColors.BackgroundDeep),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(accent.copy(alpha = 0.7f)),
            )
        }

        Text(
            text = value,
            color = HudColors.Foreground,
            style = HudType.metricValue,
            modifier = Modifier.padding(start = 8.dp),
        )

        // Render the trend arrow only when we have enough history to compute one.
        // WeatherNormalizer currently feeds emptyList() for recent observations
        // so every trend has MISSING confidence (Plan 1 limitation); hiding the
        // arrow avoids showing an always-steady "·" that misleads the user.
        if (trend != null && trend.confidence == TrendConfidence.OK) {
            val arrow = when (trend.direction) {
                TrendDirection.UP -> "▲"
                TrendDirection.DOWN -> "▼"
                TrendDirection.STEADY -> "·"
            }
            Text(
                text = arrow,
                color = accent,
                style = HudType.metricValue,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
