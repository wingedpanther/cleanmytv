package io.github.wingedpanther.cleanmytv.home

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/*
 * Teletext: every screen is a page of text lines in one monospace face on black. No icons, banners or cards.
 * A line's number on the remote opens it, and the four colour buttons are the words on the bottom row. The
 * D-pad reaches all of it, since many remotes have neither number nor colour keys: the numbers only show once
 * Home has seen a number key (see RemoteKeys). The layout brings its own colours and type: wallpaper, accent
 * and the Home font don't apply while it's on.
 */

internal val TT_BLACK = Color(0xFF000000)
internal val TT_WHITE = Color(0xFFF2F2F2)
internal val TT_YELLOW = Color(0xFFFFE600)
internal val TT_CYAN = Color(0xFF19E3FF)
internal val TT_GREEN = Color(0xFF2BE04A)
internal val TT_MAGENTA = Color(0xFFFF4DD8)
internal val TT_RED = Color(0xFFE6212B)
internal val TT_BLUE = Color(0xFF1437D6)
internal val TT_DIM = Color(0xFF3A3A3A)
internal val TT_META = TT_WHITE.copy(alpha = 0.7f)

/** IBM Plex Mono, bundled: the grid needs fixed-width letters, and the TV's own monospace may lack Cyrillic. */
private val TT_FONT = FontFamily(Font(R.font.plex_mono_medium, FontWeight.Medium), Font(R.font.plex_mono_bold, FontWeight.Bold))
private val TT_X = 40.dp
private val TT_Y = 27.dp
internal val TT_ROW = 30.dp
internal val TT_NUM = 48.dp
private const val DOTS = "................................................................................"

/** Up to six dock apps take the first lines; the other pages and Customize follow on, so 0 is the tenth line. */
internal const val TEXT_DOCK = 6

/** One of the four words on the bottom row, in red, green, yellow and blue order. */
internal class ColourWord(val label: String, val act: () -> Unit)

private val WORD_BG = listOf(TT_RED, TT_GREEN, TT_YELLOW, TT_BLUE)
private val WORD_INK = listOf(TT_WHITE, TT_BLACK, TT_BLACK, TT_WHITE)
private val COLOUR_KEYS = listOf(Key.ProgramRed, Key.ProgramGreen, Key.ProgramYellow, Key.ProgramBlue)

/** Which colour button [k] is, 0 to 3, or null. */
internal fun colourKey(k: Key): Int? = COLOUR_KEYS.indexOf(k).takeIf { it >= 0 }

private val DIGIT_KEYS = listOf(Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
    Key.NumPad0, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5, Key.NumPad6, Key.NumPad7, Key.NumPad8, Key.NumPad9)

/** 0 to 9 for a number key, on the main keys or the number pad; null for any other key. */
internal fun digitKey(k: Key): Int? = DIGIT_KEYS.indexOf(k).takeIf { it >= 0 }?.let { it % 10 }

/**
 * The TV's name, as the owner set it, with underscores as spaces. Null when it has none, or only the raw model
 * code some boxes leave there ("sdk_google_atv64_arm64").
 */
internal fun tvName(ctx: android.content.Context): String? {
    val n = runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val codes = listOf(android.os.Build.MODEL, android.os.Build.DEVICE, android.os.Build.PRODUCT).map { it.lowercase() }
    return if ('_' in n && n.lowercase() in codes) null else n.replace('_', ' ')
}

/**
 * A page: its name, date and clock on the first row, the title on a coloured band, the body, one hint in cyan,
 * and the four colour words. [picked] draws one word as focused while the arrows belong to a move.
 */
