package com.skyframe.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skyframe.domain.IconCode

@Composable
fun WxIcon(
    code: IconCode,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val vector = when (code) {
        IconCode.SUN -> WxIcons.Sun
        IconCode.MOON -> WxIcons.Moon
        IconCode.CLOUD -> WxIcons.Cloud
        IconCode.PARTLY_DAY -> WxIcons.PartlyDay
        IconCode.PARTLY_NIGHT -> WxIcons.PartlyNight
        IconCode.RAIN -> WxIcons.Rain
        IconCode.SNOW -> WxIcons.Snow
        IconCode.THUNDER -> WxIcons.Thunder
        IconCode.FOG -> WxIcons.Fog
    }
    Icon(
        imageVector = vector,
        contentDescription = code.name,
        tint = tint,
        modifier = modifier.size(size),
    )
}
