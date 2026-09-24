package com.worldgallery.spatial.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.design.windows.SubwindowPlacement
import com.pico.spatial.ui.design.windows.Toolbar
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.app.InfoBinding
import com.worldgallery.spatial.data.Catalog
import com.worldgallery.spatial.data.FrameMode
import com.worldgallery.spatial.data.FrameSettings
import com.worldgallery.spatial.data.QueueSource
import com.worldgallery.spatial.data.tr

/**
 * 09–13 · 画框: an independent Shared-Space window that shows one published painting, fixed or as
 * a slideshow (WG-18). The window owns its lifecycle; the slideshow pauses while it is minimized,
 * while its info window is open and while the immersive Stage is active.
 */
@Composable
fun FrameWindow(sizeModifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm = remember { GalleryViewModel.get(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        vm.onFrameWindowOpened()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> vm.onFrameVisibility(true)
                Lifecycle.Event.ON_STOP -> vm.onFrameVisibility(false)
                Lifecycle.Event.ON_DESTROY -> vm.onFrameWindowClosed()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); vm.onFrameWindowClosed() }
    }
    val a = vm.frameArtwork

    // "Art Frame / physical border" (ink) → "Mat" → uncropped painting. design-style: opaque-root — the
    // container is declared with enableMaterialBackground = false.
    Box(sizeModifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(WG.Ink).padding(10.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(5.dp))
                .background(WG.Mat)
                .padding(38.dp)
                .pointerInput(a?.id) { detectTapGestures(onTap = { if (a != null) vm.openInfo(InfoBinding.FRAME) }) },
            contentAlignment = Alignment.Center,
        ) {
            val img = a?.image
            if (img != null) AssetImage(img.path, Modifier.fillMaxSize(), contentDescription = a.title)
            else Text(tr("在观赏页点击画框图标，把一幅名画留在这里。", "Tap the frame icon while viewing to keep a painting here."), color = WG.Secondary, style = WGType.body, textAlign = TextAlign.Center)
        }
    }

    Toolbar(cornerSize = 40.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            // Playback transport lives in the settings panel; the bar keeps what you use on a frame you
            // just look at: what it is, one-tap size, details, settings.
            Column(Modifier.width(256.dp)) {
                Text(a?.title ?: tr("画框", "Art frame"), color = WG.Ink, style = WGType.label, maxLines = 1)
                Text(frameStatus(vm), color = WG.Secondary, style = WGType.caption, maxLines = 1)
            }
            ControlButton(WGIcon.MINUS, tr("缩小画框", "Smaller frame"), enabled = vm.canShrinkFrame) { vm.stepFrameSize(-1) }
            Text(vm.sizeLabel(), color = WG.Secondary, style = WGType.caption)
            ControlButton(WGIcon.PLUS, tr("放大画框", "Larger frame"), enabled = vm.canEnlargeFrame) { vm.stepFrameSize(1) }
            ControlButton(WGIcon.INFO, tr("作品介绍", "About"), enabled = a != null, active = vm.infoBinding == InfoBinding.FRAME) {
                if (vm.infoBinding == InfoBinding.FRAME) vm.closeInfo() else vm.openInfo(InfoBinding.FRAME)
            }
            ControlButton(WGIcon.MORE, tr("画框设置", "Frame settings"), active = vm.frameSettingsOpen) { vm.frameSettingsOpen = !vm.frameSettingsOpen }
        }
    }

    when {
        vm.frameSettingsOpen -> Subwindow(placement = SubwindowPlacement.Right) { FrameSettingsContent(vm) }
        vm.infoBinding == InfoBinding.FRAME && a != null -> Subwindow(placement = SubwindowPlacement.Right) { InfoContent(vm, a, InfoBinding.FRAME) }
    }
}

/** "近看这幅": the Gallery viewer opens this work; the frame keeps showing what it shows (PRD 5.7). */
fun exploreFromFrame(vm: GalleryViewModel, artworkId: String) {
    val src = vm.frame.playlistSource
    val ids = when (src) {
        is QueueSource.FromCollection -> Catalog.collection(src.collectionId)?.artworkIds
        QueueSource.Favorites -> vm.favorites.toList()
        is QueueSource.Search -> Catalog.search(src.query).map { it.id }
        null -> Catalog.collections.firstOrNull { it.artworkIds.contains(artworkId) }?.artworkIds
    }?.ifEmpty { null } ?: listOf(artworkId)
    val source = src ?: Catalog.collections.firstOrNull { it.artworkIds.contains(artworkId) }?.let { QueueSource.FromCollection(it.id) } ?: QueueSource.FromCollection("essentials")
    vm.startQueue(source, ids, ids.indexOf(artworkId).coerceAtLeast(0))
}

