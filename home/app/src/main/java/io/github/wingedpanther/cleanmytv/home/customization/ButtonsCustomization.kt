package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ButtonMap
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.KeyLog
import io.github.wingedpanther.cleanmytv.home.KeyService
import io.github.wingedpanther.cleanmytv.home.Memory
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.AlertType
import io.github.wingedpanther.cleanmytv.home.customization.components.DIALOG_BG
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvDestructiveButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/** What a button can do, worded as the desktop app's Remote buttons tab. */
private val ACTIONS = listOf(
    "settings" to "Open Settings", "inputs" to "Open Inputs", "home" to "Go Home", "back" to "Back",
    "app" to "Open an app…", "wifi" to "Open Wi-Fi", "bluetooth" to "Open Bluetooth & remotes",
    "display" to "Open Picture & sound", "apps" to "Open the app list", "sleep" to "Start the screensaver",
    "search" to "Open Search", "music" to "Music bars on or off", "panel" to "Open Quick settings",
    "themes" to "Open Themes", "fireplace" to "Show the fireplace", "memory" to "Clean memory",
    "next_wallpaper" to "Next picture of the theme", "brightness" to "Brightness: 10, 25, 50, 75, 100%", "timer" to "Sleep timer: 15, 30, 60, 90, 120 min, off", "customize" to "Open this editor", "none" to "Do nothing",
)

/** The arrows, OK and Back move around Home and this editor, so they can't be given another job here. */
private val NAVIGATION = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
)

/** KEYCODE_PROG_RED -> "Prog red", as the desktop app names them. */
private fun keyName(code: Int): String {
    val s = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").lowercase().replace('_', ' ')
    return s.replaceFirstChar { it.uppercase() }
}

/**
 * Remote buttons from the remote: press the button to give it a job, as the desktop app's button finder does.
 * Only the buttons mapped here change; every other button reaches the TV as before, and a mapped one fires
 * when it's let go, as Home's button service already does it.
 */
@Composable
fun ButtonsCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    var finding by remember { mutableStateOf(false) }
    // A found button waiting for its job, and the job waiting for its app.
    var code by remember { mutableStateOf<Int?>(null) }
    var pickApp by remember { mutableStateOf(false) }
    val save = { next: List<ButtonMap> ->
        ConfigStore.update(ctx, JSONObject().put("buttons", JSONArray(next.map { b ->
            JSONObject().put("code", b.code).put("action", b.action).apply { b.app?.let { put("app", it) } }
        })))
        KeyService.ensureOn(ctx)
    }
    val label = { b: ButtonMap ->
        if (b.action == "app") "Open ${apps.firstOrNull { it.pkg == b.app }?.label ?: b.app}"
        else ACTIONS.firstOrNull { it.first == b.action }?.second ?: b.action
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = "Remote buttons",
            subtitle = "Give spare buttons, like the colour buttons or a streaming app's, a new job",
            checked = config.buttonsOn,
            onCheckedChange = {
                ConfigStore.update(ctx, JSONObject().put("buttons_on", it))
                KeyService.ensureOn(ctx)
            }
        )
        if (config.buttonsOn) {
            config.buttons.forEach { b ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x11FFFFFF)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(keyName(b.code), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(label(b), color = MUTED_COLOR, fontSize = 13.sp)
                    }
                    TvSecondaryButton(text = "Change", onClick = { code = b.code })
                    TvDestructiveButton(text = "Remove", onClick = { save(config.buttons.filter { it.code != b.code }) })
                }
            }
            ServiceNote("mapped buttons do nothing")
            TvPrimaryButton(text = "Add a button", onClick = { finding = true })
            if (Memory.canClean.not()) Alert(text = "Clean memory doesn't work on Android 14 and later, so a button for it does nothing.", modifier = Modifier.fillMaxWidth())
        }
    }

    if (finding) FindButton(onDismiss = { finding = false }) { found -> finding = false; code = found }
    code?.let { c ->
        if (pickApp) {
            Choose("Which app should ${keyName(c)} open?", apps.map { it.pkg to it.label }, onDismiss = { pickApp = false; code = null }) { pkg ->
                save(config.buttons.filter { it.code != c } + ButtonMap(c, "app", pkg))
                pickApp = false; code = null
            }
        } else {
            Choose("What should ${keyName(c)} do?", ACTIONS.filter { it.first != "memory" || Memory.canClean }, onDismiss = { code = null }) { action ->
                if (action == "app") pickApp = true
                else { save(config.buttons.filter { it.code != c } + ButtonMap(c, action)); code = null }
            }
        }
    }
}

/**
 * Waits for one press. Home's button service sees buttons the TV keeps from apps; while it listens, it logs
 * them. The editor's own window catches the rest. The arrows, OK and Back don't count.
 */
@Composable
private fun FindButton(onDismiss: () -> Unit, onFound: (Int) -> Unit) {
    val focus = remember { FocusRequester() }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        val mark = KeyLog.mark()
        KeyService.listen()
        repeat(240) {
            delay(500)
            KeyLog.since(mark).lastOrNull { it !in NAVIGATION }?.let { onFound(it); return@LaunchedEffect }
        }
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xBB000000)).focusRequester(focus).focusable().onPreviewKeyEvent { e ->
                val k = e.nativeKeyEvent.keyCode
                when {
                    k == KeyEvent.KEYCODE_BACK -> { if (e.type == KeyEventType.KeyUp) onDismiss(); true }
                    k in NAVIGATION -> { if (e.type == KeyEventType.KeyUp) note = "The arrows and OK move around Home, so they keep their job."; true }
                    else -> { if (e.type == KeyEventType.KeyUp) onFound(k); true }
                }
            },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier.width(460.dp).clip(RoundedCornerShape(16.dp)).background(DIALOG_BG).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Press the button on your remote", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Back cancels. If nothing happens, the TV handles that button itself and Home can't change it.", color = MUTED_COLOR, fontSize = 13.sp)
                if (note.isNotEmpty()) Alert(text = note, type = AlertType.WARNING, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Mapped buttons, App lock and the power-on input run in Home's button service. It comes on by itself where
 * CleanMyTV granted Home the secure-settings permission; elsewhere this says so, with [what] stops working.
 */
@Composable
internal fun ServiceNote(what: String) {
    var on by remember { mutableStateOf(KeyService.isOn) }
    LaunchedEffect(Unit) { repeat(10) { on = KeyService.isOn; if (on) return@LaunchedEffect; delay(500) } }
    if (!on) Alert(
        text = "Home's button service is off, so $what. In CleanMyTV on your computer, open the Home screen step and press Update and use to turn it on.",
        type = AlertType.WARNING,
        modifier = Modifier.fillMaxWidth()
    )
}

/** A list of choices in a dialog, for a button's job or app. */
@Composable
internal fun Choose(title: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(460.dp).clip(RoundedCornerShape(16.dp)).background(DIALOG_BG).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(options.size) { i ->
                        val (id, text) = options[i]
                        TvSecondaryButton(text = text, onClick = { onPick(id) }, modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(first) else Modifier))
                    }
                }
            }
        }
    }
}
