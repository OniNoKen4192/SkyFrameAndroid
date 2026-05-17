package com.skyframe.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skyframe.theme.hudTextGlow

/**
 * Text with a soft accent-colored glow halo. Stacks a blurred copy
 * underneath a crisp copy in a Box so the text stays sharp while the
 * glow halo extends behind it.
 *
 * The web equivalent is `text-shadow: 0 0 8px rgba(accent, 0.5)`.
 */
@Composable
fun HudGlowText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    glowColor: Color = color,
    glowRadius: Dp = 8.dp,
) {
    Box(modifier = modifier) {
        // Blurred copy beneath
        Text(
            text = text,
            color = glowColor,
            style = style,
            modifier = Modifier.hudTextGlow(glowColor, glowRadius),
        )
        // Crisp copy on top
        Text(
            text = text,
            color = color,
            style = style,
        )
    }
}
