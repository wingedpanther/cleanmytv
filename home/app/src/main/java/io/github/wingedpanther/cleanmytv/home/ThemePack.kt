package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A theme CleanMyTV put on the TV: its look, and a few pictures drawn for it. */
data class PackTheme(val id: String, val name: String, val look: JSONObject, val pictures: Int)

/**
 * The built-in themes, so the TV can switch them without the computer: CleanMyTV draws each theme's
 * pictures and uploads them once ("Put themes on the TV"). They go into themes.upload/ and replace
 * themes/ only when the last one has arrived, so a broken upload leaves the old set alone.
 */
object ThemePack {
    private val ID = Regex("^[a-z0-9-]{1,40}$")
    const val MAX_PICTURES = 4
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private fun dir(ctx: Context) = File(ctx.filesDir, "themes")
    private fun upload(ctx: Context) = File(ctx.filesDir, "themes.upload")
    fun picture(ctx: Context, id: String, v: Int) = File(File(dir(ctx), id), "$v.jpg")

    fun list(ctx: Context): List<PackTheme> {
        val order = runCatching { JSONArray(File(dir(ctx), "order.json").readText()) }.getOrNull() ?: return emptyList()
        return (0 until order.length()).mapNotNull { i ->
            val id = order.optString(i).takeIf { ID.matches(it) } ?: return@mapNotNull null
            val meta = runCatching { JSONObject(File(File(dir(ctx), id), "meta.json").readText()) }.getOrNull() ?: return@mapNotNull null
            val n = (0 until MAX_PICTURES).count { picture(ctx, id, it).exists() }
            if (n == 0) null else PackTheme(id, meta.optString("name", id).take(40), meta.optJSONObject("look") ?: JSONObject(), n)
        }
    }

    // ---- upload, from ConfigProvider --------------------------------------------------------------

    fun begin(ctx: Context) { upload(ctx).deleteRecursively(); upload(ctx).mkdirs() }

    fun meta(ctx: Context, o: JSONObject) {
        val id = o.optString("id").takeIf { ID.matches(it) } ?: throw IllegalArgumentException("bad theme id")
        // Only the look settings a theme may carry; the rest of the config stays the owner's.
        val look = HomeConfig.fromJson(o.optJSONObject("look") ?: JSONObject()).toJson()
        val keep = JSONObject()
        for (k in LOOK_KEYS) if (o.optJSONObject("look")?.has(k) == true) keep.put(k, look.get(k))
        File(upload(ctx), id).mkdirs()
        File(File(upload(ctx), id), "meta.json").writeText(JSONObject().put("name", o.optString("name").take(40)).put("look", keep).toString())
    }

    /** Where the next picture's bytes go, as "<id>:<picture>". */
    fun target(ctx: Context, spec: String): File {
        val (id, v) = spec.split(":").let { it.getOrNull(0).orEmpty() to (it.getOrNull(1)?.toIntOrNull() ?: -1) }
        require(ID.matches(id) && v in 0 until MAX_PICTURES) { "bad picture $spec" }
        return File(File(upload(ctx), id), "$v.jpg")
    }

    fun end(ctx: Context, order: JSONArray) {
        File(upload(ctx), "order.json").writeText(order.toString())
        val old = File(ctx.filesDir, "themes.old")
        old.deleteRecursively()
        dir(ctx).renameTo(old)
        upload(ctx).renameTo(dir(ctx))
        old.deleteRecursively()
        _version.value++
    }

    fun clear(ctx: Context) { dir(ctx).deleteRecursively(); _version.value++ }

    // ---- use, from the carousel and the remote ----------------------------------------------------

    /** The theme's look and picture [v] become Home's, and the new wallpaper comes in with the intro. */
    fun apply(ctx: Context, t: PackTheme, v: Int, withLook: Boolean = true) {
        val src = picture(ctx, t.id, v)
        if (!src.exists()) return
        val tmp = ConfigStore.wallpaperUpload(ctx)
        src.copyTo(tmp, overwrite = true)
        tmp.renameTo(ConfigStore.wallpaper(ctx))
        val change = if (withLook) JSONObject(t.look.toString()) else JSONObject()
        ConfigStore.update(ctx, change.put("wallpaper_from", "${t.id}:$v"))
        ConfigStore.wallpaperChanged()
    }

    /** The current theme's next picture, for a remote button. Nothing happens on a wallpaper of the owner's own. */
    fun next(ctx: Context) {
        val (id, v) = ConfigStore.config.value.wallpaperFrom.split(":").takeIf { it.size == 2 } ?: return
        val t = list(ctx).firstOrNull { it.id == id } ?: run { Log.i(TAG, "next wallpaper: $id isn't on the TV"); return }
        apply(ctx, t, ((v.toIntOrNull() ?: 0) + 1) % t.pictures, withLook = false)
    }

    private val LOOK_KEYS = listOf(
        "accent", "background", "focus", "wallpaper_dim", "viz_style", "viz_colour", "viz_peaks", "icon_style", "tile_shape",
    )
}
