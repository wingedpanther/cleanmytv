package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Glass: content first, quiet chrome. A headline for the focused dock app and what it has to continue, its
 * cards, and the dock in a glass tray at the bottom. Focus is a white fill with a lift and a soft shadow,
 * never a ring. The glass is a flat white fill over Glass's own slate backdrop, or the owner's wallpaper when
 * they ask for it: a real blur costs too much on a 2 GB box, except behind Control centre (see GlassControl).
 */

internal val GLASS_INK = Color(0xFF1D1D1F)
internal val GLASS_FILL = Color.White.copy(alpha = 0.12f)
internal val GLASS_EDGE = Color.White.copy(alpha = 0.16f)
internal val GLASS_TRAY = Color.White.copy(alpha = 0.10f)
internal val GLASS_TRAY_EDGE = Color.White.copy(alpha = 0.18f)
internal val GLASS_MUTED = Color.White.copy(alpha = 0.72f)
private val GLASS_AMBER = Color(0xFFFF9F0A)
private val GLASS_X = 48.dp
private val GLASS_Y = 27.dp
/** The board's focus shadow: black 55%, 17 dp down, 30 dp blur. */
private val GLASS_DROP = Shadow(radius = 30.dp, color = Color.Black, offset = DpOffset(0.dp, 17.dp), alpha = 0.55f)
/** Over the owner's photo, white glass vanishes: the tray and tiles turn to dark glass with the same edge. */
internal val LocalGlassOnPhoto = compositionLocalOf { false }
private val PHOTO_TRAY = Color.Black.copy(alpha = 0.38f)
private val PHOTO_TILE = Color.Black.copy(alpha = 0.30f)
/** Control centre's glass: lighter than the tiles behind it, nearly opaque, with a white hairline. */
private val GLASS_SHEET = Color(0xF03B404C)
private val GLASS_SHEET_EDGE = Color.White.copy(alpha = 0.24f)

/** Amber, unless the owner picked an accent of their own. */
internal fun glassAccent(cfg: HomeConfig): Color = if (cfg.accent == HomeConfig().accent) GLASS_AMBER else Color(cfg.accent)

/** Glass's own picture when the owner hasn't set one: slate into near black, a plum glow low left, light high right. */
@Composable
internal fun GlassBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 160 degrees: from the top, leaning a little left, to the bottom.
        drawRect(Brush.linearGradient(0f to Color(0xFF2B3A55), 0.64f to Color(0xFF0B0D12), 1f to Color(0xFF0B0D12), start = Offset(w * 0.33f, 0f), end = Offset(w * 0.67f, h)))
        drawRect(Brush.radialGradient(0f to Color(0xFF4A3560), 1f to Color(0x004A3560), center = Offset(w * 0.06f, h * 1.04f), radius = w * 0.55f))
        drawRect(Brush.radialGradient(0f to Color.White.copy(alpha = 0.22f), 0.62f to Color.Transparent, center = Offset(w * 0.8f, h * 0.14f), radius = w * 0.4f))
    }
}

/**
 * Over the owner's wallpaper: black from the left edge to mid screen and up from the bottom, so the headline
 * and the tray read over a bright photo.
 */
@Composable
internal fun GlassScrim() {
    Box(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.5f to Color.Transparent))
        val reach = 200.dp.toPx()
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), startY = size.height - reach, endY = size.height))
    })
}

