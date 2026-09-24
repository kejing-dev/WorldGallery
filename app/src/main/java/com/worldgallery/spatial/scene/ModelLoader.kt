package com.worldgallery.spatial.scene

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.worldgallery.spatial.data.ModelAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * WG-03 "Load 3D": `未加载 → 下载中 → 校验中 → 加载中 → 可交互`, with cancel, retry and
 * de-duplication. Network progress (this class) and the on-device Entity load (the SpatialView that
 * shows the model, see [ModelLoadState.LoadingEntity]) are reported separately.
 */
sealed class ModelLoadState {
    object NotLoaded : ModelLoadState()
    data class Downloading(val received: Long, val total: Long) : ModelLoadState() {
        val fraction: Float? get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
    }
    object Verifying : ModelLoadState()
    object LoadingEntity : ModelLoadState()
    data class Ready(val file: File) : ModelLoadState()
    data class Failed(val reason: String, val retryable: Boolean) : ModelLoadState()

    val isBusy: Boolean get() = this is Downloading || this is Verifying || this is LoadingEntity
}

class ModelLoader(private val context: Context) {
    val states: SnapshotStateMap<String, ModelLoadState> = mutableStateMapOf()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()
    private val dir = File(context.filesDir, "models").apply { mkdirs() }
    /** `pico-cli files push` can write here without any permission: /sdcard/Android/data/<pkg>/files/models */
    private val externalDir: File? = runCatching { context.getExternalFilesDir("models") }.getOrNull()

    /** Optional runtime URL overrides (debug channel), for models whose source needs a manual download. */
    var urlOverrides: Map<String, String> = emptyMap()

    /** `files/models/<id>.<format>`; a side-loaded file with another extension (e.g. .usdz) is accepted too. */
    fun localFile(asset: ModelAsset): File =
        listOfNotNull(dir, externalDir).flatMap { d -> d.listFiles()?.toList() ?: emptyList() }
            .firstOrNull { it.isFile && it.nameWithoutExtension == asset.id && it.extension != "part" && it.length() > 0 }
            ?: File(dir, "${asset.id}.${asset.format}")

    /** Current state; a file already on disk counts as ready without any confirmation (PRD 5.1). */
    fun state(asset: ModelAsset): ModelLoadState {
        states[asset.id]?.let { return it }
        val f = localFile(asset)
        return if (f.exists() && f.length() > 0) ModelLoadState.Ready(f).also { states[asset.id] = it } else ModelLoadState.NotLoaded
    }

    fun setState(asset: ModelAsset, s: ModelLoadState) { states[asset.id] = s }

    fun request(asset: ModelAsset) {
        val current = state(asset)
        if (current is ModelLoadState.Ready || current.isBusy) return          // de-duplicated
        val url = urlOverrides[asset.id] ?: asset.downloadUrl
        if (url == null) {
            states[asset.id] = ModelLoadState.Failed(
                if (com.worldgallery.spatial.data.Lang.isZh) "模型文件尚未取得：${asset.noteZh}" else "Model file not available yet: ${asset.noteEn}",
                retryable = false,
            )
            return
        }
        states[asset.id] = ModelLoadState.Downloading(0, asset.expectedBytes ?: -1)
        val job = scope.launch {
            val local = url.startsWith("file:") || url.startsWith("/")
            val ext = if (local) url.substringAfterLast('.', asset.format).substringBefore('?').lowercase().takeIf { it.length in 2..5 } ?: asset.format else asset.format
            val target = File(dir, "${asset.id}.$ext")
            val tmp = File(dir, "${asset.id}.part")
            try {
                if (local) copyLocal(url, tmp) { received, total -> states[asset.id] = ModelLoadState.Downloading(received, total) }
                else download(url, tmp, asset.expectedBytes) { received, total -> states[asset.id] = ModelLoadState.Downloading(received, total) }
                ensureActive()
                states[asset.id] = ModelLoadState.Verifying
                verify(tmp, asset, checkSize = !local, checkMagic = ext == "glb")
                dir.listFiles()?.filter { it.nameWithoutExtension == asset.id && it != tmp }?.forEach { it.delete() }
                if (!tmp.renameTo(target)) throw IllegalStateException("rename failed")
                states[asset.id] = ModelLoadState.Ready(target)
                Log.i(TAG, "model ${asset.id} ready: ${target.length()} bytes")
            } catch (e: kotlinx.coroutines.CancellationException) {
                tmp.delete()
                states[asset.id] = ModelLoadState.NotLoaded
                Log.i(TAG, "model ${asset.id} cancelled")
            } catch (e: Exception) {
                tmp.delete()
                Log.e(TAG, "model ${asset.id} failed", e)
                states[asset.id] = ModelLoadState.Failed(e.message ?: e.javaClass.simpleName, retryable = true)
            } finally {
                jobs.remove(asset.id)
            }
        }
        jobs[asset.id] = job
    }

    fun cancel(asset: ModelAsset) { jobs[asset.id]?.cancel() }

    fun retry(asset: ModelAsset) {
        states.remove(asset.id)
        request(asset)
    }

    /** Debug hook: a model file pushed with `pico-cli files push` is picked up on the next state check. */
    fun forget(asset: ModelAsset) { states.remove(asset.id) }

    private suspend fun download(url: String, tmp: File, expected: Long?, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WorldGallery/0.1 (PICO Spatial)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val total = if (conn.contentLengthLong > 0) conn.contentLengthLong else (expected ?: -1L)
            var received = 0L
            var lastReport = 0L
            conn.inputStream.use { input ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        received += n
                        if (received - lastReport > 128 * 1024) { lastReport = received; progress(received, total) }
                    }
                    out.fd.sync()
                }
            }
            progress(received, total)
        } finally {
            conn.disconnect()
        }
    }

    /** Side-loaded models (`file:///sdcard/...` or an absolute path) go through the same states as a download. */
    private suspend fun copyLocal(url: String, tmp: File, progress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val src = File(if (url.startsWith("file:")) java.net.URI(url).path else url)
        if (!src.exists()) throw IllegalStateException("file not found: ${src.path}")
        val total = src.length()
        var received = 0L
        src.inputStream().use { input ->
            FileOutputStream(tmp).use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    received += n
                    progress(received, total)
                }
            }
        }
    }

    private fun verify(f: File, asset: ModelAsset, checkSize: Boolean = true, checkMagic: Boolean = asset.format == "glb") {
        if (!f.exists() || f.length() <= 0) throw IllegalStateException("empty file")
        if (checkSize) asset.expectedBytes?.let { if (f.length() != it) throw IllegalStateException("size mismatch: ${f.length()} != $it") }
        if (checkMagic) {
            val magic = ByteArray(4)
            f.inputStream().use { it.read(magic) }
            if (String(magic, Charsets.US_ASCII) != "glTF") throw IllegalStateException("not a GLB file")
        }
    }

    companion object { private const val TAG = "WorldGallery.Model" }
}
