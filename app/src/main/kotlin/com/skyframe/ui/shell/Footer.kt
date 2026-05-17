package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.skyframe.domain.StationOverride
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun Footer(
    stationId: String,
    stationOverride: StationOverride,
    lastFetchedLabel: String,
    nextRefreshLabel: String,
    onStationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinSuffix = if (stationOverride == StationOverride.FORCE_SECONDARY) " [PIN]" else ""
    val linkColor = if (stationOverride == StationOverride.FORCE_SECONDARY) Color(0xFFFFAA22) else HudColors.ForegroundDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(HudColors.BackgroundDeep)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "LINK.${stationId.ifBlank { "----" }}$pinSuffix",
            color = linkColor,
            style = HudType.footerMono,
            modifier = Modifier.clickable { onStationClick() },
        )
        Text(
            text = lastFetchedLabel,
            color = HudColors.ForegroundDim,
            style = HudType.footerMono,
        )
        Text(
            text = nextRefreshLabel,
            color = HudColors.ForegroundDim,
            style = HudType.footerMono,
        )
    }
}
