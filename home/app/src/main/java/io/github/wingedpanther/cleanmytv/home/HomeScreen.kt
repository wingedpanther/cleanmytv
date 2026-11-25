package io.github.wingedpanther.cleanmytv.home

import androidx.compose.material.icons.outlined.SettingsEthernet
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.State
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// CleanMyTV's dark world: warm off-white and sand text on the green-black ground.
private val TEXT = Color(0xFFF0EBE5)
private val MUTED = Color(0xFFD7CFC4)
private val TILE: Color get() = HomeColours.panel.copy(alpha = 0.9f)
private val TILE_FOCUSED: Color get() = HomeColours.raised

/** A focused row in Home's menus: the accent, mixed into the tile so the light text still reads (#112). */
private fun menuFocus(ui: Ui): Color = lerp(TILE, ui.accent, 0.45f)
private val GROUND = Color(0xFF1C2A1F)
private val PANEL: Color get() = HomeColours.panel

// The desktop app's face, for the clock and date: tabular figures that don't jitter as the minutes turn.
private val CLOCK = FontFamily(Font(R.font.jetbrains_mono_light, FontWeight.Light), Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

/** Text on an accent fill: the ground colour or off-white, whichever reads better on it. */
private fun onAccent(c: Color): Color = if (c.luminance() > 0.36f) GROUND else TEXT

/**
 * A shade in the background colour, so overlays keep the theme's own dark instead of plain black. Before
 * 1.7.5 it was always the default green-black, which tinted a black background green (#102). A light
 * background is darkened first, so text over the shade still reads.
 */
private fun scrim(cfg: HomeConfig, alpha: Float): Color {
    val g = Color(cfg.background)
    return (if (g.luminance() > 0.2f) lerp(g, Color.Black, 0.75f) else g).copy(alpha = alpha)
}

// A 1080p TV is 960x540 dp; 5% of each edge stays clear because some TVs crop it.
private val SAFE_X = 48.dp
private val SAFE_Y = 27.dp
private val GAP = 16.dp
// Room on each side of a row so a focused tile's glow, shadow and zoom aren't clipped.
private val BLEED = 22.dp
// The strip at the bottom kept for the Bing caption: the safe edge, its one line, and a gap above it.
private val CAPTION_STRIP = SAFE_Y + 24.dp

/** The sunrise after a new wallpaper, in its own scope so its frames don't recompose the screen. */
@Composable
private fun IntroLayer(intro: Animatable<Float, *>) {
    if (intro.value < 1f) Sunrise(intro.value)
}

/** Everything the layouts draw, worked out once from the apps on the TV and the settings. */
internal class Home(all: List<TvApp>, val cfg: HomeConfig, recentPkgs: List<String> = emptyList(), watching: List<WatchItem> = emptyList(), usual: List<String> = emptyList(), guest: Boolean = false, watchRemoved: Map<String, Long> = emptyMap()) {
    // "Hide locked apps": they stay out of every section, and only open from elsewhere with the PIN.
    // Guest mode: only the guest apps, wherever the owner put them.
    private val apps = (if (cfg.lockOn && cfg.lockShow == "hide") all.filter { it.pkg !in cfg.lockApps } else all)
        .let { list -> if (guest) list.filter { it.pkg in cfg.guestApps } else list }
    private val byPkg = apps.associateBy { it.pkg }
    val shown = arrange(apps, cfg)
    private val inFolders = cfg.folders.flatMap { it.apps }.toSet()
    /** Phone apps with no TV banner, in their own folder when the owner asks (#3). Apps they placed stay put. */
    private val other = if (cfg.otherApps) shown.filter { !it.banner && it.pkg !in inFolders } else emptyList()
    /** New apps the owner sends to the dock: a day there, ahead of the usual ones (NewApps). */
    private val newcomers = NewApps.forDock(cfg, shown.associate { it.pkg to it.installed }, System.currentTimeMillis()).mapNotNull { byPkg[it] }
    val dock = newcomers + shown.filter { it.pkg !in inFolders && it !in other && it !in newcomers }.take(cfg.dockSize)
    val grid = shown.filter { it.pkg !in inFolders && it !in dock && it !in other }
    val rows = cfg.rows.map { g -> g.name to g.apps.mapNotNull { byPkg[it] }.filter { it.pkg !in cfg.hidden } }.filter { it.second.isNotEmpty() }
    val folders = (cfg.folders.map { g -> g.name to g.apps.mapNotNull { byPkg[it] } } + listOf(tr("Other apps") to other)).filter { it.second.isNotEmpty() }
    val recent = recentPkgs.mapNotNull { byPkg[it] }.filter { it.pkg !in cfg.hidden }
    val watch = WatchNext.pick(watching, byPkg.keys, cfg, removed = watchRemoved)
    fun app(pkg: String): TvApp? = byPkg[pkg]
    /** The section the focus starts in, its key and its apps. */
    private val start: Pair<String, List<TvApp>>? = cfg.sections.firstNotNullOfOrNull { s ->
        when (s) {
            "recent" -> recent.takeIf { it.isNotEmpty() }?.let { "recent" to it }
            "dock" -> dock.takeIf { it.isNotEmpty() }?.let { "dock" to it }
            "rows" -> rows.firstOrNull()?.second?.let { "row0" to it }
            "grid" -> grid.takeIf { it.isNotEmpty() }?.let { "grid" to it.take(cfg.columns) }
            else -> null
        }
    }
    /**
     * The tile that takes focus when Home opens, as "<section>:<package>" (an app can sit in two sections): the
     * first one, or the most [usual] app among the first few of that same row, so the screen doesn't scroll.
     */
    val first: String? = start?.let { (key, list) ->
        val near = list.take(USUAL_NEAR).associateBy { it.pkg }
        "$key:${(usual.firstNotNullOfOrNull { near[it] } ?: list.first()).pkg}"
    }
}

/** How far along the starting row the usual app can be and still take the focus: always on screen. */
private const val USUAL_NEAR = 4

@Composable
fun HomeScreen(visualiser: Visualiser, appsVersion: Int, open: androidx.compose.runtime.MutableState<String?>) {
    val ctx = LocalContext.current
    val cfg by ConfigStore.config.collectAsState()
    val wallVersion by ConfigStore.wallpaperVersion.collectAsState()
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val glyphs = cfg.iconStyle != "banner"
    LaunchedEffect(appsVersion, glyphs, cfg.liveTv, cfg.inputTiles) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx, glyphs).also { NewApps.check(ctx, it) } }
        inputs = withContext(Dispatchers.Default) { tvInputs(ctx, cfg.liveTv) }
    }
    // Animations off in the TV's settings (or from CleanMyTV): Home stops moving too.
    var motion by remember { mutableStateOf(true) }
    LaunchedEffect(appsVersion) {
        motion = runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f
    }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    val intro = remember { Animatable(1f) }
    var wallSeen by remember { mutableStateOf<Int?>(null) }
    var caption by remember { mutableStateOf<Wallpapers.Caption?>(null) }
    LaunchedEffect(wallVersion) {
        // The picture and its caption land together, so a new picture never shows the last one's caption.
        val (pic, line) = withContext(Dispatchers.IO) {
            val f = ConfigStore.wallpaper(ctx)
            val pic = if (f.exists()) Wallpapers.screenPicture(ctx, f.path) else null
            pic to Wallpapers.caption(ctx)
        }
        wallpaper = pic
        caption = line
        // A new wallpaper, not the one Home opened with, comes in like a sunrise.
        val fresh = wallSeen != null && wallSeen != wallVersion
        wallSeen = wallVersion
        if (fresh && wallpaper != null && cfg.intro && motion) { intro.snapTo(0f); intro.animateTo(1f, tween(1800, easing = FastOutSlowInEasing)) }
    }
    // Slideshow: a new wallpaper every few minutes while Home is open, from the owner's pictures or the theme's.
    LaunchedEffect(cfg.slideMinutes, cfg.slideFrom) {
        if (cfg.slideMinutes <= 0) return@LaunchedEffect
        while (true) {
            delay(cfg.slideMinutes * 60_000L)
            withContext(Dispatchers.IO) { runCatching { if (cfg.slideFrom == "theme") ThemePack.next(ctx) else Slides.next(ctx) } }
        }
    }
    val fireVersion by Fire.version.collectAsState()
    val fireWall = cfg.fireWall && remember(fireVersion) { Fire.ready(ctx) }
    // Idle screen: no button pressed for cfg.idleMinutes, and nothing open over Home. Any button wakes it,
    // and that press only wakes it, so it can't open the app that happens to have focus.
    var idle by remember { mutableStateOf(false) }
    var lastPress by remember { mutableLongStateOf(0L) }
    var swallowUp by remember { mutableStateOf(false) }
    LaunchedEffect(appsVersion) { idle = false; lastPress = System.currentTimeMillis() } // Home came back into view

    LaunchedEffect(cfg.vizBars, cfg.vizSensitivity, cfg.visualiser, idle) {
        visualiser.bars = cfg.vizBars
        visualiser.sensitivity = cfg.vizSensitivity / 100f
        if (cfg.visualiser && !idle) visualiser.start(ctx) else visualiser.stop()
    }

    val track by NowPlaying.track.collectAsState()
    var weather by remember { mutableStateOf<WeatherNow?>(null) }
    LaunchedEffect(cfg.weatherPlace, cfg.weatherLat, cfg.weatherLon, cfg.weatherUnit) {
        weather = null
        if (cfg.weatherPlace.isBlank()) return@LaunchedEffect
        // A failed refresh keeps the last reading; the next try is in 30 minutes, or 2 if there's none yet.
        while (true) {
            Weather.fetch(cfg)?.let { weather = it }
            delay(if (weather == null) 2 * 60_000L else 30 * 60_000L)
        }
    }

    // Free memory under the clock, read every 10 seconds; Clean memory reads it again straight away.
    var memory by remember { mutableStateOf<MemoryNow?>(null) }
    var memoryTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(cfg.memory, memoryTick, appsVersion) {
        memory = null
        if (!cfg.memory) return@LaunchedEffect
        while (true) {
            memory = withContext(Dispatchers.Default) { runCatching { Memory.read(ctx) }.getOrNull() }
            delay(10_000L)
        }
    }
    // Bing every day: looked at when Home comes back into view, and each hour while it stays.
    LaunchedEffect(cfg.bingDaily, appsVersion) {
        while (cfg.bingDaily) { Wallpapers.daily(ctx); delay(60 * 60_000L) }
    }
    // Day and night looks: checked each minute while Home is open, and when it comes back into view.
    LaunchedEffect(cfg.dayNight, cfg.dayAt, cfg.nightAt, appsVersion) {
        if (!cfg.dayNight) return@LaunchedEffect
        while (true) {
            val c = Calendar.getInstance()
            withContext(Dispatchers.IO) { runCatching { DayNight.check(ctx, c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) } }
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val scope = rememberCoroutineScope()

    // Read again each time Home comes back, which is when the list can have changed.
    var recentPkgs by remember { mutableStateOf<List<String>>(emptyList()) }
    val wantRecent = "recent" in cfg.sections
    LaunchedEffect(appsVersion, wantRecent, apps) {
        if (wantRecent) recentPkgs = withContext(Dispatchers.Default) { Recent.apps(ctx, apps.map { it.pkg }.toSet()) }
    }
    // Screen time: read when Home comes back, and every few minutes while it stays.
    val wantScreen = "screentime" in cfg.sections
    var screenTime by remember { mutableStateOf<ScreenSummary?>(null) }
    var screenSeen by remember { mutableStateOf(false) }
    LaunchedEffect(appsVersion, wantScreen) {
        while (wantScreen) {
            screenTime = withContext(Dispatchers.IO) { ScreenTimeApps.summary(ctx) }
            screenSeen = true
            delay(5 * 60_000L)
        }
    }
    // Continue watching: read when Home comes back, and when an app changes the list while Home is in front.
    // Changes while an app plays are left for the read when Home comes back, so playback isn't disturbed.
    // Glass and Teletext show what there is to continue whether or not the row is on.
    val wantWatch = "watchnext" in cfg.sections || cfg.layout == "glass" || cfg.layout == "teletext"
    var watching by remember { mutableStateOf<List<WatchItem>>(emptyList()) }
    var watchRemoved by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var watchChanged by remember { mutableIntStateOf(0) }
    DisposableEffect(wantWatch) {
        val resumed = { (ctx as? androidx.activity.ComponentActivity)?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) != false }
        val stop = if (wantWatch) WatchNext.watch(ctx) { if (resumed()) watchChanged++ } else ({})
        onDispose { stop() }
    }
    LaunchedEffect(appsVersion, wantWatch, watchChanged) {
        if (!wantWatch) { watching = emptyList(); return@LaunchedEffect }
        // An app saving its place often writes a few times in a row: one read for the lot.
        if (watchChanged > 0) delay(300)
        watchRemoved = withContext(Dispatchers.IO) { WatchNext.removed(ctx) }
        watching = withContext(Dispatchers.IO) { WatchNext.read(ctx) }
    }
    // Worked out again only when the apps or settings change, not on every redraw.
    val guest by Guest.on.collectAsState()
    // The apps usually opened at this hour, most first, looked up each time Home comes back (Usual).
    var usual by remember { mutableStateOf<List<String>>(emptyList()) }
    var usualAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(appsVersion, cfg.focusUsual, apps.isEmpty()) {
        val asked = System.currentTimeMillis()
        usual = if (cfg.focusUsual && apps.isNotEmpty()) withContext(Dispatchers.IO) { Usual.apps(ctx, apps.map { it.pkg }.toSet()) } else emptyList()
        usualAt = asked
    }
    val home = remember(apps, cfg, recentPkgs, wantRecent, watching, usual, guest, watchRemoved) { Home(apps, cfg, if (wantRecent) recentPkgs else emptyList(), watching, usual, guest, watchRemoved) }
    // Glass brings amber unless the owner picked an accent of their own.
    val accent = if (cfg.layout == "glass") glassAccent(cfg) else Color(cfg.accent)
    // Quick-settings icons this TV can actually open; Inputs only on TVs that have inputs.
    val all = remember { quickActions() }
    // App shortcuts use the app's icon; a TV app's banner is too wide for the round button.
    val quickPkgs = cfg.quick.mapNotNull { HomeConfig.quickApp(it) }
    var quickIcons by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(appsVersion, quickPkgs) {
        quickIcons = withContext(Dispatchers.IO) {
            quickPkgs.mapNotNull { p ->
                runCatching { p to ctx.packageManager.getApplicationIcon(p).toBitmap(96, 96).asImageBitmap() }.getOrNull()
            }.toMap()
        }
    }
    val byPkg = (if (guest) apps.filter { it.pkg in cfg.guestApps } else apps).associateBy { it.pkg }
    // A newer Home on the release page: its icon goes first in the bar until it's installed, skipped or put off.
    val update by Updates.available.collectAsState()
    // Switched-off apps a system update turned back on: all of them, and the ones the owner hasn't heard about.
    var back by remember { mutableStateOf<List<String>>(emptyList()) }
    var backNews by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(appsVersion, cfg.switchedOff, cfg.cameBackSeen) {
        back = withContext(Dispatchers.Default) { CameBack.check(ctx, cfg.switchedOff) }
        backNews = CameBack.news(back, cfg.cameBackSeen)
    }
    LaunchedEffect(appsVersion, cfg.updateCheck) {
        val r = Updates.check(ctx) ?: return@LaunchedEffect
        if (Updates.announce(ctx, r)) android.widget.Toast.makeText(ctx, tr("CleanMyTV Home {version} is out. The update icon is first in the top bar.", "version" to r.version), android.widget.Toast.LENGTH_LONG).show()
    }
    // On a cable, the Wi-Fi icon shows a plug and says Ethernet. It opens the same network screen.
    val cable by remember { TvSettings.onCable(ctx) }.collectAsState(initial = false)
    // Debugging left on: a quiet icon after the update one, until it's off or the owner keeps it on.
    var debugging by remember { mutableStateOf(false) }
    LaunchedEffect(appsVersion) { debugging = withContext(Dispatchers.IO) { Debugging.shown(ctx) } }
    val quick = listOfNotNull(all["update"]?.takeIf { update != null }, all["debugging"]?.takeIf { debugging }) + cfg.quick.mapNotNull { id ->
        HomeConfig.quickApp(id)?.let { p -> byPkg[p]?.let { appAction(it, quickIcons[p]) } } ?: all[id]?.let { a ->
            val b = if (id == "wifi" && cable) a.copy(label = "Ethernet", icon = Icons.Outlined.SettingsEthernet) else a
            cfg.names[id]?.let { b.copy(label = it) } ?: b
        }
    }.filter { canOpen(ctx, it, inputs.isNotEmpty()) && (!guest || it.id in Guest.QUICK) }
    var openFolder by remember { mutableStateOf<Pair<String, List<TvApp>>?>(null) }
    // Long press on a tile: its menu, and Move, which hands the arrow keys to the tile until OK or Back.
    var menuFor by remember { mutableStateOf<Pair<String, TvApp>?>(null) }
    var moving by remember { mutableStateOf<Moving?>(null) }
    // The order and rows as they were when the move started: Back puts them back.
    var moveFrom by remember { mutableStateOf<Pair<List<String>, List<Group>>?>(null) }
    // Teletext: the colour word the arrows have reached while a dock app moves.
    var textPick by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(moving == null) { moveFrom = if (moving != null) cfg.order to cfg.rows else null; textPick = null }
    // Teletext's "Add an app": All apps picks an app for the dock instead of opening it.
    var pickForDock by remember { mutableStateOf(false) }
    // After a move, Home leaves focus on the app that moved instead of going back to the first tile.
    var keepFocus by remember { mutableStateOf(false) }
    LaunchedEffect(appsVersion) { moving = null; keepFocus = false }
    var showInputs by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }
    var showThemes by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showDebugging by remember { mutableStateOf(false) }
    var showGuest by remember { mutableStateOf(false) }
    var watchMenu by remember { mutableStateOf<WatchItem?>(null) }
    var showFind by remember { mutableStateOf(false) }
    var showAllApps by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf<TvApp?>(null) }
    val note by TvNote.note.collectAsState()
    // The tip about holding OK, on the first few days (see Hints), for 8 seconds. The timer hangs on the tip
    // itself: Home coming back or an app installing restarts the check, and used to cancel the timer with it.
    var tip by remember { mutableStateOf(false) }
    LaunchedEffect(appsVersion) { if (withContext(Dispatchers.IO) { Hints.take(ctx) }) tip = true }
    LaunchedEffect(tip) { if (tip) { delay(8_000); tip = false } }
    var panelPin by remember { mutableStateOf(false) }
    val openPanel = { if (Lock.needsPin(ctx, Lock.PANEL)) panelPin = true else showPanel = true }
    // App lock asks for the PIN here when its "Customize Home" switch is on.
    var editPin by remember { mutableStateOf(false) }
    val openEditor = { if (Lock.needsPin(ctx, Lock.EDITOR)) editPin = true else openEditorNow(ctx) }
    LaunchedEffect(lastPress, cfg.idleMinutes, openFolder, showInputs, showPanel, showThemes) {
        if (cfg.idleMinutes == 0 || openFolder != null || showInputs || showPanel || showThemes) return@LaunchedEffect
        delay(cfg.idleMinutes * 60_000L)
        idle = true
    }
    val shown by animateFloatAsState(if (idle) 0f else 1f, tween(if (idle) 1200 else 250), label = "idle")
    LaunchedEffect(open.value) {
        when (open.value) {
            "inputs" -> showInputs = true
            "panel" -> openPanel()
            "themes" -> showThemes = true
            "customize" -> openEditor()
            "timer" -> showTimer = true
            "update" -> showUpdate = true
            "fireplace" -> runCatching { ctx.startActivity(android.content.Intent(ctx, FireplaceActivity::class.java)) }
            "screentime" -> ScreenTimeApps.open(ctx)
            "guest" -> showGuest = true
            "find" -> showFind = true
            "allapps" -> showAllApps = true
            // Home pressed twice: the app it came from, or the last one opened before that.
            MainActivity.LAST -> withContext(Dispatchers.Default) { Recent.apps(ctx, byPkg.keys) }.firstOrNull()?.let { byPkg[it] }?.let { launch(ctx, it) }
        }
        open.value = null
    }
    val onQuick: (QuickAction) -> Unit = { a ->
        if (a.id == "inputs") showInputs = true
        else if (a.id == "panel") openPanel()
        else if (a.id == "themes") showThemes = true
        else if (a.id == "customize") openEditor()
        else if (a.id == "timer") showTimer = true
        else if (a.id == "update") showUpdate = true
        else if (a.id == "debugging") showDebugging = true
        else if (a.id == "guest") showGuest = true
        else if (a.id == "find") showFind = true
        else if (a.id == "allapps") showAllApps = true
        else if (a.id == "fireplace") runCatching { ctx.startActivity(android.content.Intent(ctx, FireplaceActivity::class.java)) }
        else if (a.id == "screentime") ScreenTimeApps.open(ctx)
        else if (a.id == "memory") scope.launch { Memory.cleanAndSay(ctx); memoryTick++ }
        else if (a.id == "music") ConfigStore.update(ctx, org.json.JSONObject().put("visualiser", !cfg.visualiser))
        else resolve(ctx, a)?.let { i -> runCatching { ctx.startActivity(i) }.onFailure { Log.w(TAG, "${a.id}: $it") } }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(home.first, cfg.layout) { if (home.first != null && !keepFocus) runCatching { first.requestFocus() } }
    // Back on Home: the focus goes to the usual app, unless a button was pressed while it was looked up. Only once
    // the window has focus again: Android hands focus back to the last tile when it does, which would undo this.
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(usualAt) {
        if (!cfg.focusUsual || usual.isEmpty() || keepFocus) return@LaunchedEffect
        repeat(60) { if (!view.hasWindowFocus()) withFrameNanos { } }
        withFrameNanos { }
        if (lastPress <= usualAt) runCatching { first.requestFocus() }
    }
    // While an app moves, the tile that holds focus is the moving one, wherever its new place is.
    val movingKey = moving?.let { m -> if (m.kind == "main") "${if (home.dock.any { it.pkg == m.pkg }) "dock" else "grid"}:${m.pkg}" else "${m.kind}:${m.pkg}" }
    LaunchedEffect(movingKey, cfg.order, cfg.rows) {
        if (movingKey == null) return@LaunchedEffect
        repeat(5) { delay(40); runCatching { first.requestFocus() } }
    }
    fun step(by: Int) {
        val m = moving ?: return
        val change = if (m.kind == "main") {
            JSONObject().put("order", JSONArray(Reorder.order(cfg.order, (home.dock + home.grid).map { it.pkg }, m.pkg, by)))
        } else {
            val (name, list) = home.rows.getOrNull(m.kind.removePrefix("row").toIntOrNull() ?: -1) ?: return
            val rows = cfg.rows.map { g -> if (g.name == name) Group(g.name, Reorder.row(g.apps, list.map { it.pkg }, m.pkg, by)) else g }
            JSONObject().put("rows", JSONArray(rows.map { g -> JSONObject().put("name", g.name).put("apps", JSONArray(g.apps)) }))
        }
        ConfigStore.update(ctx, change)
    }
    // Out of the dock, to just after the last app left in it.
    fun takeOut() {
        val m = moving ?: return
        val all = (home.dock + home.grid).map { it.pkg }.filter { it != m.pkg }
        val at = (home.dock.size).coerceAtMost(all.size)
        ConfigStore.update(ctx, JSONObject().put("order", JSONArray(all.take(at) + m.pkg + all.drop(at))))
        moving = null; keepFocus = false
    }
    // Back puts the apps where they were before the move.
    fun cancelMove() {
        moveFrom?.let { (order, rows) -> ConfigStore.update(ctx, JSONObject().put("order", JSONArray(order)).put("rows", HomeConfig.groupsJson(rows))) }
        moving = null; keepFocus = true
    }
    val text = cfg.layout == "teletext"
    // Glass over the owner's photo: darker glass and a scrim, since white glass vanishes over a bright picture.
    val glassOnPhoto = cfg.layout == "glass" && cfg.glassBackdrop == "wallpaper" && (fireWall || wallpaper != null)
    val textActions = TextActions(
        apps = { showAllApps = true }, find = { showFind = true }, settings = { openPanel() }, inputs = { showInputs = true }.takeIf { inputs.isNotEmpty() },
        takeOut = { takeOut() }, done = { moving = null; keepFocus = true }, add = { moving = null; keepFocus = true; pickForDock = true; showAllApps = true },
        cancel = { cancelMove() }, customize = { openEditor() },
    )
    var focusColour by remember { mutableStateOf<Color?>(null) }
    val tint by animateColorAsState(focusColour ?: Color.Transparent, tween(600), label = "tint")
    // The Bing caption, while the picture it belongs to is the one on screen.
    val shownCaption = caption?.takeIf { cfg.bingCaption && !fireWall && wallpaper != null }
    val ui = Ui(cfg, accent, first, movingKey ?: home.first, onFolder = { openFolder = it }, track = track, weather = weather, memory = memory,
        onFocusApp = { a -> focusColour = a.colour?.let { Color(it) } },
        motion = motion, onAppMenu = { s, a -> if (!guest) { menuFor = s to a; if (tip) tip = false; Hints.done(ctx) } },
        onWatchMenu = { if (!guest) watchMenu = it }, movingKey = movingKey,
        captioned = shownCaption != null, screenTime = screenTime, screenTimeSeen = screenSeen && wantScreen)

    Box(
        Modifier.fillMaxSize().background(if (text) TT_BLACK else Color(cfg.background)).onPreviewKeyEvent { e ->
            lastPress = System.currentTimeMillis()
            if (e.type == KeyEventType.KeyDown) RemoteKeys.seen(ctx, e.key)
            if (moving != null && text && movingKey?.startsWith("dock:") == true) {
                // Teletext's Arrange page: Up and Down move the app within the dock, Left and Right reach the colour words.
                val place = home.dock.indexOfFirst { it.pkg == moving?.pkg }
                val words = listOf(textActions.takeOut, textActions.done, textActions.add, textActions.cancel)
                if (e.type == KeyEventType.KeyDown) when {
                    e.key == Key.DirectionUp -> { textPick = null; if (place > 0) step(-1) }
                    e.key == Key.DirectionDown -> { textPick = null; if (place < minOf(home.dock.size, TEXT_DOCK) - 1) step(1) }
                    e.key == Key.DirectionLeft -> textPick = ((textPick ?: 4) + 3) % 4
                    e.key == Key.DirectionRight -> textPick = ((textPick ?: -1) + 1) % 4
                    colourKey(e.key) != null -> words[colourKey(e.key)!!]()
                }
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) cancelMove()
                else if (e.type == KeyEventType.KeyUp && e.key in OK_KEYS) textPick?.let { words[it]() } ?: textActions.done()
                return@onPreviewKeyEvent true
            }
            if (moving != null) {
                val inGrid = movingKey?.startsWith("grid:") == true
                val glassDock = cfg.layout == "glass" && movingKey?.startsWith("dock:") == true
                if (e.type == KeyEventType.KeyDown) when (e.key) {
                    Key.DirectionLeft -> step(-1)
                    Key.DirectionRight -> step(1)
                    Key.DirectionUp -> if (inGrid) step(-cfg.columns)
                    // Glass: Down takes the app out of the dock.
                    Key.DirectionDown -> if (inGrid) step(cfg.columns) else if (glassDock) takeOut()
                    else -> {}
                }
                if (e.type == KeyEventType.KeyUp && (e.key == Key.Back || e.key == Key.Escape)) cancelMove()
                else if (e.type == KeyEventType.KeyUp && e.key in DONE_KEYS) { moving = null; keepFocus = true }
                return@onPreviewKeyEvent true
            }
            if (e.type == KeyEventType.KeyDown) Motion.seen(e.key)
            when {
                idle && e.type == KeyEventType.KeyDown -> { idle = false; swallowUp = true; true }
                swallowUp && e.type == KeyEventType.KeyUp -> { swallowUp = false; true }
                // 1 to 9 open the dock's first nine apps, on remotes that have number keys. Teletext has its own.
                !text && cfg.numberKeys && e.type == KeyEventType.KeyDown && dockNumber(e.key) != null ->
                    home.dock.getOrNull(dockNumber(e.key)!! - 1)?.let { launch(ctx, it); true } ?: false
                else -> false
            }
        }
    ) {
        // Teletext is black, with no wallpaper, colour wash or snow. Glass has no wash or snow either, and its own
        // slate when there's no wallpaper or the owner picked it; over a wallpaper it lays a scrim so the headline
        // and tray read.
        val glass = cfg.layout == "glass"
        if (text) Unit
        else if (glass && cfg.glassBackdrop == "slate") GlassBackdrop()
        else if (fireWall) {
            FireWallpaper(cfg, motion)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cfg.wallpaperDim / 100f)))
        } else wallpaper?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cfg.wallpaperDim / 100f)))
        } ?: run { if (glass) GlassBackdrop() }
        if (glassOnPhoto) GlassScrim()
        IntroLayer(intro)
        // The focused app's colour washes in from the top; the wallpaper still shows through.
        // Drawn in the draw phase, so the fading colour doesn't redraw the whole screen each frame.
        if (!text && !glass && cfg.followColour && cfg.wash > 0) Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.verticalGradient(0f to tint.copy(alpha = tint.alpha * cfg.wash / 100f), cfg.washReach / 100f to Color.Transparent))
        })
        if (!text && !glass && cfg.snow) {
            // Still while anything is open over Home, and with the TV's animations off.
            val menus = openFolder != null || menuFor != null || showInputs || showPanel || showThemes || showTimer || showUpdate || showDebugging || showGuest || watchMenu != null || showFind || showAllApps || panelPin || editPin
            Snow(running = resumed() && motion && !menus)
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
            // Rows sit over the lower half of the photo; a ground-tinted shade keeps their names readable.
            if (!text && !glass && cfg.shadeBottom > 0 && cfg.shadeBottomReach > 0) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(1f - cfg.shadeBottomReach / 100f to Color.Transparent, 1f to scrim(cfg, cfg.shadeBottom / 100f))))
            }
            when (cfg.layout) {
                "split" -> SplitLayout(home, ui, quick, onQuick)
                "rail" -> RailLayout(home, ui, quick, onQuick)
                // Search is only in Quick settings (Control centre in Glass) in these two, so it always leads.
                "bau" -> BauLayout(home, ui, listOfNotNull(all["panel"]?.takeIf { !guest }) + quick.filter { it.id != "panel" }, onQuick)
                "glass" -> GlassLayout(home, ui, listOfNotNull(all["panel"]?.copy(label = tr("Control centre"))?.takeIf { !guest }) + quick.filter { it.id != "panel" }, onQuick, onAllApps = { showAllApps = true }, moving, appsVersion, glassOnPhoto)
                "teletext" -> TeletextLayout(home, ui, appsVersion, moving, textPick, textActions)
                else -> ShelfLayout(home, ui, quick, onQuick)
            }
            if (!text) shownCaption?.let { BingCaption(it, visualiser, cfg, Modifier.align(Alignment.BottomEnd).padding(start = SAFE_X, end = SAFE_X, bottom = SAFE_Y)) }
            if (cfg.visualiser && !text) Bars(visualiser, cfg, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        if (shown < 1f) IdleScreen(ui, Modifier.fillMaxSize().graphicsLayer { alpha = 1f - shown })
        val tipShown by animateFloatAsState(if (tip && !idle && moving == null && !text) 1f else 0f, tween(400), label = "tip")
        // The tip also says where Customize is, when the top bar has a way in.
        val tipText = when {
            quick.any { it.id == "customize" } -> tr("Tip: hold OK on an app to move it or hide it. The rest is in Customize, at the top.")
            cfg.editOnTv && quick.any { it.id == "panel" } -> tr("Tip: hold OK on an app to move it or hide it. Customize Home is in Quick settings.")
            else -> tr("Tip: hold OK on an app to move it or hide it")
        }
        // Glass puts it in a capsule above the tray, so it never covers the dock; Bauhaus in its cream.
        if (tipShown > 0f) when (cfg.layout) {
            "glass" -> GlassTip(tipText, glassOnPhoto, Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = tipShown })
            "bau" -> Text(
                tipText.lowercase(), color = BAU_INK, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SAFE_Y).graphicsLayer { alpha = tipShown }
                    .background(BAU_CREAM).border(1.5.dp, BAU_INK).padding(horizontal = 18.dp, vertical = 10.dp)
            )
            else -> Text(
                tipText,
                color = TEXT, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = SAFE_Y).graphicsLayer { alpha = tipShown }
                    .background(PANEL, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
        // Glass says it in its own headline, Teletext on its Arrange page. Bauhaus: square, cream and lower case.
        moving?.takeIf { (cfg.layout != "glass" && !text) || it.kind != "main" }?.let { m ->
            val note = tr("Moving {app}. Use the arrows, then OK.", "app" to m.label)
            // In the left column under the date, where it covers no app.
            if (cfg.layout == "bau") Text(
                note.lowercase(), color = BAU_INK, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = SAFE_X, top = 136.dp).width(276.dp).background(BAU_CREAM).border(1.5.dp, BAU_INK).padding(horizontal = 18.dp, vertical = 10.dp)
            )
            else Text(
                note, color = TEXT, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = SAFE_Y).background(PANEL, RoundedCornerShape(12.dp)).padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }

    openFolder?.let { (name, list) -> FolderOverlay(name, list, ui) { openFolder = null } }
    if (showInputs) InputsOverlay(inputs, ui) { showInputs = false }
    menuFor?.let { (section, app) ->
        // Folders keep their order in the desktop app; the dock, All apps and rows move here.
        val kind = when {
            section == "dock" || section == "grid" -> "main"
            section.startsWith("row") -> section
            else -> null
        }
        AppMenu(app, ui, onMove = kind?.let { k -> { menuFor = null; openFolder = null; moving = Moving(k, app.pkg, app.label) } }) { menuFor = null }
    }
    if (showPanel) {
        if (cfg.layout == "glass") GlassControl(onFind = { showFind = true }, onEditor = { openEditor() }.takeIf { cfg.editOnTv }) { showPanel = false }
        else if (text) TextSettings(onEditor = { openEditor() }) { showPanel = false }
        else SettingsPanel(accent, bau = cfg.layout == "bau", onFind = { showFind = true }, onEditor = { openEditor() }.takeIf { cfg.editOnTv }) { showPanel = false }
    }
    if (showThemes) ThemeCarousel(accent) { showThemes = false }
    if (showTimer) TimerMenu(ui) { showTimer = false }
    if (showUpdate) update?.let { UpdateDialog(it, accent) { showUpdate = false } }
    if (showDebugging) DebuggingDialog(accent) { showDebugging = false; debugging = Debugging.shown(ctx) }
    if (showGuest) GuestDialog(cfg, accent) { showGuest = false }
    // Search this TV and the All apps page see what Home shows: no locked apps with "Hide locked apps", only the guest apps in guest mode.
    val pageApps = remember(byPkg, cfg.lockOn, cfg.lockShow, cfg.lockApps) { byPkg.values.filter { !(cfg.lockOn && cfg.lockShow == "hide" && it.pkg in cfg.lockApps) } }
    val dockPkgs = home.dock.map { it.pkg }.toSet()
    if (showFind) {
        if (text) TextFind(pageApps, dockPkgs, onApp = { launch(ctx, it) }) { showFind = false }
        else FindPage(cfg, pageApps, dockPkgs, onApp = { launch(ctx, it) }, backdrop = wallpaper.takeIf { cfg.glassBackdrop == "wallpaper" }) { showFind = false }
    }
    if (showAllApps) {
        if (text) TextApps(pageApps, dockPkgs, onOpen = { launch(ctx, it) }, onMenu = { if (!guest) pageMenu = it }, onFind = { showFind = true },
            onPick = if (pickForDock) { app -> addToDock(ctx, home, cfg, app.pkg, TEXT_DOCK) } else null) { showAllApps = false; pickForDock = false }
        else AppsPage(cfg, pageApps, onOpen = { launch(ctx, it) }, onMenu = { if (!guest) pageMenu = it }, backdrop = wallpaper.takeIf { cfg.glassBackdrop == "wallpaper" }) { showAllApps = false }
    }
    pageMenu?.let { app ->
        // All apps has no Move in Glass and Bauhaus: an app outside the dock gets there from its menu. When the
        // dock is full, the menu names the app it replaces.
        val cap = if (cfg.layout == "glass") minOf(cfg.dockSize, GLASS_DOCK) else cfg.dockSize
        val toDock = if (cfg.layout !in setOf("glass", "bau") || app.pkg in dockPkgs || cap == 0) null else {
            val out = home.dock.take(cap).takeIf { it.size >= cap }?.last()
            val what = if (cfg.layout == "bau") tr("Add to favourites") else tr("Add to the dock")
            (if (out != null) tr("{action}, in place of {app}", "action" to what, "app" to out.label) else what) to { pageMenu = null; addToDock(ctx, home, cfg, app.pkg, cap) }
        }
        AppMenu(app, ui, onMove = null, toDock = toDock) { pageMenu = null }
    }
    watchMenu?.let { item ->
        val remove = { WatchNext.remove(ctx, item); watchRemoved = watchRemoved + (item.key to System.currentTimeMillis()); watchMenu = null }
        if (cfg.layout == "glass") GlassMenuDialog(item.title, tr("It comes back if you watch it again."),
            listOf(tr("Remove from Continue watching") to remove, tr("Cancel") to { watchMenu = null })) { watchMenu = null }
        else WatchMenu(item, accent, onRemove = remove) { watchMenu = null }
    }
    if (backNews.isNotEmpty() && !showUpdate) CameBackDialog(backNews, accent) { CameBack.seen(ctx, back); backNews = emptyList() }
    if (note != null && !showUpdate && !showDebugging && !showGuest && backNews.isEmpty() && !idle) note?.let { n -> NoteDialog(n, accent) { TvNote.clear(ctx) } }
    if (panelPin) Dialog(onDismissRequest = { panelPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(tr("Enter the PIN for Quick settings"), Lock.PANEL, accent) { panelPin = false; showPanel = true }
    }
    if (editPin) Dialog(onDismissRequest = { editPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(tr("Enter the PIN to customize Home"), Lock.EDITOR, accent) { editPin = false; openEditorNow(ctx) }
    }
}

/**
 * Teletext's "Add an app", and "Add to the dock" in the app menu: [pkg] goes after the last app in the dock, or
 * takes the last place when the [cap] apps the layout shows are there, and comes out of Hidden if it was there.
 */
private fun addToDock(ctx: android.content.Context, home: Home, cfg: HomeConfig, pkg: String, cap: Int) {
    val all = (home.dock + home.grid).map { it.pkg }.filter { it != pkg }
    val at = minOf(home.dock.count { it.pkg != pkg }, cfg.dockSize - 1, cap - 1).coerceAtLeast(0)
    ConfigStore.update(ctx, JSONObject().put("order", JSONArray(all.take(at) + pkg + all.drop(at))).put("hidden", JSONArray(cfg.hidden - pkg)))
}

/** 1 for the 1 key, on the main keys or the number pad; null for any other key. */
private fun dockNumber(k: Key): Int? = DOCK_KEYS.indexOf(k).takeIf { it >= 0 }?.let { it % 9 + 1 }
private val DOCK_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
    Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9)

