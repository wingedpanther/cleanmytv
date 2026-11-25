package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val PAGE_X = 48.dp
private val PAGE_Y = 27.dp

/** A focusable piece of glass, Home's tile on the pages: white when focused, larger, 3 dp up, a soft shadow. */
@Composable
private fun Pane(radius: Dp, modifier: Modifier, scale: Float = 1.1f, keepFill: Boolean = false, onFocus: () -> Unit = {}, onLongClick: (() -> Unit)? = null, onClick: () -> Unit, content: @Composable (Boolean) -> Unit) =
    GlassTile(radius, modifier, focusScale = scale, lift = 3.dp, keepFill = keepFill, onFocus = onFocus, onLongClick = onLongClick, onClick = onClick, content = content)

/** A result's second line: nothing when it only repeats the kind, else with a capital first letter. */
private fun subLine(h: Hit): String = when {
    h.kind == Hit.APP && h.sub == tr("app") -> ""
    h.sub == tr("tv settings") -> tr("TV settings")
    else -> h.sub.replaceFirstChar { it.uppercase() }
}

/**
 * All apps in Glass: a large title, the kinds in a segmented capsule, and a grid of six where only the focused
 * app has a caption, centred under it. Hold OK has the same menu as on Home. Behind it, Home's own background.
 */