@Composable
internal fun TextPage(
    name: String, title: String, band: Color, bandInk: Color, counter: String, hint: String,
    words: List<ColourWord?>, modifier: Modifier = Modifier, picked: Int? = null, firstWord: FocusRequester? = null, gap: Dp = 15.dp, keys: (KeyEvent) -> Boolean = { false },
    body: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    remember { RemoteKeys.load(ctx) }
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = TT_FONT, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, color = TT_WHITE)) {
        Column(modifier.fillMaxSize().background(TT_BLACK).padding(horizontal = TT_X, vertical = TT_Y).onPreviewKeyEvent { e ->
            if (e.type == KeyEventType.KeyDown) RemoteKeys.seen(ctx, e.key)
            if (e.type == KeyEventType.KeyDown) colourKey(e.key)?.let { i -> words.getOrNull(i)?.let { it.act(); return@onPreviewKeyEvent true } }
            keys(e)
        }) {
            Row(Modifier.fillMaxWidth().height(TT_ROW).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(name.uppercase(), color = TT_GREEN, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.weight(1f))
                TextClock()
            }
            Row(Modifier.fillMaxWidth().height(60.dp).background(band).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title.uppercase(), color = bandInk, fontSize = 44.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(counter, color = bandInk, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 16.dp))
            }
            Column(Modifier.weight(1f).fillMaxWidth().padding(top = gap)) { body() }
            Text(hint, color = TT_CYAN, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(TT_ROW).padding(horizontal = 8.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(TT_ROW)) {
                for (i in 0..3) Word(words.getOrNull(i), i, picked == i, Modifier.weight(1f).then(if (i == 0 && firstWord != null) Modifier.focusRequester(firstWord) else Modifier))
            }
        }
    }
}

/** The date and a clock with seconds. Only this redraws each second. */
@Composable
private fun TextClock() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000L - System.currentTimeMillis() % 1000L); now = System.currentTimeMillis() } }
    val l = Locale.getDefault()
    val (day, time) = remember(l) {
        val clock = if (android.text.format.DateFormat.is24HourFormat(ctx)) "Hms" else "hms"
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, "EEEdMMM"), l) to SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(l, clock), l)
    }
    Text(day.format(Date(now)), color = TT_CYAN, fontWeight = FontWeight.Bold, maxLines = 1)
    Text(time.format(Date(now)), color = TT_YELLOW, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 20.dp))
}

@Composable
private fun Word(w: ColourWord?, i: Int, picked: Boolean, modifier: Modifier) {
    // A word this page doesn't use is a dim block, so no bright empty key asks to be pressed.
    if (w == null) { Box(modifier.fillMaxHeight().background(TT_DIM)); return }
    TextFocus(modifier.fillMaxHeight(), bg = if (picked) TT_CYAN else WORD_BG[i], onClick = w.act) { f ->
        // German and Russian words run long: past 16 letters they drop a size so they still fit their quarter.
        Text(w.label, color = if (f || picked) TT_BLACK else WORD_INK[i], fontSize = if (w.label.length > 16) 16.sp else 20.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 12.dp))
    }
}

/** Anything that takes focus here: a flat bar that turns cyan. No scale, ring or glow. */
@Composable
internal fun TextFocus(
    modifier: Modifier, bg: Color = Color.Transparent, focusBg: Color = TT_CYAN, onFocus: () -> Unit = {},
    onLongClick: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.pointer(onLongClick = onLongClick, onClick = onClick).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = bg, focusedContainerColor = focusBg, pressedContainerColor = focusBg),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { content(focused) } }
}

/**
 * A numbered line: the number in yellow, an optional kind word, the name, blue leader dots, and the value at the
 * right. Focused, the whole line is a cyan bar with black bold text. [marked] paints it yellow, for a move.
 * Until the remote has shown it has number keys, the number's column stays, empty, so nothing shifts.
 * Left and Right do nothing on a line unless [sideways] (the two-column All apps), or Right goes to [toRight]:
 * the usual search found the colour words at the bottom and the list lost its place.
 */
