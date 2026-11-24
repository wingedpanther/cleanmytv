package io.github.wingedpanther.cleanmytv.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.DisplaySettings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Timer
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

/** The system's own "start screensaver now" activity, present on most Android TV builds. */
val SCREENSAVER = ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")

fun quickActions(): Map<String, QuickAction> = listOf(
    QuickAction("inputs", tr("Inputs"), Icons.Outlined.Input, emptyList()),
    QuickAction("wifi", "Wi-Fi", Icons.Outlined.Wifi, listOf(settings(Settings.ACTION_WIFI_SETTINGS))),
    // Android TV keeps Bluetooth under "Remotes & accessories"; phones have the plain settings screen.
    QuickAction("bluetooth", "Bluetooth", Icons.Outlined.Bluetooth, listOf(
        settings(Settings.ACTION_BLUETOOTH_SETTINGS),
        Intent("com.google.android.intent.action.CONNECT_INPUT"),
        Intent().setComponent(ComponentName("com.android.tv.settings", "com.android.tv.settings.accessories.AddAccessoryActivity")),
        // Android 12+ guards the pairing screen with a Bluetooth permission Home doesn't hold (#97): Settings then.
        settings(Settings.ACTION_SETTINGS),
    )),
    QuickAction("display", tr("Picture & sound"), Icons.Outlined.Tv, listOf(settings(Settings.ACTION_DISPLAY_SETTINGS), settings(Settings.ACTION_SOUND_SETTINGS))),
    QuickAction("apps", tr("Apps"), Icons.Outlined.Apps, listOf(settings(Settings.ACTION_APPLICATION_SETTINGS), settings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))),
    QuickAction("sleep", tr("Screensaver"), Icons.Outlined.Bedtime, listOf(Intent().setComponent(SCREENSAVER), settings(Settings.ACTION_DREAM_SETTINGS))),
    // Needs a search app such as Google's (Katniss); a cleaned TV may have switched it off.
    QuickAction("search", tr("Search"), Icons.Outlined.Search, listOf(Intent(Intent.ACTION_ASSIST), Intent("android.search.action.GLOBAL_SEARCH"), Intent(Intent.ACTION_SEARCH_LONG_PRESS))),
    // Handled by Home itself: turns the music bars on and off.
    QuickAction("music", tr("Music bars"), Icons.Outlined.MusicNote, emptyList()),
    QuickAction("settings", tr("Settings"), Icons.Outlined.Settings, listOf(settings(Settings.ACTION_SETTINGS))),
    // Handled by Home itself: its own settings panel, and the theme carousel.
    QuickAction("panel", tr("Quick settings"), Icons.Outlined.Tune, emptyList()),
    QuickAction("themes", tr("Themes"), Icons.Outlined.Palette, emptyList()),
    // Handled by Home itself: the on-TV editor, while the owner has it switched on (experimental).
    QuickAction("customize", tr("Customize"), Icons.Outlined.DisplaySettings, emptyList()),
    QuickAction("fireplace", tr("Fireplace"), Icons.Outlined.LocalFireDepartment, emptyList()),
    // Handled by Home itself: picks when the TV goes to sleep.
    QuickAction("timer", tr("Sleep timer"), Icons.Outlined.Timer, emptyList()),
    // Handled by Home itself: the screen time dashboard.
    QuickAction("screentime", tr("Screen time"), Icons.Outlined.BarChart, emptyList()),
    // Shown by Home itself, first in the bar, only while a newer Home is on the release page.
    QuickAction("update", tr("Update Home"), Icons.Outlined.SystemUpdate, emptyList()),
    // Shown by Home itself, after the update icon, only while debugging is on (see Debugging).
    QuickAction("debugging", tr("Debugging is on"), Icons.Outlined.BugReport, emptyList()),
    // Handled by Home itself: closes apps in the background. Up to Android 13 (see Memory).
    QuickAction("memory", tr("Clean memory"), Icons.Outlined.CleaningServices, emptyList()),
).associateBy { it.id }

/**
 * The first of the action's intents this TV can open, or null when none can (the icon is then left out).
 * A screen behind a permission Home doesn't hold is skipped, since starting it would only throw.
 */
fun resolve(ctx: Context, a: QuickAction): Intent? =
    a.intents.firstOrNull { canStart(ctx, it) }?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

