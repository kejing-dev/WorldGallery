package com.worldgallery.spatial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stroke icons exactly as drawn in the Figma file (24-unit view box, 1.7 stroke, round caps). */
enum class WGIcon(val d: String) {
    BACK("M15 18l-6-6 6-6"),
    NEXT("M9 18l6-6-6-6"),
    CLOSE("M6 6l12 12M18 6L6 18"),
    SEARCH("M21 21l-5-5M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0"),
    PLUS("M12 5v14M5 12h14"),
    MINUS("M5 12h14"),
    PAUSE("M8 5v14M16 5v14"),
    PLAY("M8 5l11 7-11 7z"),
    INFO("M12 11v6M12 7v.1M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0"),
    HEART("M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8"),
    FRAME("M4 3h16v18H4zM7 6h10v12H7z"),
    EXPAND("M8 3H3v5M16 3h5v5M21 16v5h-5M8 21H3v-5"),
    RESET("M3 11a9 9 0 1 1 2.7 7M3 4v7h7"),
    CHECK("M5 12l4 4L19 6"),
    MORE("M5 12h.1M12 12h.1M19 12h.1"),
    CUBE("M12 3l9 5v8l-9 5-9-5V8zM3 8l9 5 9-5M12 13v8"),
    REFRESH("M20 7v5h-5M4 17v-5h5M6 6a8 8 0 0 1 13 2M18 18a8 8 0 0 1-13-2"),
    LANG("M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20M2 12h20M12 2c3 3 3 17 0 20M12 2c-3 3-3 17 0 20"),
}

@Composable
fun Glyph(icon: WGIcon, tint: Color, size: Dp = 24.dp, fill: Boolean = false, modifier: Modifier = Modifier) {
    val path: Path = remember(icon) { runCatching { PathParser().parsePathString(icon.d).toPath() }.getOrElse { Path() } }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.width / 24f
        scale(scaleX = s, scaleY = s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (fill) drawPath(path, tint)
            drawPath(path, tint, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