private fun openEditorNow(ctx: android.content.Context) {
    runCatching { ctx.startActivity(android.content.Intent(ctx, io.github.wingedpanther.cleanmytv.home.customization.CustomizationActivity::class.java)) }
        .onFailure { Log.w(TAG, "editor: $it") }
}

/**
 * The fireplace behind Home, in place of the picture. It plays while Home is on screen and holds still while
 * an app is open, or when the TV's animations are off. Silent unless the owner turned its sound on.
 */
@Composable
private fun FireWallpaper(cfg: HomeConfig, motion: Boolean) {
    val resumed = resumed()
    val volume = if (cfg.fireWallSound) cfg.fireVolume / 100f else 0f
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { FireView(it) { if (ConfigStore.config.value.fireWallSound) ConfigStore.config.value.fireVolume / 100f else 0f } },
        modifier = Modifier.fillMaxSize(),
        update = { v -> v.setVolume(volume); v.play(resumed && motion) },
        onRelease = { it.release() },
    )
}

/**
 * Where the Bing picture was taken, small and quiet in the bottom corner (#123). The rows
 * stop above it. The music bars cover the same strip, so it steps aside while they play and comes back after.
 */
@Composable
private fun BingCaption(c: Wallpapers.Caption, visualiser: Visualiser, cfg: HomeConfig, modifier: Modifier) {
    val levels = visualiser.levels.collectAsState()
    val playing by remember(cfg.visualiser) { derivedStateOf { cfg.visualiser && levels.value.any { it > 0.01f } } }
    val alpha by animateFloatAsState(if (playing) 0f else 1f, if (playing) tween(200) else tween(800, delayMillis = 1500), label = "caption")
    Text(
        c.place,
        color = TEXT.copy(alpha = 0.72f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 6f)),
        modifier = modifier.widthIn(max = 640.dp).graphicsLayer { this.alpha = alpha }
    )
}