@Composable
internal fun TextLine(
    number: String?, name: String, value: String, modifier: Modifier = Modifier, valueInk: Color = TT_GREEN,
    kind: Pair<String, Color>? = null, kindWidth: Dp = 96.dp, marked: Boolean = false, badge: Boolean? = null, sideways: Boolean = false, toRight: FocusRequester? = null,
    onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit,
) {
    val held = if (sideways) Modifier else Modifier.focusProperties { left = FocusRequester.Cancel; right = toRight ?: FocusRequester.Cancel }
    TextFocus(modifier.then(held).fillMaxWidth().height(TT_ROW), bg = if (marked) TT_YELLOW else Color.Transparent, focusBg = if (marked) TT_YELLOW else TT_CYAN,
        onFocus = onFocus, onLongClick = onLongClick, onClick = onClick) { f ->
        val on = f || marked
        val weight = if (on) FontWeight.Bold else FontWeight.Medium
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (RemoteKeys.digits) number.orEmpty() else "", color = if (on) TT_BLACK else TT_YELLOW, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(TT_NUM))
            kind?.let { (word, c) -> Text(word, color = if (on) TT_BLACK else c, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.width(kindWidth)) }
            Leader(name, value, if (on) TT_BLACK else TT_WHITE, if (on) TT_BLACK else TT_BLUE, if (on) TT_BLACK else valueInk, weight, Modifier.weight(1f))
            badge?.let { Badge(it, Modifier.padding(start = 10.dp)) }
        }
    }
}

/** ON in black on green, OFF in white on grey. */
@Composable
internal fun Badge(on: Boolean, modifier: Modifier = Modifier) {
    Text((if (on) tr("On") else tr("Off")).uppercase(), color = if (on) TT_BLACK else TT_WHITE, fontWeight = FontWeight.Bold, maxLines = 1,
        modifier = modifier.background(if (on) TT_GREEN else TT_DIM).padding(horizontal = 6.dp))
}

/**
 * The name, the dots and the value on one line. The name gets the room first, keeping three dots' worth; the
 * value is cut to what's left, and the dots fill the rest in whole characters, flush against the value.
 */
