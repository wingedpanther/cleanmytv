package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val P_TEXT = Color(0xFFF0EBE5)
private val P_MUTED = Color(0xFFD7CFC4)
private val P_PANEL = Color(0xF51C2A1F)

/**
 * Quick settings, down the right edge: the settings people change most, set right here with left and right.
 * Rows CleanMyTV couldn't get permission for show their value and open the TV's own screen. In Bau it's a cream
 * sheet with switches for the rows that are on or off.
 */
@Composable
fun SettingsPanel(accent: Color, bau: Boolean = false, onFind: () -> Unit = {}, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var armOff by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var cleaning by remember { mutableStateOf(false) }
    fun open(i: Intent?) {
        i ?: return
        runCatching { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "panel: $it") }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(if (bau) BAU_INK.copy(alpha = 0.6f) else Color.Transparent).background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(if (bau) 440.dp else 420.dp).fillMaxHeight().background(if (bau) BAU_CREAM else P_PANEL).padding(horizontal = if (bau) 34.dp else 24.dp, vertical = 27.dp)
            ) {
                if (bau) {
                    Text(tr("Quick settings").lowercase(), color = BAU_INK, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
                    Text(deviceLine(ctx).lowercase(), color = BAU_DIM, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BAU_INK))
                } else {
                    Text(tr("Quick settings"), color = P_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text(tr("Left and right change a setting."), color = P_MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                }
                // Read again after every change, so each row shows what the TV has now.
                val choices = remember(tick) {
                    listOf(TvSettings.volume(ctx), TvSettings.brightness(ctx), TvSettings.screensaver(ctx), TvSettings.saverApp(ctx), TvSettings.sleep(ctx),
                        TvSettings.dns(ctx), TvSettings.animations(ctx), TvSettings.clock(ctx))
                }
                val network = remember(tick) { TvSettings.network(ctx) }
                val adb = remember(tick) { TvSettings.debuggingOn(ctx) }
                val fire = remember(tick) { Fire.ready(ctx) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (bau) 0.dp else 6.dp)) {
                    item("find") { PanelRow(tr("Search this TV"), "", tr("Apps, the TV's settings and Home's"), accent, Modifier.focusRequester(first), bau = bau) { onClose(); onFind() } }
                    choices.forEachIndexed { n, c ->
                        item("choice$n") {
                            fun step(d: Int) {
                                if (c.set == null) return
                                val i = (c.current + d).coerceIn(0, c.options.size - 1)
                                if (i != c.current) { c.set.invoke(i); tick++ }
                            }
                            // Two choices, the first Off: a switch in Bau.
                            val toggle = if (c.options.size == 2 && c.options[0] == tr("Off")) c.current == 1 else null
                            PanelRow(c.label, if (toggle != null && bau) (if (toggle) c.options[1].substringAfter("(").removeSuffix(")") else c.options[0]) else c.options.getOrElse(c.current) { "" },
                                if (c.writable) null else tr("Change it in the TV's settings"), accent,
                                Modifier.onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionLeft -> { step(-1); c.set != null }
                                        Key.DirectionRight -> { step(1); c.set != null }
                                        else -> false
                                    }
                                },
                                arrows = c.set != null, bau = bau, toggle = toggle,
                            ) {
                                if (c.set != null) { c.set.invoke((c.current + 1) % c.options.size); tick++ } else open(c.fallback)
                            }
                        }
                    }
                    // The fireplace, once CleanMyTV has put it on the TV: left and right set it behind Home, OK shows it full screen.
                    if (fire) item("fireplace") {
                        val cfg = ConfigStore.config.value
                        val modes = listOf(tr("Off"), tr("Behind Home"), tr("With crackle"))
                        val cur = if (!cfg.fireWall) 0 else if (cfg.fireWallSound) 2 else 1
                        fun step(d: Int) {
                            val i = (cur + d).coerceIn(0, modes.size - 1)
                            if (i == cur) return
                            ConfigStore.update(ctx, org.json.JSONObject().put("fire_wall", i > 0).put("fire_wall_sound", i == 2))
                            tick++
                        }
                        PanelRow(tr("Fireplace"), modes[cur], tr("OK shows it full screen"), accent,
                            Modifier.onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { step(-1); true }
                                    Key.DirectionRight -> { step(1); true }
                                    else -> false
                                }
                            },
                            arrows = true, bau = bau,
                        ) { onClose(); open(Intent(ctx, FireplaceActivity::class.java)) }
                    }
                    item("memory") {
                        val m = remember(tick) { Memory.read(ctx) }
                        PanelRow(tr("Memory"), if (cleaning) tr("Cleaning…") else Memory.line(m), if (Memory.canClean) tr("OK closes apps in the background") else null, accent, bau = bau) {
                            if (Memory.canClean && !cleaning) scope.launch { cleaning = true; Memory.cleanAndSay(ctx); cleaning = false; tick++ }
                        }
                    }
                    item("timer") {
                        val sleepAt by SleepTimer.at.collectAsState()
                        val left = sleepAt?.let { ((it - System.currentTimeMillis() + 59_999) / 60_000L).toInt() }?.takeIf { it > 0 }
                        val steps = listOf<Int?>(null) + SleepTimer.CHOICES
                        val cur = if (left == null) 0 else steps.indexOfFirst { it != null && it >= left }.let { if (it < 0) steps.size - 1 else it }
                        fun step(d: Int) { val i = (cur + d).coerceIn(0, steps.size - 1); if (i != cur) SleepTimer.set(ctx, steps[i]) }
                        PanelRow(tr("Sleep timer"), left?.let { tr("In {time}", "time" to Limits.words(it)) } ?: tr("Off"), null, accent,
                            Modifier.onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) { Key.DirectionLeft -> { step(-1); true }; Key.DirectionRight -> { step(1); true }; else -> false }
                            }, arrows = true, bau = bau) { step(1) }
                    }
                    item("picture") { PanelRow(tr("Picture & sound"), remember { displayLine(ctx) } ?: tr("Open"), null, accent, bau = bau) { quickActions()["display"]?.let { a -> resolve(ctx, a)?.let { open(it) } } } }
                    item("network") { PanelRow(tr("Network"), network, null, accent, bau = bau) { open(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
                    item("bluetooth") {
                        PanelRow(tr("Remotes & Bluetooth"), tr("Open"), null, accent, bau = bau) { quickActions()["bluetooth"]?.let { a -> resolve(ctx, a)?.let { open(it) } } }
                    }
                    if (adb) item("adb") {
                        PanelRow(tr("Debugging"), if (armOff) tr("Press OK again to turn it off") else tr("On"), tr("CleanMyTV can't connect once it's off"), accent, bau = bau, toggle = !armOff) {
                            if (armOff) { TvSettings.debuggingOff(ctx); armOff = false; tick++ } else armOff = true
                        }
                    }
                    if (!bau) {
                        item("all") { PanelRow(tr("All settings"), tr("Open"), null, accent) { open(Intent(Settings.ACTION_SETTINGS)) } }
                        if (KeyService.running != null) item("standby") { PanelRow(tr("Standby"), "", tr("Turns the TV off"), accent) { onClose(); KeyService.sleep(ctx) } }
                    }
                }
                // Bau ends on two outlined buttons, as the design has them.
                if (bau) Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BauButton(tr("All settings"), Modifier.weight(1f)) { open(Intent(Settings.ACTION_SETTINGS)) }
                    if (KeyService.running != null) BauButton(tr("Standby"), Modifier.weight(1f)) { onClose(); KeyService.sleep(ctx) }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

/** "Living room TV · Android TV 14": the TV's name, as set in its settings, and its Android version. */
private fun deviceLine(ctx: android.content.Context): String {
    val name = runCatching { Settings.Global.getString(ctx.contentResolver, "device_name") }.getOrNull()?.takeIf { it.isNotBlank() } ?: android.os.Build.MODEL
    return "$name · Android TV ${android.os.Build.VERSION.RELEASE}"
}

/** "3840×2160 · 60 Hz · HDR" from the display's current mode; null if the TV won't say. */
internal fun displayLine(ctx: android.content.Context): String? = runCatching {
    val d = ctx.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
    val m = d.mode
    @Suppress("DEPRECATION")
    val hdr = d.hdrCapabilities?.supportedHdrTypes?.isNotEmpty() == true
    listOfNotNull("${m.physicalWidth}×${m.physicalHeight}", "${kotlin.math.round(m.refreshRate).toInt()} Hz", "HDR".takeIf { hdr }).joinToString(" · ")
}.getOrNull()

@Composable
private fun BauButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.pointer(onClick = onClick).height(52.dp).border(1.5.dp, BAU_INK).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(androidx.compose.ui.graphics.RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = BAU_INK)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label.lowercase(), color = if (focused) BAU_CREAM else BAU_INK, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Bau's switch: blue and filled to the right when on, grey to the left when off. */
@Composable
private fun BauSwitch(on: Boolean, ink: Color) {
    Box(Modifier.width(44.dp).height(22.dp).border(1.5.dp, if (on) BAU_BLUE else ink.copy(alpha = 0.45f)).padding(3.dp), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.width(18.dp).fillMaxHeight().background(if (on) BAU_BLUE else ink.copy(alpha = 0.3f)))
    }
}

@Composable
private fun PanelRow(label: String, value: String, hint: String?, accent: Color, modifier: Modifier = Modifier, arrows: Boolean = false, bau: Boolean = false, toggle: Boolean? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    if (bau) {
        // A ruled row in ink on cream; focused, the row turns ink with a yellow dot, as in the design.
        Surface(
            onClick = onClick,
            modifier = Modifier.pointer(onClick = onClick).then(modifier).fillMaxWidth().height(54.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(androidx.compose.ui.graphics.RectangleShape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = BAU_INK)
        ) {
            val ink = if (focused) BAU_CREAM else BAU_INK
            Column {
                Row(Modifier.weight(1f).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (focused) Box(Modifier.padding(end = 10.dp).size(10.dp).background(BAU_YELLOW, androidx.compose.foundation.shape.CircleShape))
                    Text(label.lowercase(), color = ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val shown = (if (arrows && focused) "‹  " else "") + value.lowercase() + (if (arrows && focused) "  ›" else "")
                    if (shown.isNotBlank()) Text(shown, color = ink.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.padding(start = 8.dp).width(170.dp))
                    if (toggle != null) Box(Modifier.padding(start = 12.dp)) { BauSwitch(toggle, ink) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BAU_INK.copy(alpha = if (focused) 0f else 0.25f)))
            }
        }
        return
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.pointer(onClick = onClick).then(modifier).fillMaxWidth().height(if (hint != null) 64.dp else 54.dp).onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), focusedContainerColor = accent.copy(alpha = 0.9f))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, color = P_TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hint != null) Text(hint, color = P_MUTED.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(10.dp))
            Text((if (arrows && focused) "‹  " else "") + value + (if (arrows && focused) "  ›" else ""), color = P_TEXT.copy(alpha = 0.9f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(170.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