/** All apps and Search: Home's wallpaper under a 60% scrim, or Glass's own slate when there's none. */
@Composable
internal fun GlassPageBackdrop(picture: ImageBitmap?) {
    if (picture == null) return GlassBackdrop()
    Image(picture, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
}

/** The glass tray and panels: a flat white fill and a hairline edge. Not clipped: a picked-up tile rises out of the tray. */
private fun Modifier.glass(radius: Dp, fill: Color = GLASS_TRAY, edge: Color = GLASS_TRAY_EDGE) =
    this.background(fill, RoundedCornerShape(radius)).border(1.dp, edge, RoundedCornerShape(radius))

/**
 * Glass's focus: white with near-black content, a little larger, raised, with a soft shadow. A banner keeps its
 * picture and shows focus by size, lift and shadow alone. The tile grows in its own layer, with the shadow
 * inside it, so the shadow grows with the tile instead of hiding under it.
 */
@Composable
internal fun GlassTile(
    radius: Dp, modifier: Modifier, focusScale: Float = 1.14f, lift: Dp = 5.dp, keepFill: Boolean = false, shadow: Boolean = true,
    onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(radius)
    val grow by animateFloatAsState(if (focused) focusScale else 1f, tween(160), label = "grow")
    val rise by animateDpAsState(if (focused) lift else 0.dp, tween(160), label = "rise")
    val photo = LocalGlassOnPhoto.current
    val fill = if (photo) PHOTO_TILE else GLASS_FILL
    Surface(
        onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.pointer(onLongClick = onLongClick, onClick = onClick).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }
            .graphicsLayer { translationY = -rise.toPx(); scaleX = grow; scaleY = grow }
            .then(if (focused && shadow) Modifier.dropShadow(shape, GLASS_DROP) else Modifier),
        shape = ClickableSurfaceDefaults.shape(shape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        border = ClickableSurfaceDefaults.border(border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, if (photo) GLASS_TRAY_EDGE else GLASS_EDGE), shape = shape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, if (keepFill) Color.Transparent else Color.White), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(containerColor = fill, focusedContainerColor = if (keepFill) fill else Color.White),
    ) { content(focused) }
}

/**
 * Home in Glass. [onAllApps] opens the All apps page from the dock's last tile. While a dock app moves, the
 * headline says so and the tray shows the picked-up app raised and tilted.
 */
@Composable
internal fun GlassLayout(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit, onAllApps: () -> Unit, moving: Moving?, seen: Int, onPhoto: Boolean) {
    CompositionLocalProvider(LocalGlassOnPhoto provides onPhoto) { GlassHome(home, ui, quick, onQuick, onAllApps, moving, seen) }
}

@Composable
private fun GlassHome(home: Home, ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit, onAllApps: () -> Unit, moving: Moving?, seen: Int) {
    val ctx = LocalContext.current
    val dock = home.dock.take(GLASS_DOCK)
    // Most sideloaded apps never write Watch Next: then the headline says when the app was last open, and
    // there is no shelf.
    val used by produceState(emptyMap<String, Long>(), seen, dock.map { it.pkg }) { value = withContext(Dispatchers.IO) { Recent.lastUsed(ctx, dock.map { it.pkg }) } }
    var current by remember { mutableStateOf(dock.firstOrNull()) }
    val movingApp = moving?.takeIf { m -> m.kind == "main" && dock.any { it.pkg == m.pkg } }
    // Home's first focus can be in a section Glass doesn't draw: then it starts on the first dock app.
    val start = remember { FocusRequester() }
    val ownStart = ui.focusKey?.startsWith("dock:") != true
    LaunchedEffect(ownStart, dock.firstOrNull()?.pkg) { if (ownStart && dock.isNotEmpty()) { delay(60); runCatching { start.requestFocus() } } }
    Column(Modifier.fillMaxSize().padding(horizontal = GLASS_X, vertical = GLASS_Y)) {
        // Focus paths set by hand: a lifted, scaled tile makes its neighbours look lower than it, so the
        // usual search sends Down sideways along the top bar instead of to the shelf or the dock.
        // The app last focused in the dock, or the first one once that app has left the dock.
        val shownApp = current?.takeIf { c -> dock.any { it.pkg == c.pkg } } ?: dock.firstOrNull()
        val head = headItem(shownApp, home)
        val slots = shelfSlots(head, home)
        val hasShelf = movingApp == null && slots.isNotEmpty()
        val top = remember { FocusRequester() }
        val shelf = remember { FocusRequester() }
        val dockBack = remember { FocusRequester() }
        GlassTopBar(ui, quick, onQuick, top) { if (hasShelf) shelf else dockBack }
        if (movingApp != null) GlassMoving(movingApp, dock)
        else {
            shownApp?.let { GlassHeadline(it, head, home, ui, used[it.pkg]) }
            GlassCards(slots, ui, home, shelf, Modifier.focusProperties { up = top; down = dockBack })
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.align(Alignment.CenterHorizontally).glass(24.dp, if (LocalGlassOnPhoto.current) PHOTO_TRAY else GLASS_TRAY).padding(14.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            dock.forEachIndexed { i, app ->
                GlassDockTile(app, ui, movingApp?.pkg == app.pkg, dimmed = movingApp != null && movingApp.pkg != app.pkg,
                    Modifier.then(ui.focusFor("dock", app)).then(if (i == 0 && ownStart) Modifier.focusRequester(start) else Modifier)
                        .then(if (app.pkg == shownApp?.pkg) Modifier.focusRequester(dockBack) else Modifier)
                        .focusProperties { up = if (hasShelf) shelf else top; if (i == 0) left = FocusRequester.Cancel }) { current = app }
            }
            // All apps stays in place during a move, so the tray keeps its width.
            Box(Modifier.graphicsLayer { alpha = if (movingApp != null) 0.5f else 1f }) {
                // The ends of the tray stop the arrows; the usual search jumped from here up to the top bar.
                GlassTile(12.dp, Modifier.width(104.dp).height(59.dp).focusProperties { right = FocusRequester.Cancel; up = if (hasShelf) shelf else top }, onClick = onAllApps) { f ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(tr("All apps"), color = if (f) GLASS_INK else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
                    }
                }
            }
        }
    }
}

/** Home's first-run tip in a glass capsule just above the tray, clear of the dock. */
@Composable
internal fun GlassTip(text: String, onPhoto: Boolean, modifier: Modifier) {
    val fill = if (onPhoto) PHOTO_TILE else GLASS_FILL
    Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
        modifier = modifier.padding(bottom = GLASS_Y + 101.dp).glass(18.dp, fill).padding(horizontal = 18.dp, vertical = 8.dp))
}

