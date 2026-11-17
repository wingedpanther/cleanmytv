package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A remote button and what it does: an action id, or "app" with a package. */
data class ButtonMap(val code: Int, val action: String, val app: String? = null)

/** A named group of apps: a row on the home screen, or a folder. */
data class Group(val name: String, val apps: List<String>)

/** Everything CleanMyTV can change. The desktop app sends any subset; the rest keeps its value. */
data class HomeConfig(
    // Arrangement
    val layout: String = "shelf", // shelf, split, rail
    val sections: List<String> = DEFAULT_SECTIONS, // top to bottom; absent = hidden
    val topSpace: Int = 25, // percent of the screen left open above the apps (shelf)
    val quick: List<String> = listOf("inputs", "wifi", "settings"), // quick-settings icons and "app:<package>" shortcuts, in order
    val rows: List<Group> = emptyList(),
    val folders: List<Group> = emptyList(),
    val dockScale: Int = 100, // percent
    val tileScale: Int = 100, // percent
    // Remote buttons (KeyService)
    val buttonsOn: Boolean = false,
    val buttons: List<ButtonMap> = emptyList(),
    // A launcher that stays on and keeps taking Home: Amazon's on Fire TV, or Google TV's when apps need it
    // (#99). KeyService sends its home screen back to this one. Empty when Home is really ours.
    val stockRedirect: String = "",
    // Back to the input that was on when the TV went to sleep (#94)
    val powerOnInput: Boolean = false,
    // What power on opens: "" for the input it slept on, else one input's TvInputManager id, every time
    val powerOnTarget: String = "",
    // Seconds to wait before opening it, on top of PowerOn.SETTLE_MS, for TVs slow to put their own screen up
    val powerOnWait: Int = 0,
    // Look: CleanMyTV's dark green-and-earth world by default
    val accent: Long = 0xFF4CAF50,
    val focus: String = "lift", // lift, glow, outline, none
    val background: Long = 0xFF1C2A1F,
    val wallpaperDim: Int = 35, // percent of black over the wallpaper
    val snow: Boolean = false, // snow falling over the wallpaper, behind the apps; the Christmas theme turns it on
    // Shading over the wallpaper, each a strength and how far it reaches, in percent of the screen's height
    val shadeTop: Int = 60, // behind the top bar (Shelf layout)
    val shadeTopReach: Int = 40,
    val shadeBottom: Int = 80, // behind the app rows
    val shadeBottomReach: Int = 65,
    val wash: Int = 50, // the focused app's colour, with followColour
    val washReach: Int = 100,
    // Layout
    val order: List<String> = emptyList(), // apps first in this order, the rest alphabetically
    val hidden: Set<String> = emptySet(),
    val dockSize: Int = 5, // the first visible apps in `order` sit in the dock
    val columns: Int = 6, // apps per row in the grid
    val showNames: Boolean = true,
    // Feel
    val nameFocus: Boolean = false, // with showNames: only the focused app's name shows
    val followColour: Boolean = false, // the background takes the focused app's colour
    val pinFocus: Boolean = false, // the focused tile stays put and the row slides under it
    val tilt: Boolean = false, // with lift: the tile leans toward the move, then settles
    // Icons: "banner" is the app's own TV banner; the rest redraw every app's logo the same way
    val iconStyle: String = "banner", // banner, colour, mono, duo
    val tileShape: String = "wide", // wide, square, squircle, round; banner tiles are always wide
    val tileGlass: Int = 10, // Mono: percent of white in the glass behind each logo
    val intro: Boolean = true, // a new wallpaper comes in like a sunrise
    /** Which built-in theme picture the wallpaper is, as "<theme id>:<picture>", or "" for anything else. */
    val wallpaperFrom: String = "",
    // App lock: the PIN itself lives in Lock, never in these settings
    val lockOn: Boolean = false,
    val lockApps: List<String> = emptyList(),
    val lockSystem: Boolean = false, // Settings, the Play Store, the uninstaller and Quick settings too; off until ticked
    val lockFor: String = "sleep", // sleep, 15, always: how long an unlock lasts
    val lockShow: String = "badge", // badge, hide: locked apps on Home
    val lockEditor: Boolean = false, // the on-TV editor asks for the PIN too
    // Fireplace: a looping video CleanMyTV puts on the TV, as the screensaver, full screen, or the wallpaper
    val fireWall: Boolean = false, // behind Home instead of the picture
    val fireSound: Boolean = true, // full screen and as the screensaver
    val fireWallSound: Boolean = false, // behind Home: quiet unless asked
    val fireVolume: Int = 40,
    val otherApps: Boolean = false, // phone apps with no TV banner go in an "Other apps" folder (#3)
    val liveTv: Boolean = true, // the TV's tuner in the Inputs menu (#50)
    val fireTimer: Int = 0, // full screen: minutes until the fire goes out and the TV sleeps; 0 burns on
    // Slideshow: a new wallpaper every few minutes, from the owner's pictures or the current theme's; 0 is off
    val slideMinutes: Int = 0,
    val slideFrom: String = "mine", // mine, theme
    val clock: String = "time_date", // time_date, time, off
    val greeting: String = "",
    // The owner's own words for the top bar buttons and the All apps title, by id (#67)
    val names: Map<String, String> = emptyMap(),
    // Idle screen: after this many minutes without a button press, only the wallpaper and the clock show; 0 is off
    val idleMinutes: Int = 0,
    val idleStyle: String = "corner", // corner, center, photos: what the idle screen shows
    val font: String = "default", // default, condensed, serif, casual: Home's text (the clock keeps its own)
    val bingDaily: Boolean = false, // Bing's picture of the day becomes the wallpaper each day
    val themeMemory: Boolean = true, // each theme keeps the look changes made while it's on
    val updateCheck: Boolean = true, // look for a newer Home on the release page once a day
    val keyboardGuard: Boolean = true, // put the owner's keyboard back when the TV switches it at a restart or wake (#98)
    val limits: Map<String, Int> = emptyMap(), // package -> minutes a day, then the app needs the PIN
    // Weather next to the clock, from Open-Meteo; off while the place is blank
    val weatherPlace: String = "",
    val weatherLat: Double = 0.0,
    val weatherLon: Double = 0.0,
    val weatherUnit: String = "c", // c, f
    val memory: Boolean = false, // free memory under the clock
    // Day and night: the looks the owner saved as "day" and "night" take turns at these minutes past midnight
    val dayNight: Boolean = false,
    val dayAt: Int = 7 * 60,
    val nightAt: Int = 19 * 60,
    // Music bars
    val visualiser: Boolean = true,
    val vizStyle: String = "bars", // bars, mirror, wave
    val vizBars: Int = 32,
    val vizHeight: Int = 12, // percent of the screen height
    val vizOpacity: Int = 85,
    val vizColour: String = "accent", // accent, rainbow, white
    val vizPeaks: Boolean = true,
    val vizSensitivity: Int = 100, // percent
    val editOnTv: Boolean = false, // the on-TV editor (experimental), off until the owner turns it on in the desktop app
) {
    fun toJson(): JSONObject = JSONObject()
        .put("layout", layout)
        .put("sections", JSONArray(sections))
        .put("sections_known", JSONArray(SECTIONS.toList()))
        .put("top_space", topSpace)
        .put("quick", JSONArray(quick))
        .put("rows", groupsJson(rows))
        .put("folders", groupsJson(folders))
        .put("dock_scale", dockScale)
        .put("tile_scale", tileScale)
        .put("buttons_on", buttonsOn)
        .put("buttons", JSONArray(buttons.map { b -> JSONObject().put("code", b.code).put("action", b.action).apply { b.app?.let { put("app", it) } } }))
        .put("stock_redirect", stockRedirect)
        .put("power_on_input", powerOnInput)
        .put("power_on_target", powerOnTarget)
        .put("power_on_wait", powerOnWait)
        .put("accent", hex(accent))
        .put("focus", focus)
        .put("background", hex(background))
        .put("wallpaper_dim", wallpaperDim)
        .put("snow", snow)
        .put("shade_top", shadeTop)
        .put("shade_top_reach", shadeTopReach)
        .put("shade_bottom", shadeBottom)
        .put("shade_bottom_reach", shadeBottomReach)
        .put("wash", wash)
        .put("wash_reach", washReach)
        .put("order", JSONArray(order))
        .put("hidden", JSONArray(hidden.sorted()))
        .put("dock_size", dockSize)
        .put("columns", columns)
        .put("show_names", showNames)
        .put("name_focus", nameFocus)
        .put("follow_colour", followColour)
        .put("pin_focus", pinFocus)
        .put("tilt", tilt)
        .put("icon_style", iconStyle)
        .put("tile_shape", tileShape)
        .put("tile_glass", tileGlass)
        .put("intro", intro)
        .put("wallpaper_from", wallpaperFrom)
        .put("lock_on", lockOn)
        .put("lock_apps", JSONArray(lockApps))
        .put("lock_system", lockSystem)
        .put("lock_for", lockFor)
        .put("lock_show", lockShow)
        .put("lock_editor", lockEditor)
        .put("fire_wall", fireWall)
        .put("fire_sound", fireSound)
        .put("fire_wall_sound", fireWallSound)
        .put("fire_volume", fireVolume)
        .put("other_apps", otherApps)
        .put("live_tv", liveTv)
        .put("fire_timer", fireTimer)
        .put("slide_minutes", slideMinutes)
        .put("slide_from", slideFrom)
        .put("clock", clock)
        .put("greeting", greeting)
        .put("names", JSONObject(names as Map<*, *>))
        .put("idle_minutes", idleMinutes)
        .put("idle_style", idleStyle)
        .put("font", font)
        .put("bing_daily", bingDaily)
        .put("theme_memory", themeMemory)
        .put("update_check", updateCheck)
        .put("keyboard_guard", keyboardGuard)
        .put("limits", JSONObject(limits as Map<*, *>))
        .put("weather_place", weatherPlace)
        .put("weather_lat", weatherLat)
        .put("weather_lon", weatherLon)
        .put("weather_unit", weatherUnit)
        .put("memory", memory)
        .put("day_night", dayNight)
        .put("day_at", dayAt)
        .put("night_at", nightAt)
        .put("visualiser", visualiser)
        .put("viz_style", vizStyle)
        .put("viz_bars", vizBars)
        .put("viz_height", vizHeight)
        .put("viz_opacity", vizOpacity)
        .put("viz_colour", vizColour)
        .put("viz_peaks", vizPeaks)
        .put("viz_sensitivity", vizSensitivity)
        .put("edit_on_tv", editOnTv)

    companion object {
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
        val FONTS = setOf("default", "condensed", "serif", "casual")
        private val PKG = Regex("^[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        private fun hex(c: Long) = "#%06X".format(c and 0xFFFFFF)
        val QUICK = setOf("settings", "wifi", "inputs", "bluetooth", "display", "apps", "sleep", "search", "music", "panel", "themes", "fireplace", "memory", "customize", "timer")
        private val THEME_PICTURE = Regex("^[a-z0-9-]{1,40}:[0-9]$")
        /** A TvInputManager input id, like com.mediatek.tvinput/.hdmi.HDMIInputService/HW5. */
        private val INPUT_ID = Regex("^[A-Za-z0-9_.\\$/:-]{1,200}$")
        val POWER_ON_WAITS = listOf(0, 3, 5, 10)
        val SECTIONS = setOf("nowplaying", "recent", "dock", "watchnext", "rows", "folders", "grid")
        val DEFAULT_SECTIONS = listOf("nowplaying", "dock", "watchnext", "rows", "folders", "grid")
        /** The sections Home 1.12 and older knew, for settings saved before "sections_known" was. */
        private val SECTIONS_BEFORE = setOf("nowplaying", "recent", "dock", "rows", "folders", "grid")

        /**
         * Settings saved by an older Home: a section that's on for a new TV and that Home didn't know then goes
         * in once, after the one it follows by default, or first. Turned off after that, it stays off.
         */
        fun addNewSections(sections: List<String>, known: Set<String>?): List<String> {
            val out = sections.toMutableList()
            for ((i, s) in DEFAULT_SECTIONS.withIndex()) {
                if (s in (known ?: SECTIONS_BEFORE) || s in out) continue
                val after = DEFAULT_SECTIONS.take(i).lastOrNull { it in out }
                out.add(if (after == null) 0 else out.indexOf(after) + 1, s)
            }
            return out
        }
        val BUTTON_ACTIONS = QUICK - "inputs" + setOf("inputs", "home", "back", "none", "app", "next_wallpaper", "brightness")
        /** App shortcuts in the top bar, after the quick-settings icons that fit. */
        const val MAX_QUICK_APPS = 6
        fun quickApp(entry: String): String? = entry.removePrefix("app:").takeIf { entry.startsWith("app:") && PKG.matches(it) }

        private fun groupsJson(gs: List<Group>) = JSONArray(gs.map { JSONObject().put("name", it.name).put("apps", JSONArray(it.apps)) })

        /** Unknown keys are ignored and bad values keep what was there, so an old or new desktop app can't break Home. */
        fun fromJson(o: JSONObject, base: HomeConfig = HomeConfig()): HomeConfig {
            fun colour(key: String, cur: Long) =
                o.optString(key).takeIf { HEX.matches(it) }?.let { 0xFF000000 or it.substring(1).toLong(16) } ?: cur
            fun int(key: String, cur: Int, lo: Int, hi: Int) =
                if (o.has(key)) o.optInt(key, cur).coerceIn(lo, hi) else cur
            fun real(key: String, cur: Double, lim: Double) =
                if (o.has(key)) o.optDouble(key, cur).takeIf { it.isFinite() }?.coerceIn(-lim, lim) ?: cur else cur
            fun bool(key: String, cur: Boolean) = if (o.has(key)) o.optBoolean(key, cur) else cur
            fun choice(key: String, cur: String, allowed: Set<String>) = o.optString(key).takeIf { it in allowed } ?: cur
            fun pkgs(key: String): List<String>? = o.optJSONArray(key)?.let { a ->
                (0 until minOf(a.length(), 500)).map { a.optString(it) }.filter { PKG.matches(it) }.distinct()
            }
            fun strings(key: String, allowed: Set<String>): List<String>? = o.optJSONArray(key)?.let { a ->
                (0 until a.length()).map { a.optString(it) }.filter { it in allowed }.distinct()
            }
            fun groups(key: String, max: Int): List<Group>? = o.optJSONArray(key)?.let { a ->
                (0 until minOf(a.length(), max)).mapNotNull { i ->
                    val g = a.optJSONObject(i) ?: return@mapNotNull null
                    val name = g.optString("name").replace(Regex("[\\p{Cntrl}]"), "").trim().take(30)
                    val apps = g.optJSONArray("apps")?.let { x -> (0 until minOf(x.length(), 60)).map { x.optString(it) }.filter { PKG.matches(it) }.distinct() } ?: emptyList()
                    if (name.isEmpty()) null else Group(name, apps)
                }
            }
            fun buttons(): List<ButtonMap>? = o.optJSONArray("buttons")?.let { a ->
                (0 until minOf(a.length(), 30)).mapNotNull { i ->
                    val b = a.optJSONObject(i) ?: return@mapNotNull null
                    val code = b.optInt("code", -1)
                    val action = b.optString("action")
                    val app = b.optString("app").takeIf { PKG.matches(it) }
                    if (code !in 1..65535 || action !in BUTTON_ACTIONS || (action == "app" && app == null)) null
                    else ButtonMap(code, action, if (action == "app") app else null)
                }.distinctBy { it.code }
            }
            return base.copy(
                buttonsOn = bool("buttons_on", base.buttonsOn),
                buttons = buttons() ?: base.buttons,
                stockRedirect = if (o.has("stock_redirect")) o.optString("stock_redirect").takeIf { it.isEmpty() || PKG.matches(it) } ?: base.stockRedirect else base.stockRedirect,
                powerOnInput = bool("power_on_input", base.powerOnInput),
                powerOnTarget = if (o.has("power_on_target")) o.optString("power_on_target").takeIf { it.isEmpty() || INPUT_ID.matches(it) } ?: base.powerOnTarget else base.powerOnTarget,
                powerOnWait = if (o.has("power_on_wait")) o.optInt("power_on_wait", -1).takeIf { it in POWER_ON_WAITS } ?: base.powerOnWait else base.powerOnWait,
                layout = choice("layout", base.layout, setOf("shelf", "split", "rail")),
                sections = strings("sections", SECTIONS) ?: base.sections,
                topSpace = int("top_space", base.topSpace, 0, 40),
                quick = o.optJSONArray("quick")?.let { a ->
                    val all = (0 until minOf(a.length(), 30)).map { a.optString(it) }.filter { it in QUICK || quickApp(it) != null }.distinct()
                    val apps = all.filter { quickApp(it) != null }.take(MAX_QUICK_APPS).toSet()
                    all.filter { it in QUICK || it in apps }
                } ?: base.quick,
                rows = groups("rows", 8) ?: base.rows,
                folders = groups("folders", 12) ?: base.folders,
                dockScale = int("dock_scale", base.dockScale, 60, 150),
                tileScale = int("tile_scale", base.tileScale, 60, 150),
                accent = colour("accent", base.accent),
                focus = choice("focus", base.focus, setOf("lift", "glow", "outline", "none")),
                background = colour("background", base.background),
                wallpaperDim = int("wallpaper_dim", base.wallpaperDim, 0, 80),
                snow = bool("snow", base.snow),
                shadeTop = int("shade_top", base.shadeTop, 0, 100),
                shadeTopReach = int("shade_top_reach", base.shadeTopReach, 0, 100),
                shadeBottom = int("shade_bottom", base.shadeBottom, 0, 100),
                shadeBottomReach = int("shade_bottom_reach", base.shadeBottomReach, 0, 100),
                wash = int("wash", base.wash, 0, 100),
                washReach = int("wash_reach", base.washReach, 10, 100),
                order = pkgs("order") ?: base.order,
                hidden = pkgs("hidden")?.toSet() ?: base.hidden,
                dockSize = int("dock_size", base.dockSize, 0, 8),
                columns = int("columns", base.columns, 3, 8),
                showNames = bool("show_names", base.showNames),
                nameFocus = bool("name_focus", base.nameFocus),
                followColour = bool("follow_colour", base.followColour),
                pinFocus = bool("pin_focus", base.pinFocus),
                tilt = bool("tilt", base.tilt),
                iconStyle = choice("icon_style", base.iconStyle, setOf("banner", "colour", "mono", "duo")),
                tileShape = choice("tile_shape", base.tileShape, setOf("wide", "square", "squircle", "round")),
                tileGlass = int("tile_glass", base.tileGlass, 0, 60),
                intro = bool("intro", base.intro),
                wallpaperFrom = if (o.has("wallpaper_from")) o.optString("wallpaper_from").takeIf { it.isEmpty() || THEME_PICTURE.matches(it) } ?: base.wallpaperFrom else base.wallpaperFrom,
                lockOn = bool("lock_on", base.lockOn),
                lockApps = pkgs("lock_apps") ?: base.lockApps,
                lockSystem = bool("lock_system", base.lockSystem),
                lockFor = choice("lock_for", base.lockFor, setOf("sleep", "15", "always")),
                lockShow = choice("lock_show", base.lockShow, setOf("badge", "hide")),
                lockEditor = bool("lock_editor", base.lockEditor),
                fireWall = bool("fire_wall", base.fireWall),
                fireSound = bool("fire_sound", base.fireSound),
                fireWallSound = bool("fire_wall_sound", base.fireWallSound),
                fireVolume = int("fire_volume", base.fireVolume, 0, 100),
                otherApps = bool("other_apps", base.otherApps),
                liveTv = bool("live_tv", base.liveTv),
                fireTimer = int("fire_timer", base.fireTimer, 0, 240),
                slideMinutes = int("slide_minutes", base.slideMinutes, 0, 240),
                slideFrom = choice("slide_from", base.slideFrom, setOf("mine", "theme")),
                clock = choice("clock", base.clock, setOf("time_date", "time", "off")),
                idleMinutes = int("idle_minutes", base.idleMinutes, 0, 60),
                idleStyle = choice("idle_style", base.idleStyle, setOf("corner", "center", "photos")),
                font = choice("font", base.font, FONTS),
                bingDaily = bool("bing_daily", base.bingDaily),
                themeMemory = bool("theme_memory", base.themeMemory),
                updateCheck = bool("update_check", base.updateCheck),
                keyboardGuard = bool("keyboard_guard", base.keyboardGuard),
                limits = o.optJSONObject("limits")?.let { m ->
                    m.keys().asSequence().take(60).mapNotNull { k -> m.optInt(k, -1).takeIf { PKG.matches(k) && it in 5..720 }?.let { k to it } }.toMap()
                } ?: base.limits,
                greeting = if (o.has("greeting")) o.optString("greeting").take(40) else base.greeting,
                names = o.optJSONObject("names")?.let { n ->
                    n.keys().asSequence().mapNotNull { k -> n.optString(k).trim().take(24).takeIf { it.isNotEmpty() }?.let { k to it } }.toMap()
                } ?: base.names,
                weatherPlace = if (o.has("weather_place")) o.optString("weather_place").replace(Regex("[\\p{Cntrl}]"), "").trim().take(40) else base.weatherPlace,
                weatherLat = real("weather_lat", base.weatherLat, 90.0),
                weatherLon = real("weather_lon", base.weatherLon, 180.0),
                weatherUnit = choice("weather_unit", base.weatherUnit, setOf("c", "f")),
                memory = bool("memory", base.memory),
                dayNight = bool("day_night", base.dayNight),
                dayAt = int("day_at", base.dayAt, 0, 24 * 60 - 1),
                nightAt = int("night_at", base.nightAt, 0, 24 * 60 - 1),
                visualiser = bool("visualiser", base.visualiser),
                vizStyle = choice("viz_style", base.vizStyle, setOf("bars", "mirror", "wave")),
                vizBars = int("viz_bars", base.vizBars, 8, 64),
                vizHeight = int("viz_height", base.vizHeight, 4, 40),
                vizOpacity = int("viz_opacity", base.vizOpacity, 20, 100),
                vizColour = choice("viz_colour", base.vizColour, setOf("accent", "rainbow", "white")),
                vizPeaks = bool("viz_peaks", base.vizPeaks),
                vizSensitivity = int("viz_sensitivity", base.vizSensitivity, 25, 300),
                editOnTv = bool("edit_on_tv", base.editOnTv),
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
    /** False on a fresh install, before anything was set: CleanMyTV then puts its saved copy back. */
    fun configured(ctx: Context) = file(ctx).exists()
    fun wallpaper(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")
    fun wallpaperUpload(ctx: Context) = File(ctx.filesDir, "wallpaper.upload")

    @Synchronized
    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        runCatching {
            val saved = JSONObject(f.readText())
            _config.value = HomeConfig.fromJson(saved)
            val known = saved.optJSONArray("sections_known")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
            val sections = HomeConfig.addNewSections(_config.value.sections, known)
            if (sections != _config.value.sections) update(ctx, JSONObject().put("sections", JSONArray(sections)), remember = false)
        }
    }

    /** Merges a partial update, saves it atomically, and the UI follows through the flow. */
    @Synchronized
    /**
     * [remember]: a look change the owner made, which the theme on screen keeps (ThemePack.remember). Changes
     * from the desktop app and from putting a theme or a day/night look on pass false.
     */
    fun update(ctx: Context, json: JSONObject, remember: Boolean = true): HomeConfig {
        load(ctx)
        val next = HomeConfig.fromJson(json, _config.value)
        val text = next.toJson().toString(2)
        val tmp = File(ctx.filesDir, "config.json.tmp")
        // On adopted storage a temp file left by an earlier install can refuse to be written (#59): then the
        // settings go straight into config.json, without the swap.
        val swapped = runCatching { tmp.writeText(text); tmp.renameTo(file(ctx)) }.getOrDefault(false)
        if (!swapped) { tmp.delete(); file(ctx).writeText(text) }
        _config.value = next
        if (remember && json.keys().asSequence().any { it in ThemePack.MEMORY_KEYS }) runCatching { ThemePack.remember(ctx) }
        return next
    }

    fun wallpaperChanged() { _wallpaperVersion.value++ }
}
