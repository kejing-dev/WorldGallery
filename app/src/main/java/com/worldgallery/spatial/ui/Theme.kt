package com.worldgallery.spatial.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.systemColorScheme
import java.io.File

/**
 * Semantic colours from the Figma file "World Gallery · PICO Spatial UI UX" (variable collection
 * "World Gallery / Semantic"). Roles that map to a PICO role override it in [GalleryTheme]; the
 * rest are brand tokens used verbatim.
 */
object WG {
    val Ink = Color(0xFF282828)        // design-style: fixed-figma-color color/ink (text, primary button)
    val Secondary = Color(0xFF606260)  // design-style: fixed-figma-color color/secondary
    val White = Color(0xFFFFFFFF)      // design-style: fixed-figma-color color/white
    val Panel = Color(0xFFF1F2EF)      // design-style: fixed-figma-color color/panel (window surface)
    val Line = Color(0xFFD5D7D2)       // design-style: fixed-figma-color color/line
    val Mat = Color(0xFFE6E5E0)        // design-style: fixed-figma-color color/mat (artwork surface / frame mat)
    val Accent = Color(0xFFDDF4B0)     // design-style: fixed-figma-color color/accent (selected chip)
    val Dark = Color(0xFF1C201E)       // design-style: fixed-figma-color color/dark
    val Sage = Color(0xFF899583)       // design-style: fixed-figma-color color/sage
    val CardGlass = Color(0xA6FFFFFF)  // design-style: fixed-figma-color artwork card fill (white 65 %)
    val PanelGlass = Color(0xF2F1F2EF) // design-style: fixed-figma-color window fill (panel 95 %)
    val GhostGlass = Color(0x80FFFFFF) // design-style: fixed-figma-color ghost button (white 50 %)
    val StageTop = Color(0xFF373B32)   // design-style: fixed-figma-color Full Space gradient top
    val StageMid = Color(0xFF252B24)   // design-style: fixed-figma-color Full Space gradient middle
    val StageBottom = Color(0xFF151B17) // design-style: fixed-figma-color Full Space gradient bottom
    val Warn = Color(0xFF8A6100)       // design-style: fixed-figma-color warning text
}

/** PICO Sans ships with PICO OS 6 (`/system/fonts/PICOSans.ttf`); Chinese glyphs fall back to the system SC family. */
val PicoSans: FontFamily by lazy {
    runCatching {
        val file = File("/system/fonts/PICOSans.ttf")
        require(file.exists()) { "PICO Sans not found" }
        FontFamily(
            Font(file, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
            Font(file, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
            Font(file, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
            Font(file, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        )
    }.getOrElse { FontFamily.Default }
}

/** Text styles "World Gallery / *" from the Figma file (size / line height / weight). */
object WGType {
    val display = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 46.sp)
    val headline = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp)
    val title = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 26.sp)
    val label = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 24.sp)
    val caption = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp)
    val small = TextStyle(fontFamily = PicoSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp)
}

private fun TextStyle.pico(): TextStyle = copy(fontFamily = PicoSans)

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val system = systemColorScheme(LocalContext.current)
    val scheme = system.copy(
        fillPrimary = WG.White,            // design-style: fixed-figma-color cards / hero
        fillSecondary = WG.Panel,          // design-style: fixed-figma-color window panel
        fillTertiary = WG.Mat,             // design-style: fixed-figma-color artwork mat
        fillLight = system.fillLight,
        labelPrimaryLight = WG.White,      // design-style: fixed-figma-color text on ink buttons
        labelPrimary = WG.Ink,             // design-style: fixed-figma-color color/ink
        labelSecondary = WG.Secondary,     // design-style: fixed-figma-color color/secondary
        labelTertiary = system.labelTertiary,
        labelQuaternary = system.labelQuaternary,
        lightenHover = system.lightenHover,
        lightenPressed = system.lightenPressed,
        error = system.error,
        alert = WG.Warn,                   // design-style: fixed-figma-color warning
        passable = WG.Sage,                // design-style: fixed-figma-color color/sage
        interaction = WG.Ink,              // design-style: fixed-figma-color PICO Button primary (ink)
        dividerLine = WG.Line,             // design-style: fixed-figma-color color/line
    )
    val base = PicoTheme.typography
    val typography = base.copy(
        bodyLarge = base.bodyLarge.pico(), bodyLargeMultiline = base.bodyLargeMultiline.pico(),
        bodyMedium = base.bodyMedium.pico(), bodyMediumMultiline = base.bodyMediumMultiline.pico(),
        bodySmall = base.bodySmall.pico(), bodyTiny = base.bodyTiny.pico(),
        displayLarge = base.displayLarge.pico(), displayMedium = base.displayMedium.pico(), displaySmall = base.displaySmall.pico(),
        headlineLarge = base.headlineLarge.pico(), headlineMedium = base.headlineMedium.pico(), headlineSmall = base.headlineSmall.pico(),
        labelLarge = base.labelLarge.pico(), labelMedium = base.labelMedium.pico(), labelSmall = base.labelSmall.pico(),
        titleLarge = base.titleLarge.pico(), titleMedium = base.titleMedium.pico(), titleSmall = base.titleSmall.pico(),
    )
    PicoTheme(colorScheme = scheme, typography = typography, content = content)
}
