package com.skyframe.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skyframe.R

/** HUD monospace font family (IBM Plex Mono). */
val HudFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/**
 * Per-role typography. Maps to _reference/client/styles/hud.css and
 * terminal-modal.css. Colors are not set here — they come from
 * MaterialTheme.colorScheme or LocalHudAccent at the call site.
 */
object HudType {
    val titleBar = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.18.em,
    )
    val metaLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.15.em,
    )
    val bodyMono = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val heroTemp = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 96.sp,
        letterSpacing = 0.sp,
    )
    val heroFeel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.12.em,
    )
    val metricValue = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    )
    val metricLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.18.em,
    )
    val sectionHeader = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.20.em,
    )
    val navLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.15.em,
    )
    val footerMono = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.08.em,
    )
}
