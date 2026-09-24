package com.worldgallery.spatial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.app.InfoBinding
import com.worldgallery.spatial.data.Artwork
import com.worldgallery.spatial.data.ModelAsset
import com.worldgallery.spatial.data.tr
import com.worldgallery.spatial.scene.ModelHolder
import com.worldgallery.spatial.scene.ModelLoadState
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** 03 · 单件观赏 / 08 · 雕塑单件观赏: one work in the fixed viewing area, title and edition below. */
@Composable
fun ViewerPage(vm: GalleryViewModel) {
    val a = vm.current
    if (a == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(tr("从首页选择一件作品开始观赏。", "Choose a work on the home page to start viewing."), color = WG.Secondary, style = WGType.body)
        }
        return
    }
    val asset: ModelAsset? = a.model
    Column(
        Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 72.dp),   // bottom: the window-level Toolbar overlaps the lower edge
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (asset != null) SculptureSurface(vm, a, asset) else PaintingSurface(vm, a)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(a.title, color = WG.Ink, style = WGType.title)
            Text(
                if (a.is3D) tr("复制件扫描 · ", "Cast scan · ") + a.facts.museum else a.facts.museum,
                color = WG.Secondary, style = WGType.caption,
            )
        }
        if (vm.queueFinished) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("本组已看完。", "You have seen every work in this group."), color = WG.Ink, style = WGType.body)
                GhostButton(tr("重看", "Watch again"), small = true) { vm.restartQueue() }
                GhostButton(tr("换一组", "Choose another"), small = true) { vm.back() }
            }
        }
        if (asset != null) SculptureActions(vm, a, asset)
    }
}

/** WG-04 / WG-13: uncropped image on a plain mat; drag pans, buttons or pinch zoom, tap opens the info window. */
@Composable
private fun PaintingSurface(vm: GalleryViewModel, a: Artwork) {
    val img = a.image ?: return
    val vs = vm.viewState(a.id)
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(WG.Mat).padding(20.dp)) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val dispW = minOf(boxW, boxH * a.aspect)
        val dispH = dispW / a.aspect
        val atLimit = vm.atDetailLimit(a, maxOf(dispW, dispH))
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(a.id) { detectTapGestures(onTap = { vm.openInfo(InfoBinding.VIEWER) }) }
                .pointerInput(a.id) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (dispW > 0f && dispH > 0f) vm.panBy(a.id, drag.x / dispW, drag.y / dispH)
                    }
                },
        ) {
            AssetImage(
                img.path,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = vs.zoom; scaleY = vs.zoom
                    translationX = vs.panX * dispW; translationY = vs.panY * dispH
                },
                contentDescription = a.title,
            )
        }
        if (vs.zoom > 1.01f) {
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                ToastPill(
                    "${(vs.zoom * 100).roundToInt()}% · " + tr("拖动画面查看局部", "Drag to explore details") +
                        if (atLimit) " · " + tr("已到源图细节上限", "Source detail limit reached") else "",
                )
            }
        }
    }
}

/** 07 · 雕塑 Load 3D → 08 · 雕塑单件观赏: preview + load states, then the live model. */
@Composable
private fun SculptureSurface(vm: GalleryViewModel, a: Artwork, asset: ModelAsset) {
    val state = vm.modelState(asset)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(WG.Mat)) {
        if (state is ModelLoadState.Ready) {
            ModelView(vm, a, asset, state.file)
        } else {
            val preview = a.previewPath ?: a.image?.path
            if (preview != null) AssetImage(preview, Modifier.fillMaxSize().alpha(0.55f)) else SculpturePlaceholder(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    ModelLoadState.NotLoaded -> Text(tr("点击「加载 3D 模型」后按需加载；此画面为模型预览", "Select Load 3D to download the model. This image is a preview."), color = WG.Ink, style = WGType.body, textAlign = TextAlign.Center)
                    is ModelLoadState.Downloading -> {
                        val f = state.fraction
                        Text(tr("下载中", "Downloading") + (f?.let { " · ${(it * 100).roundToInt()}%" } ?: "") + " · " + sizeText(state.received), color = WG.Ink, style = WGType.body)
                        if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.width(320.dp)) else CircularProgressIndicator()
                    }
                    ModelLoadState.Verifying -> { Text(tr("校验中", "Verifying"), color = WG.Ink, style = WGType.body); CircularProgressIndicator() }
                    ModelLoadState.LoadingEntity -> { Text(tr("加载中", "Loading"), color = WG.Ink, style = WGType.body); CircularProgressIndicator() }
                    is ModelLoadState.Failed -> Text(state.reason, color = WG.Warn, style = WGType.body, textAlign = TextAlign.Center)
                    is ModelLoadState.Ready -> {}
                }
            }
        }
    }
}

