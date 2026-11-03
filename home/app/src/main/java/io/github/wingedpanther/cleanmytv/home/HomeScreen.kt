package io.github.wingedpanther.cleanmytv.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

// CleanMyTV's dark world: warm off-white and sand text on the green-black ground.
private val TEXT = Color(0xFFF0EBE5)
private val MUTED = Color(0xFFD7CFC4)
private val TILE = Color(0xE62D3A2E)
private val TILE_FOCUSED = Color(0xFF3A4A3B)
private val GROUND = Color(0xFF1C2A1F)
private val PANEL = Color(0xFF2D3A2E)

// The desktop app's face, for the clock and date: tabular figures that don't jitter as the minutes turn.
private val CLOCK = FontFamily(Font(R.font.jetbrains_mono_light, FontWeight.Light), Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

/** Text on an accent fill: the ground colour or off-white, whichever reads better on it. */
private fun onAccent(c: Color): Color = if (c.luminance() > 0.36f) GROUND else TEXT

/** Shades a ground-tinted scrim, so overlays keep the world's green-black instead of plain black. */
private fun scrim(alpha: Float) = GROUND.copy(alpha = alpha)

// A 1080p TV is 960x540 dp; 5% of each edge stays clear because some TVs crop it.
private val SAFE_X = 48.dp
private val SAFE_Y = 27.dp
private val GAP = 16.dp
// Room on each side of a row so a focused tile's glow, shadow and zoom aren't clipped.
private val BLEED = 22.dp

/** Everything the layouts draw, worked out once from the apps on the TV and the settings. */
private class Home(apps: List<TvApp>, val cfg: HomeConfig) {
    private val byPkg = apps.associateBy { it.pkg }
    val shown = arrange(apps, cfg)
    private val inFolders = cfg.folders.flatMap { it.apps }.toSet()
    val dock = shown.filter { it.pkg !in inFolders }.take(cfg.dockSize)
    val grid = shown.filter { it.pkg !in inFolders && it !in dock }
    val rows = cfg.rows.map { g -> g.name to g.apps.mapNotNull { byPkg[it] }.filter { it.pkg !in cfg.hidden } }.filter { it.second.isNotEmpty() }
    val folders = cfg.folders.map { g -> g.name to g.apps.mapNotNull { byPkg[it] } }.filter { it.second.isNotEmpty() }
    /** The tile that takes focus when Home opens, as "<section>:<package>" (an app can sit in two sections). */
    val first: String? = cfg.sections.firstNotNullOfOrNull { s ->
        when (s) {
            "dock" -> dock.firstOrNull()?.let { "dock:${it.pkg}" }
            "rows" -> rows.firstOrNull()?.second?.firstOrNull()?.let { "row0:${it.pkg}" }
            "grid" -> grid.firstOrNull()?.let { "grid:${it.pkg}" }
            else -> null
        }
    }
}

@Composable
fun HomeScreen(visualiser: Visualiser, appsVersion: Int, open: androidx.compose.runtime.MutableState<String?>) {
    val ctx = LocalContext.current
    val cfg by ConfigStore.config.collectAsState()
    val wallVersion by ConfigStore.wallpaperVersion.collectAsState()
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(appsVersion) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx.packageManager, ctx.packageName) }
        inputs = withContext(Dispatchers.Default) { tvInputs(ctx) }
    }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallVersion) {
        wallpaper = withContext(Dispatchers.IO) {
            val f = ConfigStore.wallpaper(ctx)
            if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
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

    val home = Home(apps, cfg)
    val accent = Color(cfg.accent)
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
    val byPkg = apps.associateBy { it.pkg }
    val quick = cfg.quick.mapNotNull { id ->
        HomeConfig.quickApp(id)?.let { p -> byPkg[p]?.let { appAction(it, quickIcons[p]) } } ?: all[id]
    }.filter { canOpen(ctx, it, inputs.isNotEmpty()) }
    var openFolder by remember { mutableStateOf<Pair<String, List<TvApp>>?>(null) }
    var showInputs by remember { mutableStateOf(false) }
    LaunchedEffect(lastPress, cfg.idleMinutes, openFolder, showInputs) {
        if (cfg.idleMinutes == 0 || openFolder != null || showInputs) return@LaunchedEffect
        delay(cfg.idleMinutes * 60_000L)
        idle = true
    }
    val shown by animateFloatAsState(if (idle) 0f else 1f, tween(if (idle) 1200 else 250), label = "idle")
    LaunchedEffect(open.value) {
        if (open.value == "inputs") showInputs = true
        open.value = null
    }
    val onQuick: (QuickAction) -> Unit = { a ->
        if (a.id == "inputs") showInputs = true
        else if (a.id == "music") ConfigStore.update(ctx, org.json.JSONObject().put("visualiser", !cfg.visualiser))
        else resolve(ctx, a)?.let { i -> runCatching { ctx.startActivity(i) }.onFailure { Log.w(TAG, "${a.id}: $it") } }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(home.first, cfg.layout) { if (home.first != null) runCatching { first.requestFocus() } }
    var focusColour by remember { mutableStateOf<Color?>(null) }
    val tint by animateColorAsState(focusColour ?: Color.Transparent, tween(600), label = "tint")
    val ui = Ui(cfg, accent, first, home.first, onFolder = { openFolder = it }, track = track, weather = weather,
        onFocusApp = { a -> focusColour = a.colour?.let { Color(it) } })

    Box(
        Modifier.fillMaxSize().background(Color(cfg.background)).onPreviewKeyEvent { e ->
            lastPress = System.currentTimeMillis()
            if (e.type == KeyEventType.KeyDown) Motion.seen(e.key)
            when {
                idle && e.type == KeyEventType.KeyDown -> { idle = false; swallowUp = true; true }
                swallowUp && e.type == KeyEventType.KeyUp -> { swallowUp = false; true }
                else -> false
            }
        }
    ) {
        wallpaper?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cfg.wallpaperDim / 100f)))
        }
        // The focused app's colour washes in from the top; the wallpaper still shows through.
        if (cfg.followColour) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = tint.alpha * 0.5f), Color.Transparent))))
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
            // Rows sit over the lower half of the photo; a ground-tinted shade keeps their names readable.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to scrim(0.8f))))
            when (cfg.layout) {
                "split" -> SplitLayout(home, ui, quick, onQuick)
                "rail" -> RailLayout(home, ui, quick, onQuick)
                else -> ShelfLayout(home, ui, quick, onQuick)
            }
            if (cfg.visualiser) Bars(visualiser, cfg, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
        if (shown < 1f) IdleClock(ui, Modifier.align(Alignment.BottomStart).graphicsLayer { alpha = 1f - shown })
    }

    openFolder?.let { (name, list) -> FolderOverlay(name, list, ui) { openFolder = null } }
    if (showInputs) InputsOverlay(inputs, ui) { showInputs = false }
}