private fun canStart(ctx: Context, i: Intent): Boolean {
    val info = i.resolveActivityInfo(ctx.packageManager, 0) ?: return false
    val needs = info.permission ?: return true
    return ctx.checkSelfPermission(needs) == PackageManager.PERMISSION_GRANTED
}

/** Inputs needs inputs, Music bars always works, the rest need a screen on this TV that opens them. */
fun canOpen(ctx: Context, a: QuickAction, hasInputs: Boolean): Boolean = when (a.id) {
    "inputs" -> hasInputs
    "memory" -> Memory.canClean
    "music", "panel", "themes", "timer", "update", "debugging", "screentime" -> true
    "fireplace" -> Fire.ready(ctx)
    "customize" -> ConfigStore.config.value.editOnTv
    else -> resolve(ctx, a) != null
}

/** The music icon shows whether the bars are on. */
fun iconFor(a: QuickAction, cfg: HomeConfig) = if (a.id == "music" && !cfg.visualiser) Icons.Outlined.MusicOff else a.icon

/** The quick-settings icons this TV can open, for the desktop editor. */
fun availableQuick(ctx: Context): List<String> {
    val hasInputs = tvInputs(ctx).isNotEmpty()
    return quickActions().values.filter { it.id != "update" && it.id != "debugging" && canOpen(ctx, it, hasInputs) }.map { it.id }
}

/**
 * HDMI and other inputs, on TVs that have them, one per port, plus the TV's own tuner (#63). A tuner counts
 * only from a system app: streaming apps register channel inputs too, and those belong in their own app.
 * The owner can leave Live TV out (#50). A box without inputs returns an empty list.
 */
fun tvInputs(ctx: Context, liveTv: Boolean = ConfigStore.config.value.liveTv): List<Pair<String, String>> = runCatching {
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    pickInputs(tim.tvInputList.filter { it.isPassthroughInput || builtInTuner(it) }.map {
        InputRow(it.id, it.parentId, it.type == TvInputInfo.TYPE_HDMI, it.isHidden(ctx), it.loadCustomLabel(ctx)?.toString(), it.loadLabel(ctx)?.toString(), tuner = !it.isPassthroughInput)
    }, liveTv)
}.getOrElse { emptyList() }

private fun builtInTuner(i: TvInputInfo): Boolean =
    i.type == TvInputInfo.TYPE_TUNER && (i.serviceInfo.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

/** XGIMI projectors don't open a port's picture from its link; their own HDMI player does (#127). */
const val XGIMI_HDMI = "com.xgimi.action.hdmiPlayer"

/**
 * The port name XGIMI's player looks for in its "sw" extra: it matches it inside the input's id, so
 * `…Hdmi2TvInputService/HW2` gives "HDMI2", and an id without one gives its last part, like "HW2".
 */
fun xgimiPort(id: String): String =
    Regex("hdmi(\\d+)", RegexOption.IGNORE_CASE).find(id)?.let { "HDMI" + it.groupValues[1] }
        ?: id.substringAfterLast('/').uppercase()

/**
 * A port opens its own picture, or on an XGIMI projector the projector's player tuned to that port. The tuner
 * opens its channels in the TV's Live TV app, which otherwise comes back on whichever source was used last.
 */
fun openInput(ctx: Context, id: String) {
    runCatching { ScreenTime.inputOpened(ctx, id) }
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
    val passthrough = runCatching { tim?.getTvInputInfo(id)?.isPassthroughInput }.getOrNull() ?: true
    val tries = if (passthrough) listOf(TvContract.buildChannelUriForPassthroughInput(id))
        else listOf(TvContract.buildChannelsUriForInput(id), TvContract.Channels.CONTENT_URI)
    // XGIMI's player also answers the port's link but ignores it, so it goes first, told the port by name.
    val xgimi = Intent(XGIMI_HDMI).putExtra("sw", xgimiPort(id)).putExtra("launcher_start_source", xgimiPort(id)).takeIf { passthrough }
    val intent = (listOfNotNull(xgimi) + tries.map { Intent(Intent.ACTION_VIEW, it) })
        .map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        .firstOrNull { it.resolveActivity(ctx.packageManager) != null }
    runCatching { ctx.startActivity(intent ?: Intent(Intent.ACTION_VIEW, tries.first()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w(TAG, "input $id: $it") }
}
