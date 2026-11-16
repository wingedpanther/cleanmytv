package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.KeyService
import io.github.wingedpanther.cleanmytv.home.Limits
import io.github.wingedpanther.cleanmytv.home.Lock
import io.github.wingedpanther.cleanmytv.home.PinPad
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.CUSTOMIZATION_BG
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.ServiceNote
import io.github.wingedpanther.cleanmytv.home.customization.components.TEXT_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvDestructiveButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvRadioGroup
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import io.github.wingedpanther.cleanmytv.home.tr
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
            Alert(text = tr("Type your PIN to change App lock."), modifier = Modifier.fillMaxWidth())
            TvPrimaryButton(text = tr("Enter the PIN"), onClick = { asking = true })
            return@Column
        }
        if (!hasPin) {
            Alert(text = tr("Set a PIN first. It's 4 to 6 digits, and it's stored scrambled on the TV, never readable."), modifier = Modifier.fillMaxWidth())
            TvPrimaryButton(text = tr("Set a PIN"), onClick = { settingPin = true })
            return@Column
        }
        TvSwitch(
            title = tr("App lock"),
            subtitle = if (config.lockOn) tr(if (config.lockApps.size == 1) "{n} app is locked" else "{n} apps are locked", "n" to config.lockApps.size) + (if (config.lockSystem) tr(", plus Settings, the Play Store and Quick settings") else "")
            else tr("Your PIN and the list stay while it's off"),
            checked = config.lockOn,
            onCheckedChange = { set(JSONObject().put("lock_on", it)) }
        )
        if (config.lockOn) ServiceNote("locked apps still open without the PIN")
        TvSwitch(
            title = tr("Settings, Play Store and Quick settings"),
            subtitle = tr("Also the uninstaller, so the lock can't be switched off around it"),
            checked = config.lockSystem,
            onCheckedChange = { set(JSONObject().put("lock_system", it)) }
        )
        TvSwitch(
            title = tr("Customize Home"),
            subtitle = tr("This editor asks for the PIN before it opens. App lock here always does."),
            checked = config.lockEditor,
            onCheckedChange = { set(JSONObject().put("lock_editor", it)) }
        )
        TvRadioGroup(
            title = tr("An unlock lasts"),
            options = listOf("sleep" to tr("Until the TV sleeps"), "15" to tr("15 minutes"), "always" to tr("Until another app opens")),
            current = config.lockFor,
            onSelect = { set(JSONObject().put("lock_for", it)) }
        )
        TvRadioGroup(
            title = tr("Locked apps on Home"),
            options = listOf("badge" to tr("Show them with a lock"), "hide" to tr("Hide them")),
            current = config.lockShow,
            onSelect = { set(JSONObject().put("lock_show", it)) }
        )

        // --- Locked Apps Header & Quick Lock/Unlock Actions ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("Select Locked Apps ({n} locked)", "n" to config.lockApps.size), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(tr("Select an app to require PIN before launching."), color = MUTED_COLOR, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSecondaryButton(
                    text = tr("Lock All"),
                    onClick = {
                        val allPkgs = apps.map { it.pkg }
                        set(JSONObject().put("lock_apps", JSONArray(allPkgs)))
                    }
                )

                TvSecondaryButton(
                    text = tr("Unlock All"),
                    onClick = {
                        set(JSONObject().put("lock_apps", JSONArray()))
                    }
                )
            }
        }

        // --- 5-Column App Cards for Lock Selection ---
        val lockedSet = remember(config.lockApps) { config.lockApps.toSet() }
        val appRows = remember(apps) { apps.chunked(5) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appRows.forEach { rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowApps.forEach { app ->
                        val isLocked = app.pkg in lockedSet
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = {
                                val nextLocked = lockedSet.toMutableSet()
                                if (isLocked) nextLocked.remove(app.pkg) else nextLocked.add(app.pkg)
                                set(JSONObject().put("lock_apps", JSONArray(nextLocked.toList())))
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLocked) Color(0x44FFFFFF) else Color(0x11FFFFFF),
                                focusedContainerColor = Color.White,
                                focusedContentColor = CUSTOMIZATION_BG
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFocused = it.isFocused }
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x22FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = app.art,
                                        contentDescription = app.label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit
                                    )

                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x99000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = tr("Locked"),
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = app.label,
                                    color = if (isFocused) CUSTOMIZATION_BG else TEXT_COLOR,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = if (isLocked) tr("LOCKED") else tr("UNLOCKED"),
                                    color = if (isFocused) CUSTOMIZATION_BG else if (isLocked) Color(0xFF4CAF50) else MUTED_COLOR,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    repeat(5 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Text(tr("Daily limits"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(tr("After its minutes for the day, an app needs the PIN, which gives it the rest of the day. The count starts again at midnight."), color = MUTED_COLOR, fontSize = 13.sp)
        config.limits.forEach { (pkg, minutes) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val used = (Limits.used(ctx, pkg) / 60_000L).toInt()
                Text(tr("{app}: {limit} a day, {used} used today", "app" to (apps.firstOrNull { it.pkg == pkg }?.label ?: pkg), "limit" to Limits.words(minutes), "used" to Limits.words(used)), color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TvDestructiveButton(text = tr("Remove"), onClick = { set(JSONObject().put("limits", JSONObject((config.limits - pkg) as Map<*, *>))) })
            }
        }
        TvSecondaryButton(text = tr("Add a daily limit"), onClick = { pickingLimit = true })
        if (config.limits.isNotEmpty()) ServiceNote("the limits don't count")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvSecondaryButton(text = tr("Change the PIN"), onClick = { settingPin = true })
            TvDestructiveButton(text = tr("Forget the PIN"), onClick = {
                Lock.clearPin(ctx)
                ConfigStore.update(ctx, JSONObject().put("lock_on", false))
                hasPin = false
            })
        }
        Text(tr("Forgetting the PIN turns App lock off. The list of locked apps stays."), color = MUTED_COLOR, fontSize = 13.sp)
    }

    if (asking) Dialog(onDismissRequest = { asking = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PinPad(tr("Enter the PIN for App lock"), Lock.PANEL, Color(config.accent)) { asking = false; verified = true }
    }
    if (pickingLimit) Choose(tr("Which app gets a daily limit?"), apps.filter { it.pkg !in config.limits }.map { it.pkg to it.label }, onDismiss = { pickingLimit = false }) {
        pickingLimit = false; limitApp = it
    }
    limitApp?.let { pkg ->
        Choose(tr("How long a day?"), listOf(15, 30, 45, 60, 90, 120, 180, 240).map { "$it" to Limits.words(it) }, onDismiss = { limitApp = null }) { m ->
            limitApp = null
            set(JSONObject().put("limits", JSONObject((config.limits + (pkg to m.toInt())) as Map<*, *>)))
        }
    }
    if (settingPin) Dialog(onDismissRequest = { settingPin = false; newPin = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Home's own number pad, the same one that asks for the PIN later; the remote's number keys work too.
        PinPad(
            title = if (newPin == null) tr("Choose a PIN, 4 to 6 digits") else tr("Type it again"),
            what = Lock.PANEL,
            accent = Color(config.accent),
            onEntered = { pin ->
                when {
                    newPin == null -> { newPin = pin; null }
                    pin != newPin -> { newPin = null; tr("The two didn't match. Start again.") }
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