/** True while Home is in front: false once an app or the editor opens over it, or the screen goes off. */
@Composable
private fun resumed(): Boolean {
    val owner = LocalContext.current as? androidx.activity.ComponentActivity
    var resumed by remember { mutableStateOf(owner?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ?: true) }
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumed = true
            if (e == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) resumed = false
        }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    return resumed
}

/**
 * A new wallpaper's entrance: the picture starts in a cool, dim twilight, the light arrives from the top
 * down, and a soft band of light crosses it once. [p] runs from 0 to 1.
 */
@Composable
private fun Sunrise(p: Float) {
    Box(Modifier.fillMaxSize().drawWithContent {
        val dusk = Color(0xFF0A1424)
        val edge = -0.25f + p * 1.5f
        val a = (edge - 0.3f).coerceIn(0f, 0.999f)
        val b = edge.coerceIn(a + 0.001f, 1f)
        drawRect(Brush.verticalGradient(a to dusk.copy(alpha = 0f), b to dusk.copy(alpha = 0.92f), 1f to dusk.copy(alpha = 0.92f)))
        drawRect(dusk.copy(alpha = 0.35f * (1f - p)))
        val w = size.width
        val x = -w * 0.6f + p * w * 1.8f
        val glow = kotlin.math.sin(p * Math.PI).toFloat() * 0.16f
        drawRect(Brush.linearGradient(0f to Color.Transparent, 0.5f to Color.White.copy(alpha = glow), 1f to Color.Transparent,
            start = Offset(x, 0f), end = Offset(x + w * 0.35f, size.height)))
    })
}