@Composable
internal fun GlassApps(cfg: HomeConfig, apps: List<TvApp>, onOpen: (TvApp) -> Unit, onMenu: (TvApp) -> Unit, backdrop: ImageBitmap?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val real = remember(apps) { apps.filter { !InputTiles.isInput(it.pkg) }.sortedBy { it.label.lowercase() } }
    val facts by produceState(emptyMap<String, AppFacts>(), real) { value = withContext(Dispatchers.IO) { real.mapNotNull { a -> appFacts(ctx, a.pkg)?.let { a.pkg to it } }.toMap() } }
    var tab by remember { mutableStateOf("all") }
    var focused by remember { mutableStateOf<String?>(null) }
    val tabs = TABS.filter { t -> t == "all" || real.any { inTab(t, facts[it.pkg]) } }
    val shown = real.filter { inTab(tab, facts[it.pkg]) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            GlassPageBackdrop(backdrop)
            Column(Modifier.fillMaxSize().padding(horizontal = PAGE_X, vertical = PAGE_Y)) {
                Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Apps"), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.035).em, modifier = Modifier.weight(1f))
                    Row(Modifier.clip(RoundedCornerShape(18.dp)).background(GLASS_TRAY).border(1.dp, GLASS_TRAY_EDGE, RoundedCornerShape(18.dp)).padding(3.dp).focusGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        tabs.forEach { t ->
                            Segment(tabName(t), t == tab) { tab = t }
                        }
                    }
                }
                LazyVerticalGrid(GridCells.Fixed(6), Modifier.weight(1f).padding(top = 26.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(29.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 6.dp, bottom = 24.dp)) {
                    items(shown, key = { it.pkg }) { a ->
                        // The same rule as the dock: names on glass, banners only when the owner asked for them,
                        // and a banner keeps its picture when focused.
                        val banner = a.banner && cfg.glassBanners
                        Box(Modifier.height(74.dp)) {
                            Pane(13.dp, Modifier.fillMaxSize().then(if (a == shown.firstOrNull()) Modifier.focusRequester(first) else Modifier), keepFill = banner,
                                onFocus = { focused = a.pkg }, onLongClick = { onMenu(a) }, onClick = { onClose(); onOpen(a) }) { f ->
                                if (banner) Image(a.art, a.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(a.label, color = if (f) GLASS_INK else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em,
                                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            // In the row gap under the focused tile: its name under a banner, else where it came from.
                            // It may run past the tile, so it starts at the left edge in the first column and
                            // ends at the right one in the last, and stays on the screen.
                            if (focused == a.pkg) {
                                val col = shown.indexOf(a) % 6
                                val side = when (col) { 0 -> Alignment.Start; 5 -> Alignment.End; else -> Alignment.CenterHorizontally }
                                Text(if (banner) a.label else facts[a.pkg]?.line ?: a.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().wrapContentWidth(side, unbounded = true).widthIn(max = 420.dp).offset(y = 23.dp))
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    Text(tr("Hold OK"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
                    Text(tr("Dock, hide, app info, uninstall"), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Segment(label: String, on: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.pointer(onClick = onClick).height(30.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onClick() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(15.dp)), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (on) Color.White else Color.Transparent, focusedContainerColor = Color.White),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (on || focused) GLASS_INK else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Search this TV in Glass: the field at the top with Say it at its end, the alphabet in one line under it, then
 * the best match on its own card at the left and the rest at the right. Number keys and a keyboard type too.
 * The letter row wraps both ways, through Space, Delete and 123.
 */
@Composable
internal fun GlassFind(cfg: HomeConfig, apps: List<TvApp>, dock: Set<String>, onApp: (TvApp) -> Unit, backdrop: ImageBitmap?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val last = remember { FocusRequester() }
    // Focus paths set by hand: Down from any key goes to the top result, Right from there to More, and Up from
    // either back to the key it came from. The keys sit in one wide line, so the usual search picks badly.
    val topCard = remember { FocusRequester() }
    val more = remember { FocusRequester() }
    val keyFocus = remember { List(26) { FocusRequester() } }
    val space = remember { FocusRequester() }
    val delete = remember { FocusRequester() }
    var cameFrom by remember { mutableStateOf<FocusRequester?>(null) }
    val kinds = remember(apps) { emptyMap<String, String>() }
    val hits = remember(query, apps) { searchTv(ctx, query, apps, dock, kinds, onClose) { onClose(); onApp(it) } }
    val speech = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).takeIf { it.resolveActivity(ctx.packageManager) != null } }
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { query = it.lowercase().take(MAX_QUERY) }
    }
    fun type(s: String) { query = (query + s).take(MAX_QUERY) }
    val accent = glassAccent(cfg)
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val c = e.utf16CodePoint
            when {
                e.key == Key.Backspace || e.key == Key.Delete -> { query = query.dropLast(1); true }
                c > 0 && !Character.isISOControl(c) && e.key != Key.DirectionCenter && e.key != Key.Enter -> { type(String(Character.toChars(c)).lowercase()); true }
                else -> false
            }
        }) {
            GlassPageBackdrop(backdrop)
            Column(Modifier.fillMaxSize().padding(horizontal = PAGE_X, vertical = PAGE_Y)) {
                Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(GLASS_FILL).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(26.dp)).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Image(Icons.Outlined.Search, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(GLASS_MUTED))
                    Text(query, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(start = 11.dp))
                    Box(Modifier.padding(start = 2.dp).width(2.dp).height(28.dp).background(accent, RoundedCornerShape(1.dp)))
                    Spacer(Modifier.weight(1f))
                    Text(if (query.isBlank()) tr("Apps, the TV's settings and Home's") else tr("{n} on this TV", "n" to hits.size), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (speech != null) Box(Modifier.padding(start = 12.dp)) { Capsule(tr("Say it")) { runCatching { listen.launch(speech) } } }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).height(42.dp).focusGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // One line: the letters alone, as the design has them; - and . are a keyboard press away.
                    val keys = if (digits) DIGITS.joinToString("").take(26) else "abcdefghijklmnopqrstuvwxyz"
                    val hasTop = hits.isNotEmpty()
                    fun key(r: FocusRequester) = Modifier.focusRequester(r).onFocusChanged { if (it.isFocused) cameFrom = r }
                        .focusProperties { down = if (hasTop) topCard else FocusRequester.Cancel }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        keys.forEachIndexed { i, ch ->
                            val r = if (i == 0) first else keyFocus[i]
                            val wrap = if (i == 0) Modifier.onPreviewKeyEvent { e ->
                                e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft && runCatching { last.requestFocus() }.isSuccess
                            } else Modifier
                            Letter(ch.toString(), wrap.then(key(r))) { type(ch.toString()) }
                        }
                    }
                    Capsule(tr("Space"), key(space)) { type(" ") }
                    Capsule(tr("Delete"), key(delete)) { query = query.dropLast(1) }
                    Capsule(if (digits) "abc" else "123", key(last).onPreviewKeyEvent { e ->
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight && runCatching { first.requestFocus() }.isSuccess
                    }) { digits = !digits }
                }
                Row(Modifier.weight(1f).padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    val top = hits.firstOrNull()
                    val back = cameFrom ?: first
                    Column(Modifier.width(280.dp).fillMaxHeight()) {
                        // Room under the labels for the focused card and row to grow into.
                        Text(tr("Top result"), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
                        if (top != null) TopCard(top, apps, Modifier.focusRequester(topCard).focusProperties {
                            up = back; right = if (hits.size > 1) more else FocusRequester.Cancel
                        })
                        else Text(if (query.isBlank()) tr("Type a name. It finds apps, the TV's settings and Home's own.") else tr("Nothing on this TV is called that."), color = GLASS_MUTED, fontSize = 14.sp)
                    }
                    if (hits.size > 1) Column(Modifier.weight(1f).fillMaxHeight()) {
                        Text(tr("More"), color = GLASS_MUTED, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                        LazyColumn(Modifier.fillMaxSize().focusGroup(), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            itemsIndexed(hits.drop(1), key = { _, h -> h.kind.toString() + h.title }) { i, h ->
                                ResultRow(h, Modifier.focusProperties { left = topCard; if (i == 0) up = back }.then(if (i == 0) Modifier.focusRequester(more) else Modifier))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Letter(ch: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.pointer(onClick = onClick).size(24.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(CircleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.3f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.White),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ch, color = if (focused) GLASS_INK else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Capsule(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Pane(13.dp, modifier.height(26.dp), scale = 1.08f, onClick = onClick) { f ->
        Box(Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, color = if (f) GLASS_INK else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The best match on its own glass card: the app's banner when it's an app, its name large, and Open. */
@Composable
private fun TopCard(h: Hit, apps: List<TvApp>, modifier: Modifier) {
    val app = if (h.kind == Hit.APP) apps.firstOrNull { it.label == h.title } else null
    Pane(20.dp, modifier.fillMaxWidth().height(280.dp), scale = 1.03f, onClick = h.go) { f ->
        val ink = if (f) GLASS_INK else Color.White
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // On the white focused card the tile goes a shade darker, so the letter keeps its ground.
            Box(Modifier.width(132.dp).height(74.dp).clip(RoundedCornerShape(13.dp)).background(if (f) GLASS_INK.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                if (app != null) Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit)
                else Text(h.title.take(1).uppercase(), color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            Text(h.title, color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, letterSpacing = (-0.035).em, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 18.dp))
            Text(subLine(h), color = ink.copy(alpha = 0.78f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
            Spacer(Modifier.weight(1f))
            Text(h.action.replaceFirstChar { it.uppercase() }, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(17.dp)).background(if (f) GLASS_INK.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.16f)).padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun ResultRow(h: Hit, modifier: Modifier) {
    Pane(15.dp, modifier.fillMaxWidth().height(56.dp), scale = 1.02f, onClick = h.go) { f ->
        val ink = if (f) GLASS_INK else Color.White
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(when (h.kind) { Hit.APP -> tr("App"); Hit.HOME -> tr("Home"); else -> tr("Setting") }, color = ink.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(62.dp))
            Column(Modifier.weight(1f)) {
                Text(h.title, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subLine(h).takeIf { it.isNotEmpty() }?.let { Text(it, color = ink.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Text(h.action.replaceFirstChar { it.uppercase() }, color = ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
    }
}
