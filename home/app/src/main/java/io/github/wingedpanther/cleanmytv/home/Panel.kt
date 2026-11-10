package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
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

private val P_TEXT = Color(0xFFF2EEE7)
private val P_MUTED = Color(0xFFC8C0B3)
private val P_PANEL = Color(0xF517191F)

/**
 * Quick settings, down the right edge: the settings people change most, set right here with left and right.
 * Rows CleanMyTV couldn't get permission for show their value and open the TV's own screen.
 */
@Composable
fun SettingsPanel(accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var armOff by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    fun open(i: Intent?) {
        i ?: return
        runCatching { ctx.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { Log.w(TAG, "panel: $it") }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))) {
            Column(
                Modifier.align(Alignment.CenterEnd).width(420.dp).fillMaxHeight().background(P_PANEL).padding(horizontal = 24.dp, vertical = 27.dp)
            ) {
                Text("Quick settings", color = P_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                Text("Left and right change a setting.", color = P_MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                // Read again after every change, so each row shows what the TV has now.
                val choices = remember(tick) {
                    listOf(TvSettings.volume(ctx), TvSettings.screensaver(ctx), TvSettings.saverApp(ctx), TvSettings.sleep(ctx),
                        TvSettings.dns(ctx), TvSettings.animations(ctx), TvSettings.clock(ctx))
                }
                val network = remember(tick) { TvSettings.network(ctx) }
                val adb = remember(tick) { TvSettings.debuggingOn(ctx) }
                val fire = remember(tick) { Fire.ready(ctx) }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEachIndexed { n, c ->
                        item(c.label) {
                            fun step(d: Int) {
                                if (c.set == null) return
                                val i = (c.current + d).coerceIn(0, c.options.size - 1)
                                if (i != c.current) { c.set.invoke(i); tick++ }
                            }
                            PanelRow(c.label, c.options.getOrElse(c.current) { "" }, if (c.writable) null else "Change it in the TV's settings", accent,
                                Modifier.then(if (n == 0) Modifier.focusRequester(first) else Modifier).onPreviewKeyEvent { e ->
                                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (e.key) {
                                        Key.DirectionLeft -> { step(-1); c.set != null }
                                        Key.DirectionRight -> { step(1); c.set != null }
                                        else -> false
                                    }
                                },
                                arrows = c.set != null,
                            ) {
                                if (c.set != null) { c.set.invoke((c.current + 1) % c.options.size); tick++ } else open(c.fallback)
                            }
                        }
                    }
                    // The fireplace, once CleanMyTV has put it on the TV: left and right set it behind Home, OK shows it full screen.
                    if (fire) item("fireplace") {
                        val cfg = ConfigStore.config.value
                        val modes = listOf("Off", "Behind Home", "With crackle")
                        val cur = if (!cfg.fireWall) 0 else if (cfg.fireWallSound) 2 else 1
                        fun step(d: Int) {
                            val i = (cur + d).coerceIn(0, modes.size - 1)
                            if (i == cur) return
                            ConfigStore.update(ctx, org.json.JSONObject().put("fire_wall", i > 0).put("fire_wall_sound", i == 2))
                            tick++
                        }
                        PanelRow("Fireplace", modes[cur], "OK shows it full screen", accent,
                            Modifier.onPreviewKeyEvent { e ->
                                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (e.key) {
                                    Key.DirectionLeft -> { step(-1); true }
                                    Key.DirectionRight -> { step(1); true }
                                    else -> false
                                }
                            },
                            arrows = true,
                        ) { onClose(); open(Intent(ctx, FireplaceActivity::class.java)) }
                    }
                    item("network") { PanelRow("Network", network, null, accent) { open(Intent(Settings.ACTION_WIFI_SETTINGS)) } }
                    item("bluetooth") {
                        PanelRow("Remotes & Bluetooth", "Open", null, accent) { quickActions()["bluetooth"]?.let { a -> resolve(ctx, a)?.let { open(it) } } }
                    }
                    if (adb) item("adb") {
                        PanelRow("Debugging", if (armOff) "Press OK again to turn it off" else "On", "CleanMyTV can't connect once it's off", accent) {
                            if (armOff) { TvSettings.debuggingOff(ctx); armOff = false; tick++ } else armOff = true
                        }
                    }
                    item("all") { PanelRow("All settings", "Open", null, accent) { open(Intent(Settings.ACTION_SETTINGS)) } }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    }
}

@Composable
private fun PanelRow(label: String, value: String, hint: String?, accent: Color, modifier: Modifier = Modifier, arrows: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(if (hint != null) 64.dp else 54.dp).onFocusChanged { focused = it.isFocused },
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