/** Six apps in the tray, then All apps. */
internal const val GLASS_DOCK = 6

@Composable
private fun GlassTopBar(ui: Ui, quick: List<QuickAction>, onQuick: (QuickAction) -> Unit, top: FocusRequester, down: () -> FocusRequester) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000L - System.currentTimeMillis() % 60_000L) } }
    val debugging by produceState(false) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { TvSettings.debuggingOn(ctx) } }
    val network = remember(now) { TvSettings.network(ctx).substringBefore(" · ") }
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ui.cfg.clock != "off") {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (ui.cfg.clock == "time_date") Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 9.dp))
        }
        Spacer(Modifier.weight(1f))
        val capsule = if (LocalGlassOnPhoto.current) PHOTO_TILE else GLASS_FILL
        // The icons have no labels: while one has focus, the status capsule says what it opens.
        var named by remember { mutableStateOf<String?>(null) }
        Row(Modifier.height(28.dp).glass(14.dp, capsule).padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(named ?: network, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            if (debugging && named == null) {
                Box(Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.28f)))
                Box(Modifier.size(6.dp).background(glassAccent(ui.cfg), CircleShape))
                Text(tr("Debugging on"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        // The quick icons in one quiet capsule beside the status one: no ring each, only the focused one gets a
        // white circle. Five at most; the rest are in Control centre.
        if (quick.isNotEmpty()) Row(Modifier.padding(start = 10.dp).height(28.dp).glass(14.dp, capsule).focusGroup()) {
            quick.take(5).forEachIndexed { i, a ->
                QuickIcon(a, ui, Modifier.then(if (i == 0) Modifier.focusRequester(top) else Modifier).focusProperties { this.down = down() }
                    .onFocusChanged { if (it.isFocused) named = a.label else if (named == a.label) named = null }) { onQuick(a) }
            }
        }
    }
}

@Composable
private fun QuickIcon(a: QuickAction, ui: Ui, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, modifier = modifier.pointer(onClick = onClick).size(28.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val art = a.art
            if (art != null) Image(art, a.label, Modifier.size(18.dp).clip(CircleShape))
            else Image(iconFor(a, ui.cfg), a.label, Modifier.size(16.dp), colorFilter = ColorFilter.tint(if (focused) GLASS_INK else Color.White.copy(alpha = 0.86f)))
        }
    }
}

