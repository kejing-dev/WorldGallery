package com.worldgallery.spatial.app

import android.content.Context
import android.util.Log
import com.pico.spatial.ui.platform.containers.closeWindowContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.worldgallery.spatial.data.ArtCategory
import com.worldgallery.spatial.data.Artwork
import com.worldgallery.spatial.data.Catalog
import com.worldgallery.spatial.data.FrameMode
import com.worldgallery.spatial.data.FrameSettings
import com.worldgallery.spatial.data.Lang
import com.worldgallery.spatial.data.ModelAsset
import com.worldgallery.spatial.data.QueueSource
import com.worldgallery.spatial.data.Store
import com.worldgallery.spatial.data.ViewState
import com.worldgallery.spatial.data.ViewingSession
import com.worldgallery.spatial.scene.ModelLoadState
import com.worldgallery.spatial.scene.ModelLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pages inside the main browsing window (Figma 01 · UI Screens). */
sealed class Page {
    object Home : Page()
    data class CollectionPage(val id: String) : Page()
    object Viewer : Page()
    object Favorites : Page()
    object Search : Page()
}

enum class Tab { PAINTINGS, SCULPTURE, FAVORITES }

/** Which surface the single information window is bound to (PRD 5.5). */
enum class InfoBinding { VIEWER, FRAME }

/**
 * App-scoped state shared by the main window, the art-frame window and the immersive Stage.
 * Shared Space and Full Space reuse the same [session] and per-artwork [ViewState] (PRD 5.6).
 */