/** What every tile and section needs to know. */
internal class Ui(
    val cfg: HomeConfig,
    val accent: Color,
    val focus: FocusRequester,
    val focusKey: String?,
    val onFolder: (Pair<String, List<TvApp>>) -> Unit,
    val track: Track? = null,
    val weather: WeatherNow? = null,
    val memory: MemoryNow? = null,
    val onFocusApp: (TvApp) -> Unit = {},
    /** False when the TV's animations are off: tiles still lift, without springs, tilt or sheen. */
    val motion: Boolean = true,
    /** A long press on a tile, with the section it is in. */
    val onAppMenu: (String, TvApp) -> Unit = { _, _ -> },
    /** Hold OK on a Continue watching tile. */
    val onWatchMenu: (WatchItem) -> Unit = {},
    /** "<section>:<package>" of the app being moved, drawn with an outline. */
    val movingKey: String? = null,
    /** Where the Bing picture was taken shows in the bottom corner: the rows stop above it. */
    val captioned: Boolean = false,
    /** Screen time's numbers for its section; [screenTimeSeen] once they've been looked for, null meaning no usage access. */
    val screenTime: ScreenSummary? = null,
    val screenTimeSeen: Boolean = false,
) {
    /** Banners are always wide; the icon styles take the chosen shape. */
    val shape: String = if (cfg.iconStyle == "banner") "wide" else cfg.tileShape
    fun focusFor(section: String, app: TvApp): Modifier = if ("$section:${app.pkg}" == focusKey) Modifier.focusRequester(focus) else Modifier
}

// ---- Layouts --------------------------------------------------------------------------------------

/** Shelf: a slim top bar, open wallpaper above the apps, the sections below. */
@Composable
private fun ShelfLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit) {
    val cfg = ui.cfg
    if (cfg.shadeTop > 0 && cfg.shadeTopReach > 0) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to scrim(cfg, cfg.shadeTop / 100f), cfg.shadeTopReach / 100f to Color.Transparent)))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val open = maxHeight * (cfg.topSpace / 100f)
        Column(Modifier.fillMaxSize()) {
            // Greeting on the left; on the right the clock, the date, and the quick-settings icons under them.
            Row(Modifier.fillMaxWidth().padding(start = SAFE_X, end = SAFE_X, top = SAFE_Y), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp)) { Greeting(cfg, 20.sp) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Clock(cfg, 26.sp, 12.sp, Alignment.End)
                    WeatherLine(ui, 13.sp, Modifier.padding(top = 4.dp))
                    HourLine(ui, 13.sp, Modifier.padding(top = 6.dp))
                    DayLine(ui, 13.sp, Modifier.padding(top = 6.dp))
                    MemoryLine(ui, 13.sp, Modifier.padding(top = 4.dp))
                    if (quick.isNotEmpty()) {
                        if (cfg.clock != "off") Spacer(Modifier.height(10.dp))
                        Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { quick.forEach { QuickButton(it, ui, onQuick) } }
                    }
                }
            }
            Sections(home, ui, Modifier.fillMaxSize(), PaddingValues(start = SAFE_X - BLEED, end = SAFE_X - BLEED, top = open, bottom = 96.dp), dockWidth = 200.dp)
        }
    }
}

/** Split: the time, greeting and quick settings hold the left third; the apps scroll on the right. */
@Composable
private fun SplitLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit) {
    val cfg = ui.cfg
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scrim(cfg, 0.7f), Color.Transparent), endX = 900f)))
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(0.32f).fillMaxHeight().padding(start = SAFE_X, top = SAFE_Y + 24.dp, bottom = SAFE_Y + 48.dp)) {
            // 46 sp keeps "12:51 PM" on one line in the left third; 12-hour time is the widest.
            Clock(cfg, 46.sp, 15.sp, Alignment.Start)
            WeatherLine(ui, 15.sp, Modifier.padding(top = 6.dp))
            // 13 sp keeps four hours inside the left third.
            HourLine(ui, 13.sp, Modifier.padding(top = 8.dp))
            DayLine(ui, 13.sp, Modifier.padding(top = 8.dp))
            MemoryLine(ui, 15.sp, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(14.dp))
            Greeting(cfg, 18.sp)
            Spacer(Modifier.weight(1f))
            Column(Modifier.focusGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) { quick.forEach { QuickRow(it, ui, onQuick, expanded = true) } }
        }
        Sections(home, ui, Modifier.fillMaxSize(), PaddingValues(start = 0.dp, end = SAFE_X - BLEED, top = SAFE_Y + 12.dp, bottom = 96.dp), dockWidth = 168.dp)
    }
}

/** Rail: icons down the left edge that widen with labels when focus moves onto them. */
@Composable
private fun RailLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit) {
    val cfg = ui.cfg
    var railFocused by remember { mutableStateOf(false) }
    // Icons start at the 48 dp safe edge; only the rail's shade runs to the screen edge.
    val railW by animateDpAsState(if (railFocused) 260.dp else SAFE_X + 56.dp, tween(220), label = "rail")
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(railW).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(scrim(cfg, if (railFocused) 0.92f else 0.6f), scrim(cfg, if (railFocused) 0.75f else 0.1f))))
                .padding(start = SAFE_X - 4.dp, top = SAFE_Y + 8.dp, end = 8.dp)
                .onFocusChanged { railFocused = it.hasFocus }
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quick.forEach { QuickRow(it, ui, onQuick, expanded = railFocused) }
            if (home.folders.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                home.folders.forEach { f ->
                    RailItem(Icons.Outlined.Folder, f.first, ui, railFocused) { ui.onFolder(f) }
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            // As Shelf: the greeting level with the time, and the date, weather and memory stacked under the time.
            // Side by side they were centred on the clock's two lines and sat between them.
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = SAFE_X, top = SAFE_Y), verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp)) { Greeting(cfg, 20.sp) }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Clock(cfg, 26.sp, 12.sp, Alignment.End)
                    WeatherLine(ui, 13.sp, Modifier.padding(top = 4.dp))
                    HourLine(ui, 13.sp, Modifier.padding(top = 6.dp))
                    DayLine(ui, 13.sp, Modifier.padding(top = 6.dp))
                    MemoryLine(ui, 13.sp, Modifier.padding(top = 4.dp))
                }
            }
            Sections(home, ui, Modifier.fillMaxSize(), PaddingValues(start = 24.dp - BLEED, end = SAFE_X - BLEED, top = 20.dp, bottom = 96.dp), dockWidth = 196.dp, showFolders = false)
        }
    }
}

/**
 * Bau, after the Bauhaus design: its own flat charcoal ground and cream type over the wallpaper and theme. On the
 * left a heavy clock, the date in lower case and three primary shapes, the top bar icons as ruled rows and the
 * remote's keys; on the right the sections as flat grids ruled in cream, each app marked with a shape in red,
 * yellow or blue.
 */
@Composable
private fun BauLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit) {
    val cfg = ui.cfg
    Row(Modifier.fillMaxSize().background(BAU_INK).padding(start = SAFE_X, end = SAFE_X), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Column(Modifier.width(276.dp).fillMaxHeight().padding(top = SAFE_Y, bottom = SAFE_Y)) {
            BauClock(cfg)
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                BauGlyph(0, BAU_RED, 28.dp); BauGlyph(1, BAU_YELLOW, 28.dp); BauGlyph(2, BAU_BLUE, 28.dp)
            }
            if (cfg.greeting.isNotBlank()) Box(Modifier.padding(top = 18.dp)) { Greeting(cfg, 14.sp) }
            WeatherLine(ui, 12.sp, Modifier.padding(top = 8.dp))
            HourLine(ui, 12.sp, Modifier.padding(top = 6.dp))
            DayLine(ui, 12.sp, Modifier.padding(top = 6.dp))
            MemoryLine(ui, 12.sp, Modifier.padding(top = 4.dp))
            Spacer(Modifier.weight(1f))
            Column(Modifier.focusGroup()) {
                quick.forEach { q -> BauRule(); BauQuickRow(q, ui, onQuick) }
                if (quick.isNotEmpty()) BauRule()
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                BauKey("ok"); Text(tr("Open").lowercase(), color = BAU_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                BauKey(tr("hold ok")); Text(tr("Options").lowercase(), color = BAU_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        BauSections(home, ui, Modifier.weight(1f).fillMaxHeight())
    }
}

/** Bauhaus's own menu, as Glass and Teletext have theirs: a square cream sheet, black rules, lower case. */
@Composable
internal fun BauMenu(title: String, items: List<Pair<String, () -> Unit>>, focus: FocusRequester) {
    Column(Modifier.width(380.dp).background(BAU_CREAM).border(1.5.dp, BAU_INK)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(BAU_RED))
            Text(title.lowercase(), color = BAU_INK, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
        }
        items.forEachIndexed { i, (label, act) ->
            var focused by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(BAU_INK))
            Surface(
                onClick = act,
                modifier = Modifier.pointer { act() }.then(if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth().height(52.dp).onFocusChanged { focused = it.isFocused },
                shape = ClickableSurfaceDefaults.shape(androidx.compose.ui.graphics.RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = BAU_INK)
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                    Text(label.lowercase(), color = if (focused) BAU_CREAM else BAU_INK, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

internal val BAU_INK = Color(0xFF141414)
internal val BAU_CREAM = Color(0xFFF2EEE3)
internal val BAU_MUTED = Color(0xFFA8A398)
internal val BAU_DIM = Color(0xFF4A4740)
internal val BAU_LINE = Color(0xFF3A3A3A)
internal val BAU_RED = Color(0xFFE03A2B)
internal val BAU_YELLOW = Color(0xFFF5C518)
internal val BAU_BLUE = Color(0xFF2F6BDE)
internal val BAU_COLOURS = listOf(BAU_RED, BAU_YELLOW, BAU_BLUE)

@Composable
internal fun BauRule(colour: Color = BAU_LINE) = Box(Modifier.fillMaxWidth().height(1.dp).background(colour))

/** A remote key as the design draws it: its name in a cream outline. */
@Composable
internal fun BauKey(name: String) {
    Text(name, color = BAU_CREAM, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.border(1.dp, BAU_CREAM).padding(horizontal = 6.dp, vertical = 3.dp))
}

/**
 * The seven Bauhaus forms, drawn in [colour]: circle, square, triangle, quarter disc, half disc, ring and a
 * triangle pointing right.
 */
@Composable
internal fun BauGlyph(kind: Int, colour: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        when (kind % 7) {
            0 -> drawCircle(colour)
            1 -> drawRect(colour)
            2 -> drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, w); lineTo(0f, w); close() }, colour)
            3 -> drawArc(colour, 180f, 90f, true, topLeft = Offset.Zero, size = Size(w * 2, w * 2))
            4 -> drawArc(colour, 0f, 180f, true, topLeft = Offset(0f, 0f), size = Size(w, w))
            5 -> drawCircle(colour, radius = w / 2 - w / 8, style = Stroke(width = w / 4))
            else -> drawPath(Path().apply { moveTo(0f, 0f); lineTo(w, w / 2); lineTo(0f, w); close() }, colour)
        }
    }
}

/** Each app keeps its own form and colour, from its package name. */
internal fun bauMark(pkg: String): Pair<Int, Color> {
    val h = pkg.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return h % 7 to BAU_COLOURS[(h / 7) % 3]
}

/** What kind of app it is, from the category it declares; null when it declares none. */
internal fun bauKind(ctx: android.content.Context, pkg: String): String? {
    if (InputTiles.isInput(pkg)) return tr("Input")
    val c = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).category }.getOrDefault(-1)
    return when (c) {
        android.content.pm.ApplicationInfo.CATEGORY_GAME -> tr("Games")
        android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> tr("Music and audio")
        android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> tr("Video")
        android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> tr("Photos")
        android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> tr("Social")
        android.content.pm.ApplicationInfo.CATEGORY_NEWS -> tr("News")
        android.content.pm.ApplicationInfo.CATEGORY_MAPS -> tr("Maps")
        android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> tr("Productivity")
        else -> null
    }
}

/** Bau's clock: heavy and tight, sized to the left column; 12-hour time is wider, so smaller. The date in lower case. */
@Composable
private fun BauClock(cfg: HomeConfig) {
    if (cfg.clock == "off") return
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
    val size = if (time.length <= 5) 106.sp else 62.sp
    Text(time, color = BAU_CREAM, fontSize = size, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false,
        style = TextStyle(letterSpacing = size * -0.055f, lineHeight = size * 0.86f), modifier = Modifier.offset(x = (-5).dp))
    if (cfg.clock == "time_date") {
        val locale = java.util.Locale.getDefault()
        val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale).format(now).lowercase(locale).replace(",", "")
        Text(day, color = BAU_MUTED, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
    }
}

/** A top bar icon as a ruled row: its name on the left, its mark on the right; cream when focused. */
@Composable
private fun BauQuickRow(a: QuickAction, ui: Ui, onQuick: (QuickAction) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onQuick(a) },
        modifier = Modifier.fillMaxWidth().height(32.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = BAU_CREAM)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(a.label.lowercase(), color = if (focused) BAU_INK else BAU_CREAM, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // Black on the cream of a focused line; the accent's ink could match the cream and vanish.
            QuickGlyph(a, ui, focused, 16.dp, ink = if (focused) BAU_INK else BAU_CREAM)
        }
    }
}

