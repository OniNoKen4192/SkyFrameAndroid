package com.skyframe.ui.sheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.data.alerts.AlertDescriptionFormat
import com.skyframe.data.nws.NwsClient
import com.skyframe.domain.StationOverride
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt

@Composable
fun StationOverrideSheet(
    currentMode: StationOverride,
    primaryStationId: String,
    secondaryStationId: String,
    timezone: String,
    client: NwsClient,
    onApply: (StationOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = LocalHudAccent.current.accent
    var selectedMode by remember { mutableStateOf(currentMode) }

    var preview by remember {
        mutableStateOf<Pair<Result<StationSnapshot>, Result<StationSnapshot>>?>(null)
    }
    LaunchedEffect(primaryStationId, secondaryStationId) {
        preview = StationPreview.fetch(client, primaryStationId, secondaryStationId)
    }

    val tz = remember(timezone) {
        runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    }

    HudBottomSheet(title = "STATION OVERRIDE", onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(top = 12.dp)) {

            HudRadioRow(
                label = "AUTO",
                description = "Primary station with automatic fallback to secondary when stale",
                selected = selectedMode == StationOverride.AUTO,
                onSelect = { selectedMode = StationOverride.AUTO },
                accent = accent,
            )
            Spacer(Modifier.height(8.dp))
            HudRadioRow(
                label = "FORCE SECONDARY",
                description = "Always use the secondary station",
                selected = selectedMode == StationOverride.FORCE_SECONDARY,
                onSelect = { selectedMode = StationOverride.FORCE_SECONDARY },
                accent = accent,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "─────────────── PREVIEW ───────────────",
                color = HudColors.ForegroundDim,
                style = HudType.metricLabel,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            StationPreviewRow(
                label = "PRIMARY",
                stationId = primaryStationId,
                result = preview?.first,
                tz = tz,
            )
            Spacer(Modifier.height(8.dp))
            StationPreviewRow(
                label = "SECONDARY",
                stationId = secondaryStationId,
                result = preview?.second,
                tz = tz,
            )

            Spacer(Modifier.height(24.dp))
            ApplyButton(
                enabled = selectedMode != currentMode,
                onClick = { onApply(selectedMode) },
                accent = accent,
            )
        }
    }
}

@Composable
private fun HudRadioRow(
    label: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Custom radio: outer ring + filled inner circle when selected
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(BorderStroke(2.dp, accent), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = if (selected) accent else HudColors.Foreground, style = HudType.titleBar)
            Text(description, color = HudColors.ForegroundDim, style = HudType.metaLabel)
        }
    }
}

@Composable
private fun StationPreviewRow(
    label: String,
    stationId: String,
    result: Result<StationSnapshot>?,
    tz: TimeZone,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            color = HudColors.ForegroundDim,
            style = HudType.metricLabel,
            modifier = Modifier.width(100.dp).padding(top = 2.dp),
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(stationId, color = HudColors.Foreground, style = HudType.metricValue)
            when {
                result == null ->
                    Text("Fetching…", color = HudColors.ForegroundDim, style = HudType.metaLabel)
                result.isFailure ->
                    StatusDot("● ERROR", Color(0xFFFF4444))
                else -> {
                    val snap = result.getOrThrow()
                    val time = snap.observedAt?.let { AlertDescriptionFormat.formatTime(it, tz) } ?: "—"
                    val temp = snap.tempF?.let { "${it.roundToInt()}°" } ?: "—"
                    Text("Observed $time · $temp", color = HudColors.Foreground, style = HudType.metaLabel)
                    if (snap.isStale) StatusDot("● STALE", Color(0xFFFFAA22))
                    else StatusDot("● LIVE", HudColors.DefaultAccent)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Text(text, color = color, style = HudType.metaLabel)
}

@Composable
private fun ApplyButton(enabled: Boolean, onClick: () -> Unit, accent: Color) {
    val color = if (enabled) accent else HudColors.ForegroundDim
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "[ APPLY ]",
            color = color,
            style = HudType.titleBar,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onClick)
                .border(BorderStroke(1.dp, color))
                .padding(horizontal = 24.dp, vertical = 8.dp),
        )
    }
}