private fun sizeText(bytes: Long): String = if (bytes > 1_000_000) "%.1f MB".format(bytes / 1_000_000f) else "${bytes / 1000} KB"

/** The model lives inside the planar window's SpatialView; drag rotates, two-hand pinch scales (PRD 5.3). */
@Composable
private fun ModelView(vm: GalleryViewModel, a: Artwork, asset: ModelAsset, file: File) {
    val context = LocalContext.current
    val converter = LocalPhysicalLengthConverter.current
    val holder = remember(file) { ModelHolder() }
    var loading by remember(file) { mutableStateOf(true) }
    val sceneRoot = remember(file) { Entity().apply { setName("viewerRoot") } }
    DisposableEffect(holder) { onDispose { holder.destroy(); runCatching { sceneRoot.destroy(true) } } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightM = with(converter) { converter.dpToLength(maxHeight, LengthUnit.Meters) }
        val widthM = with(converter) { converter.dpToLength(maxWidth, LengthUnit.Meters) }
        val depthM = with(converter) { converter.dpToLength(640.dp, LengthUnit.Meters) }   // Planar window depth
        val targetHeight = minOf(heightM * 0.78f, asset.displayHeight)
        SpatialView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(a.id) {
                    // PRD 5.5: a tap on the work in the viewing area opens its information window.
                    detectSpatialTapGesture(context, TargetEntity.any()) { vm.openInfo(InfoBinding.VIEWER) }
                }
                .pointerInput(a.id) {
                    detectSpatialDragGesture(context = context, targetedToEntity = TargetEntity.any()) { v ->
                        vm.rotateBy(a.id, v.dragAmount.x * 0.35f, v.dragAmount.y * 0.18f)
                    }
                }
                .pointerInput(a.id) {
                    detectSpatialScaleGesture(context) { s -> vm.scaleBy(a.id, s.scaleValue) }
                },
            initial = { content, _ ->
                content.addEntity(sceneRoot)
                try {
                    holder.load(sceneRoot, file, targetHeight, Vector3(0f, 0f, 0f), maxWidth = widthM * 0.85f, maxDepth = depthM * 0.8f)
                    holder.apply(vm.viewState(a.id), asset.frontYaw)
                    loading = false
                    vm.prefetchNextModel()
                } catch (e: Exception) {
                    android.util.Log.e("WorldGallery.Viewer", "model load failed", e)
                    vm.models.setState(asset, ModelLoadState.Failed(tr("模型无法解析：", "Could not read the model: ") + (e.message ?: e.javaClass.simpleName), retryable = false))
                }
            },
        )
        val vs = vm.viewState(a.id)
        LaunchedEffect(holder, vs, loading) { if (!loading) holder.apply(vs, asset.frontYaw) }
        if (loading) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text(tr("加载中", "Loading"), color = WG.Ink, style = WGType.caption)
            }
        }
    }
}

@Composable
private fun SculptureActions(vm: GalleryViewModel, a: Artwork, asset: ModelAsset) {
    val state = vm.modelState(asset)
    val vs = vm.viewState(a.id)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        when (state) {
            ModelLoadState.NotLoaded -> {
                PrimaryButton(tr("加载 3D 模型", "Load 3D")) { vm.load3D(asset) }
                Text(tr("按需下载 · 可取消与重试", "Download on demand · Cancel or retry"), color = WG.Secondary, style = WGType.caption)
            }
            is ModelLoadState.Downloading, ModelLoadState.Verifying -> {
                GhostButton(tr("取消", "Cancel")) { vm.cancel3D(asset) }
                Text(tr("网络进度与本地解码分开显示", "Network progress and decoding are shown separately"), color = WG.Secondary, style = WGType.caption)
            }
            ModelLoadState.LoadingEntity -> Text(tr("加载中…", "Loading…"), color = WG.Secondary, style = WGType.caption)
            is ModelLoadState.Failed -> {
                if (state.retryable) PrimaryButton(tr("重试", "Retry")) { vm.retry3D(asset) }
                else GhostButton(tr("重新检查文件", "Check again")) { vm.models.forget(asset) }
                Text(asset.licenseLabel + " · " + asset.credit, color = WG.Secondary, style = WGType.caption, modifier = Modifier.width(620.dp))
            }
            is ModelLoadState.Ready -> {
                fun near(deg: Float) = abs(((vs.yaw - deg) % 360f + 540f) % 360f - 180f) < 15f
                GhostButton(tr("正面", "Front"), selected = near(0f) && vs.isDefault) { vm.setYaw(a.id, 0f) }
                GhostButton(tr("侧面", "Side"), selected = near(90f)) { vm.setYaw(a.id, 90f) }
                GhostButton(tr("背面", "Back"), selected = near(180f)) { vm.setYaw(a.id, 180f) }
                GhostButton(tr("重置视图", "Reset view")) { vm.resetView(a.id) }
            }
        }
    }
}
