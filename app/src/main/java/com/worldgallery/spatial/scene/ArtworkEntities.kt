package com.worldgallery.spatial.scene

import android.content.Context
import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.PointLightComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.worldgallery.spatial.app.GalleryViewModel
import com.worldgallery.spatial.data.Artwork
import com.worldgallery.spatial.ui.CROSSFADE_MS
import com.worldgallery.spatial.data.ModelAsset
import com.worldgallery.spatial.data.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

private const val TAG = "WorldGallery.Scene"

fun Entity.transform(): TransformComponent = components[TransformComponent::class.java]!!

/**
 * A loaded sculpture / scan under a pivot entity: the pivot carries the collider, the user's yaw,
 * pitch and scale; the model is centred inside it so rotation happens about the model centre (PRD 5.3).
 */
class ModelHolder {
    var pivot: Entity? = null; private set
    private var loaded: Entity? = null
    private var baseScale = 1f
    val isLoaded: Boolean get() = pivot != null

    /** Fits the model into a box (metres) about its centre; width / depth limits keep wide objects inside the container. */
    suspend fun load(parent: Entity, file: File, targetHeight: Float, position: Vector3, maxWidth: Float = targetHeight * 1.6f, maxDepth: Float = targetHeight * 1.6f) {
        // Documented off-main-thread path: parsing a large USDZ/GLB on the main thread ANRs the emulator.
        val root = withContext(Dispatchers.IO) { Entity.load(file) }
        val bb = root.getVisualBounds(root, true, false)
        fun safe(v: Float) = if (v > 1e-4f) v else 1f
        baseScale = minOf(targetHeight / safe(bb.size.y), maxWidth / safe(bb.size.x), maxDepth / safe(bb.size.z))
        val center = (bb.min + bb.max) * 0.5f
        root.transform().setPosition(center * -1f)
        val p = Entity().apply { setName("artwork") }
        p.addChild(root)
        p.transform().setPosition(position)
        p.transform().setScaleVector(Vector3(baseScale, baseScale, baseScale))
        val pad = bb.size * 1.05f
        p.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(pad.x, pad.y, pad.z))), PhysicsMaterialResource()))
        p.components.set(InteractableComponent())
        runCatching { p.components.set(HoverEffectComponent()) }
        parent.addChild(p)
        pivot = p
        loaded = root
        Log.i(TAG, "model ${file.name} bounds=${bb.size} scale=$baseScale")
    }

    fun apply(vs: ViewState, frontYaw: Float) {
        val p = pivot ?: return
        p.transform().apply {
            setEulerAngles(EulerAngles(pitch = vs.pitch, yaw = frontYaw + vs.yaw))
            val s = baseScale * vs.scale
            setScaleVector(Vector3(s, s, s))
        }
    }

    fun destroy() {
        pivot?.let { p -> runCatching { p.removeFromParent() }; runCatching { p.destroy(true) } }
        pivot = null
        loaded = null
    }
}

/**
 * Full Space scene (Figma "Full Space / viewer"): a quiet dark gradient sky, a floor, soft lights and
 * exactly one artwork in front of the seated viewer. No windows, labels or buttons (PRD 5.6).
 */
