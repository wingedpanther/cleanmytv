package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.customization.CustomizationActivity
import io.github.wingedpanther.cleanmytv.home.customization.searchSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The colours of Search this TV and the All apps page: Bau's charcoal and cream in Bau, Home's own dark panel
 * otherwise. Bau also writes everything in lower case and marks apps with its shapes instead of their icons.
 */
internal class PageLook(val bau: Boolean, val ground: Color, val ink: Color, val text: Color, val muted: Color, val line: Color, val focus: Color, val onFocus: Color, val cursor: Color) {
    fun case(s: String) = if (bau) s.lowercase() else s

    companion object {
        fun of(cfg: HomeConfig) = if (cfg.layout == "bau") PageLook(true, BAU_INK, BAU_INK, BAU_CREAM, BAU_MUTED, BAU_CREAM, BAU_CREAM, BAU_INK, BAU_RED)
        else PageLook(false, Color(0xFF121A15), Color(0xFF121A15), Color(0xFFF0EBE5), Color(0xFFD7CFC4), Color.White.copy(alpha = 0.22f), Color(0xFFF0EBE5), Color(0xFF121A15), Color(cfg.accent))
    }
}

private val PAGE_X = 56.dp
private val PAGE_Y = 36.dp

// ---- Search this TV ---------------------------------------------------------------------------------

/** One search result: an app ([APP]), one of the TV's own settings ([TV]) or one of Home's ([HOME]). */
internal class Hit(val kind: Int, val title: String, val sub: String, val action: String, val go: () -> Unit) {
    companion object { const val APP = 0; const val HOME = 1; const val TV = 2 }
}

