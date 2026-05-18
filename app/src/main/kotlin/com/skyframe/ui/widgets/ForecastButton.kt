package com.skyframe.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Small ▶ trigger glyph used by NowScreen (next to TEMP/FEEL label),
 * HourlyScreen (next to NEXT 12H header), and any other surface that
 * wants to open ForecastNarrativeSheet.
 */
@Composable
fun ForecastButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "▶",
        color = LocalHudAccent.current.accent,
        style = HudType.metricLabel,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
