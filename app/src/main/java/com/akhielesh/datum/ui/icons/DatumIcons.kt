package com.akhielesh.datum.ui.icons

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.theme.Datum

/**
 * Hand-drawn line icons in the spirit of SF Symbols: 24-unit grid, 1.7 stroke, round caps and joins.
 */
object DatumIcons {
    private fun icon(name: String, vararg strokes: String, fills: List<String> = emptyList(), width: Float = 1.7f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in strokes) {
            b.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        for (d in fills) {
            b.addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
        }
        return b.build()
    }

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

    private fun roundRect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

    val Level by lazy {
        icon("level", roundRect(2.5f, 7.5f, 19f, 9f, 4.5f), "M8.6 10v4 M15.4 10v4", fills = listOf(circle(12f, 12f, 2.1f)))
    }
    val Slide by lazy {
        icon("slide", roundRect(3f, 13.5f, 18f, 6f, 1.5f), "M6.5 13.5v2.5 M10 13.5v1.6 M13.5 13.5v2.5 M17 13.5v1.6", "M5 7.5h13 M15.5 4.8l2.8 2.7-2.8 2.7")
    }
    val Height by lazy { icon("height", "M12 3.5v13.5 M8.6 6.9L12 3.5l3.4 3.4 M4.5 20.5h15 M7.5 17h2 M14.5 17h2") }
    val Cube by lazy { icon("cube", "M12 2.8l8 4.6v9.2l-8 4.6-8-4.6V7.4z M12 12l8-4.6 M12 12v9.2 M12 12L4 7.4") }
    val Ruler by lazy {
        icon(
            "ruler",
            "M3.9 16.4L16.4 3.9a1.4 1.4 0 0 1 2 0l1.7 1.7a1.4 1.4 0 0 1 0 2L7.6 20.1a1.4 1.4 0 0 1-2 0l-1.7-1.7a1.4 1.4 0 0 1 0-2z",
            "M7.6 12.7l1.6 1.6 M10.2 10.1l1.1 1.1 M12.8 7.5l1.6 1.6",
        )
    }
    val Angle by lazy { icon("angle", "M4 19.5h16 M4 19.5L14.5 5.5", "M9.4 19.5a6 6 0 0 0-2.2-4.6") }
    val Ar by lazy {
        icon(
            "ar",
            "M4 8.5V5.5A1.5 1.5 0 0 1 5.5 4h3 M15.5 4h3A1.5 1.5 0 0 1 20 5.5v3 M20 15.5v3a1.5 1.5 0 0 1-1.5 1.5h-3 M8.5 20h-3A1.5 1.5 0 0 1 4 18.5v-3",
            "M9 15l6-6",
            fills = listOf(circle(8.6f, 15.4f, 1.5f), circle(15.4f, 8.6f, 1.5f)),
        )
    }
    val Magnet by lazy { icon("magnet", "M6.5 4.5h3.5v7.5a2 2 0 0 0 4 0V4.5h3.5V12a5.5 5.5 0 0 1-11 0z", "M6.5 8h3.5 M14 8h3.5") }
    val Compass by lazy { icon("compass", circle(12f, 12f, 8.8f), "M14.8 9.2l-1.7 3.9-3.9 1.7 1.7-3.9z") }
    val Altimeter by lazy { icon("altimeter", "M2.8 19.5l6.1-9.2 4 5.6 2.4-3.1 5.9 6.7z", "M18 3v6.2 M15.8 5.2L18 3l2.2 2.2") }
    val Light by lazy {
        icon(
            "light", circle(12f, 12f, 3.6f),
            "M12 2.8v2.1 M12 19.1v2.1 M2.8 12h2.1 M19.1 12h2.1 M5.5 5.5L7 7 M17 17l1.5 1.5 M5.5 18.5L7 17 M17 7l1.5-1.5",
        )
    }
    val Sound by lazy { icon("sound", "M4 10v4 M8 7v10 M12 4v16 M16 8v8 M20 10.5v3") }
    val Vibration by lazy { icon("vibration", "M2.5 12.5h3.2l1.6-4.5 3 9.5 3.1-13.5 2.7 10.5 1.5-2h4") }
    val Library by lazy { icon("library", "M3.6 12.5a8.4 8.4 0 1 0 2.5-6.4", "M3.5 4.2v4.3h4.3", "M12 7.8v4.6l3.1 1.9") }
    val Settings by lazy {
        icon("settings", "M3.5 7h10.2 M18.3 7h2.2 M3.5 17h2.2 M10.3 17h10.2", circle(16f, 7f, 2.3f), circle(8f, 17f, 2.3f))
    }
    val Chip by lazy {
        icon(
            "chip", roundRect(7f, 7f, 10f, 10f, 1.6f),
            "M9.7 3.8V7 M14.3 3.8V7 M9.7 17v3.2 M14.3 17v3.2 M3.8 9.7H7 M3.8 14.3H7 M17 9.7h3.2 M17 14.3h3.2",
        )
    }
    val Grid by lazy {
        icon(
            "grid", roundRect(4f, 4f, 6.6f, 6.6f, 1.8f), roundRect(13.4f, 4f, 6.6f, 6.6f, 1.8f),
            roundRect(4f, 13.4f, 6.6f, 6.6f, 1.8f), roundRect(13.4f, 13.4f, 6.6f, 6.6f, 1.8f),
        )
    }
    val ChevronLeft by lazy { icon("chevron_left", "M14.5 5.5L8 12l6.5 6.5", width = 2.1f) }
    val ChevronRight by lazy { icon("chevron_right", "M9.5 5.5L16 12l-6.5 6.5", width = 2f) }
    val ChevronDown by lazy { icon("chevron_down", "M5.5 9.2L12 15.7l6.5-6.5", width = 2f) }
    val Close by lazy { icon("close", "M6.5 6.5l11 11 M17.5 6.5l-11 11", width = 2f) }
    val Check by lazy { icon("check", "M5 12.6l4.4 4.4L19 7.4", width = 2.1f) }
    val Plus by lazy { icon("plus", "M12 5v14 M5 12h14", width = 2f) }
    val Minus by lazy { icon("minus", "M5 12h14", width = 2f) }
    val Camera by lazy {
        icon(
            "camera",
            "M4 8.5A1.5 1.5 0 0 1 5.5 7h2.3l1.4-2h5.6l1.4 2h2.3A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z",
            circle(12f, 12.8f, 3.4f),
        )
    }
    val Photo by lazy {
        icon("photo", roundRect(3f, 5f, 18f, 14f, 2f), "M3.5 16.5l5-5 4 4 3-3 5 5", circle(15.5f, 9.3f, 1.5f))
    }
    val Share by lazy { icon("share", "M12 3.5v11 M8 7.3l4-3.8 4 3.8 M6.5 10.5h-.5A1.5 1.5 0 0 0 4.5 12v6.5A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5V12a1.5 1.5 0 0 0-1.5-1.5h-.5") }
    val Trash by lazy { icon("trash", "M4.5 6.8h15 M9.8 6.8V4.6h4.4v2.2 M6.8 6.8l.9 12.7a1.5 1.5 0 0 0 1.5 1.4h5.6a1.5 1.5 0 0 0 1.5-1.4l.9-12.7 M10.2 10.5v6 M13.8 10.5v6") }
    val Undo by lazy { icon("undo", "M8.8 6.5L4.5 10.8l4.3 4.3", "M4.5 10.8H15a4.6 4.6 0 0 1 0 9.2h-3") }
    val Reset by lazy { icon("reset", "M19.5 12a7.5 7.5 0 1 1-2.2-5.3", "M19.6 4.2v4.3h-4.3") }
    val Target by lazy { icon("target", circle(12f, 12f, 6.8f), "M12 2.5v4 M12 17.5v4 M2.5 12h4 M17.5 12h4", fills = listOf(circle(12f, 12f, 1.4f))) }
    val Lock by lazy { icon("lock", "M7.5 10.5V8a4.5 4.5 0 0 1 9 0v2.5", roundRect(5.5f, 10.5f, 13f, 10f, 2f)) }
    val Unlock by lazy { icon("unlock", "M7.5 10.5V8a4.5 4.5 0 0 1 8.7-1.6", roundRect(5.5f, 10.5f, 13f, 10f, 2f)) }
    val Info by lazy { icon("info", circle(12f, 12f, 8.8f), "M12 10.8v5.6", fills = listOf(circle(12f, 7.7f, 1.05f))) }
    val Sparkles by lazy {
        icon(
            "sparkles",
            "M11 3.5l1.7 4.4 4.4 1.7-4.4 1.7L11 15.7l-1.7-4.4-4.4-1.7 4.4-1.7z",
            "M18 14.5l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z",
        )
    }
    val Pin by lazy { icon("pin", "M12 21s-6.3-6-6.3-10.7a6.3 6.3 0 0 1 12.6 0C18.3 15 12 21 12 21z", circle(12f, 10.2f, 2.3f)) }
    val Flag by lazy { icon("flag", "M5.5 21V3.8 M5.5 4.3h11.5l-2.3 4.2 2.3 4.2H5.5") }
    val Phone by lazy { icon("phone", roundRect(6.5f, 2.5f, 11f, 19f, 2.2f), "M10.5 18.6h3") }
    val Moon by lazy { icon("moon", "M19.8 14.6A8 8 0 0 1 9.4 4.2a8 8 0 1 0 10.4 10.4z") }
    val Sun by lazy {
        icon(
            "sun", circle(12f, 12f, 4f),
            "M12 2.5v1.8 M12 19.7v1.8 M2.5 12h1.8 M19.7 12h1.8 M5.3 5.3l1.3 1.3 M17.4 17.4l1.3 1.3 M5.3 18.7l1.3-1.3 M17.4 6.6l1.3-1.3",
        )
    }
    val Download by lazy { icon("download", "M12 4v11 M8 11.2l4 3.8 4-3.8 M5 19.5h14") }
    val Eye by lazy { icon("eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", circle(12f, 12f, 3f)) }
    val Cylinder by lazy {
        icon("cylinder", "M5 6.2c0-1.7 3.1-3 7-3s7 1.3 7 3-3.1 3-7 3-7-1.3-7-3z", "M5 6.2v11.6c0 1.7 3.1 3 7 3s7-1.3 7-3V6.2")
    }
    val Sphere by lazy { icon("sphere", circle(12f, 12f, 8.8f), "M3.3 12c0 1.9 3.9 3.4 8.7 3.4s8.7-1.5 8.7-3.4") }
    val Speaker by lazy { icon("speaker", "M4 9.5h3l4.5-4v13L7 14.5H4z", "M15 9a4.2 4.2 0 0 1 0 6 M17.6 6.4a7.8 7.8 0 0 1 0 11.2") }
    val Haptic by lazy { icon("haptic", roundRect(7.5f, 4f, 9f, 16f, 1.8f), "M4 9v6 M20 9v6") }
    val Appearance by lazy { icon("appearance", circle(12f, 12f, 8.8f), fills = listOf("M12 3.2a8.8 8.8 0 0 1 0 17.6z")) }
    val Person by lazy { icon("person", circle(12f, 7.8f, 3.6f), "M5 20.5a7 7 0 0 1 14 0") }
    val Pause by lazy { icon("pause", "M8.5 5.5v13 M15.5 5.5v13", width = 2.4f) }
    val Play by lazy { icon("play", fills = listOf("M7.5 5.2v13.6a.8.8 0 0 0 1.2.7l10.6-6.8a.8.8 0 0 0 0-1.4L8.7 4.5a.8.8 0 0 0-1.2.7z")) }
    val Axis by lazy { icon("axis", "M7.5 4v16 M4.6 6.9L7.5 4l2.9 2.9 M16.5 20V4 M13.6 17.1l2.9 2.9 2.9-2.9") }
    val Move by lazy {
        icon(
            "move",
            "M12 3v18 M3 12h18 M9.5 5.5L12 3l2.5 2.5 M9.5 18.5L12 21l2.5-2.5 M5.5 9.5L3 12l2.5 2.5 M18.5 9.5L21 12l-2.5 2.5",
        )
    }
    val Doc by lazy { icon("doc", "M7.5 3h6.8l4.7 4.7v12.8a1.5 1.5 0 0 1-1.5 1.5h-10A1.5 1.5 0 0 1 6 20.5v-16A1.5 1.5 0 0 1 7.5 3z", "M14 3.2V8h4.8") }
    val Pencil by lazy { icon("pencil", "M15.2 4.8l4 4L9.4 18.6l-4.9.9.9-4.9z", "M13.3 6.7l4 4") }
    val Search by lazy { icon("search", circle(10.5f, 10.5f, 6.3f), "M15.2 15.2L20 20") }
    val Layers by lazy { icon("layers", "M12 3.5l8.5 4.6L12 12.7 3.5 8.1z", "M3.5 12.4l8.5 4.6 8.5-4.6 M3.5 16.6l8.5 4.6 8.5-4.6") }
    val Hand by lazy {
        icon(
            "hand",
            "M8.5 12.5V5.8a1.4 1.4 0 0 1 2.8 0v5.4 M11.3 10.8V4.6a1.4 1.4 0 0 1 2.8 0v6.2 M14.1 11V6.2a1.4 1.4 0 0 1 2.8 0v7.3c0 4.2-2.4 7-6.1 7-2.4 0-3.9-1.1-5.1-3.2l-2.2-3.9a1.4 1.4 0 0 1 2.4-1.4l1.6 2.3",
        )
    }
    val Box3d by lazy { Cube }
    val Wave by lazy { icon("wave", "M2.5 12c2.2 0 2.2-5 4.4-5s2.2 10 4.4 10 2.2-10 4.4-10 2.2 5 4.4 5h1.4") }
    val Bolt by lazy { icon("bolt", "M13.2 2.8L5.5 13.5h6l-1 7.7 7.7-10.7h-6z") }
    val Edges by lazy {
        icon("edges", "M12 13L4.5 17.3 M12 13l7.5 4.3 M12 13V4.5", "M3.5 15.6l2 3.4 M20.5 15.6l-2 3.4 M10 4.5h4")
    }
    val Corner by lazy {
        icon("corner", "M12 12L4.5 7.7 M12 12l7.5-4.3 M12 12v8.6", circle(12f, 12f, 4.4f), fills = listOf(circle(12f, 12f, 2f)))
    }
    val Volume by lazy { icon("volume", roundRect(9f, 3f, 6f, 18f, 3f), "M12 7v3 M12 14v3") }
}

/** Renders a [DatumIcons] vector tinted with [tint] (defaults to the primary label color). */
@Composable
fun DIcon(icon: ImageVector, modifier: Modifier = Modifier, tint: Color = Datum.colors.label, size: Dp = 22.dp) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = icon.name,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}