class GalleryViewModel private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "WorldGallery.VM"
        const val MAIN_WINDOW_ID = "WorldGalleryMain"
        const val FRAME_WINDOW_ID = "WorldGalleryFrame"
        const val FRAME_TAG = "frame"
        const val STAGE_ID = "WorldGalleryImmersive"
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
        const val MIN_SCALE = 0.5f

        /**
         * One-tap frame sizes (width × height in dp, 1094 × 740 is the Figma frame). The window is pinned to
         * the selected step with Modifier.windowConstraints, which needs ContainerResizeType.ContentSize.
         */
        val FRAME_SIZES: List<Pair<Int, Int>> = listOf(
            547 to 370,     // 50 %
            766 to 518,     // 70 %
            1094 to 740,    // 100 % — default
            1423 to 962,    // 130 %
            1751 to 1184,   // 160 %
        )
        const val MAX_SCALE = 3f

        @Volatile private var instance: GalleryViewModel? = null
        fun get(context: Context): GalleryViewModel =
            instance ?: synchronized(this) { instance ?: GalleryViewModel(context.applicationContext).also { instance = it } }
    }

    val store = Store(appContext)
    val models = ModelLoader(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ------------------------------------------------------------------ navigation

    var tab: Tab by mutableStateOf(Tab.PAINTINGS); private set
    var page: Page by mutableStateOf(Page.Home); private set
    private val backStack = ArrayList<Page>()
    var searchQuery: String by mutableStateOf("")
    var reduceMotion: Boolean by mutableStateOf(store.reduceMotion); private set
    var menuOpen: Boolean by mutableStateOf(false)
    var toast: String? by mutableStateOf(null); private set


    fun selectTab(t: Tab) {
        tab = t
        backStack.clear()
        page = when (t) {
            Tab.PAINTINGS -> Page.Home
            Tab.SCULPTURE -> Page.CollectionPage("classic-sculpture")
            Tab.FAVORITES -> Page.Favorites
        }
        if (infoBinding == InfoBinding.VIEWER) closeInfo()
    }

    fun navigate(p: Page) {
        if (p == page) return
        backStack.add(page)
        page = p
        if (p !is Page.Viewer && infoBinding == InfoBinding.VIEWER) closeInfo()
    }

    /** Title-bar back: returns to the previous page, restoring the grid the user came from. */
    fun back() {
        val prev = backStack.removeLastOrNull() ?: rootPage()
        page = prev
        if (prev !is Page.Viewer && infoBinding == InfoBinding.VIEWER) closeInfo()
    }

    private fun rootPage(): Page = when (tab) {
        Tab.PAINTINGS -> Page.Home
        Tab.SCULPTURE -> Page.CollectionPage("classic-sculpture")
        Tab.FAVORITES -> Page.Favorites
    }

    fun openSearch() { navigate(Page.Search) }

    fun setLocale(locale: String) {
        Lang.set(locale)
        store.locale = Lang.locale
    }

    fun toggleLocale() = setLocale(if (Lang.isZh) Lang.EN else Lang.ZH)

    fun updateReduceMotion(v: Boolean) { reduceMotion = v; store.reduceMotion = v }

    fun showToast(text: String) {
        toast = text
        scope.launch { delay(2400); if (toast == text) toast = null }
    }

    // ------------------------------------------------------------------ viewing session

    var session: ViewingSession? by mutableStateOf(null); private set
    val current: Artwork? get() = session?.currentId?.let { Catalog.byId[it] }
    /** Set when the user reaches the end of the queue ("本组已看完", PRD 5.4 #8). */
    var queueFinished: Boolean by mutableStateOf(false); private set

    fun startQueue(source: QueueSource, ids: List<String>, startIndex: Int = 0) {
        val valid = ids.filter { Catalog.byId.containsKey(it) }
        if (valid.isEmpty()) { showToast(com.worldgallery.spatial.data.tr("这里还没有可观赏的作品", "Nothing to view here yet")); return }
        val previous = session
        session = ViewingSession(source, valid, startIndex.coerceIn(0, valid.size - 1))
        queueFinished = false
        // a fresh entry shows the full composition; the same work keeps its own detail view (PRD 5.2)
        if (previous?.currentId != session?.currentId) resetViewIfNewEntry()
        store.saveSession(session)
        navigate(Page.Viewer)
        if (infoBinding == InfoBinding.VIEWER) infoScrollKey++
        Log.i(TAG, "queue ${source.label()} size=${valid.size} index=${session?.index}")
    }

    /** "继续观赏": resumes the saved session. */
    fun continueViewing() {
        val s = session ?: return startQueue(QueueSource.FromCollection("essentials"), Catalog.collection("essentials")!!.artworkIds)
        queueFinished = false
        navigate(Page.Viewer)
    }

    /** From a collection grid: the queue is the collection, starting at the tapped work. */
    fun openFromCollection(collectionId: String, artworkId: String) {
        val c = Catalog.collection(collectionId) ?: return
        startQueue(QueueSource.FromCollection(collectionId), c.artworkIds, c.artworkIds.indexOf(artworkId).coerceAtLeast(0))
    }

    fun openFromFavorites(artworkId: String) {
        val ids = favorites.toList()
        startQueue(QueueSource.Favorites, ids, ids.indexOf(artworkId).coerceAtLeast(0))
    }

    fun openFromSearch(artworkId: String) {
        val ids = Catalog.search(searchQuery).map { it.id }
        startQueue(QueueSource.Search(searchQuery), ids, ids.indexOf(artworkId).coerceAtLeast(0))
    }

    fun next(): Boolean {
        val s = session ?: return false
        if (!s.hasNext) { queueFinished = true; return false }
        session = s.copy(index = s.index + 1)
        onArtworkSwitched(forward = true)
        return true
    }

    fun prev(): Boolean {
        val s = session ?: return false
        if (!s.hasPrev) return false
        session = s.copy(index = s.index - 1)
        onArtworkSwitched(forward = false)
        return true
    }

    fun restartQueue() {
        val s = session ?: return
        session = s.copy(index = 0)
        onArtworkSwitched(forward = true)
    }

    private fun onArtworkSwitched(forward: Boolean) {
        queueFinished = false
        // next → new work starts as the full composition; previous → the work's own view is restored (PRD 5.2)
        if (forward) current?.let { resetView(it.id) }
        store.saveSession(session)
        if (infoBinding == InfoBinding.VIEWER) infoScrollKey++
        current?.let { prefetch(it) }
        Log.i(TAG, "now viewing ${current?.id} (${(session?.index ?: 0) + 1}/${session?.ids?.size})")
    }

    private fun resetViewIfNewEntry() { current?.let { resetView(it.id) } }

    private fun prefetch(a: Artwork) {
        // next work: decode its image in the background so the switch is instant (PRD 5.4 #2)
        val s = session ?: return
        s.ids.getOrNull(s.index + 1)?.let { Catalog.byId[it] }?.image?.let { img ->
            scope.launch(Dispatchers.IO) { com.worldgallery.spatial.scene.Bitmaps.decodeSync(appContext, img.path) }
        }
    }

    // ------------------------------------------------------------------ per-artwork view state

    private val viewStates = mutableStateMapOf<String, ViewState>()
    private var saveJob: Job? = null

    fun viewState(id: String): ViewState = viewStates[id] ?: ViewState()

    fun updateView(id: String, transform: (ViewState) -> ViewState) {
        viewStates[id] = transform(viewState(id))
        saveJob?.cancel()
        saveJob = scope.launch { delay(800); store.saveViewStates(viewStates) }
    }

    fun resetView(id: String) = updateView(id) { ViewState() }

    /** Zoom around the centre; pan is scaled with the zoom so the same detail stays in view. */
    fun zoomBy(id: String, factor: Float) = updateView(id) { v ->
        val z = (v.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val k = z / v.zoom
        clampPan(v.copy(zoom = z, panX = v.panX * k, panY = v.panY * k))
    }

    fun zoomStep(id: String, dir: Int) = zoomBy(id, if (dir > 0) 1.5f else 1f / 1.5f)

    /** Pan in fractions of the displayed artwork size. */
    fun panBy(id: String, dx: Float, dy: Float) = updateView(id) { v -> clampPan(v.copy(panX = v.panX + dx, panY = v.panY + dy)) }

    private fun clampPan(v: ViewState): ViewState {
        val limit = (v.zoom - 1f) / 2f          // keep the image covering the surface
        return v.copy(panX = v.panX.coerceIn(-limit, limit), panY = v.panY.coerceIn(-limit, limit))
    }

    fun rotateBy(id: String, dYaw: Float, dPitch: Float) = updateView(id) { v ->
        v.copy(yaw = ((v.yaw + dYaw) % 360f + 360f) % 360f, pitch = (v.pitch + dPitch).coerceIn(-35f, 35f))
    }

    fun setYaw(id: String, yaw: Float) = updateView(id) { it.copy(yaw = yaw, pitch = 0f) }

    fun scaleBy(id: String, factor: Float) = updateView(id) { v -> v.copy(scale = (v.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)) }

    /** True when the current zoom exceeds what the legal source file can resolve (PRD 5.2 "到达源图细节上限"). */
    fun atDetailLimit(a: Artwork, displayedLongEdgePx: Float): Boolean {
        val img = a.image ?: return false
        val v = viewState(a.id)
        return displayedLongEdgePx * v.zoom > img.sourceLongEdge.coerceAtMost(maxOf(img.width, img.height)) * 1.05f
    }

    // ------------------------------------------------------------------ favorites

    val favorites = mutableStateListOf<String>()

    fun isFavorite(id: String) = favorites.contains(id)

    fun toggleFavorite(id: String) {
        if (favorites.contains(id)) favorites.remove(id) else favorites.add(id)
        store.saveFavorites(favorites)
        showToast(if (favorites.contains(id)) com.worldgallery.spatial.data.tr("已收藏", "Added to favorites") else com.worldgallery.spatial.data.tr("已取消收藏", "Removed from favorites"))
    }

    // ------------------------------------------------------------------ information window (one instance, bound to a source)

    var infoBinding: InfoBinding? by mutableStateOf(null); private set
    /** Bumped when the bound work changes so the window scrolls back to the top. */
    var infoScrollKey: Int by mutableStateOf(0); private set

    val infoArtwork: Artwork?
        get() = when (infoBinding) {
            InfoBinding.VIEWER -> current
            InfoBinding.FRAME -> frame.currentId?.let { Catalog.byId[it] }
            null -> null
        }

    fun openInfo(binding: InfoBinding) {
        if (infoBinding != binding) infoScrollKey++
        infoBinding = binding
        restartFrameTimer()
    }

    fun closeInfo() {
        infoBinding = null
        restartFrameTimer()      // resumes only if the user's intent was "playing" (PRD 5.5)
    }

    // ------------------------------------------------------------------ art frame

    var frame: FrameSettings by mutableStateOf(FrameSettings()); private set
    /** Frame window lifecycle: false while it is minimized / stopped, so the slideshow pauses. */
    var frameVisible: Boolean by mutableStateOf(true); private set
    var frameSettingsOpen: Boolean by mutableStateOf(false)
    /** Pending "replace the frame content?" confirmation (PRD 5.7 "设为画框"). */
    var frameReplaceCandidate: String? by mutableStateOf(null); private set
    var frameNextSwitchAt: Long by mutableStateOf(0L); private set
    /** Request for the main window to open / close the frame WindowContainer; consumed by the UI. */
    var frameOpenRequest: Int by mutableStateOf(0); private set
    private var frameTimer: Job? = null

    val frameArtwork: Artwork? get() = frame.currentId?.let { Catalog.byId[it] }

    val frameEffectivelyPlaying: Boolean
        get() = frame.windowOpen && frame.mode == FrameMode.LOOP && frame.playlist.size > 1 && frame.playing &&
            infoBinding != InfoBinding.FRAME && !immersiveActive && frameVisible

    /** "设为画框" from the viewer. */
    fun setAsFrame(artworkId: String, force: Boolean = false) {
        val a = Catalog.byId[artworkId] ?: return
        if (a.image == null) { showToast(com.worldgallery.spatial.data.tr("画框只展示名画图像版本", "The frame shows painting images only")); return }
        if (frame.windowOpen && frame.currentId != artworkId && !force) { frameReplaceCandidate = artworkId; return }
        frameReplaceCandidate = null
        frame = frame.copy(mode = FrameMode.FIXED, artworkId = artworkId, playlistSource = null, playlist = emptyList(), index = 0)
        persistFrame()
        requestFrameWindow()
        showToast(com.worldgallery.spatial.data.tr("已设为画框", "Set as art frame"))
    }

    fun cancelFrameReplace() { frameReplaceCandidate = null }

    /** "在画框中循环": snapshot of the published paintings in a collection or the favorites. */
    fun loopInFrame(source: QueueSource) {
        val ids = when (source) {
            is QueueSource.FromCollection -> Catalog.collection(source.collectionId)?.artworkIds ?: emptyList()
            QueueSource.Favorites -> favorites.toList()
            is QueueSource.Search -> Catalog.search(source.query).map { it.id }
        }.filter { Catalog.byId[it]?.image != null }
        if (ids.isEmpty()) { showToast(com.worldgallery.spatial.data.tr("没有可循环的名画", "No paintings to play")); return }
        val list = if (frame.shuffle) ids.shuffled() else ids
        frame = frame.copy(mode = FrameMode.LOOP, playlistSource = source, playlist = list, index = 0, artworkId = list.first(), playing = true)
        persistFrame()
        requestFrameWindow()
        restartFrameTimer()
    }

    fun setFrameMode(mode: FrameMode) {
        if (mode == FrameMode.LOOP && frame.playlist.isEmpty()) {
            loopInFrame(frame.playlistSource ?: QueueSource.FromCollection("essentials")); return
        }
        frame = frame.copy(mode = mode, artworkId = if (mode == FrameMode.FIXED) frame.currentId else frame.artworkId)
        persistFrame(); restartFrameTimer()
    }

    fun setFrameInterval(minutes: Int) { frame = frame.copy(intervalMinutes = minutes); persistFrame(); restartFrameTimer() }

    fun setFrameShuffle(shuffle: Boolean) {
        val list = if (shuffle) frame.playlist.shuffled() else (frame.playlistSource?.let { src ->
            when (src) {
                is QueueSource.FromCollection -> Catalog.collection(src.collectionId)?.artworkIds
                QueueSource.Favorites -> favorites.toList()
                is QueueSource.Search -> Catalog.search(src.query).map { it.id }
            }
        }?.filter { frame.playlist.contains(it) } ?: frame.playlist)
        val cur = frame.currentId
        frame = frame.copy(shuffle = shuffle, playlist = list, index = list.indexOf(cur).coerceAtLeast(0))
        persistFrame(); restartFrameTimer()
    }

    fun frameSetPlaying(playing: Boolean) { frame = frame.copy(playing = playing); persistFrame(); restartFrameTimer() }

    fun frameNext(manual: Boolean = true) = frameStep(1, manual)
    fun framePrev() = frameStep(-1, true)

    private fun frameStep(delta: Int, manual: Boolean) {
        if (frame.mode != FrameMode.LOOP || frame.playlist.isEmpty()) return
        val n = frame.playlist.size
        frame = frame.copy(index = ((frame.index + delta) % n + n) % n)
        persistFrame()
        if (infoBinding == InfoBinding.FRAME) infoScrollKey++
        restartFrameTimer()      // a manual change restarts the countdown (PRD 5.7)
        Log.i(TAG, "frame ${if (manual) "manual" else "auto"} → ${frame.currentId}")
    }

    fun onFrameWindowOpened() { frame = frame.copy(windowOpen = true); frameVisible = true; persistFrame(); restartFrameTimer() }

    fun onFrameWindowClosed() {
        frame = frame.copy(windowOpen = false); frameVisible = true
        if (infoBinding == InfoBinding.FRAME) closeInfo()
        frameSettingsOpen = false
        persistFrame(); restartFrameTimer()
    }

    fun onFrameVisibility(visible: Boolean) { frameVisible = visible; restartFrameTimer() }

    private fun requestFrameWindow() { frameOpenRequest++ }

    /** "我的画框" entry: re-opens the configured frame window; never creates one without content. */
    fun openMyFrame() {
        if (frame.currentId == null) {
            showToast(com.worldgallery.spatial.data.tr("先在观赏页点击画框图标，或在合集页选择「在画框中循环」", "Tap the frame icon while viewing, or choose \"Play in frame\" in a collection"))
            return
        }
        requestFrameWindow()
    }

    fun consumeFrameOpenRequest() { }

    private fun persistFrame() = store.saveFrame(frame)

    private fun restartFrameTimer() {
        frameTimer?.cancel()
        frameTimer = null
        if (!frameEffectivelyPlaying) { frameNextSwitchAt = 0L; return }
        val ms = frame.intervalMinutes * 60_000L
        frameNextSwitchAt = System.currentTimeMillis() + ms
        frameTimer = scope.launch {
            delay(ms)
            if (frameEffectivelyPlaying) frameStep(1, manual = false)
        }
    }

    // ------------------------------------------------------------------ immersive (Full Space)

    var immersiveActive: Boolean by mutableStateOf(false); private set
    var immersiveHintOpen: Boolean by mutableStateOf(false)
    /** Set by the immersive Stage while it is closing, so the main window can restore itself. */
    /** Bumped by the Stage when the Shared-Space windows should un-minimize themselves (while the Stage is still open). */
    /** True once the main window has (re)composed — set by the window itself; cleared when a Stage opens. */
    @Volatile var mainWindowRestored: Boolean = false; private set
    /** The frame window was open when the Stage closed it, so the Stage reopens it on exit. */
    @Volatile var frameHiddenForStage: Boolean = false
    /** Bumped by the debug channel's `leaveImmersive`; the Stage answers with the same ordered exit as its strip button. */
    var immersiveExitRequest: Int by mutableStateOf(0); private set
    /** Bumped requests already acted on by a Stage. */
    var immersiveExitHandled: Int = 0
    /** How the Stage hides / brings back the gallery window: "minimize" (restore before closeStage) or the tutorial's
     *  close-then-reopen variants "context" | "navigator" | "intent" (debug `reopenMode`, for A/B on a device). */
    @Volatile var stageReopenMode: String = "minimize"
    /** Last `frameOpenRequest` the main window acted on — it may be recreated after a Stage and must not reopen a frame the user closed. */
    var frameOpenHandled: Int = 0

    // ------------------------------------------------------------------ frame window size (one tap)

    var frameSizeStep: Int by mutableStateOf(2); private set

    val frameSize: Pair<Int, Int> get() = FRAME_SIZES[frameSizeStep.coerceIn(0, FRAME_SIZES.lastIndex)]
    val canEnlargeFrame: Boolean get() = frameSizeStep < FRAME_SIZES.lastIndex
    val canShrinkFrame: Boolean get() = frameSizeStep > 0

    /** One tap = one step; the window resizes, the artwork keeps its aspect inside it. */
    fun stepFrameSize(delta: Int) {
        val next = (frameSizeStep + delta).coerceIn(0, FRAME_SIZES.lastIndex)
        if (next == frameSizeStep) return
        frameSizeStep = next
        store.frameSizeStep = next
        val (w, h) = frameSize
        Log.i(TAG, "frame size → ${w}x${h} dp (step $next)")
        showToast(com.worldgallery.spatial.data.tr("画框尺寸 ${sizeLabel()}", "Frame size ${sizeLabel()}"))
    }

    fun sizeLabel(): String = "${(frameSize.first * 100) / FRAME_SIZES[2].first}%"
    fun onMainWindowRestored() { mainWindowRestored = true }

    /** Returns true when the Stage may be opened now; false when the one-time hint is shown first. */
    fun requestImmersive(): Boolean {
        if (current == null) return false
        if (!store.immersiveHintSeen) { immersiveHintOpen = true; return false }
        return true
    }

    fun confirmImmersiveHint() { store.immersiveHintSeen = true; immersiveHintOpen = false }

    fun onImmersiveOpened() {
        immersiveActive = true
        mainWindowRestored = false
        store.saveSession(session)
        restartFrameTimer()
        Log.i(TAG, "immersive opened for ${current?.id}")
    }

    fun onImmersiveClosed(windowsReopened: Boolean = true) {
        if (!immersiveActive) return
        immersiveActive = false
        restartFrameTimer()
        store.saveSession(session)
        Log.i(TAG, "immersive closed, back on ${current?.id} (windowsReopened=$windowsReopened)")
        scope.launch {
            // Safety net for a Stage the app did not close itself (the windows are still hidden): a minimized
            // window cannot be restored any more, so close it first — otherwise the relaunch stacks a second
            // gallery window on top of the hidden one — then relaunch the way the launcher does.
            delay(if (windowsReopened) 1500 else 600)
            if (mainWindowRestored) { Log.i(TAG, "gallery window is back"); return@launch }
            if (stageReopenMode == "minimize") {
                runCatching { appContext.closeWindowContainer(MAIN_WINDOW_ID) }.onFailure { Log.w(TAG, "close hidden main failed", it) }
                if (frameHiddenForStage) runCatching { appContext.closeWindowContainer(FRAME_WINDOW_ID, FRAME_TAG) }
                Log.i(TAG, "hidden windows closed before relaunch")
            }
            runCatching {
                val intent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                    ?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent != null) appContext.startActivity(intent)
                Log.i(TAG, "main window reopened via launcher intent")
            }.onFailure { Log.w(TAG, "launcher intent fallback failed", it) }
        }
    }

    // ------------------------------------------------------------------ 3D loading

    fun load3D(asset: ModelAsset) = models.request(asset)
    fun cancel3D(asset: ModelAsset) = models.cancel(asset)
    fun retry3D(asset: ModelAsset) = models.retry(asset)
    fun modelState(asset: ModelAsset): ModelLoadState = models.state(asset)

    /** Auto-load the next sculpture in the queue once the current one is ready (PRD 5.1 #3). */
    fun prefetchNextModel() {
        val s = session ?: return
        val next = s.ids.getOrNull(s.index + 1)?.let { Catalog.byId[it] } ?: return
        val asset = next.model ?: return
        val cur = current?.model ?: return
        if (models.state(cur) is ModelLoadState.Ready && models.state(asset) is ModelLoadState.NotLoaded && (asset.downloadUrl != null || models.urlOverrides.containsKey(asset.id))) models.request(asset)
    }

    fun setModelUrl(id: String, url: String) {
        models.urlOverrides = models.urlOverrides + (id to url)
        store.saveModelUrlOverrides(models.urlOverrides)
    }

    // ------------------------------------------------------------------ debug channel (emulator cannot script spatial input)

    /** Emulator test harness; every command goes through the same paths as real input. */
    fun debug(cmd: String, arg: String): String {
        val a = current
        fun id() = a?.id ?: ""
        when (cmd) {
            "tab" -> selectTab(runCatching { Tab.valueOf(arg.uppercase()) }.getOrDefault(Tab.PAINTINGS))
            "home" -> selectTab(Tab.PAINTINGS)
            "collection" -> navigate(Page.CollectionPage(arg.ifBlank { "essentials" }))
            "favorites" -> navigate(Page.Favorites)
            "search" -> { searchQuery = arg; navigate(Page.Search) }
            "open" -> { val c = Catalog.collections.firstOrNull { it.artworkIds.contains(arg) }; if (c != null) openFromCollection(c.id, arg) }
            "start" -> Catalog.collection(arg.ifBlank { "essentials" })?.let { startQueue(QueueSource.FromCollection(it.id), it.artworkIds) }
            "continue" -> continueViewing()
            "back" -> back()
            "leaveImmersive" -> immersiveExitRequest++
            "reopenMode" -> stageReopenMode = arg
            "next" -> next()
            "prev" -> prev()
            "restart" -> restartQueue()
            "zoomIn" -> zoomStep(id(), 1)
            "zoomOut" -> zoomStep(id(), -1)
            "fit" -> resetView(id())
            "pan" -> arg.split(",").map { it.trim().toFloatOrNull() ?: 0f }.let { if (it.size >= 2) panBy(id(), it[0], it[1]) }
            "yaw" -> setYaw(id(), arg.toFloatOrNull() ?: 0f)
            "rotate" -> arg.split(",").map { it.trim().toFloatOrNull() ?: 0f }.let { if (it.size >= 2) rotateBy(id(), it[0], it[1]) }
            "scale" -> scaleBy(id(), arg.toFloatOrNull() ?: 1f)
            "fav" -> a?.let { toggleFavorite(it.id) }
            "info" -> openInfo(InfoBinding.VIEWER)
            "frameInfo" -> openInfo(InfoBinding.FRAME)
            "closeInfo" -> closeInfo()
            "frame" -> a?.let { setAsFrame(it.id, force = arg == "force") }
            "frameLoop" -> loopInFrame(if (arg == "favorites") QueueSource.Favorites else QueueSource.FromCollection(arg.ifBlank { "essentials" }))
            "frameMode" -> setFrameMode(if (arg == "loop") FrameMode.LOOP else FrameMode.FIXED)
            "frameInterval" -> setFrameInterval(arg.toIntOrNull() ?: 5)
            "frameShuffle" -> setFrameShuffle(arg == "on")
            "framePause" -> frameSetPlaying(false)
            "framePlay" -> frameSetPlaying(true)
            "frameNext" -> frameNext()
            "framePrev" -> framePrev()
            "frameSettings" -> frameSettingsOpen = arg != "off"
            "load3d" -> a?.model?.let { load3D(it) }
            "cancel3d" -> a?.model?.let { cancel3D(it) }
            "retry3d" -> a?.model?.let { retry3D(it) }
            "forget3d" -> a?.model?.let { models.forget(it) }
            "modelUrl" -> arg.split(" ", limit = 2).let { if (it.size == 2) setModelUrl(it[0], it[1]) }
            "lang" -> setLocale(if (arg.startsWith("en")) Lang.EN else Lang.ZH)
            "hintSeen" -> store.immersiveHintSeen = true
            "frameSize" -> when (arg) {
                "in", "+", "up" -> stepFrameSize(1)
                "out", "-", "down" -> stepFrameSize(-1)
                else -> arg.toIntOrNull()?.let { stepFrameSize(it - frameSizeStep) }
            }
            "reduceMotion" -> updateReduceMotion(arg == "on")
            "wipe" -> { store.wipe(); favorites.clear(); viewStates.clear(); session = null; frame = FrameSettings() }
            "state" -> {}
            else -> return "unknown command: $cmd"
        }
        return state()
    }

    fun state(): String = buildString {
        append("tab=").append(tab).append(" page=").append(page)
        append(" current=").append(current?.id).append(" index=").append(session?.index).append('/').append(session?.ids?.size)
        append(" source=").append(session?.source?.label())
        append(" view=").append(current?.let { viewState(it.id) })
        append(" favorites=").append(favorites)
        append(" info=").append(infoBinding).append(" infoArtwork=").append(infoArtwork?.id)
        append(" frameSize=").append(frameSize).append(" step=").append(frameSizeStep)
        append(" frame=").append(frame).append(" framePlaying=").append(frameEffectivelyPlaying)
        append(" immersive=").append(immersiveActive)
        append(" models=").append(models.states.toMap())
        append(" locale=").append(Lang.locale)
    }

    // Property initialisers above run first; restore persisted state last.
    init {
        Lang.set(store.locale ?: Lang.defaultForSystem())
        favorites.addAll(store.loadFavorites().filter { Catalog.byId.containsKey(it) })
        viewStates.putAll(store.loadViewStates())
        models.urlOverrides = store.loadModelUrlOverrides()
        frameSizeStep = store.frameSizeStep.coerceIn(0, FRAME_SIZES.lastIndex)
        session = store.loadSession()?.takeIf { s -> s.ids.all { Catalog.byId.containsKey(it) } }
        frame = store.loadFrame().let { f ->
            // a frame is never re-created silently after a restart (PRD 5.7 "关闭": user opens it again)
            f.copy(windowOpen = false, playlist = f.playlist.filter { Catalog.byId.containsKey(it) })
        }
        Log.i(TAG, "init locale=${Lang.locale} session=${session?.currentId} favorites=${favorites.size} frame=${frame.currentId}")
    }
}
