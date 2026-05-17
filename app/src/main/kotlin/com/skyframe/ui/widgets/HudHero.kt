package com.skyframe.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.IconCode
import com.skyframe.domain.TempUnit
import com.skyframe.domain.Units
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import kotlin.math.roundToInt

@Composable
fun HudHero(
    current: CurrentConditions,
    tempUnit: TempUnit,
    accent: Color,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val temp = Units.convertTempF(current.tempF, tempUnit).roundToInt()
    val feel = Units.convertTempF(current.feelsLikeF, tempUnit).roundToInt()
    val unitSuffix = if (tempUnit == TempUnit.FAHRENHEIT) "F" else "C"
    val isClear = current.iconCode in setOf(IconCode.SUN, IconCode.MOON)

    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.clickable { onToggleUnit() }
        ) {
            HudGlowText(
                text = "$temp°",
                color = accent,
                style = HudType.heroTemp,
            )
            Text(
                text = "TEMP / FEEL  $feel°$unitSuffix",
                color = HudColors.ForegroundDim,
                style = HudType.heroFeel,
            )
        }
        WxIcon(
            code = current.iconCode,
            tint = accent,
            size = if (isClear) 96.dp else 72.dp,
        )
    }
}
