package com.skyframe.ui.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Compose ImageVector ports of _reference/client/icons.svg. SMIL animations
 * are NOT ported in Plan 1 - icons are static. Compose Transition equivalents
 * for the sun-ray rotation, cloud drift, etc. are a v2 enhancement.
 *
 * All icons use 64x64 viewport with stroke-only rendering; the caller tints
 * via WxIcon's tint parameter (typically LocalHudAccent.current.accent).
 * Compose Icon's tint replaces the white stroke at render time.
 */
object WxIcons {

    private val stroke = SolidColor(Color.White)

    /** Standard cloud silhouette used by cloud, rain, snow, thunder, partly-*. */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.cloudPath() {
        // M16 36H47.5C51.09 36 54 33.09 54 29.5C54 25.91 51.09 23 47.5 23C46.83 23 ...
        moveTo(16f, 36f)
        horizontalLineTo(47.5f)
        curveTo(51.09f, 36f, 54f, 33.09f, 54f, 29.5f)
        curveTo(54f, 25.91f, 51.09f, 23f, 47.5f, 23f)
        curveTo(46.83f, 23f, 46.18f, 23.1f, 45.57f, 23.3f)
        curveTo(43.93f, 18.49f, 39.36f, 15f, 34f, 15f)
        curveTo(29.97f, 15f, 26.38f, 16.98f, 24.18f, 20.03f)
        curveTo(23.3f, 19.69f, 22.34f, 19.5f, 21.33f, 19.5f)
        curveTo(17.01f, 19.5f, 13.5f, 23.01f, 13.5f, 27.33f)
        curveTo(13.5f, 27.83f, 13.55f, 28.32f, 13.64f, 28.79f)
        curveTo(10.92f, 29.77f, 9f, 32.38f, 9f, 35.45f)
        curveTo(9f, 39.36f, 12.14f, 42.5f, 16f, 42.5f)
    }

    /** Interior cloud lines (the "L" shape inside the cloud silhouette). */
    private fun androidx.compose.ui.graphics.vector.PathBuilder.cloudInner() {
        moveTo(14f, 36f); lineTo(20f, 30f); lineTo(30f, 30f); lineTo(36f, 24f); lineTo(45f, 24f)
        moveTo(16f, 42.5f); horizontalLineTo(47f)
    }

