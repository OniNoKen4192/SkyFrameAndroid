package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TopBar(
    locationName: String,
    timezone: String,
    isOnline: Boolean,
    onLocationClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current
    val tz = remember(timezone) { runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault()) }

    var clockText by remember { mutableStateOf(formatClock(tz)) }
    LaunchedEffect(tz) {
        while (true) {
            clockText = formatClock(tz)
            delay(1_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HudColors.BackgroundDeep)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOnline) accent.accent else Color(0xFFFF4444)),
        )

        Text(
            text = "  $clockText",
            color = HudColors.Foreground,
            style = HudType.titleBar,
            modifier = Modifier.padding(start = 4.dp),
        )

        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = locationName.ifBlank { "UNCONFIGURED" },
                color = accent.accent,
                style = HudType.titleBar,
                modifier = Modifier
                    .clickable { onLocationClick() }
                    .padding(8.dp),
            )
        }

        Text(
            text = "≡",
            color = accent.accent,
            style = HudType.titleBar.copy(fontSize = TextUnit(20f, TextUnitType.Sp)),
            modifier = Modifier
                .clickable { onMenuClick() }
                .padding(8.dp),
        )
    }
}

private fun formatClock(tz: TimeZone): String {
    val ldt = Clock.System.now().toLocalDateTime(tz)
    val h = ldt.hour.toString().padStart(2, '0')
    val m = ldt.minute.toString().padStart(2, '0')
    val s = ldt.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