/** The sections down the right, each under a lower-case title, as flat grids ruled in cream. */
@Composable
private fun BauSections(home: Home, ui: Ui, modifier: Modifier) {
    val cfg = ui.cfg
    LazyColumn(modifier, contentPadding = PaddingValues(top = SAFE_Y, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (s in cfg.sections) when (s) {
            "nowplaying" -> ui.track?.let { t -> item("nowplaying") { NowPlayingCard(t, ui) } }
            "screentime" -> if (ui.screenTimeSeen) item("screentime") { ScreenTimeCard(ui) }
            "dock" -> if (home.dock.isNotEmpty()) {
                item("dock-title") { BauTitle(tr("Favourites"), tr("All apps") + ", " + (home.dock.size + home.grid.size)) }
                item("dock") { BauGrid("dock", home.dock, ui, columns = 3, height = 155.dp, mark = 52.dp, nameSize = 20.sp) }
            }
            "watchnext" -> if (home.watch.isNotEmpty()) {
                item("watchnext-title") { BauTitle(tr("Continue"), top = 14.dp) }
                item("watchnext") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        home.watch.take(4).forEach { w -> BauWatch(w, home.app(w.pkg), ui, Modifier.weight(1f)) }
                        repeat(4 - minOf(4, home.watch.size)) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            "recent" -> if (home.recent.isNotEmpty()) {
                item("recent-title") { BauTitle(cfg.names["recent"] ?: tr("Recently used"), top = 14.dp) }
                item("recent") { BauGrid("recent", home.recent, ui, columns = cfg.columns, height = 82.dp, mark = 22.dp, nameSize = 15.sp) }
            }
            "rows" -> home.rows.forEachIndexed { i, (name, list) ->
                item("row$i-title") { BauTitle(name, top = 14.dp) }
                item("row$i") { BauGrid("row$i", list, ui, columns = cfg.columns, height = 82.dp, mark = 22.dp, nameSize = 15.sp) }
            }
            "folders" -> if (home.folders.isNotEmpty()) {
                item("folders-title") { BauTitle(tr("Folders"), top = 14.dp) }
                item("folders") {
                    BauCells(home.folders, cfg.columns, 82.dp) { f, m -> BauCell(f.first, "${f.second.size}", 3 to BAU_YELLOW, ui, 82.dp, 22.dp, 15.sp, m, onClick = { ui.onFolder(f) }) }
                }
            }
            "grid" -> if (home.grid.isNotEmpty()) {
                item("grid-title") { BauTitle(cfg.names["all_apps"] ?: tr("All apps"), top = 14.dp) }
                item("grid") { BauGrid("grid", home.grid, ui, columns = cfg.columns, height = 82.dp, mark = 22.dp, nameSize = 15.sp) }
            }
        }
    }
}

@Composable
private fun BauTitle(name: String, side: String? = null, top: Dp = 0.dp) {
    Row(Modifier.fillMaxWidth().padding(top = top), verticalAlignment = Alignment.Bottom) {
        Text(name.lowercase(), color = BAU_CREAM, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        side?.let { Text(it.lowercase(), color = BAU_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** Items in rows of [columns], ruled in cream: the grid's cream shows through 1 dp gaps. */
@Composable
private fun <T> BauCells(items: List<T>, columns: Int, height: Dp, cell: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth().background(BAU_CREAM).padding(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        items.chunked(columns.coerceAtLeast(1)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                // The rest of a short last row stays dark, as the design's empty cells do.
                repeat(columns - row.size) { Box(Modifier.weight(1f).height(height).background(BAU_INK)) }
            }
        }
    }
}

@Composable
private fun BauGrid(section: String, apps: List<TvApp>, ui: Ui, columns: Int, height: Dp, mark: Dp, nameSize: androidx.compose.ui.unit.TextUnit) {
    val ctx = LocalContext.current
    BauCells(apps, columns, height) { app, m ->
        val kind = remember(app.pkg) { bauKind(ctx, app.pkg) }
        BauCell(app.label, if (Lock.isLocked(ui.cfg, app.pkg)) tr("Locked") else if (height > 100.dp) kind else null, bauMark(app.pkg), ui, height, mark, nameSize,
            m.then(ui.focusFor(section, app)), moving = ui.movingKey == "$section:${app.pkg}",
            onFocus = { ui.onFocusApp(app) }, onLongClick = { ui.onAppMenu(section, app) }, onClick = { launch(ctx, app) })
    }
}

/** One square-edged cell: its shape at the top, its name and kind at the foot; cream with dark type when focused. */
@Composable
private fun BauCell(name: String, sub: String?, mark: Pair<Int, Color>, ui: Ui, height: Dp, markSize: Dp, nameSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier,
                    moving: Boolean = false, onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.height(height).pointer(onLongClick = onLongClick) { onClick() }.onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .then(if (moving) Modifier.border(3.dp, BAU_RED) else Modifier),
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = BAU_INK, focusedContainerColor = BAU_CREAM)
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (height > 100.dp) 15.dp else 12.dp, vertical = if (height > 100.dp) 14.dp else 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            BauGlyph(mark.first, mark.second, markSize)
            Column {
                // Two lines, so "open shelf" isn't cut to "open sh…" in the six-across strip. A one-word name stays
                // on one line and ends in "…": a second line would break it mid-word.
                Text(name.lowercase(), color = if (focused) BAU_INK else BAU_CREAM, fontSize = nameSize, fontWeight = FontWeight.ExtraBold, maxLines = if (' ' in name.trim()) 2 else 1, overflow = TextOverflow.Ellipsis,
                    style = TextStyle(letterSpacing = nameSize * -0.02f, lineHeight = nameSize * 1.05f))
                sub?.let { Text(it.lowercase(), color = if (focused) BAU_DIM else BAU_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** A film or episode to go back to: a cream rule over its name, its app and time left, and a thick progress bar. */
@Composable
private fun BauWatch(item: WatchItem, app: TvApp?, ui: Ui, modifier: Modifier) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val go: () -> Unit = { (WatchNext.intent(ctx, item) ?: app?.launch)?.let { open(ctx, item.pkg, it, app?.launch) } }
    val left = if (item.duration > 0 && item.position > 0) ((item.duration - item.position) / 60_000L).toInt().takeIf { it > 0 } else null
    val meta = listOfNotNull(app?.label, left?.let { tr("{time} left", "time" to Limits.words(it)) }).joinToString(" · ")
    Surface(
        onClick = go,
        onLongClick = { ui.onWatchMenu(item) },
        modifier = modifier.pointer(onLongClick = { ui.onWatchMenu(item) }) { go() }.onFocusChanged { focused = it.isFocused; if (it.isFocused) app?.let(ui.onFocusApp) },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = BAU_CREAM)
    ) {
        val ink = if (focused) BAU_INK else BAU_CREAM
        Column {
            BauRule(ink)
            Text(item.title.lowercase(), color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp))
            Text(meta.lowercase(), color = if (focused) BAU_DIM else BAU_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp, start = 2.dp, end = 2.dp))
            Box(Modifier.padding(top = 8.dp, bottom = 6.dp).fillMaxWidth().height(4.dp).background(BAU_LINE)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(item.progress ?: 0f).background(ink))
            }
        }
    }
}

// ---- Sections -------------------------------------------------------------------------------------

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun Sections(home: Home, ui: Ui, modifier: Modifier, padding: PaddingValues, dockWidth: Dp, showFolders: Boolean = true) {
    val cfg = ui.cfg
    // Rows keep Android TV's own sideways scrolling; only the column up and down follows STAY.
    val rowSpec = LocalBringIntoViewSpec.current
    BoxWithConstraints(modifier) {
        val inner = maxWidth - padding.calcHorizontal() - BLEED * 2
        // Rows scroll out to the screen's edges (#124): the layout's side inset goes into each row's own
        // padding, so at rest a row still starts in line with its title, but a tile scrolling past slides off
        // the edge of the screen instead of being cut off in mid-air short of it. Titles, Now playing and the
        // grid don't scroll sideways and keep the inset as plain padding.
        val start = padding.calculateLeftPadding(LayoutDirection.Ltr)
        val end = padding.calculateRightPadding(LayoutDirection.Ltr)
        val rowPad = PaddingValues(start = start + BLEED, end = end + BLEED, top = 16.dp, bottom = 16.dp)
        val still = Modifier.padding(start = start, end = end)
        val gridW: Dp = (inner - GAP * (cfg.columns - 1)) / cfg.columns
        // At 100% the dock fits the screen; the size slider scales that, and a bigger dock scrolls sideways (#89).
        val n = home.dock.size.coerceIn(1, 5)
        val dockW: Dp = minOf(dockWidth, (inner - GAP * (n - 1)) / n) * (cfg.dockScale / 100f)
        // An app moved with the arrows can land in a row that isn't drawn yet: scroll there so it can keep focus.
        val list = rememberLazyListState()
        LaunchedEffect(ui.movingKey, home.grid.size) {
            val key = ui.movingKey ?: return@LaunchedEffect
            val (section, pkg) = key.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val target = if (section == "grid") "grid${home.grid.indexOfFirst { it.pkg == pkg } / cfg.columns}" else section
            if (list.layoutInfo.visibleItemsInfo.none { it.key == target }) {
                val at = sectionKeys(home, ui, showFolders).indexOf(target)
                if (at >= 0) list.scrollToItem(at)
            }
        }
        // Near the top, the list goes all the way back when the focused row still fits on screen. STAY alone
        // stops the first row at 30% from the top on the way up, above where Top space put it (#129).
        val column = remember(list) {
            object : BringIntoViewSpec {
                override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                    val d = STAY.calculateScrollDistance(offset, size, containerSize)
                    // How far the list is from its top: the first item sits at 0 there. Asked again as the list
                    // moves, so this keeps going until the top, past the 30% line where STAY alone would stop.
                    val top = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 } ?: return d
                    val back = -top.offset.toFloat()
                    return if (back > 0 && offset + back + size <= containerSize) -back else d
                }
            }
        }
        // Top space can push the first row down to the bottom edge, never past it (#134): the rest waits below
        // until Down. The first row is measured on screen, its title included; the 16 dp under its tiles
        // counts toward the safe area, so the next title stays out of sight.
        var lead by remember { mutableIntStateOf(0) }
        LaunchedEffect(list) {
            snapshotFlow {
                val items = list.layoutInfo.visibleItemsInfo
                val first = items.firstOrNull { it.index == 0 } ?: return@snapshotFlow null
                val row = items.firstOrNull { !(it.key as? String).orEmpty().endsWith("-title") } ?: items.last()
                row.offset + row.size - first.offset
            }.collect { if (it != null) lead = it }
        }
        val room = maxHeight - (if (ui.captioned) CAPTION_STRIP else 0.dp) - (SAFE_Y - 16.dp) - with(androidx.compose.ui.platform.LocalDensity.current) { lead.toDp() }
        val top = padding.calculateTopPadding().coerceAtMost(room.coerceAtLeast(0.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides column) { LazyColumn(Modifier.fillMaxSize().padding(bottom = if (ui.captioned) CAPTION_STRIP else 0.dp).fadeEdges(bottom = ui.captioned), state = list, contentPadding = PaddingValues(top = top, bottom = padding.calculateBottomPadding())) {
            for (s in cfg.sections) when (s) {
                "nowplaying" -> ui.track?.let { t -> item("nowplaying") { Box(still) { NowPlayingCard(t, ui) } } }
                "recent" -> if (home.recent.isNotEmpty()) {
                    item("recent-title") { SectionTitle(cfg.names["recent"] ?: tr("Recently used"), still) }
                    item("recent") { TileRow("recent", home.recent, ui, 148.dp * (cfg.tileScale / 100f), big = false, rowSpec, rowPad) }
                }
                "screentime" -> if (ui.screenTimeSeen) item("screentime") { Box(still) { ScreenTimeCard(ui) } }
                "dock" -> if (home.dock.isNotEmpty()) item("dock") {
                    TileRow("dock", home.dock, ui, dockW, big = true, rowSpec, rowPad)
                }
                "watchnext" -> if (home.watch.isNotEmpty()) {
                    item("watchnext-title") { SectionTitle(tr("Continue watching"), still) }
                    item("watchnext") { WatchRow(home, ui, 196.dp * (cfg.tileScale / 100f), rowSpec, rowPad) }
                }
                "rows" -> home.rows.forEachIndexed { i, (name, list) ->
                    item("row$i-title") { SectionTitle(name, still) }
                    item("row$i") { TileRow("row$i", list, ui, 148.dp * (cfg.tileScale / 100f), big = false, rowSpec, rowPad) }
                }
                "folders" -> if (showFolders && home.folders.isNotEmpty()) {
                    item("folders-title") { SectionTitle(tr("Folders"), still) }
                    item("folders") {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides rowSpec) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP), contentPadding = rowPad) {
                                // By place, not name: two folders can share a name, and a key used twice crashes the row.
                                items(home.folders.indices.toList(), key = { it }) { i -> FolderTile(home.folders[i], ui, Modifier.width(148.dp * (cfg.tileScale / 100f))) }
                            }
                        }
                    }
                }
                "grid" -> if (home.grid.isNotEmpty()) grid(home, ui, gridW, still)
            }
        } }
    }
}

