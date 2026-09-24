package com.worldgallery.spatial.ui

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TitleBar
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem
import com.pico.spatial.ui.design.windows.AlertDialog
import com.pico.spatial.ui.design.windows.Sheet
import com.pico.spatial.ui.design.windows.Subwindow
import com.pico.spatial.ui.design.windows.SubwindowPlacement
import com.pico.spatial.ui.design.windows.TabBar
import com.pico.spatial.ui.design.windows.Toolbar
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.app.InfoBinding
import com.worldgallery.spatial.app.Page
import com.worldgallery.spatial.app.Tab
import com.worldgallery.spatial.data.Catalog
import com.worldgallery.spatial.data.Lang
import com.worldgallery.spatial.data.tr
import kotlinx.coroutines.launch

private const val TAG = "WorldGallery.Main"

/** Main browsing window ("Gallery / Main Window", 1134 × 748 dp): pages, tab bar, viewing toolbar, info subwindow. */
@Composable
fun GalleryWindow() {
    val context = LocalContext.current
    val vm = remember { GalleryViewModel.get(context) }
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()

    // This window is closed while a Stage is open and reopened afterwards; tell the model it is back.
    LaunchedEffect(Unit) { vm.onMainWindowRestored() }
    // The frame is a second WindowContainer of this app; reuse mode via the fixed tag (PRD 5.7: never a second frame).
    // Requests are consumed once, so a recreated window does not reopen a frame the user has closed.
    LaunchedEffect(vm.frameOpenRequest) {
        val req = vm.frameOpenRequest
        if (req > vm.frameOpenHandled) {
            vm.frameOpenHandled = req
            runCatching { navigator.openWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }
                .onFailure { Log.e(TAG, "openWindowContainer(frame) failed", it) }
        }
    }

    val openImmersive: () -> Unit = {
        scope.launch {
            val result = runCatching { navigator.openStage(GalleryViewModel.STAGE_ID, StageStyle.Full) }
                .getOrElse { OpenStageResult.Error(-1, it.message ?: it.javaClass.simpleName) }
            Log.i(TAG, "openStage → $result")
            when (result) {
                OpenStageResult.Allowed -> vm.onImmersiveOpened()   // the Stage closes this window itself and reopens it on exit
                OpenStageResult.NotAllowed -> vm.showToast(tr("系统暂不允许进入沉浸观赏", "Immersive viewing is not allowed right now"))
                is OpenStageResult.Error -> vm.showToast(tr("进入沉浸观赏失败：", "Could not enter immersive viewing: ") + result.reason)
            }
        }
    }

    // design-style: opaque-root — the launcher activity sets pico.spatial.windowcontainer.materialbackground="0";
    // the Figma window is an opaque "panel" surface (F1F2EF @ 95 %) rather than glass.
    Box(Modifier.fillMaxSize().background(WG.PanelGlass)) {
        Column(Modifier.fillMaxSize()) {
            WindowHeader(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val p = vm.page) {
                    Page.Home -> HomePage(vm)
                    is Page.CollectionPage -> CollectionPage(vm, p.id)
                    Page.Viewer -> ViewerPage(vm)
                    Page.Favorites -> FavoritesPage(vm)
                    Page.Search -> SearchPage(vm)
                }
            }
        }
        vm.toast?.let { Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { ToastPill(it) } }
    }

    if (vm.page == Page.Viewer) ViewerToolbar(vm, openImmersive) else GalleryTabBar(vm)

    if (vm.infoBinding == InfoBinding.VIEWER) {
        vm.infoArtwork?.let { a -> Subwindow(placement = SubwindowPlacement.Right) { InfoContent(vm, a, InfoBinding.VIEWER) } }
    }
    if (vm.immersiveHintOpen) ImmersiveHintSheet(vm) { vm.confirmImmersiveHint(); openImmersive() }
    vm.frameReplaceCandidate?.let { FrameReplaceDialog(vm, it) }
}