class ImmersiveScene(private val context: Context, private val vm: GalleryViewModel) {
    companion object {
        val ART_POS = Vector3(0f, 1.35f, -2.4f)
        val MODEL_POS = Vector3(0f, 1.15f, -2.0f)
        const val MAX_W = 2.6f
        const val MAX_H = 1.7f
        const val MODEL_HEIGHT = 1.3f
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var content: SpatialViewContent? = null
    private val root = Entity().apply { setName("immersiveRoot") }
    private var painting: Entity? = null
    private var paintingTexture: TextureResource? = null
    private var paintingMaterial: UnlitMaterial? = null
    /** The previous painting while it fades out under the new one. */
    private var fading: Triple<Entity, TextureResource?, UnlitMaterial?>? = null
    private val navPads = ArrayList<Entity>()
    private val model = ModelHolder()
    private var showJob: Job? = null

    var built: Boolean = false; private set
    var showing3D: Boolean = false; private set
    var planeW: Float = 1f; private set
    var planeH: Float = 1f; private set
    var frontYaw: Float = 0f; private set

    fun build(c: SpatialViewContent) {
        content = c
        Log.i(TAG, "stage build: adding root")
        c.addEntity(root)
        buildEnvironment()
        built = true
        Log.i(TAG, "stage build: done")
    }

    private fun buildEnvironment() {
        Log.i(TAG, "stage env: sky")
        runCatching {
            val sky = Entity().apply { setName("sky") }
            val tex = TextureResource.create(Bitmaps.gradient(intArrayOf(0xFF373B32.toInt(), 0xFF252B24.toInt(), 0xFF151B17.toInt())))
            val mat = UnlitMaterial.create().apply { setBaseColorTexture(tex); setCullingMode(MaterialCullingMode.FRONT) }
            sky.components.set(ModelComponent(MeshResource.createSphere(14f), mat))
            root.addChild(sky)
        }.onFailure { Log.w(TAG, "sky sphere unavailable", it) }
        Log.i(TAG, "stage env: floor")
        runCatching {
            val floor = Entity().apply { setName("floor") }
            val mat = UnlitMaterial.create().apply { setBaseColor(Color4(0.10f, 0.12f, 0.10f, 1f)) }
            floor.components.set(ModelComponent(MeshResource.createPlane(14f, 14f), mat))
            floor.transform().setEulerAngles(EulerAngles(pitch = -90f))
            floor.transform().setPosition(Vector3(0f, 0.0f, -3f))
            root.addChild(floor)
        }.onFailure { Log.w(TAG, "floor unavailable", it) }
        Log.i(TAG, "stage env: lights")
        runCatching {
            val key = Entity().apply { setName("keyLight") }
            key.components.set(DirectionalLightComponent(Color4(1f, 0.97f, 0.92f, 1f), 2.5f))
            key.transform().setEulerAngles(EulerAngles(pitch = -50f, yaw = 35f))
            root.addChild(key)
            val fill = Entity().apply { setName("fillLight") }
            fill.components.set(PointLightComponent(Color4(0.9f, 0.95f, 1f, 1f), 40f, 8f))
            fill.transform().setPosition(Vector3(-1.6f, 2.2f, -0.8f))
            root.addChild(fill)
        }.onFailure { Log.w(TAG, "lights unavailable", it) }
    }

    /** Switches the single displayed work; the previous one stays until the new one is ready (PRD 5.4 #3). */
    fun show(a: Artwork?) {
        showJob?.cancel()
        if (a == null) { clearArtwork(); return }
        showJob = scope.launch {
            val asset = a.model
            val ready = asset?.let { vm.models.state(it) as? ModelLoadState.Ready }
            if (asset != null && ready != null) show3D(a, asset, ready.file) else show2D(a)
        }
    }

    private suspend fun show2D(a: Artwork) {
        val img = a.image ?: run { clearArtwork(); return }
        val bmp = withContext(Dispatchers.IO) { Bitmaps.decodeSync(context, img.path) } ?: run { clearArtwork(); return }
        val aspect = img.width.toFloat() / img.height
        val w = min(MAX_W, MAX_H * aspect)
        val h = w / aspect
        // Painting → painting cross-fades: the old plane stays and fades out under the new one (PRD 5.4 #3, no blank frame).
        val old = if (!showing3D && painting != null) Triple(painting!!, paintingTexture, paintingMaterial) else null
        if (old == null) clearArtwork() else { clearFading(); clearNavPads(); painting = null; paintingTexture = null; paintingMaterial = null }
        val mat = runCatching {
            Log.i(TAG, "stage 2D: texture ${bmp.width}x${bmp.height} config=${bmp.config}")
            val tex = TextureResource.create(bmp)
            val mat = UnlitMaterial.create().apply {
                setBaseColorTexture(tex)
                setBlendingMode(BlendingMode.TRANSPARENT)
                setOpacity(if (old == null) 1f else 0f)
            }
            val e = Entity().apply { setName("artwork") }
            e.components.set(ModelComponent(MeshResource.createPlane(w, h), mat))
            e.transform().setPosition(ART_POS)
            e.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(w, h, 0.02f))), PhysicsMaterialResource()))
            e.components.set(InteractableComponent())
            root.addChild(e)
            painting = e
            paintingTexture = tex
            paintingMaterial = mat
            planeW = w; planeH = h; showing3D = false
            addNavPads(w, h, ART_POS)
            applyView(vm.viewState(a.id))
            Log.i(TAG, "immersive 2D ${a.id} ${w}x${h} m")
            mat
        }.getOrElse { Log.e(TAG, "painting plane failed", it); old?.let { clearFading(it) }; return }
        if (old == null) return
        fading = old
        // the old plane sits a hair behind the new one so the two never z-fight while both are visible
        runCatching { old.first.transform().setPosition(Vector3(ART_POS.x, ART_POS.y, ART_POS.z - 0.01f)) }
        val ms = if (vm.reduceMotion) 0 else CROSSFADE_MS
        try {
            if (ms > 0) {
                val steps = 18
                for (i in 1..steps) {
                    delay((ms / steps).toLong())
                    val t = i / steps.toFloat()
                    mat.setOpacity(t)
                    old.third?.setOpacity(1f - t)
                }
            }
        } finally {
            runCatching { mat.setOpacity(1f) }   // a cancelled fade must not leave the new work translucent
            clearFading()
        }
    }

    private fun clearFading(f: Triple<Entity, TextureResource?, UnlitMaterial?>? = fading) {
        f ?: return
        runCatching { f.first.removeFromParent() }; runCatching { f.first.destroy(true) }
        f.second?.let { runCatching { it.close() } }
        if (f === fading) fading = null
    }

    private fun clearNavPads() {
        for (p in navPads) { runCatching { p.removeFromParent() }; runCatching { p.destroy(true) } }
        navPads.clear()
    }

    private suspend fun show3D(a: Artwork, asset: ModelAsset, file: File) {
        clearArtwork()
        runCatching {
            model.load(root, file, MODEL_HEIGHT, MODEL_POS, maxWidth = 2.2f, maxDepth = 1.6f)
            frontYaw = asset.frontYaw
            showing3D = true
            planeW = 1f; planeH = 1f
            addNavPads(1.2f, MODEL_HEIGHT, MODEL_POS)
            applyView(vm.viewState(a.id))
            Log.i(TAG, "immersive 3D ${a.id}")
        }.onFailure {
            Log.e(TAG, "model failed in stage, falling back to image", it)
            if (a.image != null) show2D(a)
        }
    }

    /** Invisible tap targets left / right of the work: previous / next (the only navigation in Full Space). */
    private fun addNavPads(w: Float, h: Float, at: Vector3) {
        for ((name, sign) in listOf("nav:prev" to -1f, "nav:next" to 1f)) {
            val pad = Entity().apply { setName(name) }
            pad.transform().setPosition(Vector3(at.x + sign * (w / 2f + 0.5f), at.y, at.z))
            pad.components.set(CollisionComponent(listOf(ShapeResource.createBox(Vector3(0.7f, h, 0.06f))), PhysicsMaterialResource()))
            pad.components.set(InteractableComponent())
            root.addChild(pad)
            navPads.add(pad)
        }
    }

    fun applyView(vs: ViewState?) {
        vs ?: return
        if (showing3D) { model.apply(vs, frontYaw); return }
        painting?.transform()?.apply {
            setPosition(Vector3(ART_POS.x + vs.panX * planeW, ART_POS.y - vs.panY * planeH, ART_POS.z))
            setScaleVector(Vector3(vs.zoom, vs.zoom, 1f))
        }
    }

    private fun clearArtwork() {
        clearFading()
        painting?.let { e -> runCatching { e.removeFromParent() }; runCatching { e.destroy(true) } }
        painting = null
        paintingTexture?.let { runCatching { it.close() } }
        paintingTexture = null
        paintingMaterial = null
        model.destroy()
        clearNavPads()
        showing3D = false
    }

    fun destroy() {
        showJob?.cancel()
        scope.cancel()
        clearArtwork()
        runCatching { content?.removeEntity(root) }
        runCatching { root.destroy(true) }
        built = false
    }
}