/** The keys of Sections' items, in the order it lays them out. */
private fun sectionKeys(home: Home, ui: Ui, showFolders: Boolean): List<String> = buildList {
    for (s in ui.cfg.sections) when (s) {
        "nowplaying" -> if (ui.track != null) add("nowplaying")
        "recent" -> if (home.recent.isNotEmpty()) { add("recent-title"); add("recent") }
        "screentime" -> if (ui.screenTimeSeen) add("screentime")
        "dock" -> if (home.dock.isNotEmpty()) add("dock")
        "watchnext" -> if (home.watch.isNotEmpty()) { add("watchnext-title"); add("watchnext") }
        "rows" -> home.rows.indices.forEach { add("row$it-title"); add("row$it") }
        "folders" -> if (showFolders && home.folders.isNotEmpty()) { add("folders-title"); add("folders") }
        "grid" -> if (home.grid.isNotEmpty()) { add("grid-title"); home.grid.chunked(ui.cfg.columns).indices.forEach { add("grid$it") } }
    }
}

/**
 * Up and down, the sections stay where the layout puts them while the focused tile is on screen below the
 * top 30%, so Top space holds (#90). Android TV's own rule moved every focused row to 30% from the top,
 * which pulled the dock up. Off screen, or above that line, the row still goes to 30%; the list stops at its top.
 */
@OptIn(ExperimentalFoundationApi::class)
private val STAY = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val pivot = containerSize * 0.3f
        return if (offset + size > containerSize || offset < pivot) offset - pivot else 0f
    }
}

/**
 * Rows scrolling up fade out over the top 28 dp instead of being sliced by a hard edge. With [bottom], rows
 * scrolling down fade out over the last 16 dp too, above the Bing caption.
 */
private fun Modifier.fadeEdges(bottom: Boolean): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = 28.dp.toPx()), blendMode = BlendMode.DstIn)
    if (bottom) drawRect(Brush.verticalGradient(0f to Color.Black, 1f to Color.Transparent, startY = size.height - 16.dp.toPx(), endY = size.height), blendMode = BlendMode.DstIn)
}

private fun PaddingValues.calcHorizontal(): Dp = calculateLeftPadding(LayoutDirection.Ltr) + calculateRightPadding(LayoutDirection.Ltr)

private fun LazyListScope.grid(home: Home, ui: Ui, w: Dp, still: Modifier) {
    val label = ui.cfg.names["all_apps"] ?: if (home.dock.isEmpty() && home.rows.isEmpty()) tr("Apps") else tr("All apps")
    item("grid-title") { SectionTitle(label, still) }
    // A square tile fills the middle 72% of its slot: the rows move left by the 14% before it, so the first
    // tile lines up with the heading and the dock above instead of sitting in from them.
    val inset = if (ui.shape == "wide") 0.dp else w * 0.14f
    home.grid.chunked(ui.cfg.columns).forEachIndexed { i, row ->
        item("grid$i") {
            Row(still.padding(start = BLEED - inset, end = BLEED + inset, top = 10.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (app in row) Tile("grid", app, ui, Modifier.width(w), big = false)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, still: Modifier) {
    Text(text, color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = still.padding(start = BLEED, top = 12.dp))
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TileRow(section: String, list: List<TvApp>, ui: Ui, w: Dp, big: Boolean, rowSpec: BringIntoViewSpec, pad: PaddingValues) {
    val row = @Composable {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP), contentPadding = pad) {
            val tw = if (ui.shape == "wide") w else w * 0.72f
            items(list, key = { it.pkg }) { app -> Tile(section, app, ui, Modifier.width(tw), big) }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides if (ui.cfg.pinFocus) PINNED else rowSpec) { row() }
}

// ---- Tiles ----------------------------------------------------------------------------------------

/** [lifted]: the caller zooms the tile itself with [lift], so the surface doesn't. [white]: a white glow, for [blackOnWhite]. */
@Composable
private fun focusStyle(ui: Ui, shape: Shape, zoom: Float = 1.08f, lifted: Boolean = false, white: Boolean = false) = Triple(
    ClickableSurfaceDefaults.scale(focusedScale = if (ui.cfg.focus == "none" || (lifted && ui.cfg.focus == "lift")) 1f else zoom),
    ClickableSurfaceDefaults.glow(
        focusedGlow = when (ui.cfg.focus) {
            "glow" -> Glow(if (white) Color.White else ui.accent, 16.dp)
            "lift" -> Glow(ui.accent.copy(alpha = 0.35f), 16.dp) // a faint halo; the shadow does the lifting
            else -> Glow.None
        }
    ),
    ClickableSurfaceDefaults.border(
        focusedBorder = when (ui.cfg.focus) {
            "outline" -> Border(BorderStroke(3.dp, ui.accent), shape = shape)
            "glow" -> Border(BorderStroke(2.dp, TEXT), shape = shape) // a light ring that reads on any banner
            "lift" -> Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = shape)
            else -> Border.None
        }
    ),
)

/** The last D-pad move, for the tilt: -1 or 1 on one axis. */
private object Motion {
    var dx = 0
    var dy = 0
    fun seen(k: Key) {
        when (k) {
            Key.DirectionLeft -> { dx = -1; dy = 0 }
            Key.DirectionRight -> { dx = 1; dy = 0 }
            Key.DirectionUp -> { dx = 0; dy = -1 }
            Key.DirectionDown -> { dx = 0; dy = 1 }
            else -> {}
        }
    }
}

/** Lift: the tile springs up on a soft shadow and, with tilt on, leans toward the move and settles. */
@Composable
private fun Modifier.lift(ui: Ui, shape: Shape, focused: Boolean): Modifier {
    if (ui.cfg.focus != "lift") return this
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, if (ui.motion) spring(dampingRatio = 0.62f, stiffness = 420f) else androidx.compose.animation.core.snap(), label = "lift")
    val lean = remember { Animatable(0f) }
    val leanX = remember { mutableStateOf(true) }
    LaunchedEffect(focused) {
        if (focused && ui.cfg.tilt && ui.motion && (Motion.dx != 0 || Motion.dy != 0)) {
            leanX.value = Motion.dx != 0
            lean.snapTo(if (Motion.dx != 0) Motion.dx * 4f else -Motion.dy * 4f)
            lean.animateTo(0f, tween(220))
        } else lean.snapTo(0f)
    }
    return zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = -(scale - 1f) * 40f
            if (leanX.value) rotationY = lean.value else rotationX = lean.value
            cameraDistance = 12f * density
        }
        .shadow(if (focused) 12.dp else 0.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
}

/** Over a lifted tile: one sweep of light when focus lands, and a slight dim on the tiles around it. */
@Composable
private fun LiftLight(ui: Ui, focused: Boolean) {
    if (ui.cfg.focus != "lift") return
    val p = remember { Animatable(0f) }
    LaunchedEffect(focused) {
        p.snapTo(0f)
        if (focused && ui.motion) { delay(80); p.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
    }
    if (!focused) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
    else if (p.value in 0.001f..0.999f) Box(
        Modifier.fillMaxSize().drawWithContent {
            val w = size.width
            val x = -w * 0.8f + p.value * w * 1.6f
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent, 0.45f to Color.White.copy(alpha = 0.22f), 0.55f to Color.White.copy(alpha = 0.05f), 1f to Color.Transparent,
                    start = Offset(x, 0f), end = Offset(x + w * 0.6f, size.height * 0.4f)
                )
            )
        }
    )
}

/** Keeps the focused tile one step in from the left; the row slides under it. */
@OptIn(ExperimentalFoundationApi::class)
private val PINNED = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - containerSize * 0.12f
}

@Composable
private fun Tile(section: String, app: TvApp, ui: Ui, size: Modifier, big: Boolean) {
    val ctx = LocalContext.current
    val shape = tileShape(ui.shape, big)
    // Mono icons with the Glow focus: the focused tile goes white with a black logo, as the top-bar icons do (#119).
    val inverted = blackOnWhite(ui)
    val (scale, glow, border) = focusStyle(ui, shape, lifted = true, white = inverted)
    var focused by remember { mutableStateOf(false) }
    // With names on focus, the name keeps its room so the rows don't jump.
    val nameAlpha by animateFloatAsState(if (!ui.cfg.nameFocus || focused) 1f else 0f, tween(if (ui.motion) 200 else 0), label = "name")
    val wide = ui.shape == "wide"
    // In the grid and folders each slot keeps its width; a square tile sits in the middle of it.
    val inSlot = section == "grid" || section == "folder"
    val banner = ui.cfg.iconStyle == "banner"
    // Mono glass is see-through, so the Glow focus, and Lift's faint halo, showed inside the tile as a lighter
    // box: a focused one gets a solid back.
    val solid = ui.cfg.iconStyle == "mono" && (ui.cfg.focus == "glow" || ui.cfg.focus == "lift")
    Column(size, horizontalAlignment = if (wide) Alignment.Start else Alignment.CenterHorizontally) {
        Surface(
            onClick = { launch(ctx, app) },
            onLongClick = { ui.onAppMenu(section, app) },
            modifier = Modifier.pointer(onLongClick = { ui.onAppMenu(section, app) }) { launch(ctx, app) }.then(ui.focusFor(section, app)).fillMaxWidth(if (!wide && inSlot) 0.72f else 1f).aspectRatio(if (wide) 16f / 9f else 1f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) ui.onFocusApp(app) }
                .lift(ui, shape, focused),
            shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
            colors = ClickableSurfaceDefaults.colors(containerColor = if (banner) TILE else Color.Transparent, focusedContainerColor = if (banner) TILE_FOCUSED else if (inverted) Color.White else if (solid) Color(ui.cfg.background) else Color.Transparent)
        ) {
            when {
                !banner -> IconTile(app, ui, inverted = inverted && focused)
                app.banner -> Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else -> GeneratedTile(app, big, named = !ui.cfg.showNames)
            }
            LiftLight(ui, focused)
            if (ui.movingKey == "$section:${app.pkg}") Box(Modifier.fillMaxSize().border(3.dp, ui.accent, shape))
            if (Lock.isLocked(ui.cfg, app.pkg)) LockBadge(Modifier.align(if (ui.shape == "round") Alignment.BottomCenter else Alignment.TopEnd))
            if (ui.cfg.newAppsTo.isNotEmpty() && NewApps.isNew(app.installed, System.currentTimeMillis())) NewBadge(Modifier.align(if (ui.shape == "round") Alignment.TopCenter else Alignment.TopStart))
        }
        if (ui.cfg.showNames) {
            Text(app.label, color = TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = if (wide) TextAlign.Start else TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp, start = 2.dp).graphicsLayer { alpha = nameAlpha })
        }
    }
}

/** Opens an app, through the PIN pad when it's locked. */
internal fun launch(ctx: android.content.Context, app: TvApp) = open(ctx, app.pkg, app.launch)

/** Opens [target] in [pkg], through the PIN pad when it's locked; [fallback] when [target] won't start. */
internal fun open(ctx: android.content.Context, pkg: String, target: android.content.Intent, fallback: android.content.Intent? = null) {
    val gate = when {
        Lock.needsPin(ctx, pkg) -> LockActivity.intent(ctx, pkg, target)
        Limits.over(ctx, pkg) -> LockActivity.intent(ctx, pkg, target, limit = true)
        else -> null
    }
    runCatching { ctx.startActivity(gate ?: target); Recent.note(ctx, pkg) }.onFailure { e ->
        Log.w(TAG, "launch $pkg: $e")
        if (gate == null && fallback != null) runCatching { ctx.startActivity(fallback); Recent.note(ctx, pkg) }.onFailure { Log.w(TAG, "launch $pkg: $it") }
    }
}

// ---- Continue watching ----------------------------------------------------------------------------

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WatchRow(home: Home, ui: Ui, w: Dp, rowSpec: BringIntoViewSpec, pad: PaddingValues) {
    // Something new goes in at the start; the row keeps its place by item, so it would land out of view to
    // the left. Back to the start, unless the owner is in the row.
    val state = rememberLazyListState()
    var inside by remember { mutableStateOf(false) }
    val newest = home.watch.firstOrNull()?.key
    LaunchedEffect(newest) { if (!inside) state.scrollToItem(0) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides if (ui.cfg.pinFocus) PINNED else rowSpec) {
        LazyRow(Modifier.onFocusChanged { inside = it.hasFocus }, state = state, horizontalArrangement = Arrangement.spacedBy(GAP), contentPadding = pad) {
            items(home.watch, key = { it.key }) { item -> WatchTile(item, home.app(item.pkg), ui, w) }
        }
    }
}

/**
 * A film or episode the owner is part way through: its picture, how far they got, its name and episode. OK
 * goes back to it in its app, or opens the app when the app's link doesn't work. A locked app asks for the
 * PIN first, as its tile does.
 */
@Composable
private fun WatchTile(item: WatchItem, app: TvApp?, ui: Ui, w: Dp) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(10.dp)
    val (scale, glow, border) = focusStyle(ui, shape, lifted = true)
    var focused by remember { mutableStateOf(false) }
    // Decoded at the tile's own size: an app's poster can be 2000 pixels wide.
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { w.roundToPx() }
    // The tile stays when an app gives the same film a new picture, and so does this state: start it over.
    val picture by androidx.compose.runtime.produceState(item.image?.let { WatchNext.cached(it) }, item.image, px) {
        value = item.image?.let { WatchNext.cached(it) }
        if (value == null) value = item.image?.let { WatchNext.picture(ctx, it, px) }
    }
    val go: () -> Unit = { (WatchNext.intent(ctx, item) ?: app?.launch)?.let { open(ctx, item.pkg, it, app?.launch) } }
    // Focus only brings the picture into view, which can leave the name and episode under the bottom edge
    // (#147). The whole tile is asked for too, so Down from the dock always shows them.
    val whole = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    Column(Modifier.width(w).bringIntoViewRequester(whole)) {
        Surface(
            onClick = go,
            onLongClick = { ui.onWatchMenu(item) },
            modifier = Modifier.pointer(onLongClick = { ui.onWatchMenu(item) }) { go() }.fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) { app?.let(ui.onFocusApp); scope.launch { whole.bringIntoView() } } }
                .lift(ui, shape, focused),
            shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
            colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = TILE_FOCUSED)
        ) {
            val p = picture
            when {
                p != null -> Image(p, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app?.banner == true -> Image(app.art, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app != null -> GeneratedTile(app, big = false, named = false)
            }
            item.progress?.let { done ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).height(4.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(ui.accent, RoundedCornerShape(2.dp)))
                }
            }
            LiftLight(ui, focused)
            if (Lock.isLocked(ui.cfg, item.pkg)) LockBadge(Modifier.align(Alignment.TopEnd))
        }
        Text(item.title, color = TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, start = 2.dp))
        Text(WatchNext.episodeLine(item) ?: app?.label.orEmpty(), color = MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp, start = 2.dp))
    }
}