/** What every tile and section needs to know. */
private class Ui(
    val cfg: HomeConfig,
    val accent: Color,
    val focus: FocusRequester,
    val focusKey: String?,
    val onFolder: (Pair<String, List<TvApp>>) -> Unit,
    val track: Track? = null,
    val weather: WeatherNow? = null,
    val onFocusApp: (TvApp) -> Unit = {},
) {
    fun focusFor(section: String, app: TvApp): Modifier = if ("$section:${app.pkg}" == focusKey) Modifier.focusRequester(focus) else Modifier
}

// ---- Layouts --------------------------------------------------------------------------------------

/** Shelf: a slim top bar, open wallpaper above the apps, the sections below. */
@Composable
private fun ShelfLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit) {
    val cfg = ui.cfg
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(scrim(0.6f), Color.Transparent), endY = 420f)))
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
    Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(scrim(0.7f), Color.Transparent), endX = 900f)))
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(0.32f).fillMaxHeight().padding(start = SAFE_X, top = SAFE_Y + 24.dp, bottom = SAFE_Y + 48.dp)) {
            // 46 sp keeps "12:51 PM" on one line in the left third; 12-hour time is the widest.
            Clock(cfg, 46.sp, 15.sp, Alignment.Start)
            WeatherLine(ui, 15.sp, Modifier.padding(top = 6.dp))
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
                .background(Brush.horizontalGradient(listOf(scrim(if (railFocused) 0.92f else 0.6f), scrim(if (railFocused) 0.75f else 0.1f))))
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
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = SAFE_X, top = SAFE_Y), verticalAlignment = Alignment.CenterVertically) {
                Greeting(cfg, 20.sp)
                Spacer(Modifier.weight(1f))
                WeatherLine(ui, 13.sp, Modifier.padding(end = 18.dp))
                Clock(cfg, 26.sp, 12.sp, Alignment.End)
            }
            Sections(home, ui, Modifier.fillMaxSize(), PaddingValues(start = 24.dp - BLEED, end = SAFE_X - BLEED, top = 20.dp, bottom = 96.dp), dockWidth = 196.dp, showFolders = false)
        }
    }
}

