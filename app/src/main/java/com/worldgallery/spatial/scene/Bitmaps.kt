package com.worldgallery.spatial.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes bundled artwork images once and keeps them for the session (paintings ≈ 20 MB each at 2560 px). */
object Bitmaps {
    private const val TAG = "WorldGallery.Bitmaps"
    private val cache = object : LruCache<String, Bitmap>(160 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    /**
     * ImageBitmap wrappers for the same bitmaps. Bounded like [cache]: an unbounded map would pin every
     * full-resolution painting (≈53 MB at 4096 px, ARGB_8888) for the life of the process and would also
     * keep handing out wrappers around bitmaps the LruCache had already evicted.
     */
    private val imageCache = object : LruCache<String, ImageBitmap>(4) {}

    fun decodeSync(context: Context, path: String): Bitmap? {
        cache.get(path)?.let { return it }
        return runCatching {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                    inMutable = false
                })
            }
        }.onFailure { Log.e(TAG, "decode failed: $path", it) }.getOrNull()?.also { cache.put(path, it) }
    }

    suspend fun decode(context: Context, path: String): Bitmap? = withContext(Dispatchers.IO) { decodeSync(context.applicationContext, path) }

    suspend fun image(context: Context, path: String): ImageBitmap? {
        imageCache.get(path)?.let { return it }
        val bmp = decode(context, path) ?: return null
        return bmp.asImageBitmap().also { imageCache.put(path, it) }
    }

    /** Called when the system asks for memory back; the next read re-decodes from assets. */
    fun trim() {
        imageCache.evictAll()
        cache.evictAll()
        Log.i(TAG, "bitmap caches trimmed")
    }

    /** Vertical gradient used as the immersive sky (top → horizon → floor). */
    fun gradient(colors: IntArray, height: Int = 256): Bitmap {
        val bmp = Bitmap.createBitmap(4, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, height.toFloat(), colors, null, android.graphics.Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 4f, height.toFloat(), paint)
        return bmp
    }
}