/** Rounded rectangle, rounded square, squircle (a superellipse, like Samsung's and Belle UI's) or circle. */
private fun tileShape(kind: String, big: Boolean): Shape = when (kind) {
    "square" -> RoundedCornerShape(22)
    "squircle" -> SQUIRCLE
    "round" -> CircleShape
    else -> RoundedCornerShape(if (big) 12.dp else 10.dp)
}

private val SQUIRCLE = GenericShape { size, _ ->
    val cx = size.width / 2; val cy = size.height / 2
    for (i in 0..96) {
        val t = i / 96.0 * Math.PI * 2
        val c = kotlin.math.cos(t); val s = kotlin.math.sin(t)
        val x = cx + cx * Math.signum(c) * kotlin.math.sqrt(kotlin.math.abs(c))
        val y = cy + cy * Math.signum(s) * kotlin.math.sqrt(kotlin.math.abs(s))
        if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
    }
    close()
}

/** "New" on an app installed in the last day, while new apps have somewhere to go. Dark like the lock. */
@Composable
private fun NewBadge(modifier: Modifier) {
    Text(tr("New"), color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = modifier.padding(6.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** The small lock in a locked app's corner. */
@Composable
private fun LockBadge(modifier: Modifier) {
    Box(modifier.padding(6.dp).size(20.dp).background(Color.Black.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
        Image(Icons.Outlined.Lock, tr("Locked"), Modifier.size(12.dp), colorFilter = ColorFilter.tint(TEXT))
    }
}

/**
 * An app in one of the icon styles: its logo on a tile, every app drawn the same way.
 * Mono: the logo in one colour from the accent, on glass. Colour: white on the brand's own colour (or
 * the logo's own colours on a dark tile). Duotone: two shades of the accent on the theme's colours.
 * An app with no usable logo gets its first letter, in the same style.
 */
@Composable
private fun IconTile(app: TvApp, ui: Ui, inverted: Boolean = false) {
    val accent = ui.accent
    val ground = Color(ui.cfg.background)
    // A picture icon has no logo to recolour: Mono and Duotone use the letter instead.
    val g = app.glyph?.takeIf { !it.picture || ui.cfg.iconStyle == "colour" }
    val ink = lerp(accent, Color.White, 0.35f)
    val back: Brush; val filter: ColorFilter?; val letterInk: Color
    when (ui.cfg.iconStyle) {
        "mono" -> if (inverted) {
            back = SolidColor(Color.White); filter = ColorFilter.tint(Color.Black); letterInk = Color.Black
        } else {
            val glass = ui.cfg.tileGlass / 100f
            back = Brush.linearGradient(listOf(Color.White.copy(alpha = glass * 1.3f), Color.White.copy(alpha = glass * 0.7f))); filter = ColorFilter.tint(ink); letterInk = ink
        }
        "duo" -> {
            back = Brush.linearGradient(listOf(lerp(ground, accent, 0.3f), lerp(ground, Color.Black, 0.2f)))
            filter = ColorFilter.colorMatrix(IconArt.duotone(lerp(accent, Color.Black, 0.25f), lerp(accent, Color.White, 0.55f))); letterInk = accent
        }
        else -> {
            val plate = g?.plate?.let { Color(it) } ?: if (g == null) lerp(accent, Color.Black, 0.35f) else null
            back = if (plate != null) Brush.linearGradient(listOf(lerp(plate, Color.White, 0.08f), lerp(plate, Color.Black, 0.25f)))
                   else Brush.linearGradient(listOf(lerp(ground, Color.White, 0.12f), lerp(ground, Color.White, 0.06f)))
            filter = if (plate != null) ColorFilter.tint(Color.White) else null; letterInk = Color.White
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(back), contentAlignment = Alignment.Center) {
        val wide = ui.shape == "wide"
        if (g != null && g.picture && ui.cfg.iconStyle == "colour") {
            Image(g.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (g != null && !g.picture) {
            val art = if (ui.cfg.iconStyle == "mono") g.mono ?: g.art else g.art
            Image(art, app.label, Modifier.size(width = maxWidth * 0.62f, height = maxHeight * (if (wide) 0.48f else 0.46f)), contentScale = ContentScale.Fit, colorFilter = filter)
        } else {
            Text(app.label.take(1).uppercase(), color = letterInk, fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (minOf(maxWidth, maxHeight) * 0.42f).toSp() }, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * A tile for an app with no TV banner, like most sideloaded apps (#4): the icon on a gradient of its own
 * colour. With names off it reads like a banner, icon on the left and the name beside it.
 */
@Composable
private fun GeneratedTile(app: TvApp, big: Boolean, named: Boolean) {
    val base = app.tint?.let { Color(it) }
    val fill = if (base == null) Modifier else Modifier.background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.12f), lerp(base, Color.Black, 0.35f))))
    BoxWithConstraints(Modifier.fillMaxSize().then(fill), contentAlignment = Alignment.Center) {
        // Grid tiles are ~120 dp wide: a name beside the icon would break mid-word, so it goes underneath.
        val wide = maxWidth >= 170.dp
        val icon = if (wide) (if (big) 52.dp else 44.dp) else 32.dp
        when {
            !named -> Image(app.art, null, Modifier.size(icon))
            wide -> Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(app.art, null, Modifier.size(icon))
                Spacer(Modifier.width(12.dp))
                Text(app.label, color = TEXT, fontSize = if (big) 17.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            }
            else -> Column(Modifier.padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(app.art, null, Modifier.size(icon))
                Spacer(Modifier.height(5.dp))
                Text(app.label, color = TEXT, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A folder: a 2x2 peek at its apps, the name underneath. OK opens it over the home screen. */
@Composable
private fun FolderTile(folder: Pair<String, List<TvApp>>, ui: Ui, size: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val (scale, glow, border) = focusStyle(ui, shape, lifted = true)
    var focused by remember { mutableStateOf(false) }
    // The accent mixed into the background, a quarter and then two fifths, so folders read as the theme's own.
    val ground = Color(ui.cfg.background)
    Column(size) {
        Surface(
            onClick = { ui.onFolder(folder) },
            modifier = Modifier.pointer { ui.onFolder(folder) }.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused }.lift(ui, shape, focused),
            shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
            colors = ClickableSurfaceDefaults.colors(containerColor = lerp(ground, ui.accent, 0.25f).copy(alpha = 0.9f), focusedContainerColor = lerp(ground, ui.accent, 0.4f))
        ) {
            Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                folder.second.take(4).chunked(2).forEach { pair ->
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        pair.forEach { app ->
                            Image(app.art, null, Modifier.weight(1f).fillMaxHeight().background(app.tint?.let { Color(it) } ?: Color.Black.copy(alpha = 0.25f), RoundedCornerShape(5.dp)), contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit)
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            LiftLight(ui, focused)
        }
        Text("${folder.first} · ${folder.second.size}", color = TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, start = 2.dp))
    }
}

/** Screen time on the Home screen: today, the week's bars and today's top apps. OK opens the dashboard. */
@Composable
private fun ScreenTimeCard(ui: Ui) {
    val ctx = LocalContext.current
    val c = remember(ui.cfg.stBg, ui.cfg.stOpacity, ui.cfg.stText, ui.cfg.stBars, ui.cfg.accent, ui.cfg.layout) { cardColours(ui.cfg) }
    val bau = ui.cfg.layout == "bau"
    val shape = RoundedCornerShape(if (bau) 0.dp else 14.dp)
    // Bau marks focus its own way, in cream, with no accent glow.
    val (scale, glow, border) = if (bau) Triple(ClickableSurfaceDefaults.scale(focusedScale = 1f), ClickableSurfaceDefaults.glow(focusedGlow = Glow.None),
        ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, BAU_CREAM), shape = shape))) else focusStyle(ui, shape, zoom = 1.03f, lifted = true)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { ScreenTimeApps.open(ctx) },
        modifier = Modifier.pointer { ScreenTimeApps.open(ctx) }.padding(horizontal = BLEED, vertical = 12.dp).width(600.dp).height(124.dp)
            .onFocusChanged { focused = it.isFocused }.then(if (bau) Modifier else Modifier.lift(ui, shape, focused)),
        shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
        // Focused, the accent (Bau: a little white) mixes in and the card firms up, so it still shows on a see-through one.
        colors = ClickableSurfaceDefaults.colors(containerColor = c.bg, focusedContainerColor = lerp(c.bg.copy(alpha = 1f), if (bau) Color.White else ui.accent, if (bau) 0.08f else 0.22f).copy(alpha = maxOf(c.bg.alpha, 0.6f))),
    ) {
        ScreenTimeCardBody(ui.screenTime, c)
        if (!bau) LiftLight(ui, focused)
    }
}

/** What another app is playing, with play/pause and next. Only there while a session has something loaded. */
@Composable
private fun NowPlayingCard(t: Track, ui: Ui) {
    Row(
        Modifier.padding(horizontal = BLEED, vertical = 12.dp).width(520.dp).background(PANEL.copy(alpha = 0.88f), RoundedCornerShape(14.dp)).padding(12.dp).focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).background(TILE, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (t.art != null) Image(t.art, null, Modifier.fillMaxSize().background(Color.Black, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            else Image(Icons.Outlined.MusicNote, null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(MUTED))
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(t.title, color = TEXT, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(t.artist, t.app).filter { it.isNotBlank() }.joinToString(" · "), color = MUTED, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundButton(if (t.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (t.playing) tr("Pause") else tr("Play"), ui) { NowPlaying.playPause() }
            RoundButton(Icons.Outlined.SkipNext, tr("Next"), ui) { NowPlaying.next() }
        }
    }
}

// ---- Top bar pieces -------------------------------------------------------------------------------

/** Icon, temperature, today's high and low, and the place, in one muted line. MET Norway's data asks for its name. */
@Composable
private fun WeatherLine(ui: Ui, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val w = ui.weather ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(weatherIcon(w.code, w.day), null, Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 1.3f }), colorFilter = ColorFilter.tint(MUTED))
        Spacer(Modifier.width(6.dp))
        Text("${w.temp}°  ${w.high}° / ${w.low}°  ${ui.cfg.weatherPlace}${if (w.metNo) " · MET Norway" else ""}", color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The next few hours, when the owner turns them on: each hour's time over its sky and temperature, in the
 * weather line's quiet style and size, so it reads from the sofa without crowding the clock.
 */
@Composable
private fun HourLine(ui: Ui, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val hours = ui.weather?.hours?.takeIf { ui.cfg.weatherHours && it.isNotEmpty() } ?: return
    val ctx = LocalContext.current
    // "21:00" or "9 PM", as the TV keeps its clock.
    val format = remember(ctx) {
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(ctx)) "Hm" else "ha"
        java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), skeleton), java.util.Locale.getDefault())
    }
    val icon = with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 1.2f }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        hours.forEach { h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format.format(Date.from(h.at)), color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(weatherIcon(h.code, h.day), null, Modifier.size(icon), colorFilter = ColorFilter.tint(MUTED))
                    Spacer(Modifier.width(4.dp))
                    Text("${h.temp}°", color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
                }
            }
        }
    }
}

/** The next few days, when the owner turns them on (#143): each day's short name over its sky, high and low. */
@Composable
private fun DayLine(ui: Ui, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val days = ui.weather?.days?.takeIf { ui.cfg.weatherDays && it.isNotEmpty() } ?: return
    val format = remember { java.time.format.DateTimeFormatter.ofPattern("EEE", java.util.Locale.getDefault()) }
    val icon = with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 1.2f }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        days.forEach { d ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.date.format(format), color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(weatherIcon(d.code, true), null, Modifier.size(icon), colorFilter = ColorFilter.tint(MUTED))
                    Spacer(Modifier.width(4.dp))
                    Text("${d.high}°/${d.low}°", color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
                }
            }
        }
    }
}

/** Free memory, in the weather's quiet style, and when the sleep timer turns the TV off. */
@Composable
private fun MemoryLine(ui: Ui, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val sleepAt by SleepTimer.at.collectAsState()
    val m = ui.memory
    if (m == null && sleepAt == null) return
    val icon = with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 1.3f }
    Column(modifier) {
        m?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(Icons.Outlined.Memory, null, Modifier.size(icon), colorFilter = ColorFilter.tint(MUTED))
                Spacer(Modifier.width(6.dp))
                Text(Memory.line(it), color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
            }
        }
        sleepAt?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(Icons.Outlined.Timer, null, Modifier.size(icon), colorFilter = ColorFilter.tint(MUTED))
                Spacer(Modifier.width(6.dp))
                Text(tr("Sleeps at {time}", "time" to DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))), color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1)
            }
        }
    }
}

/** WMO weather codes, as Open-Meteo reports them, to a drawn icon. */
private fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0 -> if (day) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    1, 2 -> if (day) Icons.Outlined.WbCloudy else Icons.Outlined.NightsStay
    3 -> Icons.Outlined.Cloud
    45, 48 -> Icons.Outlined.Dehaze
    in 71..77, 85, 86 -> Icons.Outlined.AcUnit
    in 95..99 -> Icons.Outlined.Thunderstorm
    in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
    else -> Icons.Outlined.Cloud
}