/**
 * The headline, always three lines as on the board. [item] is the focused app's own to continue, or else the
 * newest from any app, named in the small line. With nothing to continue anywhere: the focused app's kind, its
 * name, and when it was last open.
 */
@Composable
private fun GlassHeadline(app: TvApp, item: WatchItem?, home: Home, ui: Ui, lastUsed: Long?) {
    val ctx = LocalContext.current
    Column(Modifier.padding(top = 30.dp)) {
        Text(if (item != null) tr("Continue in {app}", "app" to (home.app(item.pkg)?.label ?: app.label)) else (remember(app.pkg) { bauKind(ctx, app.pkg) } ?: tr("App")),
            color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(item?.title ?: app.label, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp, letterSpacing = (-0.035).em,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
        if (item == null) {
            Text(lastUsed?.let { tr("Opened {when}", "when" to usedWhen(it)) } ?: tr("OK opens it"), color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 9.dp))
        }
        if (item != null) {
            val left = if (item.duration > 0 && item.position > 0) ((item.duration - item.position) / 60_000L).toInt().takeIf { it > 0 } else null
            val meta = listOfNotNull(WatchNext.episodeLine(item), left?.let { tr("{time} left", "time" to Limits.words(it)) }).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 9.dp))
            item.progress?.let { p ->
                Box(Modifier.padding(top = 13.dp).width(260.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.24f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(p).background(glassAccent(ui.cfg)))
                }
            }
        }
    }
}

/** Four slots on the shelf, as on the board. */
private const val SLOTS = 4

/**
 * The shelf holds only things to continue, the headline's item first, so OK on it resumes. Apps stay in the dock
 * and All apps: the owner didn't want recent apps standing in for cards.
 */
private fun shelfSlots(head: WatchItem?, home: Home): List<WatchItem> = (listOfNotNull(head) + home.watch.filter { it !== head }).take(SLOTS)

/** The headline's item: the focused app's own, else the newest from any app. */
private fun headItem(app: TvApp?, home: Home): WatchItem? = home.watch.firstOrNull { it.pkg == app?.pkg } ?: home.watch.firstOrNull()

/** The shelf: up to four cards, each a quarter of the row wide, from the left. */
@Composable
private fun GlassCards(items: List<WatchItem>, ui: Ui, home: Home, first: FocusRequester, nav: Modifier) {
    if (items.isEmpty()) return
    BoxWithConstraints(Modifier.padding(top = 26.dp).fillMaxWidth()) {
        val w = (maxWidth - 16.dp * (SLOTS - 1)) / SLOTS
        Column {
            Text(tr("Continue watching"), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(w).padding(bottom = 10.dp))
            Row(Modifier.focusGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEachIndexed { i, item ->
                    // The shelf's first card is where Down from the top bar lands.
                    val m = (if (i == 0) nav.focusRequester(first) else nav).width(w)
                    GlassCard(item, home.app(item.pkg), ui, m)
                }
            }
        }
    }
}

/** The board's glass for a card with no picture: light at the top left, darker at the foot. */
private val CARD_GLASS = Brush.linearGradient(0f to Color.White.copy(alpha = 0.22f), 1f to Color.White.copy(alpha = 0.04f))
private val CARD_FOOT = Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))

