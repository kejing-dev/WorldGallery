package com.worldgallery.spatial.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/** Per-artwork viewing state (PRD 5.2 / 5.3). Paintings use zoom + pan, sculptures use yaw/pitch + scale. */
data class ViewState(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val scale: Float = 1f,
) {
    val isDefault: Boolean get() = this == ViewState()

    fun toJson(): JSONObject = JSONObject().put("zoom", zoom).put("panX", panX).put("panY", panY).put("yaw", yaw).put("pitch", pitch).put("scale", scale)

    companion object {
        fun fromJson(o: JSONObject) = ViewState(
            o.optDouble("zoom", 1.0).toFloat(), o.optDouble("panX", 0.0).toFloat(), o.optDouble("panY", 0.0).toFloat(),
            o.optDouble("yaw", 0.0).toFloat(), o.optDouble("pitch", 0.0).toFloat(), o.optDouble("scale", 1.0).toFloat(),
        )
    }
}

/** Where a viewing queue came from; shown in the title bar and used for "continue viewing". */
sealed class QueueSource {
    data class FromCollection(val collectionId: String) : QueueSource()
    object Favorites : QueueSource()
    data class Search(val query: String) : QueueSource()

    fun label(): String = when (this) {
        is FromCollection -> Catalog.collection(collectionId)?.title ?: collectionId
        Favorites -> tr("我的收藏", "Favorites")
        is Search -> tr("搜索：", "Search: ") + query
    }

    fun toJson(): JSONObject = when (this) {
        is FromCollection -> JSONObject().put("kind", "collection").put("id", collectionId)
        Favorites -> JSONObject().put("kind", "favorites")
        is Search -> JSONObject().put("kind", "search").put("query", query)
    }

    companion object {
        fun fromJson(o: JSONObject): QueueSource = when (o.optString("kind")) {
            "favorites" -> Favorites
            "search" -> Search(o.optString("query"))
            else -> FromCollection(o.optString("id", "essentials"))
        }
    }
}

/** `ViewingSession.queue` (PRD 5.4): a frozen id list plus the current index. */
data class ViewingSession(val source: QueueSource, val ids: List<String>, val index: Int) {
    val currentId: String? get() = ids.getOrNull(index)
    val hasPrev: Boolean get() = index > 0
    val hasNext: Boolean get() = index < ids.size - 1
    val atEnd: Boolean get() = ids.isNotEmpty() && index == ids.size - 1

    fun toJson(): JSONObject = JSONObject().put("source", source.toJson()).put("ids", JSONArray(ids)).put("index", index)

    companion object {
        fun fromJson(o: JSONObject): ViewingSession? {
            val ids = o.optJSONArray("ids")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: return null
            if (ids.isEmpty()) return null
            return ViewingSession(QueueSource.fromJson(o.getJSONObject("source")), ids, o.optInt("index", 0).coerceIn(0, ids.size - 1))
        }
    }
}

enum class FrameMode { FIXED, LOOP }

/** Art frame settings + playlist snapshot (PRD 5.7). `playing` is the user's intent, not the effective state. */
data class FrameSettings(
    val mode: FrameMode = FrameMode.FIXED,
    val artworkId: String? = null,
    val playlistSource: QueueSource? = null,
    val playlist: List<String> = emptyList(),
    val index: Int = 0,
    val intervalMinutes: Int = 5,
    val shuffle: Boolean = false,
    val playing: Boolean = true,
    val windowOpen: Boolean = false,
) {
    val currentId: String? get() = if (mode == FrameMode.LOOP) playlist.getOrNull(index) ?: artworkId else artworkId

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode.name).put("artworkId", artworkId ?: JSONObject.NULL)
        .put("playlistSource", playlistSource?.toJson() ?: JSONObject.NULL)
        .put("playlist", JSONArray(playlist)).put("index", index).put("intervalMinutes", intervalMinutes)
        .put("shuffle", shuffle).put("playing", playing).put("windowOpen", windowOpen)

    companion object {
        val INTERVALS = listOf(1, 5, 15, 60)
        fun fromJson(o: JSONObject): FrameSettings {
            val list = o.optJSONArray("playlist")?.let { arr -> List(arr.length()) { arr.getString(it) } } ?: emptyList()
            return FrameSettings(
                mode = runCatching { FrameMode.valueOf(o.optString("mode", "FIXED")) }.getOrDefault(FrameMode.FIXED),
                artworkId = o.optString("artworkId").takeIf { it.isNotEmpty() && it != "null" },
                playlistSource = o.optJSONObject("playlistSource")?.let { QueueSource.fromJson(it) },
                playlist = list, index = o.optInt("index", 0), intervalMinutes = o.optInt("intervalMinutes", 5),
                shuffle = o.optBoolean("shuffle", false), playing = o.optBoolean("playing", true), windowOpen = o.optBoolean("windowOpen", false),
            )
        }
    }
}

/** Offline persistence for favorites, session, view states, frame and preferences (WG-10). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("world_gallery", Context.MODE_PRIVATE)

    var locale: String?
        get() = prefs.getString("locale", null)
        set(v) = prefs.edit().putString("locale", v).apply()

    var immersiveHintSeen: Boolean
        get() = prefs.getBoolean("immersiveHintSeen", false)
        set(v) = prefs.edit().putBoolean("immersiveHintSeen", v).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboardingDone", false)
        set(v) = prefs.edit().putBoolean("onboardingDone", v).apply()

    /** Index into GalleryViewModel.FRAME_SIZES: the user's one-tap frame size. */
    var frameSizeStep: Int
        get() = prefs.getInt("frameSizeStep", 2)
        set(v) = prefs.edit().putInt("frameSizeStep", v).apply()


    var reduceMotion: Boolean
        get() = prefs.getBoolean("reduceMotion", false)
        set(v) = prefs.edit().putBoolean("reduceMotion", v).apply()

    fun loadFavorites(): List<String> = prefs.getString("favorites", "")!!.split(',').filter { it.isNotBlank() }
    fun saveFavorites(ids: List<String>) = prefs.edit().putString("favorites", ids.joinToString(",")).apply()

    fun loadSession(): ViewingSession? = runCatching {
        prefs.getString("session", null)?.let { ViewingSession.fromJson(JSONObject(it)) }
    }.onFailure { Log.w(TAG, "session unreadable", it) }.getOrNull()

    fun saveSession(s: ViewingSession?) = prefs.edit().putString("session", s?.toJson()?.toString()).apply()

    fun loadViewStates(): Map<String, ViewState> = runCatching {
        val o = JSONObject(prefs.getString("viewStates", "{}")!!)
        o.keys().asSequence().associateWith { ViewState.fromJson(o.getJSONObject(it)) }
    }.getOrDefault(emptyMap())

    fun saveViewStates(m: Map<String, ViewState>) {
        val o = JSONObject()
        for ((k, v) in m) if (!v.isDefault) o.put(k, v.toJson())
        prefs.edit().putString("viewStates", o.toString()).apply()
    }

    fun loadFrame(): FrameSettings = runCatching {
        prefs.getString("frame", null)?.let { FrameSettings.fromJson(JSONObject(it)) }
    }.getOrNull() ?: FrameSettings()

    fun saveFrame(f: FrameSettings) = prefs.edit().putString("frame", f.toJson().toString()).apply()

    fun loadModelUrlOverrides(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString("modelUrls", "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun saveModelUrlOverrides(m: Map<String, String>) = prefs.edit().putString("modelUrls", JSONObject(m).toString()).apply()

    fun wipe() = prefs.edit().clear().apply()

    companion object { private const val TAG = "WorldGallery.Store" }
}