@Composable
private fun Leader(name: String, value: String, ink: Color, dots: Color, valueInk: Color, weight: FontWeight, modifier: Modifier) {
    Layout({
        Text(name, color = ink, fontWeight = weight, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Text(DOTS, color = dots, fontWeight = weight, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, letterSpacing = 0.15.em)
        Text(value, color = valueInk, fontWeight = weight, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }, modifier) { ms, c ->
        val gap = 8.dp.roundToPx()
        val minDots = 40.dp.roundToPx()
        // Plex Mono is 0.6 em a letter; with the 0.15 em spacing a dot takes 0.75 of the 20 sp size.
        val dot = 15.sp.toPx()
        val n = ms[0].measure(Constraints(maxWidth = (c.maxWidth - minDots - 2 * gap).coerceAtLeast(0)))
        val v = ms[2].measure(Constraints(maxWidth = (c.maxWidth - n.width - minDots - 2 * gap).coerceAtLeast(0)))
        val room = (c.maxWidth - n.width - v.width - 2 * gap).coerceAtLeast(0)
        val run = (kotlin.math.floor(room / dot) * dot).toInt()
        val d = ms[1].measure(Constraints(minWidth = run, maxWidth = run))
        val h = maxOf(n.height, v.height, d.height)
        layout(c.maxWidth, h) {
            n.place(0, (h - n.height) / 2)
            d.place(c.maxWidth - v.width - gap - run, (h - d.height) / 2)
            v.place(c.maxWidth - v.width, (h - v.height) / 2)
        }
    }
}

/** How wide a column of these words is on the grid: 12 dp a letter at 20 sp, and a space after. */
internal fun gridWidth(words: List<String>): Dp = ((words.maxOf { it.length } + 1) * 12).dp

/** A section head in capitals, in its colour. */
@Composable
internal fun TextHead(label: String, colour: Color) {
    Text(label.uppercase(), color = colour, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.height(TT_ROW).padding(horizontal = 8.dp))
}

/** A line of plain text on the grid. */
@Composable
internal fun TextRow(text: String, colour: Color = TT_WHITE, modifier: Modifier = Modifier) {
    Text(text, color = colour, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.height(TT_ROW).padding(horizontal = 8.dp))
}

/** A bar of blocks: [filled] in [on], the rest dim. */
@Composable
internal fun Blocks(filled: Int, of: Int, on: Color, block: Dp, modifier: Modifier = Modifier) {
    Row(modifier.height(TT_ROW).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(of) { i -> Box(Modifier.width(block).height(18.dp).background(if (i < filled) on else TT_DIM)) }
    }
}

/** What Teletext's Home needs from the screen around it: the other pages, and the four actions of a dock move. */
internal class TextActions(
    val apps: () -> Unit, val find: () -> Unit, val settings: () -> Unit, val inputs: (() -> Unit)?,
    val takeOut: () -> Unit, val done: () -> Unit, val add: () -> Unit, val cancel: () -> Unit, val customize: () -> Unit,
)

/** "S2 E4", or whichever of those the app gave. */
private fun episodeNumber(it: WatchItem): String? = when {
    it.season != null && it.episode != null -> tr("S{season} E{episode}", "season" to it.season, "episode" to it.episode)
    it.episode != null -> tr("E{episode}", "episode" to it.episode)
    it.season != null -> tr("S{season}", "season" to it.season)
    else -> null
}

private fun minutesLeft(it: WatchItem): Int? = if (it.duration > 0 && it.position > 0) ((it.duration - it.position) / 60_000L).toInt().takeIf { m -> m > 0 } else null

/** "today", "yesterday", "3 days ago": when an app was last open. */
internal fun usedWhen(at: Long, now: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance()
    fun day(t: Long): Long { cal.timeInMillis = t; return cal.get(java.util.Calendar.YEAR) * 400L + cal.get(java.util.Calendar.DAY_OF_YEAR) }
    val days = ((now - at) / 86_400_000L).toInt().let { if (it == 0 && day(now) != day(at)) 1 else it }
    return when {
        days <= 0 -> tr("today")
        days == 1 -> tr("yesterday")
        days < 7 -> tr("{n} days ago", "n" to days)
        days < 14 -> tr("last week")
        else -> tr("{n} weeks ago", "n" to days / 7)
    }
}

/**
 * Home in Teletext: the dock as lines 1 to 6, then All apps, Search, Quick settings and Customize Home. At the
 * right, what there is to continue, the weather when it's on, and the network and debugging. While a dock app
 * moves, the page becomes Arrange the dock, and [pick] is the colour word the arrows have reached.
 */
@Composable
internal fun TeletextLayout(home: Home, ui: Ui, seen: Int, moving: Moving?, pick: Int?, a: TextActions) {
    val ctx = LocalContext.current
    val dock = home.dock.take(TEXT_DOCK)
    val movingApp = moving?.takeIf { m -> m.kind == "main" && dock.any { it.pkg == m.pkg } }
    // The Arrange page replaces this one, so its focused line is gone when it closes: focus goes back to the app that moved.
    var back by remember { mutableStateOf<String?>(null) }
    if (movingApp != null) { back = movingApp.pkg; return TextArrange(movingApp, dock, ui, pick, a) }
    val backTo = remember { FocusRequester() }
    val used by produceState(emptyMap<String, Long>(), seen, dock.map { it.pkg }) { value = withContext(Dispatchers.IO) { Recent.lastUsed(ctx, dock.map { it.pkg }) } }
    val debugging by produceState(false, seen) { value = withContext(Dispatchers.IO) { TvSettings.debuggingOn(ctx) } }
    val network = remember(seen) { TvSettings.network(ctx).substringBefore(" · ") }
    val room = remember(seen) { tvName(ctx) ?: tr("Home") }
    // Home's first focus can be in a section Teletext doesn't draw: then it starts on line 1.
    val start = remember { FocusRequester() }
    val ownStart = ui.focusKey?.startsWith("dock:") != true
    LaunchedEffect(ownStart, dock.firstOrNull()?.pkg) { if (ownStart) { delay(60); runCatching { start.requestFocus() } } }
    // Taken out of the dock, the app has no line here: then Home's first one.
    LaunchedEffect(back) {
        if (back == null) return@LaunchedEffect
        delay(60)
        listOf(backTo, ui.focus, start).firstOrNull { runCatching { it.requestFocus() }.isSuccess }
        back = null
    }
    // The lines after the dock: numbered straight on from it, so five dock apps make All apps 6.
    val pages = listOf(tr("All apps") to a.apps, tr("Search") to a.find, tr("Quick settings") to a.settings, tr("Customize Home") to a.customize)
    // Down from the last line goes to the first colour word; the usual search picked the one under the line's middle.
    val apps = remember { FocusRequester() }
    TextPage(tr("Home"), room, TT_BLUE, TT_YELLOW, "1/1",
        if (RemoteKeys.digits) tr("Press a number to open it, or move with the arrows.") else tr("Move with the arrows. OK opens it."),
        listOf(ColourWord(tr("Apps"), a.apps), ColourWord(tr("Search"), a.find), ColourWord(tr("Settings"), a.settings), a.inputs?.let { ColourWord(tr("Inputs"), it) }),
        firstWord = apps,
        // Six dock apps and four more lines fill the page to the hint.
        gap = if (dock.size >= TEXT_DOCK) 0.dp else 15.dp,
        keys = { e ->
            val n = if (e.type == KeyEventType.KeyDown) digitKey(e.key) else null
            when {
                n == null -> false
                else -> {
                    val line = if (n == 0) 10 else n
                    if (line <= dock.size) { launch(ctx, dock[line - 1]); true }
                    else pages.getOrNull(line - dock.size - 1)?.let { it.second(); true } ?: false
                }
            }
        }) {
        // Right from the index goes to what there is to continue, when there is something.
        val watchLine = remember { FocusRequester() }
        val toWatch = watchLine.takeIf { home.watch.isNotEmpty() }
        Row {
            Column(Modifier.width(500.dp)) {
                TextHead(tr("Index"), TT_CYAN)
                dock.forEachIndexed { i, app ->
                    val item = home.watch.firstOrNull { it.pkg == app.pkg }
                    val value = item?.let { w -> listOfNotNull(episodeNumber(w), minutesLeft(w)?.let { Limits.words(it) }).joinToString(" ") }?.ifEmpty { null }
                        ?: used[app.pkg]?.let { usedWhen(it) } ?: ""
                    TextLine("${i + 1}", app.label, value, Modifier.then(ui.focusFor("dock", app)).then(if (i == 0 && ownStart) Modifier.focusRequester(start) else Modifier)
                        .then(if (app.pkg == back) Modifier.focusRequester(backTo) else Modifier),
                        toRight = toWatch, onFocus = { ui.onFocusApp(app) }, onLongClick = { ui.onAppMenu("dock", app) }) { launch(ctx, app) }
                }
                val first = if (dock.isEmpty() && ownStart) Modifier.focusRequester(start) else Modifier
                pages.forEachIndexed { k, (label, go) ->
                    TextLine("${(dock.size + k + 1) % 10}", label, "", (if (k == 0) first else Modifier).then(if (k == pages.lastIndex) Modifier.focusProperties { down = apps } else Modifier),
                        toRight = toWatch, onClick = go)
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                home.watch.firstOrNull()?.let { w ->
                    val app = home.app(w.pkg)
                    TextHead(tr("Continue"), TT_MAGENTA)
                    TextFocus(Modifier.focusRequester(watchLine).fillMaxWidth().height(TT_ROW), onLongClick = { ui.onWatchMenu(w) },
                        onClick = { (WatchNext.intent(ctx, w) ?: app?.launch)?.let { open(ctx, w.pkg, it, app?.launch) } }) { f ->
                        Text(w.title, color = if (f) TT_BLACK else TT_WHITE, fontWeight = if (f) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    val meta = listOfNotNull(episodeNumber(w), minutesLeft(w)?.let { tr("{time} left", "time" to Limits.words(it)) }).joinToString(", ").ifEmpty { app?.label.orEmpty() }
                    TextRow(meta, TT_CYAN)
                    w.progress?.let { p -> Blocks((p * 10).toInt().coerceIn(0, 10), 10, TT_GREEN, 22.dp) }
                }
                ui.weather?.let { w ->
                    TextHead(tr("Weather"), TT_YELLOW)
                    TextRow("${w.temp}°  ${w.high}° / ${w.low}°")
                    TextRow(ui.cfg.weatherPlace, TT_CYAN)
                }
                TextHead(tr("Status"), TT_GREEN)
                TextRow(network)
                Row(Modifier.height(TT_ROW).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Debugging"), maxLines = 1)
                    Spacer(Modifier.width(12.dp))
                    Text((if (debugging) tr("On") else tr("Off")).uppercase(), color = TT_WHITE, fontWeight = FontWeight.Bold, maxLines = 1,
                        modifier = Modifier.background(if (debugging) TT_RED else TT_DIM).padding(horizontal = 6.dp))
                }
            }
        }
    }
}

/**
 * Arrange the dock: the six dock lines with the moving one in yellow. Up and Down move it, OK drops it,
 * Back cancels; Left and Right reach the colour words, since the arrows belong to the move.
 */
@Composable
private fun TextArrange(m: Moving, dock: List<TvApp>, ui: Ui, pick: Int?, a: TextActions) {
    val place = dock.indexOfFirst { it.pkg == m.pkg } + 1
    val was = remember(m.pkg) { place }
    TextPage(tr("Arrange"), tr("Arrange"), TT_YELLOW, TT_BLACK, tr("{n} of {all}", "n" to place, "all" to dock.size),
        tr("Moving {app}. The other apps shift to make room.", "app" to m.label),
        listOf(ColourWord(tr("Take out"), a.takeOut), ColourWord(tr("Done"), a.done), ColourWord(tr("Add an app"), a.add), ColourWord(tr("Cancel"), a.cancel)),
        picked = pick) {
        Row {
            Column(Modifier.width(500.dp)) {
                TextHead(tr("The dock"), TT_CYAN)
                dock.forEachIndexed { i, app ->
                    val moves = app.pkg == m.pkg
                    TextLine("${i + 1}", app.label, if (moves) tr("Moving").uppercase() else "", if (moves) ui.focusFor("dock", app) else Modifier,
                        valueInk = TT_BLACK, marked = moves) {}
                }
                if (RemoteKeys.digits) TextRow(tr("The number is the key that opens it."), TT_CYAN)
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f)) {
                TextHead(tr("Keys"), TT_MAGENTA)
                TextRow(tr("Up, Down  move"))
                TextRow(tr("OK  done"))
                TextRow(tr("Back  cancel"))
                TextRow(tr("Left, Right  bottom keys"))
                TextHead(tr("Now"), TT_GREEN)
                TextRow(tr("{app} is {n} of {all}", "app" to m.label, "n" to place, "all" to dock.size))
                TextRow(tr("It was {n} of {all}", "n" to was, "all" to dock.size), TT_CYAN)
            }
        }
    }
}

/**
 * A menu as a page: numbered lines, a number or OK picks one, and the first colour word goes back. Hold OK on
 * an app and the Inputs list use it in Teletext.
 */
@Composable
internal fun TextMenu(name: String, title: String, items: List<Pair<String, () -> Unit>>, focus: FocusRequester, onClose: () -> Unit) {
    TextPage(name, title, TT_WHITE, TT_BLACK, "1/1", if (RemoteKeys.digits) tr("Press a number, or OK on a line.") else tr("OK on a line picks it."), listOf(ColourWord(tr("Back"), onClose)),
        keys = { e ->
            val n = if (e.type == KeyEventType.KeyDown) digitKey(e.key) else null
            n?.let { items.getOrNull(it - 1) }?.let { it.second(); true } ?: false
        }) {
        Column(Modifier.width(500.dp)) {
            items.forEachIndexed { i, (label, act) ->
                TextLine("${i + 1}", label, "", if (i == 0) Modifier.focusRequester(focus) else Modifier, onClick = act)
            }
        }
    }
}
