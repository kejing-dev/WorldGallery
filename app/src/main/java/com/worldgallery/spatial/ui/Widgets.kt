package com.worldgallery.spatial.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.IconButtonDefaults
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.scene.Bitmaps

/**
 * Click behaviour for custom containers (cards, artwork surfaces, links): PICO indication (hover,
 * press, click audio) plus controller haptics on one shared interaction source (design-style R6).
 */
@Composable
fun Modifier.galleryClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
        .controllerHapticFeedback(interactionSource = interactionSource)
}

/** Official PICO button instance in the Figma file, filled with the ink colour ("PICO Button / primary"). */
@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        size = if (small) ButtonDefaults.Small else ButtonDefaults.Regular,
        colors = ButtonDefaults.buttonColors(containerColor = WG.Ink, contentColor = WG.White),
    ) { Text(text, style = WGType.label) } // design-style: inherited-content-color Button
}

/** "ghost" (white 50 %) and "selected" (accent) variants of the same PICO button. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false, small: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        size = if (small) ButtonDefaults.Small else ButtonDefaults.Regular,
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) WG.Accent else WG.White, contentColor = WG.Ink),
    ) { Text(text, style = WGType.label) } // design-style: inherited-content-color Button
}

/** 48 dp white disc with a stroke glyph ("Control / *" in Figma). */
@Composable
fun ControlButton(icon: WGIcon, label: String, enabled: Boolean = true, active: Boolean = false, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = label },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(if (active) WG.Accent else WG.White, WG.Ink),
        size = IconButtonDefaults.Regular,
    ) {
        Glyph(icon, tint = WG.Ink.copy(alpha = if (enabled) 1f else 0.3f), size = 24.dp, fill = icon == WGIcon.PLAY)
    }
}

@Composable
fun BarDivider() {
    Box(Modifier.width(1.dp).height(28.dp).background(WG.Line))
}

/** Decodes a bundled image once and draws it with the requested fit; the box keeps its size while loading. */
@Composable
fun AssetImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit, contentDescription: String? = null) {
    val context = LocalContext.current
    val decoded by produceState<ImageBitmap?>(initialValue = null, path) { value = Bitmaps.image(context, path) }
    // The last picture stays on screen while the next one decodes, then the two cross-fade: switching works
    // never shows a blank mat. "Reduce motion" makes it a plain cut.
    var shown by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(decoded) { if (decoded != null) shown = decoded }
    val reduceMotion = remember { GalleryViewModel.get(context) }.reduceMotion
    Box(modifier, contentAlignment = Alignment.Center) {
        Crossfade(targetState = shown, animationSpec = tween(if (reduceMotion) 0 else CROSSFADE_MS), label = "artwork") { bmp ->
            bmp?.let { Image(bitmap = it, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = contentScale) }
        }
    }
}

/** One duration for every picture switch — the windows (Compose) and the Stage (material opacity) use the same. */
const val CROSSFADE_MS = 450

/** "World Gallery / Artwork Card": 246 × 240, uncropped image, PICO type. Click starts viewing (never opens the info window). */
@Composable
fun ArtworkCard(title: String, meta: String, imagePath: String?, modifier: Modifier = Modifier, width: Dp = 246.dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .width(width)
            .height(240.dp)
            .clip(shape)
            .background(WG.CardGlass)
            .spatialHoverEffect()
            .galleryClickable(onClick = onClick)
            .semantics { contentDescription = title }
            .padding(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        if (imagePath != null) AssetImage(imagePath, Modifier.fillMaxWidth().height(132.dp))
        else SculpturePlaceholder(Modifier.fillMaxWidth().height(132.dp))
        Text(title, color = WG.Ink, style = WGType.label, maxLines = 1)
        Text(meta, color = WG.Secondary, style = WGType.caption, maxLines = 2)
    }
}

/** Neutral stand-in for a sculpture whose scan has no preview picture yet. */
@Composable
fun SculpturePlaceholder(modifier: Modifier = Modifier, label: String? = null) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(WG.Mat), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(WGIcon.CUBE, tint = WG.Sage, size = 40.dp)
            if (label != null) {
                Spacer(Modifier.height(8.dp))
                Text(label, color = WG.Secondary, style = WGType.caption, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(WG.Line))
}

/** Label / value pair of the fact card ("创作年代    1889"). */
@Composable
fun FactRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = WG.Secondary, style = WGType.body, modifier = Modifier.width(96.dp))
        Text(value, color = WG.Ink, style = WGType.body)
    }
}

/** Translucent pill for transient feedback (favourite added, frame set, ...). */
@Composable
fun ToastPill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(22.dp)).background(WG.Ink.copy(alpha = 0.86f)).padding(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, color = WG.White, style = WGType.caption) }
}

val Transparent: Color = Color.Transparent
