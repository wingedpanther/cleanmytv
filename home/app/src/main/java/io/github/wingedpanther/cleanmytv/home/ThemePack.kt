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
        // Themes saved on the TV that this upload doesn't carry stay, after the new ones.
        val ids = (0 until order.length()).map { order.optString(it) }.toMutableList()
        for (t in list(ctx)) if (t.id !in ids && t.id.startsWith(MINE)) {
            File(dir(ctx), t.id).copyRecursively(File(upload(ctx), t.id), overwrite = true)
            ids += t.id
        }
        File(upload(ctx), "order.json").writeText(JSONArray(ids).toString())
        val old = File(ctx.filesDir, "themes.old")
        old.deleteRecursively()
        dir(ctx).renameTo(old)
        upload(ctx).renameTo(dir(ctx))
        old.deleteRecursively()
        _version.value++
    }

    fun clear(ctx: Context) { dir(ctx).deleteRecursively(); _version.value++ }

    // ---- use, from the carousel and the remote ----------------------------------------------------

    /**
     * The theme's look and picture [v] become Home's, and the new wallpaper comes in with the intro. The look
     * is the one the owner left the theme with, when they changed it while it was on.
     */
    fun apply(ctx: Context, t: PackTheme, v: Int, withLook: Boolean = true) {
        val src = picture(ctx, t.id, v)
        if (!src.exists()) return
        val tmp = ConfigStore.wallpaperUpload(ctx)
        src.copyTo(tmp, overwrite = true)
        tmp.renameTo(ConfigStore.wallpaper(ctx))
        val change = if (withLook) JSONObject((kept(ctx, t.id) ?: t.look).toString()) else JSONObject()
        ConfigStore.update(ctx, change.put("wallpaper_from", "${t.id}:$v"), remember = false)
        ConfigStore.wallpaperChanged()
    }

    // ---- each theme keeps its own look ------------------------------------------------------------

    private fun memory(ctx: Context, id: String) = File(File(ctx.filesDir, "theme_looks"), "$id.json")

    /** The theme on screen, from the wallpaper's place in it, when it's one on the TV. */
    private fun onScreen(ctx: Context): String? =
        ConfigStore.config.value.wallpaperFrom.substringBefore(':').takeIf { ID.matches(it) && File(File(dir(ctx), it), "meta.json").exists() }

    /** The look the owner left theme [id] with, or null when they haven't changed it (or the switch is off). */
    fun kept(ctx: Context, id: String): JSONObject? {
        if (!ConfigStore.config.value.themeMemory || !ID.matches(id)) return null
        return runCatching { JSONObject(memory(ctx, id).readText()) }.getOrNull()
    }

    /** Called after the owner changes the look: the theme on screen keeps all of it, for next time. */
    fun remember(ctx: Context) {
        if (!ConfigStore.config.value.themeMemory) return
        val id = onScreen(ctx) ?: return
        memory(ctx, id).apply { parentFile?.mkdirs() }.writeText(snapshot().toString())
    }

    /** Theme [id] goes back to its own look next time. */
    fun forget(ctx: Context, id: String) { memory(ctx, id).delete(); _version.value++ }

    fun changed(ctx: Context): Set<String> = list(ctx).filter { memory(ctx, it.id).exists() }.map { it.id }.toSet()

    private fun snapshot(): JSONObject {
        val now = ConfigStore.config.value.toJson()
        return JSONObject().also { o -> for (k in MEMORY_KEYS) if (now.has(k)) o.put(k, now.get(k)) }
    }

    /**
     * Home's look and wallpaper now become a theme on the TV called [name], from the TV or the desktop app.
     * A theme of that name already there is updated: a built-in one keeps its pictures and takes the look,
     * one saved here takes both. Returns the theme's id.
     */
    fun saveCurrent(ctx: Context, name: String): String {
        val clean = name.replace(Regex("[\\u0000-\\u001f\\u007f]"), "").trim().take(40)
        require(clean.isNotEmpty()) { "the theme needs a name" }
        val same = list(ctx).firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (same != null && !same.id.startsWith(MINE)) {
            memory(ctx, same.id).apply { parentFile?.mkdirs() }.writeText(snapshot().toString())
            ConfigStore.update(ctx, JSONObject().put("wallpaper_from", "${same.id}:0"), remember = false)
            _version.value++
            return same.id
        }
        val wall = ConfigStore.wallpaper(ctx)
        require(wall.exists()) { "put a wallpaper on first; a theme needs a picture" }
        val id = same?.id ?: (MINE + clean.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(36).ifEmpty { "theme" })
        val d = File(dir(ctx), id).apply { deleteRecursively(); mkdirs() }
        wall.copyTo(File(d, "0.jpg"), overwrite = true)
        File(d, "meta.json").writeText(JSONObject().put("name", clean).put("look", snapshot()).toString())
        memory(ctx, id).delete()
        val order = runCatching { JSONArray(File(dir(ctx), "order.json").readText()) }.getOrNull() ?: JSONArray()
        if ((0 until order.length()).none { order.optString(it) == id }) order.put(id)
        File(dir(ctx), "order.json").writeText(order.toString())
        ConfigStore.update(ctx, JSONObject().put("wallpaper_from", "$id:0"), remember = false)
        _version.value++
        Log.i(TAG, "saved the look as theme $id")
        return id
    }

    /** The current theme's next picture, for a remote button. Nothing happens on a wallpaper of the owner's own. */
    fun next(ctx: Context) {
        val (id, v) = ConfigStore.config.value.wallpaperFrom.split(":").takeIf { it.size == 2 } ?: return
        val t = list(ctx).firstOrNull { it.id == id } ?: run { Log.i(TAG, "next wallpaper: $id isn't on the TV"); return }
        apply(ctx, t, ((v.toIntOrNull() ?: 0) + 1) % t.pictures, withLook = false)
    }

    private val LOOK_KEYS = listOf(
        "accent", "background", "focus", "wallpaper_dim", "viz_style", "viz_colour", "viz_peaks", "icon_style", "tile_shape",
        "shade_top", "shade_top_reach", "shade_bottom", "shade_bottom_reach", "wash", "wash_reach", "font",
    )
    /** What a saved day or night look carries: a theme's keys, plus the glass and the wallpaper's place in a theme. */
    val SAVED_LOOK_KEYS = LOOK_KEYS + listOf("tile_glass", "wallpaper_from")
    /** What each theme keeps of the owner's changes: its look, and the sizes, spacing and music bars around it. */
    val MEMORY_KEYS = (LOOK_KEYS + listOf(
        "tile_glass", "top_space", "dock_scale", "tile_scale", "show_names", "clock",
        "visualiser", "viz_bars", "viz_height", "viz_opacity", "viz_sensitivity",
    )).toSet()
    /** Ids of themes saved on the TV rather than drawn by the desktop app. */
    private const val MINE = "my-"

}
