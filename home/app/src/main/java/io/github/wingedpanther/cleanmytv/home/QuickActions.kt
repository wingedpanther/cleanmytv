package io.github.wingedpanther.cleanmytv.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One shortcut in the top bar or rail. `inputs` opens our own picker; the rest open a system screen, or for
 * "app:<package>" the app, drawn with its own icon (`art`) in the same round button.
 */
data class QuickAction(val id: String, val label: String, val icon: ImageVector, val intents: List<Intent>, val art: ImageBitmap? = null)

/** An app shortcut for the top bar, with the app's icon (not its TV banner, which wouldn't fit a circle). */
fun appAction(app: TvApp, icon: ImageBitmap?) = QuickAction("app:${app.pkg}", app.label, Icons.Outlined.Apps, listOf(app.launch), icon)

private fun settings(action: String) = Intent(action)

fun quickActions(): Map<String, QuickAction> = listOf(
    QuickAction("inputs", "Inputs", Icons.Outlined.Input, emptyList()),
    QuickAction("wifi", "Wi-Fi", Icons.Outlined.Wifi, listOf(settings(Settings.ACTION_WIFI_SETTINGS))),
    // Android TV keeps Bluetooth under "Remotes & accessories"; phones have the plain settings screen.
    QuickAction("bluetooth", "Bluetooth", Icons.Outlined.Bluetooth, listOf(
        settings(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent("com.google.android.intent.action.CONNECT_INPUT"),
        Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity")),
    )),
    QuickAction("display", "Picture & sound", Icons.Outlined.Tv, listOf(settings(Settings.ACTION_DISPLAY_SETTINGS), settings(Settings.ACTION_SOUND_SETTINGS))),
    QuickAction("apps", "Apps", Icons.Outlined.Apps, listOf(settings(Settings.ACTION_APPLICATION_SETTINGS), settings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))),
    // The system's own "start screensaver now" activity, present on most Android TV builds.
    QuickAction("sleep", "Screensaver", Icons.Outlined.Bedtime, listOf(Intent().setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")), settings(Settings.ACTION_DREAM_SETTINGS))),
    // Needs a search app such as Google's (Katniss); a cleaned TV may have switched it off.
    QuickAction("search", "Search", Icons.Outlined.Search, listOf(Intent(Intent.ACTION_ASSIST), Intent("android.search.action.GLOBAL_SEARCH"), Intent(Intent.ACTION_SEARCH_LONG_PRESS))),
    // Handled by Home itself: turns the music bars on and off.
    QuickAction("music", "Music bars", Icons.Outlined.MusicNote, emptyList()),
    QuickAction("settings", "Settings", Icons.Outlined.Settings, listOf(settings(Settings.ACTION_SETTINGS))),
    // Handled by Home itself: its own settings panel, theme carousel, and launcher customization.
    QuickAction("panel", "Quick settings", Icons.Outlined.Tune, emptyList()),
    QuickAction("themes", "Themes", Icons.Outlined.Palette, emptyList()),
    QuickAction("fireplace", "Fireplace", Icons.Outlined.LocalFireDepartment, emptyList()),
    QuickAction("customize", "Customize", Icons.Outlined.DisplaySettings, emptyList()),
).associateBy { it.id }

/** The first of the action's intents this TV can open, or null when none can (the icon is then left out). */
fun resolve(ctx: Context, a: QuickAction): Intent? =
    a.intents.firstOrNull { it.resolveActivity(ctx.packageManager) != null }?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

/** Inputs needs inputs, Music bars always works, the rest need a screen on this TV that opens them. */
fun canOpen(ctx: Context, a: QuickAction, hasInputs: Boolean): Boolean = when (a.id) {
    "inputs" -> hasInputs
    "music", "panel", "themes", "fireplace", "customize" -> true
    else -> resolve(ctx, a) != null
}

/** The music icon shows whether the bars are on. */
fun iconFor(a: QuickAction, cfg: HomeConfig) = if (a.id == "music" && !cfg.visualiser) Icons.Outlined.MusicOff else a.icon

/** The quick-settings icons this TV can open, for the desktop editor. */
fun availableQuick(ctx: Context): List<String> {
    val hasInputs = tvInputs(ctx).isNotEmpty()
    return quickActions().values.filter { canOpen(ctx, it, hasInputs) }.map { it.id }
}

/**
 * HDMI and other inputs, on TVs that have them, one per port, plus the TV's own tuner (#63). A tuner counts
 * only from a system app: streaming apps register channel inputs too, and those belong in their own app.
 * A box without inputs returns an empty list.
 */
fun tvInputs(ctx: Context): List<Pair<String, String>> = runCatching {
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    pickInputs(tim.tvInputList.filter { it.isPassthroughInput || builtInTuner(it) }.map {
        InputRow(it.id, it.parentId, it.type == TvInputInfo.TYPE_HDMI, it.isHidden(ctx), it.loadCustomLabel(ctx)?.toString(), it.loadLabel(ctx)?.toString(), tuner = !it.isPassthroughInput)
    })
}.getOrElse { emptyList() }

private fun builtInTuner(i: TvInputInfo): Boolean =
    i.type == TvInputInfo.TYPE_TUNER && (i.serviceInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

/**
 * A port opens its own picture. The tuner opens its channels in the TV's Live TV app, which otherwise
 * comes back on whichever source was used last.
 */
fun openInput(ctx: Context, id: String) {
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    val passthrough = runCatching { tim?.getTvInputInfo(id)?.isPassthroughInput }.getOrNull() ?: true
    val tries = if (passthrough) listOf(TvContract.buildChannelUriForPassthroughInput(id))
        else listOf(TvContract.buildChannelsUriForInput(id), TvContract.Channels.CONTENT_URI)
    val intent = tries.map { Intent(Intent.ACTION_VIEW, it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { it.resolveActivity(ctx.packageManager) != null }
    runCatching { ctx.startActivity(intent ?: Intent(Intent.ACTION_VIEW, tries.first()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w(TAG, "input $id: $it") }
}
