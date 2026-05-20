package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.skyframe.domain.Alert
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun AlertBanner(
    alerts: List<Alert>,
    onDismiss: (String) -> Unit,
    onAlertClick: (Alert) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    val top = alerts.first()
    val tierAccent = Color(top.tier.baseColor)
    val tierDark = Color(top.tier.darkColor)
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HudColors.BackgroundDeep)
            .drawBehind {
                // Hazard stripes - 45-degree caution-tape band across the top.
                // Dimensions in dp (converted to px) so the band is a consistent
                // physical size across densities; raw px renders as a hairline on
                // high-density screens. Each stripe is a parallelogram slanted by
                // the band height (dx == dy == 45 degrees); consecutive stripes
                // tile edge-to-edge with no gaps.
                val stripeW = 12.dp.toPx()
                val stripeH = 8.dp.toPx()
                clipRect(left = 0f, top = 0f, right = size.width, bottom = stripeH) {
                    var x = -stripeW - stripeH
                    var i = 0
                    while (x < size.width + stripeH) {
                        val path = Path().apply {
                            moveTo(x, 0f)
                            lineTo(x + stripeW, 0f)
                            lineTo(x + stripeW - stripeH, stripeH)
                            lineTo(x - stripeH, stripeH)
                            close()
                        }
                        drawPath(path, color = if (i % 2 == 0) tierAccent else tierDark)
                        x += stripeW
                        i++
                    }
                }
            }
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = top.event.uppercase(),
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onAlertClick(top) },
            )
            if (alerts.size > 1) {
                Text(
                    text = if (expanded) "▾" else "+${alerts.size - 1}",
                    color = tierAccent,
                    style = HudType.titleBar,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }
            Text(
                text = "×",
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onDismiss(top.id) },
            )
        }

        if (expanded) {
            alerts.drop(1).forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = alert.event.uppercase(),
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAlertClick(alert) },
                    )
                    Text(
                        text = "×",
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { onDismiss(alert.id) },
                    )
                }
            }
        }
    }
}