@Composable
private fun GlassCard(item: WatchItem, app: TvApp?, ui: Ui, modifier: Modifier) {
    val ctx = LocalContext.current
    val px = with(LocalDensity.current) { 216.dp.roundToPx() }
    val picture by produceState(item.image?.let { WatchNext.cached(it) }, item.image) {
        if (value == null) value = item.image?.let { WatchNext.picture(ctx, it, px) }
    }
    val go: () -> Unit = { (WatchNext.intent(ctx, item) ?: app?.launch)?.let { open(ctx, item.pkg, it, app?.launch) } }
    GlassTile(14.dp, modifier.height(118.dp), focusScale = 1.08f, keepFill = true, onFocus = { app?.let(ui.onFocusApp) }, onLongClick = { ui.onWatchMenu(item) }, onClick = go) { _ ->
        Box(Modifier.fillMaxSize().then(if (picture == null) Modifier.background(CARD_GLASS) else Modifier)) {
            picture?.let { Image(it, item.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(CARD_FOOT))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 13.dp, vertical = 11.dp)) {
                Text(item.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Always the app's name, so a card never passes for another app's.
                val meta = listOfNotNull(app?.label, WatchNext.episodeLine(item)).joinToString(" · ").ifEmpty { null }
                if (meta != null) Text(meta, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * A dock app: its name on glass, or its banner when the owner turned banners on. Picked up to move, it's white,
 * raised, larger and tilted once, with no focus growth on top, and an arrow either side drawn over its
 * neighbours, so the tray keeps its width.
 */
@Composable
private fun GlassDockTile(app: TvApp, ui: Ui, picked: Boolean, dimmed: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    val ctx = LocalContext.current
    val banner = app.banner && ui.cfg.glassBanners
    Box(modifier.zIndex(if (picked) 1f else 0f).graphicsLayer { alpha = if (dimmed) 0.5f else 1f }, contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { if (picked) { translationY = -22.dp.toPx(); scaleX = 1.18f; scaleY = 1.18f; rotationZ = -2f } }) {
            GlassTile(12.dp, Modifier.width(104.dp).height(59.dp), focusScale = if (picked) 1f else 1.14f, lift = if (picked) 0.dp else 5.dp,
                keepFill = banner && !picked, onFocus = { onFocus(); ui.onFocusApp(app) },
                onLongClick = { ui.onAppMenu("dock", app) }, onClick = { launch(ctx, app) }) { f ->
                val ink = if (f || picked) GLASS_INK else Color.White
                if (banner) Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize().background(if (picked) Color.White else Color.Transparent).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    // Two lines at 13 sp before a long name is cut.
                    Text(app.label, color = ink, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
        if (picked) {
            GlassArrow(Icons.AutoMirrored.Outlined.ArrowBack, Modifier.align(Alignment.CenterStart).offset(x = (-30).dp))
            GlassArrow(Icons.AutoMirrored.Outlined.ArrowForward, Modifier.align(Alignment.CenterEnd).offset(x = 30.dp))
        }
    }
}

@Composable
private fun GlassArrow(icon: ImageVector, modifier: Modifier) {
    Box(modifier.graphicsLayer { translationY = -13.dp.toPx() }.size(24.dp).glass(12.dp, Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Image(icon, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(Color.White))
    }
}

/** Above the tray while a dock app moves: which, where, and the three keys. */
@Composable
private fun GlassMoving(m: Moving, dock: List<TvApp>) {
    val place = dock.indexOfFirst { it.pkg == m.pkg } + 1
    Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tr("Moving in the dock"), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(m.label, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 54.sp, letterSpacing = (-0.035).em, maxLines = 1, modifier = Modifier.padding(top = 5.dp))
        Text(tr("Place {n} of {all}. The other apps slide to make room.", "n" to place, "all" to dock.size), color = Color.White.copy(alpha = 0.86f), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 9.dp))
        Row(Modifier.padding(top = 36.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            for ((k, what) in listOf(tr("OK") to tr("Done"), tr("Down") to tr("Take out of the dock"), tr("Back") to tr("Cancel"))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.height(24.dp).glass(12.dp, Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.22f)).padding(horizontal = 8.dp, vertical = 4.dp))
                    Text(what, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

// ---- Control centre ---------------------------------------------------------------------------------

/**
 * Quick settings in Glass, Control centre: one screen with no scroll, floating at the right. Four switches in a
 * 2x2 grid, volume and brightness as bars that fill, Picture & sound and Network as half tiles, then capsules.
 * The rarer settings are in Customize and the TV's own settings. On Android 12 and later, when the TV allows
 * it, the compositor blurs Home behind the panel; that costs the app nothing.
 */
@Composable
internal fun GlassControl(onFind: () -> Unit, onEditor: (() -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    var armOff by remember { mutableStateOf(false) }
    fun open(i: Intent?) { i ?: return; runCatching { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    val volume = remember(tick) { TvSettings.volume(ctx) }
    val brightness = remember(tick) { TvSettings.brightness(ctx) }
    val dns = remember(tick) { TvSettings.dns(ctx) }
    val adb = remember(tick) { TvSettings.debuggingOn(ctx) }
    val sleepAt by SleepTimer.at.collectAsState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val blurred = rememberBlurBehind()
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (blurred) 0.25f else 0.5f))) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(top = GLASS_Y, bottom = GLASS_Y, end = GLASS_X).width(340.dp).fillMaxHeight()
                    .shadow(40.dp, RoundedCornerShape(26.dp)).glass(26.dp, GLASS_SHEET, GLASS_SHEET_EDGE).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.padding(horizontal = 3.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(tr("Control centre"), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(remember { tvName(ctx).orEmpty() }, color = GLASS_MUTED, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                }
                val left = sleepAt?.let { ((it - System.currentTimeMillis() + 59_999) / 60_000L).toInt() }?.takeIf { it > 0 }
                val modules = listOfNotNull(
                    GlassModule(Icons.Outlined.ManageSearch, tr("Search"), tr("This TV"), false) { onClose(); onFind() },
                    GlassModule(Icons.Outlined.Timer, tr("Sleep timer"), left?.let { tr("In {time}", "time" to Limits.words(it)) } ?: tr("Off"), left != null) {
                        val steps = listOf<Int?>(null) + SleepTimer.CHOICES
                        val cur = if (left == null) 0 else steps.indexOfFirst { it != null && it >= left }.let { if (it < 0) steps.size - 1 else it }
                        SleepTimer.set(ctx, steps[(cur + 1) % steps.size])
                    },
                    GlassModule(Icons.Outlined.Shield, tr("Ad blocking"), if (dns.current == 1) tr("On") else tr("Off"), dns.current == 1) {
                        dns.set?.let { it(1 - dns.current); tick++ } ?: open(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                    },
                    if (adb) GlassModule(Icons.Outlined.BugReport, tr("Debugging"), if (armOff) tr("OK again turns it off") else tr("On"), true) {
                        if (armOff) { TvSettings.debuggingOff(ctx); armOff = false; tick++ } else armOff = true
                    } else null,
                )
                modules.chunked(2).forEachIndexed { r, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEachIndexed { i, m -> GlassToggle(m, Modifier.weight(1f).then(if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                GlassBar(volume) { tick++ }
                GlassBar(brightness) { tick++ }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassHalf(tr("Picture & sound"), remember { displayLine(ctx) } ?: tr("Open"), Modifier.weight(1f)) { quickActions()["display"]?.let { a -> resolve(ctx, a)?.let { open(it) } } }
                    GlassHalf(tr("Network"), remember(tick) { TvSettings.network(ctx).substringBefore(" · ") }, Modifier.weight(1f)) { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onEditor?.let { go -> GlassCapsule(tr("Customize"), Modifier.weight(1f)) { onClose(); go() } }
                    GlassCapsule(tr("All settings"), Modifier.weight(1f)) { open(Intent(Settings.ACTION_SETTINGS)) }
                    if (KeyService.running != null) GlassCapsule(tr("Sleep now"), Modifier.weight(1f)) { onClose(); KeyService.sleep(ctx) }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/**
 * Blurs what's behind this dialog's window, on Android 12 and later when the TV has cross-window blur on.
 * True when it did, so the scrim can be lighter.
 */
@Composable
private fun rememberBlurBehind(): Boolean {
    val view = LocalView.current
    return remember(view) {
        if (android.os.Build.VERSION.SDK_INT < 31) return@remember false
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@remember false
        val wm = view.context.getSystemService(android.view.WindowManager::class.java)
        if (wm?.isCrossWindowBlurEnabled != true) return@remember false
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply { blurBehindRadius = 44 }
        true
    }
}

/** Half a row: the label over its value; OK opens the TV's screen for it. */
@Composable
private fun GlassHalf(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    GlassTile(16.dp, modifier.height(58.dp), focusScale = 1.04f, lift = 0.dp, onClick = onClick) { f ->
        val ink = if (f) GLASS_INK else Color.White
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = ink.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Glass's menus, for hold OK on an app or a card: a floating glass sheet with 50 dp rows, the focused one white
 * with near-black text. [note] is a line under the title.
 */
@Composable
internal fun GlassMenu(title: String, note: String?, items: List<Pair<String, () -> Unit>>, focus: FocusRequester) {
    Column(Modifier.width(380.dp).shadow(40.dp, RoundedCornerShape(26.dp)).glass(26.dp, GLASS_SHEET, GLASS_SHEET_EDGE).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
        if (note != null) Text(note, color = GLASS_MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 4.dp))
        Spacer(Modifier.height(2.dp))
        items.forEachIndexed { i, (label, act) ->
            GlassTile(16.dp, Modifier.fillMaxWidth().height(50.dp).then(if (i == 0) Modifier.focusRequester(focus) else Modifier), focusScale = 1.03f, lift = 0.dp, shadow = false, onClick = act) { f ->
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    Text(label, color = if (f) GLASS_INK else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private class GlassModule(val icon: ImageVector, val label: String, val value: String, val on: Boolean, val act: () -> Unit)

@Composable
private fun GlassToggle(m: GlassModule, modifier: Modifier) {
    GlassTile(16.dp, modifier.height(68.dp), focusScale = 1.04f, lift = 0.dp, onClick = m.act) { f ->
        val ink = if (f) GLASS_INK else Color.White
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(if (m.on) (if (f) GLASS_INK else Color.White) else Color.White.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Image(m.icon, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(if (m.on) (if (f) Color.White else GLASS_INK) else ink))
            }
            Column(Modifier.padding(start = 9.dp)) {
                Text(m.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.value, color = ink.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Volume or brightness: a bar that fills from the left, the value at the right; left and right change it. */
@Composable
private fun GlassBar(c: TvSettings.Choice, changed: () -> Unit) {
    val ctx = LocalContext.current
    fun step(d: Int) { val i = (c.current + d).coerceIn(0, c.options.size - 1); if (c.set != null && i != c.current) { c.set.invoke(i); changed() } }
    GlassTile(16.dp, Modifier.fillMaxWidth().height(44.dp).onPreviewKeyEvent { e ->
        if (c.set == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (e.key) { Key.DirectionLeft -> { step(-1); true }; Key.DirectionRight -> { step(1); true }; else -> false }
    }, focusScale = 1.04f, lift = 0.dp, onClick = { if (c.set == null) c.fallback?.let { runCatching { ctx.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }) { f ->
        val ink = if (f) GLASS_INK else Color.White
        val part = if (c.options.size > 1) c.current / (c.options.size - 1f) else 0f
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(part).background(if (f) GLASS_INK.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.30f)))
            Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(c.label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(c.options.getOrElse(c.current) { "" }, color = ink.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GlassCapsule(label: String, modifier: Modifier, onClick: () -> Unit) {
    GlassTile(22.dp, modifier.height(44.dp), focusScale = 1.04f, lift = 0.dp, onClick = onClick) { f ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) GLASS_INK else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** [GlassMenu] in its own dialog, for hold OK on a card. Only an OK press that starts inside it counts. */
@Composable
internal fun GlassMenuDialog(title: String, note: String?, items: List<Pair<String, () -> Unit>>, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    var fresh by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).onPreviewKeyEvent { e ->
            when {
                e.key !in OK_KEYS -> false
                e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0 -> { fresh = true; false }
                else -> !fresh
            }
        }, contentAlignment = Alignment.Center) { GlassMenu(title, note, items, focus) }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
