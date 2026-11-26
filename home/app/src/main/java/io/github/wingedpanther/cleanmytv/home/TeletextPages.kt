package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Twenty apps to a sub-page, in two columns of ten, numbered from 10. */
private const val PER_PAGE = 20
private const val PER_COLUMN = 10

/**
 * All apps in Teletext: A to Z in two columns of ten, numbered from 10, so two digits open any of the
 * first ninety. Right from the right column and Left from the left one turn sub-pages. With [onPick], OK picks
 * an app for the dock instead of opening it.
 */
@Composable
internal fun TextApps(apps: List<TvApp>, dock: Set<String>, onOpen: (TvApp) -> Unit, onMenu: (TvApp) -> Unit, onFind: () -> Unit, onPick: ((TvApp) -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val real = remember(apps) { apps.filter { !InputTiles.isInput(it.pkg) }.sortedBy { it.label.lowercase() } }
    val facts by produceState(emptyMap<String, AppFacts>(), real) { value = withContext(Dispatchers.IO) { real.mapNotNull { a -> appFacts(ctx, a.pkg)?.let { a.pkg to it } }.toMap() } }
    var tab by remember { mutableStateOf("all") }
    var newest by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    val tabs = TABS.filter { t -> t == "all" || real.any { inTab(t, facts[it.pkg]) } }
    val shown = real.filter { inTab(tab, facts[it.pkg]) }.let { l -> if (newest) l.sortedByDescending { it.installed } else l }
    val pages = ((shown.size + PER_PAGE - 1) / PER_PAGE).coerceAtLeast(1)
    if (page >= pages) page = pages - 1
    val first = remember { FocusRequester() }
    // The red word takes focus when the list is empty, so the remote still reaches something.
    val red = remember { FocusRequester() }
    LaunchedEffect(page, tab, newest, shown.isEmpty()) { delay(30); runCatching { if (shown.isEmpty()) red.requestFocus() else first.requestFocus() } }
    // The first digit waits a moment for the second, then gives up.
    LaunchedEffect(typed) { if (typed.isNotEmpty()) { delay(1200); typed = "" } }
    fun go(a: TvApp) { onClose(); if (onPick != null) onPick(a) else onOpen(a) }
    val words = listOf(
        ColourWord(tr("Home"), onClose),
        // Search opens over this page, so Back from it comes back here (H14).
        ColourWord(tr("Search"), onFind).takeIf { onPick == null },
        ColourWord(if (newest) tr("Sort: newest") else tr("Sort: A to Z")) { newest = !newest; page = 0 },
        ColourWord(tr("Show: {kind}", "kind" to tabName(tab).lowercase())) { tab = tabs[(tabs.indexOf(tab) + 1) % tabs.size]; page = 0 },
    )
    val hint = when {
        typed.isNotEmpty() -> tr("{n}_ Now the second digit.", "n" to typed)
        onPick != null -> tr("Pick an app for the dock.")
        // Right only turns a page when there is a second one.
        RemoteKeys.digits && pages > 1 -> tr("Two digits open an app. Right turns the page. Hold OK for more.")
        RemoteKeys.digits -> tr("Two digits open an app. Hold OK for more.")
        pages > 1 -> tr("Right turns the page. Hold OK for more.")
        else -> tr("Hold OK for more.")
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TextPage(tr("Apps"), if (onPick != null) tr("Add to the dock") else tr("All apps"), TT_GREEN, TT_BLACK, "${page + 1}/$pages", hint, words, firstWord = red,
            keys = { e ->
                if (e.type != KeyEventType.KeyDown) return@TextPage false
                val d = digitKey(e.key)
                when {
                    d != null && typed.isEmpty() -> { if (d > 0) typed = "$d"; true }
                    d != null -> { shown.getOrNull("$typed$d".toInt() - 10)?.let { go(it) }; typed = ""; true }
                    e.key == Key.ChannelDown || e.key == Key.PageDown -> { if (page < pages - 1) page++; true }
                    e.key == Key.ChannelUp || e.key == Key.PageUp -> { if (page > 0) page--; true }
                    else -> false
                }
            }) {
            if (shown.isEmpty()) TextRow(tr("No apps here."), TT_META)
            Row {
                for (col in 0 until 2) {
                    Column(Modifier.weight(1f)) {
                        shown.drop(page * PER_PAGE + col * PER_COLUMN).take(PER_COLUMN).forEachIndexed { i, a ->
                            val at = page * PER_PAGE + col * PER_COLUMN + i
                            val f = facts[a.pkg]
                            val (value, ink) = when {
                                a.pkg in dock -> tr("dock") to TT_GREEN
                                f?.sideloaded == true -> tr("sideloaded") to TT_MAGENTA
                                else -> "" to TT_WHITE
                            }
                            TextLine((at + 10).takeIf { it <= 99 }?.toString(), a.label, value,
                                Modifier.then(if (at == page * PER_PAGE) Modifier.focusRequester(first) else Modifier).onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when {
                                        col == 1 && e.key == Key.DirectionRight && page < pages - 1 -> { page++; true }
                                        col == 0 && e.key == Key.DirectionLeft && page > 0 -> { page--; true }
                                        else -> false
                                    }
                                },
                                valueInk = ink, sideways = true, onLongClick = { if (onPick == null) onMenu(a) }) { go(a) }
                        }
                    }
                    if (col == 0) Spacer(Modifier.width(36.dp))
                }
            }
        }
    }
}