@Composable
private fun WindowHeader(vm: GalleryViewModel) {
    val page = vm.page
    val (title, subtitle) = when (page) {
        Page.Home -> "World Gallery" to tr("世界名作，近在眼前", "World masterpieces, within reach")
        is Page.CollectionPage -> Catalog.collection(page.id)?.let { it.title to it.subtitle } ?: (page.id to "")
        Page.Viewer -> (vm.session?.source?.label() ?: "") to (vm.current?.let { it.artist.name + " · " + it.facts.date } ?: "")
        Page.Favorites -> tr("我的收藏", "Favorites") to tr("${vm.favorites.size} 件作品", "${vm.favorites.size} works")
        Page.Search -> tr("搜索", "Search") to tr("作品、作者与馆藏", "Works, artists and museums")
    }
    val showBack = page != Page.Home && page != Page.Favorites && !(page is Page.CollectionPage && page.id == "classic-sculpture" && vm.tab == Tab.SCULPTURE)
    TitleBar(
        title = {
            Column {
                Text(title, color = WG.Ink, style = WGType.title, maxLines = 1)
                if (subtitle.isNotEmpty()) Text(subtitle, color = WG.Secondary, style = WGType.caption, maxLines = 1)
            }
        },
        leadingActions = { if (showBack) ControlButton(WGIcon.BACK, tr("返回", "Back")) { vm.back() } },
        trailingActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (page != Page.Search) ControlButton(WGIcon.SEARCH, tr("搜索", "Search")) { vm.openSearch() }
                GhostButton(if (Lang.isZh) "English" else "中文", small = true) { vm.toggleLocale() }
                Box {
                    ControlButton(WGIcon.MORE, tr("更多", "More"), active = vm.menuOpen) { vm.menuOpen = true }
                    if (vm.menuOpen) {
                        Menu(onDismissRequest = { vm.menuOpen = false }) {
                            MenuItem(title = { Text(tr("我的画框", "My frame"), color = WG.Ink, style = WGType.body) }, onClick = { vm.menuOpen = false; vm.openMyFrame() })
                            MenuItem(title = { Text(tr("从第一件开始：世界名画", "Start from the first: masterpieces"), color = WG.Ink, style = WGType.body) }, onClick = {
                                vm.menuOpen = false
                                Catalog.collection("essentials")?.let { vm.startQueue(com.worldgallery.spatial.data.QueueSource.FromCollection(it.id), it.artworkIds) }
                            })
                            MenuItem(title = { Text(if (vm.reduceMotion) tr("减少动效：开", "Reduce motion: on") else tr("减少动效：关", "Reduce motion: off"), color = WG.Ink, style = WGType.body) }, onClick = { vm.updateReduceMotion(!vm.reduceMotion) })
                            MenuItem(title = { Text(tr("沉浸观赏说明", "About immersive viewing"), color = WG.Ink, style = WGType.body) }, onClick = { vm.menuOpen = false; vm.immersiveHintOpen = true })
                        }
                    }
                }
            }
        },
    )
}

/** Window-level navigation: 名画 · 雕塑 · 收藏 (official PICO Tab Bar instance in the Figma file). */
@Composable
private fun GalleryTabBar(vm: GalleryViewModel) {
    TabBar {
        item(selected = vm.tab == Tab.PAINTINGS, onClick = { vm.selectTab(Tab.PAINTINGS) },
            mainContent = { Glyph(WGIcon.FRAME, tint = WG.Ink) }, supportContent = { Text(tr("名画", "Paintings"), color = WG.Ink, style = WGType.caption) })
        item(selected = vm.tab == Tab.SCULPTURE, onClick = { vm.selectTab(Tab.SCULPTURE) },
            mainContent = { Glyph(WGIcon.CUBE, tint = WG.Ink) }, supportContent = { Text(tr("雕塑", "Sculpture"), color = WG.Ink, style = WGType.caption) })
        item(selected = vm.tab == Tab.FAVORITES, onClick = { vm.selectTab(Tab.FAVORITES) },
            mainContent = { Glyph(WGIcon.HEART, tint = WG.Ink) }, supportContent = { Text(tr("收藏", "Favorites"), color = WG.Ink, style = WGType.caption) })
    }
}