fun frameStatus(vm: GalleryViewModel): String {
    val f = vm.frame
    if (f.mode == FrameMode.FIXED || f.playlist.size <= 1) return tr("固定展示", "Fixed artwork")
    val base = (f.playlistSource?.label() ?: tr("名画", "Paintings")) + " · ${f.index + 1} / ${f.playlist.size} · " + tr("每 ${f.intervalMinutes} 分钟", "Every ${f.intervalMinutes} min")
    return when {
        !f.playing -> tr("已暂停 · 当前画面保持不变", "Paused · Keeping this work on display")
        vm.infoBinding == InfoBinding.FRAME -> tr("阅读中暂停 · 收起后继续循环", "Paused for reading · Resume on close")
        vm.immersiveActive -> tr("沉浸观赏中 · 已暂停", "Paused during immersive viewing")
        !vm.frameVisible -> tr("窗口未显示 · 已暂停", "Window hidden · Paused")
        else -> base
    }
}

/** 11 · 画框：播放设置 (Figma 8:1567). */
@Composable
fun FrameSettingsContent(vm: GalleryViewModel) {
    val f = vm.frame
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("画框设置", "Frame settings"), color = WG.Ink, style = WGType.title, modifier = Modifier.weight(1f))
            ControlButton(WGIcon.CLOSE, tr("关闭", "Close")) { vm.frameSettingsOpen = false }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (f.mode == FrameMode.LOOP && f.playlist.size > 1) {
                Text(tr("播放控制", "Playback"), color = WG.Ink, style = WGType.label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ControlButton(WGIcon.BACK, tr("上一幅", "Previous")) { vm.framePrev() }
                    ControlButton(if (f.playing) WGIcon.PAUSE else WGIcon.PLAY, if (f.playing) tr("暂停", "Pause") else tr("继续", "Play")) { vm.frameSetPlaying(!f.playing) }
                    ControlButton(WGIcon.NEXT, tr("下一幅", "Next")) { vm.frameNext() }
                }
                Divider()
            }
            Text(tr("展示方式", "Display mode"), color = WG.Ink, style = WGType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(tr("固定一幅", "Single work"), selected = f.mode == FrameMode.FIXED, small = true) { vm.setFrameMode(FrameMode.FIXED) }
                GhostButton(tr("循环播放", "Slideshow"), selected = f.mode == FrameMode.LOOP, small = true) { vm.setFrameMode(FrameMode.LOOP) }
            }
            Divider()
            Text(tr("播放内容", "Art collection"), color = WG.Ink, style = WGType.label)
            Text(f.playlistSource?.label() ?: tr("尚未选择", "Not chosen yet"), color = WG.Ink, style = WGType.title)
            Text(tr("${f.playlist.size} 幅名画 · 保留完整构图", "${f.playlist.size} paintings · Fit entire artwork"), color = WG.Secondary, style = WGType.caption)
            Text(tr("更换合集", "Change collection"), color = WG.Secondary, style = WGType.caption)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in Catalog.featured()) {
                    GhostButton(c.title, selected = (f.playlistSource as? QueueSource.FromCollection)?.collectionId == c.id, small = true, modifier = Modifier.fillMaxWidth()) { vm.loopInFrame(QueueSource.FromCollection(c.id)) }
                }
                GhostButton(tr("我的收藏", "Favorites"), selected = f.playlistSource == QueueSource.Favorites, small = true, enabled = vm.favorites.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { vm.loopInFrame(QueueSource.Favorites) }
            }
            Divider()
            Text(tr("换画间隔", "Change artwork every"), color = WG.Ink, style = WGType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (m in FrameSettings.INTERVALS) GhostButton(tr("$m 分钟", "$m min"), selected = f.intervalMinutes == m, small = true) { vm.setFrameInterval(m) }
            }
            Text(tr("播放顺序", "Playback order"), color = WG.Ink, style = WGType.label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(tr("顺序播放", "In order"), selected = !f.shuffle, small = true) { vm.setFrameShuffle(false) }
                GhostButton(tr("随机播放", "Shuffle"), selected = f.shuffle, small = true) { vm.setFrameShuffle(true) }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(tr("完成", "Done"), Modifier.fillMaxWidth()) { vm.frameSettingsOpen = false }
            Spacer(Modifier.height(16.dp))
        }
    }
}