/**
 * Search this TV in Teletext: the query with a block cursor, the alphabet on one line, then numbered results,
 * two rows each. Number keys open results here, so digits are typed through 123. The letter row wraps both ways.
 */
@Composable
internal fun TextFind(apps: List<TvApp>, dock: Set<String>, onApp: (TvApp) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val last = remember { FocusRequester() }
    val hits = remember(query, apps) { searchTv(ctx, query, apps, dock, emptyMap(), onClose) { onClose(); onApp(it) } }
    fun type(s: String) { query = (query + s).take(MAX_QUERY) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val words = listOf(
        ColourWord(tr("Home"), onClose),
        ColourWord(tr("Space")) { type(" ") },
        ColourWord(tr("Delete")) { query = query.dropLast(1) },
        ColourWord(if (digits) "abc" else "123") { digits = !digits },
    )
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val hint = when {
            query.isBlank() -> if (RemoteKeys.digits) tr("Pick letters or type. Number keys open results.") else tr("Pick letters, or type on a keyboard.")
            RemoteKeys.digits -> tr("Press a number to open a result.")
            else -> tr("Down goes to the results. OK opens one.")
        }
        TextPage(tr("Search"), tr("Find"), TT_MAGENTA, TT_BLACK, if (query.isBlank()) "" else tr("{n} found", "n" to hits.size), hint, words,
            keys = { e ->
                if (e.type != KeyEventType.KeyDown) return@TextPage false
                val d = digitKey(e.key)
                val c = e.utf16CodePoint
                when {
                    d != null -> { hits.getOrNull(d - 1)?.go?.invoke(); true }
                    e.key == Key.Backspace || e.key == Key.Delete -> { query = query.dropLast(1); true }
                    c > 0 && !Character.isISOControl(c) && e.key != Key.DirectionCenter && e.key != Key.Enter -> { type(String(Character.toChars(c)).lowercase()); true }
                    else -> false
                }
            }) {
            Row(Modifier.height(TT_ROW).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Enter:") + " ", color = TT_CYAN, maxLines = 1)
                Text(query, fontWeight = FontWeight.Bold, maxLines = 1)
                Box(Modifier.padding(start = 3.dp).width(12.dp).height(22.dp).background(TT_WHITE))
            }
            val keys = if (digits) DIGITS.joinToString("").take(26) else "abcdefghijklmnopqrstuvwxyz"
            Row(Modifier.fillMaxWidth().padding(top = 3.dp).height(TT_ROW)) {
                keys.forEachIndexed { i, ch ->
                    val end = i == keys.length - 1
                    val wrap = Modifier.onPreviewKeyEvent { e ->
                        when {
                            e.type != KeyEventType.KeyDown -> false
                            i == 0 && e.key == Key.DirectionLeft -> runCatching { last.requestFocus() }.isSuccess
                            end && e.key == Key.DirectionRight -> runCatching { first.requestFocus() }.isSuccess
                            else -> false
                        }
                    }
                    TextFocus(Modifier.weight(1f).height(TT_ROW).then(if (i == 0) Modifier.focusRequester(first) else if (end) Modifier.focusRequester(last) else Modifier).then(wrap),
                        onClick = { type(ch.toString()) }) { f ->
                        Text(ch.toString(), color = if (f) TT_BLACK else TT_WHITE, fontWeight = if (f) FontWeight.Bold else FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (query.isNotBlank() && hits.isEmpty()) TextRow(tr("Nothing on this TV is called that."), TT_META, Modifier.padding(top = 6.dp))
            val kinds = listOf(tr("App"), tr("Home"), tr("Setting")).map { it.uppercase() }
            val kindWidth = maxOf(gridWidth(kinds), 96.dp)
            LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
                hits.forEachIndexed { i, h ->
                    item(h.kind.toString() + h.title) {
                        val kind = when (h.kind) { Hit.APP -> kinds[0] to TT_YELLOW; Hit.HOME -> kinds[1] to TT_CYAN; else -> kinds[2] to TT_MAGENTA }
                        TextLine((i + 1).takeIf { it <= 9 }?.toString(), h.title, h.action, kind = kind, kindWidth = kindWidth, onClick = h.go)
                        // The grid's one size, like every other line here; the grey tells it apart.
                        Text(h.sub, color = TT_META, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(TT_ROW).padding(start = TT_NUM + kindWidth + 8.dp, end = 8.dp))
                    }
                }
            }
        }
    }
}

/** Brightness's line, whose bar shows until Volume is focused. */
private const val BRIGHTNESS_LINE = 8

/** One line on the Quick settings page: a switch shows ON or OFF, a picker its value; [bar] for volume and brightness. */
private class SetLine(val label: String, val value: String, val on: Boolean? = null, val bar: TvSettings.Choice? = null, val step: ((Int) -> Unit)? = null, val act: () -> Unit)

/**
 * Quick settings in Teletext: a full page of numbered lines, the last one Customize Home. A number flips a switch
 * or steps a picker, Left and Right change the focused one, and a THIS TV column says what the TV is.
 */
@Composable
internal fun TextSettings(onEditor: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var armOff by remember { mutableStateOf(false) }
    var barFor by remember { mutableIntStateOf(BRIGHTNESS_LINE) }
    val first = remember { FocusRequester() }
    fun open(i: Intent?) { i ?: return; runCatching { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun stepper(c: TvSettings.Choice): ((Int) -> Unit)? = c.set?.let { set -> { d -> val i = (c.current + d).coerceIn(0, c.options.size - 1); if (i != c.current) { set(i); tick++ } } }
    val sleepAt by SleepTimer.at.collectAsState()
    val lines = remember(tick, armOff, sleepAt) {
        val dns = TvSettings.dns(ctx)
        val adb = TvSettings.debuggingOn(ctx)
        val saver = TvSettings.screensaver(ctx)
        val volume = TvSettings.volume(ctx)
        val brightness = TvSettings.brightness(ctx)
        val left = sleepAt?.let { ((it - System.currentTimeMillis() + 59_999) / 60_000L).toInt() }?.takeIf { it > 0 }
        val steps = listOf<Int?>(null) + SleepTimer.CHOICES
        val cur = if (left == null) 0 else steps.indexOfFirst { it != null && it >= left }.let { if (it < 0) steps.size - 1 else it }
        listOf(
            SetLine(tr("Network"), TvSettings.network(ctx)) { open(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            SetLine(tr("Remotes & Bluetooth"), tr("open")) { quickActions()["bluetooth"]?.let { a -> resolve(ctx, a)?.let { open(it) } } },
            SetLine(tr("Ad-blocking DNS"), "", on = dns.current == 1) { dns.set?.let { it(1 - dns.current); tick++ } ?: open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            SetLine(tr("Debugging"), if (armOff) tr("OK again turns it off") else "", on = adb) {
                if (!adb) open(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                else if (armOff) { TvSettings.debuggingOff(ctx); armOff = false; tick++ } else armOff = true
            },
            SetLine(tr("Picture & sound"), displayLine(ctx) ?: tr("open")) { quickActions()["display"]?.let { a -> resolve(ctx, a)?.let { open(it) } } },
            SetLine(saver.label, saver.options.getOrElse(saver.current) { "" }, step = stepper(saver)) {
                saver.set?.let { it((saver.current + 1) % saver.options.size); tick++ } ?: open(saver.fallback)
            },
            SetLine(tr("Sleep timer"), left?.let { tr("In {time}", "time" to Limits.words(it)) } ?: tr("Off"),
                step = { d -> val i = (cur + d).coerceIn(0, steps.size - 1); if (i != cur) SleepTimer.set(ctx, steps[i]) }) { SleepTimer.set(ctx, steps[(cur + 1) % steps.size]) },
            SetLine(volume.label, volume.options.getOrElse(volume.current) { "" }, bar = volume, step = stepper(volume)) { open(volume.fallback) },
            SetLine(brightness.label, brightness.options.getOrElse(brightness.current) { "" }, bar = brightness, step = stepper(brightness)) { open(brightness.fallback) },
            SetLine(tr("Customize Home"), "") { onClose(); onEditor() },
        )
    }
    val about = remember { thisTv(ctx) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val words = listOf(
        ColourWord(tr("Home"), onClose),
        ColourWord(tr("Customize")) { onClose(); onEditor() },
        if (KeyService.running != null) ColourWord(tr("Sleep now")) { onClose(); KeyService.sleep(ctx) } else null,
        ColourWord(tr("All settings")) { open(Intent(Settings.ACTION_SETTINGS)) },
    )
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        TextPage(tr("Settings"), tr("Quick settings"), TT_CYAN, TT_BLACK, "1/1",
            if (RemoteKeys.digits) tr("Press a number to switch it, or OK on a line to change it.") else tr("OK on a line changes it. Left and right step it."), words,
            keys = { e ->
                val n = if (e.type == KeyEventType.KeyDown) digitKey(e.key) else null
                // 1 to 9 are the first nine lines, and 0 the tenth.
                n?.let { lines.getOrNull((it + 9) % 10) }?.let { it.act(); true } ?: false
            }) {
            Row {
                Column(Modifier.width(530.dp)) {
                    lines.forEachIndexed { i, l ->
                        TextLine("${(i + 1) % 10}", l.label, l.value,
                            Modifier.then(if (i == 0) Modifier.focusRequester(first) else Modifier).onPreviewKeyEvent { e ->
                                val step = l.step
                                if (step == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) { Key.DirectionLeft -> { step(-1); true }; Key.DirectionRight -> { step(1); true }; else -> false }
                            },
                            valueInk = TT_CYAN, badge = l.on, onFocus = { if (l.bar != null) barFor = i; if (i != 3) armOff = false }, onClick = l.act)
                    }
                }
                Spacer(Modifier.width(36.dp))
                Column(Modifier.weight(1f)) {
                    lines.getOrNull(barFor)?.takeIf { it.bar != null }?.let { l ->
                        val c = l.bar!!
                        TextHead(l.label, TT_YELLOW)
                        val part = if (c.options.size > 1) c.current / (c.options.size - 1f) else 0f
                        Blocks(Math.round(part * 16), 16, TT_YELLOW, 11.dp)
                        TextRow(if (c.set != null) tr("Left and right change it") else tr("OK opens settings"), TT_CYAN)
                    }
                    TextHead(tr("This TV"), TT_GREEN)
                    about.forEachIndexed { i, s -> TextRow(s, if (i == 0) TT_WHITE else TT_CYAN) }
                }
            }
        }
    }
}

/** Android's version, the free space, and how long since the TV started. */
private fun thisTv(ctx: android.content.Context): List<String> {
    val android = tr("Android {version}", "version" to android.os.Build.VERSION.RELEASE)
    val space = runCatching {
        val s = StatFs(Environment.getDataDirectory().path)
        fun gb(b: Long) = if (b >= 10_000_000_000L) "${b / 1_000_000_000L} GB" else String.format(java.util.Locale.getDefault(), "%.1f GB", b / 1e9)
        tr("{free} free of {all}", "free" to gb(s.availableBytes), "all" to gb(s.totalBytes))
    }.getOrNull()
    val minutes = (SystemClock.elapsedRealtime() / 60_000L).toInt()
    val up = if (minutes < 48 * 60) tr("On for {time}", "time" to Limits.words(if (minutes < 60) minutes else minutes / 60 * 60)) else tr("On for {n} days", "n" to minutes / (24 * 60))
    return listOfNotNull(android, space, up)
}