/**
 * Mono icons with the Glow focus: a focused round button, or app tile, turns white with a black icon, on a
 * white glow (#119). With anything else the accent fills a button, as before.
 */
private fun blackOnWhite(ui: Ui) = ui.cfg.iconStyle == "mono" && ui.cfg.focus == "glow"
private fun buttonFill(ui: Ui) = if (blackOnWhite(ui)) Color.White else ui.accent.copy(alpha = 0.9f)
private fun buttonInk(ui: Ui) = if (blackOnWhite(ui)) Color.Black else onAccent(ui.accent)

@Composable
private fun RoundButton(icon: ImageVector, label: String, ui: Ui, onClick: () -> Unit) {
    val (scale, glow, border) = focusStyle(ui, RoundedCornerShape(50), zoom = 1.12f, white = blackOnWhite(ui))
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.pointer(onClick = onClick).size(44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = scale, glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = buttonFill(ui))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(icon, label, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (focused) buttonInk(ui) else TEXT))
        }
    }
}

@Composable
private fun QuickButton(a: QuickAction, ui: Ui, onQuick: (QuickAction) -> Unit) {
    val (scale, glow, border) = focusStyle(ui, RoundedCornerShape(50), zoom = 1.12f, white = blackOnWhite(ui))
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onQuick(a) },
        modifier = Modifier.pointer { onQuick(a) }.size(40.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = scale, glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = buttonFill(ui))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            QuickGlyph(a, ui, focused, 22.dp)
        }
    }
}

/**
 * The icon inside a quick button: the drawn glyph, or an app's own icon at the glyph's size plus the room
 * a glyph leaves inside its box, so app icons sit on the same centre line and look the same size.
 */
@Composable
private fun QuickGlyph(a: QuickAction, ui: Ui, focused: Boolean, size: Dp, ink: Color? = null) {
    val art = a.art
    if (art != null) Image(art, a.label, Modifier.size(size + 4.dp).clip(CircleShape))
    else Image(iconFor(a, ui.cfg), a.label, Modifier.size(size), colorFilter = ColorFilter.tint(ink ?: if (focused) buttonInk(ui) else TEXT))
}

@Composable
private fun QuickRow(a: QuickAction, ui: Ui, onQuick: (QuickAction) -> Unit, expanded: Boolean) =
    RailItem(iconFor(a, ui.cfg), a.label, ui, expanded, glyph = { focused -> QuickGlyph(a, ui, focused, 22.dp) }) { onQuick(a) }

@Composable
private fun RailItem(icon: ImageVector, label: String, ui: Ui, expanded: Boolean, glyph: (@Composable (Boolean) -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val (_, glow, border) = focusStyle(ui, shape, white = blackOnWhite(ui))
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) buttonInk(ui) else TEXT
    Surface(
        onClick = onClick,
        modifier = Modifier.pointer(onClick = onClick).height(44.dp).then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(44.dp)).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f), glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = buttonFill(ui))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            // A fixed 22 dp slot, so labels line up whether the icon is a glyph or an app's.
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (glyph != null) glyph(focused) else Image(icon, label, Modifier.size(22.dp), colorFilter = ColorFilter.tint(ink))
            }
            if (expanded) {
                Spacer(Modifier.width(14.dp))
                Text(label, color = ink, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Greeting(cfg: HomeConfig, size: androidx.compose.ui.unit.TextUnit) {
    if (cfg.greeting.isBlank()) return
    // Read again each minute: Home can stay open from morning into the evening.
    var hour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    LaunchedEffect(Unit) { while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) } }
    val part = when (hour) { in 5..11 -> tr("Good morning"); in 12..17 -> tr("Good afternoon"); else -> tr("Good evening") }
    // The name in a light shade of the accent, light enough to read on any wallpaper.
    val name = lerp(Color(cfg.accent), Color.White, 0.45f)
    Text(buildAnnotatedString { append("$part, "); withStyle(SpanStyle(color = name)) { append(cfg.greeting) } }, color = TEXT, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 2)
}

@Composable
private fun Clock(cfg: HomeConfig, timeSize: androidx.compose.ui.unit.TextUnit, dateSize: androidx.compose.ui.unit.TextUnit, align: Alignment.Horizontal) {
    if (cfg.clock == "off") return
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    Column(horizontalAlignment = align) {
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = TEXT, fontSize = timeSize, fontFamily = CLOCK, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
        if (cfg.clock == "time_date") Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = MUTED, fontSize = dateSize, fontFamily = CLOCK)
    }
}

/**
 * The idle screen, in the owner's style: a large clock and the weather in the lower left (corner), a bigger
 * one in the middle (center), or the slideshow's photos, or the themes' when there are none, with the clock
 * over them (photos). Everything stays inside the safe area.
 */
@Composable
private fun IdleScreen(ui: Ui, modifier: Modifier) {
    Box(modifier) {
        when (ui.cfg.idleStyle) {
            "center" -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Clock(ui.cfg, 120.sp, 22.sp, Alignment.CenterHorizontally)
                WeatherLine(ui, 18.sp, Modifier.padding(top = 12.dp))
                HourLine(ui, 18.sp, Modifier.padding(top = 12.dp))
                DayLine(ui, 18.sp, Modifier.padding(top = 12.dp))
            }
            else -> {
                if (ui.cfg.idleStyle == "photos") IdlePhotos()
                Column(Modifier.align(Alignment.BottomStart).padding(start = SAFE_X, bottom = SAFE_Y + 12.dp)) {
                    Clock(ui.cfg, 64.sp, 18.sp, Alignment.Start)
                    WeatherLine(ui, 16.sp, Modifier.padding(top = 8.dp))
                    HourLine(ui, 16.sp, Modifier.padding(top = 10.dp))
                    DayLine(ui, 16.sp, Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/** A new photo every 20 seconds, fading over the last, with a shade so the clock reads on any of them. */
@Composable
private fun IdlePhotos() {
    val ctx = LocalContext.current
    val files = remember {
        (0 until Slides.count(ctx)).map { Slides.file(ctx, it) }.ifEmpty {
            ThemePack.list(ctx).flatMap { t -> (0 until t.pictures).map { ThemePack.picture(ctx, t.id, it) } }
        }
    }
    if (files.isEmpty()) return
    var at by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            photo = withContext(Dispatchers.IO) { Wallpapers.screenPicture(ctx, files[at % files.size].path) }
            delay(20_000L)
            at++
        }
    }
    androidx.compose.animation.Crossfade(photo, animationSpec = tween(1500), label = "idle photo") { p ->
        if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
}

/**
 * The Slideshow screensaver (SlidesDream): the idle screen's photos with the time, the date and what's playing.
 * The words move a few dp each minute so they don't stay on the same pixels all night.
 */
@Composable
internal fun SlideshowSaver() {
    val track by NowPlaying.track.collectAsState()
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    val minute = (now.time / 60_000L).toInt()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        IdlePhotos()
        Column(Modifier.align(Alignment.BottomStart).padding(start = SAFE_X + (minute % 5 * 4).dp, bottom = SAFE_Y + 12.dp + (minute % 3 * 4).dp)) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = TEXT, fontSize = 64.sp, fontFamily = CLOCK, fontWeight = FontWeight.Light, maxLines = 1, softWrap = false)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = MUTED, fontSize = 18.sp, fontFamily = CLOCK)
            track?.takeIf { it.playing }?.let { t ->
                Text(listOf(t.title, t.artist).filter { it.isNotBlank() }.joinToString(" · "), color = MUTED, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp).widthIn(max = 560.dp))
            }
        }
    }
}

/** The sleep timer's choices, from the top bar icon. */
@Composable
private fun TimerMenu(ui: Ui, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    val sleepAt by SleepTimer.at.collectAsState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scrim(ui.cfg, 0.85f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(360.dp).background(PANEL, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Sleep timer"), color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(sleepAt?.let { tr("The TV sleeps at {time}", "time" to DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) } ?: tr("The TV goes to sleep after"), color = MUTED, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                if (KeyService.sleepsByScreensaver) Text(tr("On Android 8 Home starts the screensaver instead of turning the TV off."), color = MUTED, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                val items = SleepTimer.CHOICES.map { m -> Limits.words(m) to { SleepTimer.set(ctx, m) } } + (if (sleepAt != null) listOf(tr("Off") to { SleepTimer.set(ctx, null) }) else emptyList())
                items.forEachIndexed { i, (label, act) ->
                    val shape = RoundedCornerShape(12.dp)
                    val (scale, glow, border) = focusStyle(ui, shape, zoom = 1.03f)
                    Surface(
                        onClick = { act(); onClose() },
                        modifier = Modifier.pointer { act(); onClose() }.then(if (i == 0) Modifier.focusRequester(first) else Modifier).fillMaxWidth().height(50.dp),
                        shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
                        colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = menuFocus(ui))
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) { Text(label, color = TEXT, fontSize = 17.sp) }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

// ---- Overlays -------------------------------------------------------------------------------------

/** A folder's apps over a dimmed home screen. Back closes it. */
@Composable
private fun FolderOverlay(name: String, list: List<TvApp>, ui: Ui, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scrim(ui.cfg, 0.9f)), contentAlignment = Alignment.Center) {
            Column(Modifier.background(PANEL, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(name, color = TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = BLEED, bottom = 6.dp))
                val inner = Ui(ui.cfg, ui.accent, focus, list.firstOrNull()?.let { "folder:${it.pkg}" }, ui.onFolder, motion = ui.motion, onAppMenu = ui.onAppMenu)
                list.chunked(5).forEach { row ->
                    Row(Modifier.padding(horizontal = BLEED, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        for (app in row) Tile("folder", app, inner, Modifier.width(148.dp), big = false)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

/** An app being moved with the arrow keys: "main" for the dock and All apps, or "row<i>". */
internal data class Moving(val kind: String, val pkg: String, val label: String)

private val DONE_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape)
internal val OK_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * What a long press on a tile offers (Discord request): Move, Hide, App info (where Android keeps Force stop
 * and Clear data) and, for apps the owner installed, Uninstall. Android lets only system apps force-stop
 * another app, so that one goes through App info.
 */
@Composable
private fun AppMenu(app: TvApp, ui: Ui, onMove: (() -> Unit)?, toDock: Pair<String, () -> Unit>? = null, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    val installed = remember(app.pkg) {
        runCatching { ctx.packageManager.getApplicationInfo(app.pkg, 0).flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 }.getOrDefault(false)
    }
    fun open(i: android.content.Intent) {
        onClose()
        runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "${app.pkg}: $it") }
    }
    val pkgUri = android.net.Uri.fromParts("package", app.pkg, null)
    val items = buildList<Pair<String, () -> Unit>> {
        onMove?.let { add(tr("Move") to it) }
        toDock?.let(::add)
        add(tr("Hide from Home") to { onClose(); ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray((ui.cfg.hidden + app.pkg).distinct()))) })
        add(tr("App info") to { open(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)) })
        if (installed) add(tr("Uninstall") to { open(android.content.Intent(android.content.Intent.ACTION_DELETE, pkgUri)) })
    }
    // The menu opens while OK is still held: that press's repeats and its release would pick Move. Only a
    // press that starts inside the menu counts.
    var fresh by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(when (ui.cfg.layout) { "glass" -> Color.Black.copy(alpha = 0.5f); "bau" -> BAU_INK.copy(alpha = 0.85f); else -> scrim(ui.cfg, 0.85f) }).onPreviewKeyEvent { e ->
                when {
                    e.key !in OK_KEYS -> false
                    e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { fresh = true; false }
                    else -> !fresh
                }
            },
            contentAlignment = Alignment.Center
        ) {
            if (ui.cfg.layout == "teletext") TextMenu(tr("Menu"), app.label, items, focus, onClose)
            else if (ui.cfg.layout == "glass") GlassMenu(app.label, null, items, focus)
            else if (ui.cfg.layout == "bau") BauMenu(app.label, items, focus)
            else Column(Modifier.width(360.dp).background(PANEL, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(app.label, color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp))
                items.forEachIndexed { i, (label, act) ->
                    val shape = RoundedCornerShape(12.dp)
                    val (scale, glow, border) = focusStyle(ui, shape, zoom = 1.03f)
                    Surface(
                        onClick = act,
                        modifier = Modifier.pointer { act() }.then(if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth().height(50.dp),
                        shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
                        colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = menuFocus(ui))
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                            Text(label, color = TEXT, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

@Composable
private fun InputsOverlay(inputs: List<Pair<String, String>>, ui: Ui, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        if (ui.cfg.layout == "teletext") TextMenu(tr("Inputs"), ui.cfg.names["inputs"] ?: tr("Inputs"), inputs.map { (id, label) -> label to { onClose(); openInput(ctx, id) } }, focus, onClose)
        else if (ui.cfg.layout == "glass") Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
            GlassMenu(ui.cfg.names["inputs"] ?: tr("Inputs"), null, inputs.map { (id, label) -> label to { onClose(); openInput(ctx, id) } }, focus)
        }
        else Box(Modifier.fillMaxSize().background(scrim(ui.cfg, 0.9f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(400.dp).background(PANEL, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ui.cfg.names["inputs"] ?: tr("Inputs"), color = TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                inputs.forEachIndexed { i, (id, label) ->
                    val shape = RoundedCornerShape(12.dp)
                    val (scale, glow, border) = focusStyle(ui, shape, zoom = 1.03f)
                    Surface(
                        onClick = { onClose(); openInput(ctx, id) },
                        modifier = Modifier.pointer { onClose(); openInput(ctx, id) }.then(if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth().height(52.dp),
                        shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
                        colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = menuFocus(ui))
                    ) {
                        Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                            Text(label, color = TEXT, fontSize = 17.sp)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