// ---- Sections -------------------------------------------------------------------------------------

@Composable
private fun Sections(home: Home, ui: Ui, modifier: Modifier, padding: PaddingValues, dockWidth: Dp, showFolders: Boolean = true) {
    val cfg = ui.cfg
    BoxWithConstraints(modifier) {
        val inner = maxWidth - padding.calcHorizontal() - BLEED * 2
        val gridW: Dp = (inner - GAP * (cfg.columns - 1)) / cfg.columns
        // The default dock fits the screen; a bigger dock size scrolls sideways instead of shrinking past this.
        val n = home.dock.size.coerceIn(1, 5)
        val dockW: Dp = minOf(dockWidth * (cfg.dockScale / 100f), (inner - GAP * (n - 1)) / n)
        LazyColumn(Modifier.fillMaxSize().fadeTop(), contentPadding = padding) {
            for (s in cfg.sections) when (s) {
                "nowplaying" -> ui.track?.let { t -> item("nowplaying") { NowPlayingCard(t, ui) } }
                "dock" -> if (home.dock.isNotEmpty()) item("dock") {
                    TileRow("dock", home.dock, ui, dockW, big = true)
                }
                "rows" -> home.rows.forEachIndexed { i, (name, list) ->
                    item("row$i-title") { SectionTitle(name) }
                    item("row$i") { TileRow("row$i", list, ui, 148.dp * (cfg.tileScale / 100f), big = false) }
                }
                "folders" -> if (showFolders && home.folders.isNotEmpty()) {
                    item("folders-title") { SectionTitle("Folders") }
                    item("folders") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP), contentPadding = PaddingValues(horizontal = BLEED, vertical = 16.dp)) {
                            items(home.folders, key = { it.first }) { f -> FolderTile(f, ui, Modifier.width(148.dp * (cfg.tileScale / 100f))) }
                        }
                    }
                }
                "grid" -> if (home.grid.isNotEmpty()) grid(home, ui, gridW)
            }
        }
    }
}

/** Rows scrolling up fade out over the top 28 dp instead of being sliced by a hard edge. */
private fun Modifier.fadeTop(): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
    drawContent()
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, endY = 28.dp.toPx()), blendMode = BlendMode.DstIn)
}

private fun PaddingValues.calcHorizontal(): Dp =
    calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) + calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr)

