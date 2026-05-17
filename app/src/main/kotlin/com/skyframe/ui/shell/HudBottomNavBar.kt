package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.nav.DashboardDestination

@Composable
fun HudBottomNavBar(
    selected: DashboardDestination,
    onSelect: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(HudColors.BackgroundDeep)
            .drawBehind {
                // 2dp top border in accent — hazard-stripe equivalent (single line in v1)
                drawLine(
                    color = accent.accent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f,
                )
            },
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardDestination.entries.forEach { dest ->
            val isSelected = dest == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(dest) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${dest.glyph} ${dest.label}",
                    color = if (isSelected) accent.accent else HudColors.ForegroundDim,
                    style = HudType.navLabel,
                )
                if (isSelected) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(accent.accent),
                    )
                }
            }
        }
    }
}