/** The TV's own settings screens, with the words people use for them. Only those this TV has are offered. */
private fun tvSettings(): List<Triple<String, String, String>> = listOf(
    Triple(tr("Network & internet"), "wifi wi-fi network internet ethernet ip", Settings.ACTION_WIFI_SETTINGS),
    Triple(tr("Remotes & Bluetooth"), "bluetooth remote controller headphones pair", Settings.ACTION_BLUETOOTH_SETTINGS),
    Triple(tr("Display"), "display picture resolution hdr screen", Settings.ACTION_DISPLAY_SETTINGS),
    Triple(tr("Sound"), "sound audio speaker soundbar arc volume", Settings.ACTION_SOUND_SETTINGS),
    Triple(tr("Date & time"), "date time clock timezone", Settings.ACTION_DATE_SETTINGS),
    Triple(tr("Language"), "language locale", Settings.ACTION_LOCALE_SETTINGS),
    Triple(tr("Keyboard"), "keyboard input typing", Settings.ACTION_INPUT_METHOD_SETTINGS),
    Triple(tr("Accessibility"), "accessibility captions subtitles talkback", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    Triple(tr("Storage"), "storage space disk usb", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    Triple(tr("Apps"), "apps applications uninstall permissions", Settings.ACTION_APPLICATION_SETTINGS),
    Triple(tr("Screensaver"), "screensaver ambient daydream", Settings.ACTION_DREAM_SETTINGS),
    Triple(tr("Developer options"), "developer debugging adb usb", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    Triple(tr("About"), "about device info version update system", Settings.ACTION_DEVICE_INFO_SETTINGS),
    Triple(tr("All settings"), "settings", Settings.ACTION_SETTINGS),
)

private fun start(ctx: Context, i: Intent) =
    runCatching { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "search: $it") }

/**
 * What [query] finds on this TV: apps by name, then each one's info screen, the TV's settings screens and
 * Home's own settings. Files would need storage access, which Home doesn't ask for.
 */
internal fun searchTv(ctx: Context, query: String, apps: List<TvApp>, dock: Set<String>, kinds: Map<String, String>, onApp: (TvApp) -> Unit): List<Hit> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val found = apps.filter { q in it.label.lowercase() }.sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }.take(6)
    val hits = ArrayList<Hit>()
    for (a in found) {
        val sub = listOfNotNull(kinds[a.pkg], tr("in favourites").takeIf { a.pkg in dock }).joinToString(" · ").ifEmpty { tr("app") }
        hits += Hit(Hit.APP, a.label, sub, tr("open")) { onApp(a) }
    }
    for (a in found.filter { !InputTiles.isInput(it.pkg) }.take(2)) {
        hits += Hit(Hit.TV, tr("{app}: app info", "app" to a.label), tr("apps › {app}", "app" to a.label), tr("open")) {
            start(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", a.pkg, null)))
        }
    }
    val pm = ctx.packageManager
    for ((name, words, action) in tvSettings()) {
        if (q !in name.lowercase() && words.split(' ').none { it.startsWith(q) }) continue
        val i = Intent(action)
        if (i.resolveActivity(pm) == null) continue
        hits += Hit(Hit.TV, name, tr("tv settings"), tr("open")) { start(ctx, i) }
    }
    for (f in searchSettings(q).take(4)) {
        hits += Hit(Hit.HOME, tr(f.name), tr("home › {section}", "section" to f.where.label), tr("open")) {
            start(ctx, Intent(ctx, CustomizationActivity::class.java).putExtra(CustomizationActivity.FIND, f.name))
        }
    }
    return hits
}

private val LETTERS = listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz-.")
private val DIGITS = listOf("1234567", "890&'+!", "?:/@#()", "\"_=*,;%")

/**
 * Search this TV: a keyboard for the remote on the left, the big query over a rule, and what it finds on the
 * right. Number keys and a real keyboard type too, and the mic key says it when the TV can turn speech into text.
 */
@Composable
internal fun FindPage(cfg: HomeConfig, apps: List<TvApp>, dock: Set<String>, onApp: (TvApp) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val look = remember(cfg.layout, cfg.accent) { PageLook.of(cfg) }
    var query by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val kinds by produceState(emptyMap<String, String>(), apps) { value = withContext(Dispatchers.IO) { apps.associate { it.pkg to (kindWord(ctx, it.pkg) ?: "") }.filterValues { it.isNotEmpty() } } }
    val hits = remember(query, apps, kinds) { searchTv(ctx, query, apps, dock, kinds) { onClose(); onApp(it) } }
    val speech = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).takeIf { it.resolveActivity(ctx.packageManager) != null } }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it.lowercase().take(MAX_QUERY) }
    }
    fun type(s: String) { query = (query + s).take(MAX_QUERY) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Row(
            Modifier.fillMaxSize().background(look.ground).padding(horizontal = PAGE_X, vertical = PAGE_Y).onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val c = e.utf16CodePoint
                when {
                    e.key == Key.Backspace || e.key == Key.Delete -> { query = query.dropLast(1); true }
                    (e.key == Key.MediaPlayPause || e.key == Key.Search || e.key == Key.VoiceAssist) && speech != null -> { runCatching { listen.launch(speech) }; true }
                    c > 0 && !Character.isISOControl(c) && e.key != Key.DirectionCenter && e.key != Key.Enter -> { type(String(Character.toChars(c)).lowercase()); true }
                    else -> false
                }
            },
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            Column(Modifier.width(470.dp).fillMaxHeight()) {
                Text(look.case(tr("Search")), color = look.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.height(104.dp), verticalAlignment = Alignment.Bottom) {
                    Text(query, color = look.text, fontSize = if (query.length <= 8) 88.sp else if (query.length <= 14) 60.sp else 40.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = Modifier.weight(1f, fill = false).offset(y = 8.dp))
                    Box(Modifier.padding(start = 4.dp, bottom = 10.dp).width(16.dp).height(72.dp).background(look.cursor))
                }
                Box(Modifier.padding(top = 6.dp, bottom = 20.dp).fillMaxWidth().height(1.dp).background(look.line))
                Column(Modifier.border(1.dp, look.line.copy(alpha = 0.5f)).focusGroup()) {
                    (if (digits) DIGITS else LETTERS).forEachIndexed { r, row ->
                        Row {
                            row.forEachIndexed { i, ch ->
                                PageKey(ch.toString(), look, Modifier.weight(1f).then(if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier)) { type(ch.toString()) }
                            }
                        }
                    }
                    Row {
                        PageKey(look.case(tr("Space")), look, Modifier.weight(3f)) { type(" ") }
                        PageKey(if (digits) "abc" else "123", look, Modifier.weight(2f)) { digits = !digits }
                        PageKey(look.case(tr("Delete")), look, Modifier.weight(2f)) { query = query.dropLast(1) }
                    }
                    if (speech != null) PageKey(look.case(tr("Say it")), look, Modifier.fillMaxWidth()) { runCatching { listen.launch(speech) } }
                }
                Spacer(Modifier.weight(1f))
                Text(look.case(if (speech != null) tr("Say it types what you say. Back closes this.") else tr("Number keys and a keyboard type too. Back closes this.")),
                    color = look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(look.case(tr("Results")), color = look.text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    if (query.isNotBlank()) Text(look.case(tr("{n} on this TV", "n" to hits.size)), color = look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(look.line))
                if (query.isBlank()) Text(look.case(tr("Type a name. It finds apps, the TV's settings and Home's own.")), color = look.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
                else if (hits.isEmpty()) Text(look.case(tr("Nothing on this TV is called that.")), color = look.muted, fontSize = 15.sp, modifier = Modifier.padding(top = 18.dp))
                LazyColumn(Modifier.weight(1f).focusGroup()) {
                    items(hits, key = { it.kind.toString() + it.title }) { h -> HitRow(h, look) }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    for ((k, name) in listOf(Hit.APP to tr("app"), Hit.TV to tr("tv setting"), Hit.HOME to tr("home setting"))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HitMark(k, 12.dp)
                            Text(look.case(name), color = look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_QUERY = 30

/** The legend's shapes: a yellow circle for apps, a blue square for Home's settings, a red triangle for the TV's. */
@Composable
private fun HitMark(kind: Int, size: Dp) = when (kind) {
    Hit.APP -> BauGlyph(0, BAU_YELLOW, size)
    Hit.HOME -> BauGlyph(1, BAU_BLUE, size)
    else -> BauGlyph(2, BAU_RED, size)
}

@Composable
private fun PageKey(label: String, look: PageLook, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.pointer(onClick = onClick).height(46.dp).border(0.5.dp, look.line.copy(alpha = 0.5f)).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = look.focus),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = if (focused) look.onFocus else look.text, fontSize = if (label.length == 1) 20.sp else 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HitRow(h: Hit, look: PageLook) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = h.go,
        modifier = Modifier.pointer(onClick = h.go).fillMaxWidth().height(80.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = look.focus),
    ) {
        val ink = if (focused) look.onFocus else look.text
        Column {
            Row(Modifier.weight(1f).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                HitMark(h.kind, 24.dp)
                Column(Modifier.weight(1f).padding(start = 22.dp)) {
                    Text(look.case(h.title), color = ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(look.case(h.sub), color = if (focused) look.onFocus.copy(alpha = 0.7f) else look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(look.case(h.action), color = ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp, end = 8.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(look.line.copy(alpha = 0.6f)))
        }
    }
}

// ---- All apps ---------------------------------------------------------------------------------------

/** One app's facts for the All apps page: its tab, where it came from, and the line under the grid. */
internal class AppFacts(val tab: String, val sideloaded: Boolean, val system: Boolean, val line: String)

/** The tabs, in order; each shows only when it has apps. */
private val TABS = listOf("all", "media", "games", "tools", "other", "sideloaded", "system")

private fun tabName(id: String) = when (id) {
    "all" -> tr("All"); "media" -> tr("Media"); "games" -> tr("Games"); "tools" -> tr("Tools")
    "other" -> tr("Other"); "sideloaded" -> tr("Sideloaded"); else -> tr("System")
}

/** Its declared category as a word, or null when it declares none. */
private fun kindWord(ctx: Context, pkg: String): String? = when (runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).category }.getOrDefault(-1)) {
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> tr("media")
    ApplicationInfo.CATEGORY_GAME -> tr("game")
    ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_SOCIAL -> tr("tool")
    else -> null
}

private val STORES = mapOf("com.android.vending" to "Play Store", "com.amazon.venezia" to "Amazon Appstore", "com.google.android.feedback" to "Play Store")

/** Version, size on disk, and where it came from: "v6.1.0 · 38 MB · sideloaded, 12 Nov". */
internal fun appFacts(ctx: Context, pkg: String): AppFacts? = runCatching {
    val pm = ctx.packageManager
    val info = pm.getPackageInfo(pkg, 0)
    val ai = info.applicationInfo ?: return null
    val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
    @Suppress("DEPRECATION")
    val installer = runCatching { if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName else pm.getInstallerPackageName(pkg) }.getOrNull()
    val store = installer?.let { STORES[it] }
    val sideloaded = !system && store == null
    val tab = when (ai.category) {
        ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> "media"
        ApplicationInfo.CATEGORY_GAME -> "games"
        ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_ACCESSIBILITY -> "tools"
        else -> "other"
    }
    val bytes = (listOf(ai.sourceDir) + ai.splitSourceDirs.orEmpty()).sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
    val size = if (bytes >= 1_000_000) "${bytes / 1_000_000} MB" else "${(bytes / 1000).coerceAtLeast(1)} KB"
    val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(info.firstInstallTime))
    val from = when {
        system -> tr("came with the TV")
        store != null -> tr("from {store}, {day}", "store" to store, "day" to day)
        installer == "com.android.shell" -> tr("sideloaded with ADB, {day}", "day" to day)
        else -> tr("sideloaded, {day}", "day" to day)
    }
    AppFacts(tab, sideloaded, system, listOfNotNull(info.versionName?.let { "v$it" }, size, from).joinToString(" · "))
}.getOrNull()

/**
 * Every app on the TV on one page, sorted by name, with tabs by kind, which ones were sideloaded, and the
 * focused app's version, size and source underneath. Hold OK has the same menu as on Home.
 */
@Composable
internal fun AppsPage(cfg: HomeConfig, apps: List<TvApp>, onOpen: (TvApp) -> Unit, onMenu: (TvApp) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val look = remember(cfg.layout, cfg.accent) { PageLook.of(cfg) }
    val real = remember(apps) { apps.filter { !InputTiles.isInput(it.pkg) }.sortedBy { it.label.lowercase() } }
    val facts by produceState(emptyMap<String, AppFacts>(), real) { value = withContext(Dispatchers.IO) { real.mapNotNull { a -> appFacts(ctx, a.pkg)?.let { a.pkg to it } }.toMap() } }
    var tab by remember { mutableStateOf("all") }
    var focusedApp by remember { mutableStateOf<TvApp?>(null) }
    fun inTab(t: String, a: TvApp): Boolean {
        val f = facts[a.pkg] ?: return t == "all"
        return when (t) { "all" -> true; "sideloaded" -> f.sideloaded; "system" -> f.system; else -> f.tab == t && !f.system }
    }
    val tabs = TABS.filter { t -> t == "all" || real.any { inTab(t, it) } }
    val shown = real.filter { inTab(tab, it) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(look.ground).padding(horizontal = PAGE_X, vertical = PAGE_Y)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(look.case(tr("All apps")), color = look.text, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false, modifier = Modifier.offset(y = 10.dp))
                Spacer(Modifier.weight(1f))
                Row(Modifier.focusGroup().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    tabs.forEach { t -> TabChip(look.case(tabName(t)), real.count { inTab(t, it) }, t == tab, look) { tab = t } }
                }
            }
            Spacer(Modifier.height(26.dp))
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.weight(1f)) {
                items(shown, key = { it.pkg }) { a ->
                    AppCell(a, facts[a.pkg], look, Modifier.then(if (a == shown.firstOrNull()) Modifier.focusRequester(first) else Modifier), onFocus = { focusedApp = a }, onLong = { onMenu(a) }) { onClose(); onOpen(a) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                focusedApp?.let { a ->
                    Text(look.case(a.label), color = look.text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    Text(look.case(facts[a.pkg]?.line.orEmpty()), color = look.muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 14.dp).weight(1f))
                } ?: Spacer(Modifier.weight(1f))
                Text(look.case(tr("Hold OK")), color = look.text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.border(1.dp, look.text).padding(horizontal = 6.dp, vertical = 3.dp))
                Text(look.case(tr("move, hide, app info, uninstall")), color = look.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
private fun TabChip(name: String, count: Int, on: Boolean, look: PageLook, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.pointer(onClick = onClick).onFocusChanged { focused = it.isFocused; if (it.isFocused) onClick() },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = look.focus),
    ) {
        val ink = if (focused) look.onFocus else look.text
        Column(Modifier.width(IntrinsicSize.Max).padding(horizontal = 4.dp, vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(" $count", color = if (focused) ink.copy(alpha = 0.7f) else look.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(if (on) ink else Color.Transparent))
        }
    }
}

@Composable
private fun AppCell(app: TvApp, facts: AppFacts?, look: PageLook, modifier: Modifier, onFocus: () -> Unit, onLong: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick, onLongClick = onLong,
        modifier = modifier.pointer(onLongClick = onLong, onClick = onClick).height(106.dp).border(0.5.dp, look.line).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = look.focus),
    ) {
        val ink = if (focused) look.onFocus else look.text
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(Modifier.weight(1f)) {
                if (look.bau) bauMark(app.pkg).let { (form, colour) -> BauGlyph(form, colour, 28.dp) }
                else Image(app.art, null, Modifier.height(34.dp).width(if (app.banner) 60.dp else 34.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.weight(1f))
                if (facts?.sideloaded == true) Text(look.case(tr("Sideloaded")), color = if (focused) ink.copy(alpha = 0.6f) else look.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(look.case(app.label), color = ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
