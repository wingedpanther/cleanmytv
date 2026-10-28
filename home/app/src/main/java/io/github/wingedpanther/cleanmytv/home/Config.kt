package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Everything CleanMyTV can change. The desktop app sends any subset; the rest keeps its value. */
data class HomeConfig(
    // Look
    val accent: Long = 0xFFFF3000,
    val focus: String = "glow", // glow, outline, none
    val background: Long = 0xFF0B1E2A,
    val wallpaperDim: Int = 35, // percent of black over the wallpaper
    // Layout
    val order: List<String> = emptyList(), // apps first in this order, the rest alphabetically
    val hidden: Set<String> = emptySet(),
    val dockSize: Int = 5, // the first visible apps in `order` sit in the dock
    val columns: Int = 6, // apps per row in the grid
    val showNames: Boolean = true,
    val clock: String = "time_date", // time_date, time, off
    val greeting: String = "",
    // Music bars
    val visualiser: Boolean = true,
    val vizStyle: String = "bars", // bars, mirror, wave
    val vizBars: Int = 32,
    val vizHeight: Int = 12, // percent of the screen height
    val vizOpacity: Int = 85,
    val vizColour: String = "accent", // accent, rainbow, white
    val vizPeaks: Boolean = true,
    val vizSensitivity: Int = 100, // percent
) {
    fun toJson(): JSONObject = JSONObject()
        .put("accent", hex(accent))
        .put("focus", focus)
        .put("background", hex(background))
        .put("wallpaper_dim", wallpaperDim)
        .put("order", JSONArray(order))
        .put("hidden", JSONArray(hidden.sorted()))
        .put("dock_size", dockSize)
        .put("columns", columns)
        .put("show_names", showNames)
        .put("clock", clock)
        .put("greeting", greeting)
        .put("visualiser", visualiser)
        .put("viz_style", vizStyle)
        .put("viz_bars", vizBars)
        .put("viz_height", vizHeight)
        .put("viz_opacity", vizOpacity)
        .put("viz_colour", vizColour)
        .put("viz_peaks", vizPeaks)
        .put("viz_sensitivity", vizSensitivity)

    companion object {
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
        private val PKG = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)

        /** Unknown keys are ignored and bad values keep what was there, so an old or new desktop app can't break Home. */
        fun fromJson(o: JSONObject, base: HomeConfig = HomeConfig()): HomeConfig {
            fun colour(key: String, cur: Long) =
                o.optString(key).takeIf { HEX.matches(it) }?.let { 0xFF000000 or it.substring(1).toLong(16) } ?: cur
            fun int(key: String, cur: Int, lo: Int, hi: Int) =
                if (o.has(key)) o.optInt(key, cur).coerceIn(lo, hi) else cur
            fun bool(key: String, cur: Boolean) = if (o.has(key)) o.optBoolean(key, cur) else cur
            fun choice(key: String, cur: String, allowed: Set<String>) = o.optString(key).takeIf { it in allowed } ?: cur
            fun pkgs(key: String): List<String>? = o.optJSONArray(key)?.let { a ->
                (0 until minOf(a.length(), 500)).map { a.optString(it) }.filter { PKG.matches(it) }.distinct()
            }
            return base.copy(
                accent = colour("accent", base.accent),
                focus = choice("focus", base.focus, setOf("glow", "outline", "none")),
                background = colour("background", base.background),
                wallpaperDim = int("wallpaper_dim", base.wallpaperDim, 0, 80),
                order = pkgs("order") ?: base.order,
                hidden = pkgs("hidden")?.toSet() ?: base.hidden,
                dockSize = int("dock_size", base.dockSize, 0, 8),
                columns = int("columns", base.columns, 3, 8),
                showNames = bool("show_names", base.showNames),
                clock = choice("clock", base.clock, setOf("time_date", "time", "off")),
                greeting = if (o.has("greeting")) o.optString("greeting").take(40) else base.greeting,
                visualiser = bool("visualiser", base.visualiser),
                vizStyle = choice("viz_style", base.vizStyle, setOf("bars", "mirror", "wave")),
                vizBars = int("viz_bars", base.vizBars, 8, 64),
                vizHeight = int("viz_height", base.vizHeight, 4, 40),
                vizOpacity = int("viz_opacity", base.vizOpacity, 20, 100),
                vizColour = choice("viz_colour", base.vizColour, setOf("accent", "rainbow", "white")),
                vizPeaks = bool("viz_peaks", base.vizPeaks),
                vizSensitivity = int("viz_sensitivity", base.vizSensitivity, 25, 300),
            )
        }
    }
}

/**
 * Settings and the wallpaper live in the app's private storage, which is there as soon as Home starts.
 * That is the fix for upstream's reboot reset (#21) and the folder Android won't let ADB create (#20).
 */
object ConfigStore {
    private val _config = MutableStateFlow(HomeConfig())
    val config: StateFlow<HomeConfig> = _config
    /** Bumped when a new wallpaper lands, so Home reloads it. */
    private val _wallpaperVersion = MutableStateFlow(0)
    val wallpaperVersion: StateFlow<Int> = _wallpaperVersion
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.filesDir, "config.json")
    fun wallpaper(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")
    fun wallpaperUpload(ctx: Context) = File(ctx.filesDir, "wallpaper.upload")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (f.exists()) runCatching { _config.value = HomeConfig.fromJson(JSONObject(f.readText())) }
    }

    /** Merges a partial update, saves it atomically, and the UI follows through the flow. */
    @Synchronized
    fun update(ctx: Context, json: JSONObject): HomeConfig {
        load(ctx)
        val next = HomeConfig.fromJson(json, _config.value)
        val tmp = File(ctx.filesDir, "config.json.tmp")
        tmp.writeText(next.toJson().toString(2))
        tmp.renameTo(file(ctx))
        _config.value = next
        return next
    }

    fun wallpaperChanged() { _wallpaperVersion.value++ }
}
