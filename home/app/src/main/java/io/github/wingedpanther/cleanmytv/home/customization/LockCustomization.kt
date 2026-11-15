package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.KeyService
import io.github.wingedpanther.cleanmytv.home.Limits
import io.github.wingedpanther.cleanmytv.home.Lock
import io.github.wingedpanther.cleanmytv.home.PinPad
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvDestructiveButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvRadioGroup
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import org.json.JSONArray
import org.json.JSONObject

/**
 * App lock from the remote. With a PIN already set, nothing here shows until that PIN is typed, whatever
 * the lock covers: otherwise anyone holding the remote could unlock the apps or forget the PIN.
 */
@Composable
fun LockCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    var hasPin by remember { mutableStateOf(Lock.hasPin(ctx)) }
    var verified by remember { mutableStateOf(!hasPin) }
    var asking by remember { mutableStateOf(false) }
    // Setting a PIN takes it twice: the first entry waits here for the second.
    var newPin by remember { mutableStateOf<String?>(null) }
    var settingPin by remember { mutableStateOf(false) }
    // Adding a daily limit: the app, then its minutes.
    var limitApp by remember { mutableStateOf<String?>(null) }
    var pickingLimit by remember { mutableStateOf(false) }
    val set = { change: JSONObject -> ConfigStore.update(ctx, change); KeyService.ensureOn(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!verified) {
            Alert(text = "Type your PIN to change App lock.", modifier = Modifier.fillMaxWidth())
            TvPrimaryButton(text = "Enter the PIN", onClick = { asking = true })
            return@Column
        }
        if (!hasPin) {
            Alert(text = "Set a PIN first. It's 4 to 6 digits, and it's stored scrambled on the TV, never readable.", modifier = Modifier.fillMaxWidth())
            TvPrimaryButton(text = "Set a PIN", onClick = { settingPin = true })
            return@Column
        }
        TvSwitch(
            title = "App lock",
            subtitle = if (config.lockOn) "${config.lockApps.size} ${if (config.lockApps.size == 1) "app is" else "apps are"} locked${if (config.lockSystem) ", plus Settings, the Play Store and Quick settings" else ""}"
            else "Your PIN and the list stay while it's off",
            checked = config.lockOn,
            onCheckedChange = { set(JSONObject().put("lock_on", it)) }
        )
        if (config.lockOn) ServiceNote("locked apps still open without the PIN")
        TvSwitch(
            title = "Settings, Play Store and Quick settings",
            subtitle = "Also the uninstaller, so the lock can't be switched off around it",
            checked = config.lockSystem,
            onCheckedChange = { set(JSONObject().put("lock_system", it)) }
        )
        TvSwitch(
            title = "Customize Home",
            subtitle = "This editor asks for the PIN before it opens. App lock here always does.",
            checked = config.lockEditor,
            onCheckedChange = { set(JSONObject().put("lock_editor", it)) }
        )
        TvRadioGroup(
            title = "An unlock lasts",
            options = listOf("sleep" to "Until the TV sleeps", "15" to "15 minutes", "always" to "Until another app opens"),
            current = config.lockFor,
            onSelect = { set(JSONObject().put("lock_for", it)) }
        )
        TvRadioGroup(
            title = "Locked apps on Home",
            options = listOf("badge" to "Show them with a lock", "hide" to "Hide them"),
            current = config.lockShow,
            onSelect = { set(JSONObject().put("lock_show", it)) }
        )
        Text("Locked apps", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        apps.forEach { a ->
            TvSwitch(
                title = a.label,
                checked = a.pkg in config.lockApps,
                onCheckedChange = { on ->
                    val next = if (on) config.lockApps + a.pkg else config.lockApps - a.pkg
                    set(JSONObject().put("lock_apps", JSONArray(next.distinct())))
                }
            )
        }
        Text("Daily limits", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("After its minutes for the day, an app needs the PIN, which gives it the rest of the day. The count starts again at midnight.", color = MUTED_COLOR, fontSize = 13.sp)
        config.limits.forEach { (pkg, minutes) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val used = (Limits.used(ctx, pkg) / 60_000L).toInt()
                Text("${apps.firstOrNull { it.pkg == pkg }?.label ?: pkg}: ${Limits.words(minutes)} a day, ${Limits.words(used)} used today", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TvDestructiveButton(text = "Remove", onClick = { set(JSONObject().put("limits", JSONObject((config.limits - pkg) as Map<*, *>))) })
            }
        }
        TvSecondaryButton(text = "Add a daily limit", onClick = { pickingLimit = true })
        if (config.limits.isNotEmpty()) ServiceNote("the limits don't count")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSecondaryButton(text = "Change the PIN", onClick = { settingPin = true })
            TvDestructiveButton(text = "Forget the PIN", onClick = {
                Lock.clearPin(ctx)
                ConfigStore.update(ctx, JSONObject().put("lock_on", false))
                hasPin = false
            })
        }
        Text("Forgetting the PIN turns App lock off. The list of locked apps stays.", color = MUTED_COLOR, fontSize = 13.sp)
    }

    if (asking) Dialog(onDismissRequest = { asking = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad("Enter the PIN for App lock", Lock.PANEL, Color(config.accent)) { asking = false; verified = true }
    }
    if (pickingLimit) Choose("Which app gets a daily limit?", apps.filter { it.pkg !in config.limits }.map { it.pkg to it.label }, onDismiss = { pickingLimit = false }) {
        pickingLimit = false; limitApp = it
    }
    limitApp?.let { pkg ->
        Choose("How long a day?", listOf(15, 30, 45, 60, 90, 120, 180, 240).map { "$it" to Limits.words(it) }, onDismiss = { limitApp = null }) { m ->
            limitApp = null
            set(JSONObject().put("limits", JSONObject((config.limits + (pkg to m.toInt())) as Map<*, *>)))
        }
    }
    if (settingPin) Dialog(onDismissRequest = { settingPin = false; newPin = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Home's own number pad, the same one that asks for the PIN later; the remote's number keys work too.
        PinPad(
            title = if (newPin == null) "Choose a PIN, 4 to 6 digits" else "Type it again",
            what = Lock.PANEL,
            accent = Color(config.accent),
            onEntered = { pin ->
                when {
                    newPin == null -> { newPin = pin; null }
                    pin != newPin -> { newPin = null; "The two didn't match. Start again." }
                    else -> {
                        Lock.setPin(ctx, pin)
                        hasPin = true; verified = true; settingPin = false; newPin = null
                        null
                    }
                }
            }
        ) {}
    }
}