    val Sun: ImageVector = ImageVector.Builder(
        name = "WxSun",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        // Center circle cx=24 cy=20 r=8
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(16f, 20f)
            arcTo(8f, 8f, 0f, true, false, 32f, 20f)
            arcTo(8f, 8f, 0f, true, false, 16f, 20f)
        }
        // 8 rays
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(24f, 6f); lineTo(24f, 10f)
            moveTo(24f, 30f); lineTo(24f, 34f)
            moveTo(10f, 20f); lineTo(14f, 20f)
            moveTo(34f, 20f); lineTo(38f, 20f)
            moveTo(14.5f, 10.5f); lineTo(17.5f, 13.5f)
            moveTo(30.5f, 26.5f); lineTo(33.5f, 29.5f)
            moveTo(33.5f, 10.5f); lineTo(30.5f, 13.5f)
            moveTo(17.5f, 26.5f); lineTo(14.5f, 29.5f)
        }
    }.build()

    val Moon: ImageVector = ImageVector.Builder(
        name = "WxMoon",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            // M34 10C27.2 11.7 22.5 17.7 22.5 25C22.5 33.1 29 39.7 37.2 39.7C38.8 39.7 40.3 39.5 41.7 39
            // C38.9 42.4 34.7 44.5 30 44.5C20.9 44.5 13.5 37.1 13.5 28C13.5 19.7 19.6 12.7 27.7 11.1
            // C29.8 10.7 31.9 10.6 34 10Z
            moveTo(34f, 10f)
            curveTo(27.2f, 11.7f, 22.5f, 17.7f, 22.5f, 25f)
            curveTo(22.5f, 33.1f, 29f, 39.7f, 37.2f, 39.7f)
            curveTo(38.8f, 39.7f, 40.3f, 39.5f, 41.7f, 39f)
            curveTo(38.9f, 42.4f, 34.7f, 44.5f, 30f, 44.5f)
            curveTo(20.9f, 44.5f, 13.5f, 37.1f, 13.5f, 28f)
            curveTo(13.5f, 19.7f, 19.6f, 12.7f, 27.7f, 11.1f)
            curveTo(29.8f, 10.7f, 31.9f, 10.6f, 34f, 10f)
            close()
        }
    }.build()

    val Cloud: ImageVector = ImageVector.Builder(
        name = "WxCloud",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
    }.build()

    val PartlyDay: ImageVector = ImageVector.Builder(
        name = "WxPartlyDay",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        // Sun center circle (cx=24, cy=20, r=8)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(16f, 20f)
            arcTo(8f, 8f, 0f, true, false, 32f, 20f)
            arcTo(8f, 8f, 0f, true, false, 16f, 20f)
        }
        // Sun rays (shorter, repositioned per partly-day spec)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(24f, 8f); lineTo(24f, 12f)
            moveTo(24f, 28f); lineTo(24f, 32f)
            moveTo(12f, 20f); lineTo(16f, 20f)
            moveTo(32f, 20f); lineTo(36f, 20f)
            moveTo(15.5f, 11.5f); lineTo(18.5f, 14.5f)
            moveTo(29.5f, 25.5f); lineTo(32.5f, 28.5f)
            moveTo(32.5f, 11.5f); lineTo(29.5f, 14.5f)
            moveTo(18.5f, 25.5f); lineTo(15.5f, 28.5f)
        }
        // Cloud occluder: filled rect to hide sun overlap (BackgroundBase color)
        path(fill = SolidColor(Color(0xFF0A1018)), stroke = null, strokeLineWidth = 0f) {
            cloudPath()
            close()
        }
        // Cloud outline + inner lines
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
    }.build()

    val PartlyNight: ImageVector = ImageVector.Builder(
        name = "WxPartlyNight",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        // Moon outline (same as Moon)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(34f, 10f)
            curveTo(27.2f, 11.7f, 22.5f, 17.7f, 22.5f, 25f)
            curveTo(22.5f, 33.1f, 29f, 39.7f, 37.2f, 39.7f)
            curveTo(38.8f, 39.7f, 40.3f, 39.5f, 41.7f, 39f)
            curveTo(38.9f, 42.4f, 34.7f, 44.5f, 30f, 44.5f)
            curveTo(20.9f, 44.5f, 13.5f, 37.1f, 13.5f, 28f)
            curveTo(13.5f, 19.7f, 19.6f, 12.7f, 27.7f, 11.1f)
            curveTo(29.8f, 10.7f, 31.9f, 10.6f, 34f, 10f)
            close()
        }
        // Cloud occluder + outline
        path(fill = SolidColor(Color(0xFF0A1018)), stroke = null, strokeLineWidth = 0f) {
            cloudPath()
            close()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
    }.build()

    val Rain: ImageVector = ImageVector.Builder(
        name = "WxRain",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
        // 4 raindrops (static — no SMIL)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(18f, 46f); lineTo(16f, 50f)
            moveTo(28f, 48f); lineTo(26f, 52f)
            moveTo(38f, 46f); lineTo(36f, 50f)
            moveTo(48f, 46f); lineTo(46f, 50f)
        }
    }.build()

    val Snow: ImageVector = ImageVector.Builder(
        name = "WxSnow",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
        // 3 snowflakes (asterisk shape: vertical + horizontal + 2 diagonals)
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            // Flake 1 around (18, 50)
            moveTo(18f, 47f); lineTo(18f, 53f)
            moveTo(15f, 50f); lineTo(21f, 50f)
            moveTo(16f, 48f); lineTo(20f, 52f)
            moveTo(20f, 48f); lineTo(16f, 52f)
            // Flake 2 around (30, 49)
            moveTo(30f, 46f); lineTo(30f, 52f)
            moveTo(27f, 49f); lineTo(33f, 49f)
            moveTo(28f, 47f); lineTo(32f, 51f)
            moveTo(32f, 47f); lineTo(28f, 51f)
            // Flake 3 around (42, 50)
            moveTo(42f, 47f); lineTo(42f, 53f)
            moveTo(39f, 50f); lineTo(45f, 50f)
            moveTo(40f, 48f); lineTo(44f, 52f)
            moveTo(44f, 48f); lineTo(40f, 52f)
        }
    }.build()

    val Thunder: ImageVector = ImageVector.Builder(
        name = "WxThunder",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudPath()
        }
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter) {
            cloudInner()
        }
        // Lightning bolt: M33 42L28 49H33L30 56L40 46H35L38 42H33Z
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(33f, 42f); lineTo(28f, 49f); horizontalLineTo(33f)
            lineTo(30f, 56f); lineTo(40f, 46f); horizontalLineTo(35f)
            lineTo(38f, 42f); horizontalLineTo(33f)
            close()
        }
        // 3 small raindrops alongside the bolt
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(18f, 46f); lineTo(16f, 50f)
            moveTo(28f, 48f); lineTo(26f, 52f)
            moveTo(46f, 46f); lineTo(44f, 50f)
        }
    }.build()

    val Fog: ImageVector = ImageVector.Builder(
        name = "WxFog",
        defaultWidth = 64.dp, defaultHeight = 64.dp,
        viewportWidth = 64f, viewportHeight = 64f,
    ).apply {
        // 4 horizontal fog lines with offset segments
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Miter) {
            moveTo(12f, 18f); horizontalLineTo(28f); lineTo(32f, 14f); horizontalLineTo(52f)
            moveTo(8f, 28f); horizontalLineTo(24f); lineTo(28f, 24f); horizontalLineTo(48f); lineTo(52f, 20f); horizontalLineTo(56f)
            moveTo(12f, 38f); horizontalLineTo(34f); lineTo(38f, 34f); horizontalLineTo(56f)
            moveTo(8f, 48f); horizontalLineTo(20f); lineTo(24f, 44f); horizontalLineTo(44f); lineTo(48f, 40f); horizontalLineTo(56f)
        }
    }.build()
}
