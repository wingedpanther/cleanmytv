package io.github.wingedpanther.cleanmytv.home.customization

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.KeyService
import io.github.wingedpanther.cleanmytv.home.NowPlayingListener
import io.github.wingedpanther.cleanmytv.home.TAG
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TextPrompt
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import io.github.wingedpanther.cleanmytv.home.quickActions
import io.github.wingedpanther.cleanmytv.home.UpdateDialog
import io.github.wingedpanther.cleanmytv.home.Updates
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The greeting, the names Home shows, and the switches for the TV itself, as the desktop app has them. */
@Composable
fun MoreCustomization(config: HomeConfig, ctx: Context) {
    var greeting by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf("") }
    var showUpdate by remember { mutableStateOf(false) }
    val update by Updates.available.collectAsState()
    val scope = rememberCoroutineScope()
    val set = { k: String, v: Any -> ConfigStore.update(ctx, JSONObject().put(k, v)); KeyService.ensureOn(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (config.greeting.isBlank()) tr("No greeting") else "${tr("Good evening")}, ${config.greeting}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TvSecondaryButton(text = tr("Greeting"), onClick = { greeting = true })
        }
        TvSwitch(
            title = tr("Free memory"),
            subtitle = tr("How much memory the TV has free, under the clock"),
            checked = config.memory,
            onCheckedChange = { set("memory", it) }
        )
        TvSwitch(
            title = tr("Live TV in Inputs"),
            subtitle = tr("The TV's tuner, at the top of the Inputs menu"),
            checked = config.liveTv,
            onCheckedChange = { set("live_tv", it) }
        )
        TvSwitch(
            title = tr("Back to the input at power on"),
            subtitle = tr("If the TV slept on HDMI or TV channels, it wakes up there"),
            checked = config.powerOnInput,
            onCheckedChange = { set("power_on_input", it) }
        )
        TvSwitch(
            title = tr("Keep my keyboard"),
            subtitle = tr("If the TV switches to the phone keyboard after a restart or standby, put yours back"),
            checked = config.keyboardGuard,
            onCheckedChange = { set("keyboard_guard", it) }
        )
        if (config.powerOnInput) ServiceNote(tr("the TV starts on Home instead"))
        TvSwitch(
            title = tr("Phone apps in their own folder"),
            subtitle = tr("Apps with no TV banner go into an Other apps folder"),
            checked = config.otherApps,
            onCheckedChange = { set("other_apps", it) }
        )
        TvSwitch(
            title = tr("Sunrise when the wallpaper changes"),
            subtitle = tr("A new wallpaper starts dim and the light comes in from the top"),
            checked = config.intro,
            onCheckedChange = { set("intro", it) }
        )

        Text(tr("Updates"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(tr("This is CleanMyTV Home {version}.", "version" to Updates.installed(ctx)), color = MUTED_COLOR, fontSize = 13.sp)
        TvSwitch(
            title = tr("Look for updates"),
            subtitle = tr("Once a day. A newer Home puts an update icon first in the top bar."),
            checked = config.updateCheck,
            onCheckedChange = { set("update_check", it) }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvSecondaryButton(text = tr("Check now"), onClick = {
                checking = tr("Looking…")
                scope.launch {
                    val r = Updates.check(ctx, force = true)
                    checking = if (r == null) tr("Home is up to date.") else tr("CleanMyTV Home {version} is out.", "version" to r.version)
                }
            })
            update?.let { TvSecondaryButton(text = tr("What's new in {version}", "version" to it.version), onClick = { showUpdate = true }) }
            if (checking.isNotEmpty()) Text(checking, color = MUTED_COLOR, fontSize = 13.sp)
        }

        Text(tr("Names"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(tr("Your own words for the top bar icons and the All apps heading."), color = MUTED_COLOR, fontSize = 13.sp)
        val nameable = listOf("all_apps") + config.quick.filter { it in quickActions() }
        for (id in nameable) {
            val usual = if (id == "all_apps") tr("All apps") else quickActions()[id]?.label ?: id
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(config.names[id]?.let { "$it  ($usual)" } ?: usual, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TvSecondaryButton(text = tr("Rename"), onClick = { naming = id })
            }
        }
    }

    if (showUpdate) update?.let { UpdateDialog(it, Color(config.accent)) { showUpdate = false } }
    if (greeting) TextPrompt(
        title = tr("Your name for the greeting"),
        hint = tr("Home says good morning, good afternoon or good evening to it. Empty turns the greeting off."),
        initial = config.greeting,
        maxLength = 40,
        onDismiss = { greeting = false },
        onDone = { greeting = false; ConfigStore.update(ctx, JSONObject().put("greeting", it)) }
    )
    naming?.let { id ->
        TextPrompt(
            title = tr("A new name"),
            hint = tr("Empty goes back to the usual name."),
            initial = config.names[id].orEmpty(),
            onDismiss = { naming = null },
            onDone = { name ->
                naming = null
                val names = JSONObject(config.names as Map<*, *>)
                if (name.isEmpty()) names.remove(id) else names.put(id, name)
                ConfigStore.update(ctx, JSONObject().put("names", names))
            }
        )
    }
}

/**
 * Now playing reads other apps' media sessions, which Android only shows a notification listener. The desktop
 * app switches Home's on over ADB; from the TV, Home does it with the secure-settings permission CleanMyTV
 * granted at install. Without that permission nothing changes.
 */
internal fun nowPlayingAccess(ctx: Context, on: Boolean) {
    val me = ComponentName(ctx, NowPlayingListener::class.java).flattenToString()
    val key = "enabled_notification_listeners"
    runCatching {
        val list = Settings.Secure.getString(ctx.contentResolver, key).orEmpty().split(':').filter { it.isNotBlank() && it != me }
        Settings.Secure.putString(ctx.contentResolver, key, (if (on) list + me else list).joinToString(":"))
    }.onFailure { Log.w(TAG, "now playing access: $it") }
}
