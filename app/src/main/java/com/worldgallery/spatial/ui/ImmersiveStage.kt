package com.worldgallery.spatial.ui

import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialScaleGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.closeWindowContainer
import com.pico.spatial.ui.platform.containers.openWindowContainer
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.data.tr
import com.worldgallery.spatial.scene.ImmersiveScene
import com.worldgallery.spatial.scene.ModelLoadState
import com.worldgallery.spatial.scene.transform
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "WorldGallery.Stage"
private const val CONTROL_PANEL = "stage_control"

/**
 * 14 · 沉浸观赏 (Figma "Full Space / viewer"): the Stage shows the scene, the current work and one small
 * control strip under it — previous / next and "back to the gallery". Drag pans or rotates, pinch zooms.
 *
 * Shared Space ↔ Full Space: this app's windows are minimized when the Stage opens and restored *before*
 * `closeStage()` — the SDK allows `minimize/restoreWindowContainer` only while a Stage is open, and once
 * the Stage is gone the app is in the background and can neither restore, reopen nor relaunch its window
 * (all verified on the emulator). So the gallery window is already back, on the same work, when Shared
 * Space returns. `stageReopenMode` keeps the tutorial's close/reopen variants for A/B on a device.
 */
@Composable
fun ImmersiveStage() {
    val context = LocalContext.current
    val vm = remember { GalleryViewModel.get(context) }
    val navigator = LocalSpatialNavigator.current
    val scope = rememberCoroutineScope()
    val scene = remember { ImmersiveScene(context, vm) }
    val converter = LocalPhysicalLengthConverter.current
    val density = LocalDensity.current
    fun pxToM(px: Float): Float = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }
    var dragTarget by remember { mutableStateOf<String?>(null) }
    var built by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // Leaving: reopen the Shared-Space windows while the Stage is still open (the app is foreground then),
    // wait for the gallery window to compose, then close the Stage.
    suspend fun leaveStage(reason: String) {
        if (leaving) return
        leaving = true
        Log.i(TAG, "leave ($reason): reopening shared-space windows before closing the stage (mode=${vm.stageReopenMode})")
        if (vm.stageReopenMode == "minimize") {
            val okMain = runCatching { navigator.restoreWindowContainer(GalleryViewModel.MAIN_WINDOW_ID) }.getOrElse { Log.w(TAG, "restore main failed", it); false }
            val okFrame = if (vm.frameHiddenForStage) runCatching { navigator.restoreWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }.getOrDefault(false) else null
            Log.i(TAG, "restore before closeStage → main=$okMain frame=$okFrame")
            if (okMain) vm.onMainWindowRestored()
            delay(600)
            runCatching { navigator.closeStage() }.onFailure { Log.w(TAG, "closeStage failed", it); leaving = false }
            return
        }
        runCatching {
            when (vm.stageReopenMode) {
                "navigator" -> navigator.openWindowContainer(GalleryViewModel.MAIN_WINDOW_ID)
                "intent" -> context.startActivity(
                    context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                else -> context.openWindowContainer(GalleryViewModel.MAIN_WINDOW_ID)
            }
        }.onFailure { Log.w(TAG, "reopen main window failed", it) }
        if (vm.frameHiddenForStage) runCatching { context.openWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }
            .onFailure { Log.w(TAG, "reopen frame window failed", it) }
        val t0 = SystemClock.uptimeMillis()
        while (!vm.mainWindowRestored && SystemClock.uptimeMillis() - t0 < 3000) delay(50)
        Log.i(TAG, "gallery window back=${vm.mainWindowRestored} after ${SystemClock.uptimeMillis() - t0} ms; closing stage")
        runCatching { navigator.closeStage() }.onFailure { Log.w(TAG, "closeStage failed", it); leaving = false }
    }
    // System Back takes the same ordered path when the Stage exposes a back dispatcher.
    if (LocalOnBackPressedDispatcherOwner.current != null) BackHandler { scope.launch { leaveStage("system back") } }
    // ...and so does the debug channel's `leaveImmersive` (consumed once: a later Stage must not re-run an old request).
    LaunchedEffect(vm.immersiveExitRequest) {
        val req = vm.immersiveExitRequest
        if (req > vm.immersiveExitHandled) { vm.immersiveExitHandled = req; leaveStage("debug") }
    }

    // Enter: Full Space keeps this app's own windows visible, so close them for a pure scene. Done here (not in
    // the main window) so both entry paths — toolbar and debug channel — behave the same.
    LaunchedEffect(Unit) {
        Log.i(TAG, "stage composed")
        vm.onImmersiveOpened()
        delay(300)
        vm.frameHiddenForStage = vm.frame.windowOpen
        if (vm.stageReopenMode == "minimize") {
            val okMain = runCatching { navigator.minimizeWindowContainer(GalleryViewModel.MAIN_WINDOW_ID) }.getOrElse { Log.w(TAG, "minimize main failed", it); false }
            val okFrame = if (vm.frameHiddenForStage) runCatching { navigator.minimizeWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }.getOrDefault(false) else null
            Log.i(TAG, "shared-space windows minimized (main=$okMain frame=$okFrame)")
            return@LaunchedEffect
        }
        // Context form on purpose: closing the frame by id + tag through the Stage's navigator closed the Stage itself.
        if (vm.frameHiddenForStage) runCatching { context.closeWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }
            .onFailure { Log.w(TAG, "close frame window failed", it) }
        runCatching { context.closeWindowContainer(GalleryViewModel.MAIN_WINDOW_ID) }
            .onSuccess { Log.i(TAG, "shared-space windows closed (frame=${vm.frameHiddenForStage})") }
            .onFailure { Log.w(TAG, "close main window failed", it) }
    }
    // Gone (after leaveStage, or a close the app did not initiate): release the scene. If the windows are not
    // back yet this is a last attempt; the model adds a launcher-intent fallback, and failing that the user's
    // next tap on the app icon brings the gallery window back on the same work.
    DisposableEffect(Unit) {
        onDispose {
            if (!vm.mainWindowRestored) {
                runCatching { navigator.openWindowContainer(GalleryViewModel.MAIN_WINDOW_ID) }.onFailure { Log.w(TAG, "late reopen failed", it) }
                if (vm.frameHiddenForStage) runCatching { context.openWindowContainer(GalleryViewModel.FRAME_WINDOW_ID, GalleryViewModel.FRAME_TAG) }
            }
            Log.i(TAG, "stage disposed; gallery window back=${vm.mainWindowRestored}")
            scene.destroy()
            vm.onImmersiveClosed(windowsReopened = vm.mainWindowRestored)
        }
    }

    SpatialView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectSpatialTapGesture(context, TargetEntity.any()) { tap ->
                    when (tap.targetEntity?.getName()) {
                        "nav:prev" -> vm.prev()
                        "nav:next" -> vm.next()
                    }
                }
            }
            .pointerInput(Unit) {
                detectSpatialDragGesture(
                    context = context,
                    targetedToEntity = TargetEntity.any(),
                    onDragStart = { _, v -> dragTarget = v.targetEntity?.getName() },
                    onDragEnd = { dragTarget = null },
                    onDragCancel = { dragTarget = null },
                ) { v ->
                    val id = vm.current?.id
                    if (dragTarget == "artwork" && id != null) {
                        val dx = pxToM(v.dragAmount.x)
                        val dy = pxToM(v.dragAmount.y)
                        if (scene.showing3D) vm.rotateBy(id, dx * 240f, dy * 120f)
                        else vm.panBy(id, dx / scene.planeW, dy / scene.planeH)
                    }
                }
            }
            .pointerInput(Unit) {
                detectSpatialScaleGesture(context) { s ->
                    val id = vm.current?.id
                    if (id != null) { if (scene.showing3D) vm.scaleBy(id, s.scaleValue) else vm.zoomBy(id, s.scaleValue) }
                }
            },
        initial = { content, attachments ->
            Log.i(TAG, "SpatialView initial")
            scene.build(content)
            // The control strip sits under the work, tilted up towards the viewer (a plane faces +Z; pitch −90° lays it flat).
            attachments.entity(CONTROL_PANEL)?.apply {
                transform().setPosition(Vector3(0f, 0.38f, -2.2f))
                transform().setEulerAngles(EulerAngles(pitch = -22f))
                transform().setScaleVector(Vector3(2.4f, 2.4f, 1f))   // the panel renders at dp scale; ~0.8 m wide at this distance
                content.addEntity(this)
            } ?: Log.w(TAG, "control panel attachment missing")
            built = true
        },
        attachments = {
            AttachmentPanel(id = CONTROL_PANEL) {
                GalleryTheme {
                    StageControlStrip(vm) { scope.launch { leaveStage("control strip") } }
                }
            }
        },
    )

    val currentId = vm.current?.id
    // Key only on whether THIS work's model is ready — not on the whole state map, whose Downloading
    // ticks would otherwise rebuild the scene on every progress update.
    val readyKey = vm.current?.model?.let { (vm.modelState(it) as? ModelLoadState.Ready)?.file?.path }
    LaunchedEffect(built, currentId, readyKey) { if (built) scene.show(vm.current) }
    val vs = currentId?.let { vm.viewState(it) }
    LaunchedEffect(built, vs) { if (built) scene.applyView(vs) }
}

/** The only UI in Full Space: 上一件 · n / N · 下一件 | 回到画廊 — the way back to the Shared Space window. */
@Composable
private fun StageControlStrip(vm: GalleryViewModel, onExit: () -> Unit) {
    val s = vm.session
    Row(
        Modifier.clip(RoundedCornerShape(40.dp)).background(WG.PanelGlass).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (s != null) {
            ControlButton(WGIcon.BACK, tr("上一件", "Previous"), enabled = s.hasPrev) { vm.prev() }
            Text("${s.index + 1} / ${s.ids.size}", color = WG.Ink, style = WGType.label)
            ControlButton(WGIcon.NEXT, tr("下一件", "Next"), enabled = s.index < s.ids.size - 1) { vm.next() }
            BarDivider()
        }
        GhostButton(tr("回到画廊", "Back to gallery"), small = true) { onExit() }
    }
}