/** "Spatial viewing controls": 上一件 · 3 of 8 · 下一件 | − + 看全貌 ♡ i 画框 沉浸 (PRD 5.4). */
@Composable
private fun ViewerToolbar(vm: GalleryViewModel, openImmersive: () -> Unit) {
    val a = vm.current ?: return
    val s = vm.session ?: return
    val vs = vm.viewState(a.id)
    val is3D = a.is3D
    Toolbar(cornerSize = 40.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            ControlButton(WGIcon.BACK, tr("上一件", "Previous"), enabled = s.hasPrev) { vm.prev() }
            Text("${s.index + 1} / ${s.ids.size}", color = WG.Ink, style = WGType.label)
            ControlButton(WGIcon.NEXT, tr("下一件", "Next")) { if (!vm.next()) vm.showToast(tr("本组已看完", "End of this group")) }
            BarDivider()
            ControlButton(WGIcon.MINUS, tr("缩小", "Zoom out"), enabled = if (is3D) vs.scale > GalleryViewModel.MIN_SCALE else vs.zoom > GalleryViewModel.MIN_ZOOM) {
                if (is3D) vm.scaleBy(a.id, 1f / 1.25f) else vm.zoomStep(a.id, -1)
            }
            ControlButton(WGIcon.PLUS, tr("放大", "Zoom in"), enabled = if (is3D) vs.scale < GalleryViewModel.MAX_SCALE else vs.zoom < GalleryViewModel.MAX_ZOOM) {
                if (is3D) vm.scaleBy(a.id, 1.25f) else vm.zoomStep(a.id, 1)
            }
            GhostButton(tr("看全貌", "Fit"), small = true, enabled = !vs.isDefault) { vm.resetView(a.id) }
            ControlButton(WGIcon.HEART, tr("收藏", "Favorite"), active = vm.isFavorite(a.id)) { vm.toggleFavorite(a.id) }
            ControlButton(WGIcon.INFO, tr("作品介绍", "About"), active = vm.infoBinding == InfoBinding.VIEWER) {
                if (vm.infoBinding == InfoBinding.VIEWER) vm.closeInfo() else vm.openInfo(InfoBinding.VIEWER)
            }
            ControlButton(WGIcon.FRAME, tr("设为画框", "Set as frame"), enabled = a.image != null) { vm.setAsFrame(a.id) }
            ControlButton(WGIcon.EXPAND, tr("沉浸观赏", "Immerse")) { if (vm.requestImmersive()) openImmersive() }
        }
    }
}

/** One-time explanation shown in Shared Space before the first Full Space entry (PRD 5.6: no in-scene hints). */
@Composable
private fun ImmersiveHintSheet(vm: GalleryViewModel, onEnter: () -> Unit) {
    Sheet(
        onDismissRequest = { vm.immersiveHintOpen = false },
        title = { Text(tr("沉浸观赏", "Immersive viewing"), color = WG.Ink, style = WGType.title, fontWeight = FontWeight.SemiBold) },
        bottom = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(tr("先不进入", "Not now")) { vm.immersiveHintOpen = false }
                Spacer(Modifier.width(12.dp))
                PrimaryButton(tr("进入沉浸观赏", "Enter")) { onEnter() }
            }
        },
    ) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("画面只有安静的场景、当前作品和作品下方的一条小控制条。", "The scene shows the current work with one small control strip beneath it."), color = WG.Ink, style = WGType.body)
            Text(tr("· 控制条或作品左右两侧：上一件 / 下一件", "· Control strip, or tap left / right of the work: previous / next"), color = WG.Ink, style = WGType.body)
            Text(tr("· 拖动作品：平移画面或转动雕塑；双手捏合缩放", "· Drag the work to pan or rotate; pinch with both hands to zoom"), color = WG.Ink, style = WGType.body)
            Text(tr("· 「回到画廊」或系统返回：退出，画廊窗口回到同一作品", "· \"Back to gallery\" or system Back: exit; the gallery window returns on the same work"), color = WG.Ink, style = WGType.body)
            Spacer(Modifier.height(4.dp))
            Text(tr("阅读介绍、收藏和画框设置在这里完成。", "Details, favorites and frame settings stay in Shared Space."), color = WG.Secondary, style = WGType.caption)
        }
    }
}

@Composable
private fun FrameReplaceDialog(vm: GalleryViewModel, artworkId: String) {
    val a = Catalog.byId[artworkId] ?: return
    AlertDialog(
        onDismissRequest = { vm.cancelFrameReplace() },
        title = { Text(tr("替换画框内容？", "Replace the frame content?"), color = WG.Ink, style = WGType.title, fontWeight = FontWeight.SemiBold) },
        content = { Text(tr("画框正在展示《${vm.frameArtwork?.titleZh ?: ""}》。只有一个画框，替换后显示《${a.titleZh}》。", "The frame is showing ${vm.frameArtwork?.titleEn ?: ""}. There is one frame; it will show ${a.titleEn} instead."), color = WG.Secondary, style = WGType.body) },
        buttons = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GhostButton(tr("取消", "Cancel")) { vm.cancelFrameReplace() }
                Spacer(Modifier.width(12.dp))
                PrimaryButton(tr("替换", "Replace")) { vm.setAsFrame(artworkId, force = true) }
            }
        },
    )
}