private fun LazyListScope.grid(home: Home, ui: Ui, w: Dp) {
    val label = if (home.dock.isEmpty() && home.rows.isEmpty()) "Apps" else "All apps"
    item("grid-title") { SectionTitle(label) }
    home.grid.chunked(ui.cfg.columns).forEachIndexed { i, row ->
        item("grid$i") {
            Row(Modifier.padding(horizontal = BLEED, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (app in row) Tile("grid", app, ui, Modifier.width(w), big = false)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = TEXT, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = BLEED, top = 12.dp))
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TileRow(section: String, list: List<TvApp>, ui: Ui, w: Dp, big: Boolean) {
    val row = @Composable {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(GAP), contentPadding = PaddingValues(horizontal = BLEED, vertical = 16.dp)) {
            items(list, key = { it.pkg }) { app -> Tile(section, app, ui, Modifier.width(w), big) }
        }
    }
    if (ui.cfg.pinFocus) CompositionLocalProvider(LocalBringIntoViewSpec provides PINNED) { row() } else row()
}

// ---- Tiles ----------------------------------------------------------------------------------------

/** [lifted]: the caller zooms the tile itself with [lift], so the surface doesn't. */
@Composable
private fun focusStyle(ui: Ui, shape: RoundedCornerShape, zoom: Float = 1.08f, lifted: Boolean = false) = Triple(
    ClickableSurfaceDefaults.scale(focusedScale = if (ui.cfg.focus == "none" || (lifted && ui.cfg.focus == "lift")) 1f else zoom),
    ClickableSurfaceDefaults.glow(
        focusedGlow = when (ui.cfg.focus) {
            "glow" -> Glow(ui.accent, 16.dp)
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
    val scale by animateFloatAsState(if (focused) 1.09f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f), label = "lift")
    val lean = remember { Animatable(0f) }
    val leanX = remember { mutableStateOf(true) }
    LaunchedEffect(focused) {
        if (focused && ui.cfg.tilt && (Motion.dx != 0 || Motion.dy != 0)) {
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
        if (focused) { delay(80); p.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }
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
    val shape = RoundedCornerShape(if (big) 12.dp else 10.dp)
    val (scale, glow, border) = focusStyle(ui, shape, lifted = true)
    var focused by remember { mutableStateOf(false) }
    // With names on focus, the name keeps its room so the rows don't jump.
    val nameAlpha by animateFloatAsState(if (!ui.cfg.nameFocus || focused) 1f else 0f, tween(200), label = "name")
    Column(size) {
        Surface(
            onClick = { runCatching { ctx.startActivity(app.launch) }.onFailure { Log.w(TAG, "launch ${app.pkg}: $it") } },
            modifier = ui.focusFor(section, app).fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.isFocused; if (it.isFocused) ui.onFocusApp(app) }
                .lift(ui, shape, focused),
            shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
            colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = TILE_FOCUSED)
        ) {
            if (app.banner) {
                Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GeneratedTile(app, big, named = !ui.cfg.showNames)
            }
            LiftLight(ui, focused)
        }
        if (ui.cfg.showNames) {
            Text(app.label, color = TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp, start = 2.dp).graphicsLayer { alpha = nameAlpha })
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
    Column(size) {
        Surface(
            onClick = { ui.onFolder(folder) },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { focused = it.isFocused }.lift(ui, shape, focused),
            shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
            colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = TILE_FOCUSED)
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
            RoundButton(if (t.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, if (t.playing) "Pause" else "Play", ui) { NowPlaying.playPause() }
            RoundButton(Icons.Outlined.SkipNext, "Next", ui) { NowPlaying.next() }
        }
    }
}

// ---- Top bar pieces -------------------------------------------------------------------------------

/** Icon, temperature, today's high and low, and the place, in one muted line. */
@Composable
private fun WeatherLine(ui: Ui, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val w = ui.weather ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(weatherIcon(w), null, Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { size.toDp() * 1.3f }), colorFilter = ColorFilter.tint(MUTED))
        Spacer(Modifier.width(6.dp))
        Text("${w.temp}°  ${w.high}° / ${w.low}°  ${ui.cfg.weatherPlace}", color = MUTED, fontSize = size, fontFamily = CLOCK, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** WMO weather codes, as Open-Meteo reports them, to a drawn icon. */
private fun weatherIcon(w: WeatherNow): ImageVector = when (w.code) {
    0 -> if (w.day) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    1, 2 -> if (w.day) Icons.Outlined.WbCloudy else Icons.Outlined.NightsStay
    3 -> Icons.Outlined.Cloud
    45, 48 -> Icons.Outlined.Dehaze
    in 71..77, 85, 86 -> Icons.Outlined.AcUnit
    in 95..99 -> Icons.Outlined.Thunderstorm
    in 51..67, in 80..82 -> Icons.Outlined.WaterDrop
    else -> Icons.Outlined.Cloud
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, ui: Ui, onClick: () -> Unit) {
    val (scale, glow, border) = focusStyle(ui, RoundedCornerShape(50), zoom = 1.12f)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = scale, glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = ui.accent.copy(alpha = 0.9f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(icon, label, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (focused) onAccent(ui.accent) else TEXT))
        }
    }
}

@Composable
private fun QuickButton(a: QuickAction, ui: Ui, onQuick: (QuickAction) -> Unit) {
    val (scale, glow, border) = focusStyle(ui, RoundedCornerShape(50), zoom = 1.12f)
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = { onQuick(a) },
        modifier = Modifier.size(40.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = scale, glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = ui.accent.copy(alpha = 0.9f))
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
private fun QuickGlyph(a: QuickAction, ui: Ui, focused: Boolean, size: Dp) {
    val art = a.art
    if (art != null) Image(art, a.label, Modifier.size(size + 4.dp).clip(CircleShape))
    else Image(iconFor(a, ui.cfg), a.label, Modifier.size(size), colorFilter = ColorFilter.tint(if (focused) onAccent(ui.accent) else TEXT))
}

@Composable
private fun QuickRow(a: QuickAction, ui: Ui, onQuick: (QuickAction) -> Unit, expanded: Boolean) =
    RailItem(iconFor(a, ui.cfg), a.label, ui, expanded, glyph = { focused -> QuickGlyph(a, ui, focused, 22.dp) }) { onQuick(a) }

@Composable
private fun RailItem(icon: ImageVector, label: String, ui: Ui, expanded: Boolean, glyph: (@Composable (Boolean) -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val (_, glow, border) = focusStyle(ui, shape)
    var focused by remember { mutableStateOf(false) }
    val ink = if (focused) onAccent(ui.accent) else TEXT
    Surface(
        onClick = onClick,
        modifier = Modifier.height(44.dp).then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(44.dp)).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f), glow = glow, border = border,
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = ui.accent.copy(alpha = 0.9f))
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
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Text("$part, ${cfg.greeting}", color = TEXT, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 2)
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

/** The idle screen's only text: a large clock and the weather in the lower left, inside the safe area. */
@Composable
private fun IdleClock(ui: Ui, modifier: Modifier) {
    Column(modifier.padding(start = SAFE_X, bottom = SAFE_Y + 12.dp)) {
        Clock(ui.cfg, 64.sp, 18.sp, Alignment.Start)
        WeatherLine(ui, 16.sp, Modifier.padding(top = 8.dp))
    }
}

// ---- Overlays -------------------------------------------------------------------------------------

/** A folder's apps over a dimmed home screen. Back closes it. */
@Composable
private fun FolderOverlay(name: String, list: List<TvApp>, ui: Ui, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scrim(0.9f)), contentAlignment = Alignment.Center) {
            Column(Modifier.background(PANEL, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(name, color = TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = BLEED, bottom = 6.dp))
                val inner = Ui(ui.cfg, ui.accent, focus, list.firstOrNull()?.let { "folder:${it.pkg}" }, ui.onFolder)
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

@Composable
private fun InputsOverlay(inputs: List<Pair<String, String>>, ui: Ui, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(scrim(0.9f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(400.dp).background(PANEL, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Inputs", color = TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                inputs.forEachIndexed { i, (id, label) ->
                    val shape = RoundedCornerShape(12.dp)
                    val (scale, glow, border) = focusStyle(ui, shape, zoom = 1.03f)
                    Surface(
                        onClick = { onClose(); openInput(ctx, id) },
                        modifier = (if (i == 0) Modifier.focusRequester(focus) else Modifier).fillMaxWidth().height(52.dp),
                        shape = ClickableSurfaceDefaults.shape(shape), scale = scale, glow = glow, border = border,
                        colors = ClickableSurfaceDefaults.colors(containerColor = TILE, focusedContainerColor = TILE_FOCUSED)
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
