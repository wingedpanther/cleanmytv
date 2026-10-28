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
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/** One shortcut in the top bar or rail. `inputs` opens our own picker; the rest open a system screen. */
data class QuickAction(val id: String, val label: String, val icon: ImageVector, val intents: List<Intent>)

private fun settings(action: String) = Intent(action)

fun quickActions(): Map<String, QuickAction> = listOf(
    QuickAction("inputs", "Inputs", Icons.Outlined.Input, emptyList()),
    QuickAction("wifi", "Wi-Fi", Icons.Outlined.Wifi, listOf(settings(Settings.ACTION_WIFI_SETTINGS))),
    QuickAction("bluetooth", "Bluetooth", Icons.Outlined.Bluetooth, listOf(settings(Settings.ACTION_BLUETOOTH_SETTINGS))),
    QuickAction("display", "Picture & sound", Icons.Outlined.Tv, listOf(settings(Settings.ACTION_DISPLAY_SETTINGS), settings(Settings.ACTION_SOUND_SETTINGS))),
    QuickAction("apps", "Apps", Icons.Outlined.Apps, listOf(settings(Settings.ACTION_APPLICATION_SETTINGS))),
    // The system's own "start screensaver now" activity, present on most Android TV builds.
    QuickAction("sleep", "Screensaver", Icons.Outlined.Bedtime, listOf(Intent().setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")), settings(Settings.ACTION_DREAM_SETTINGS))),
    QuickAction("search", "Search", Icons.Outlined.Search, listOf(Intent(Intent.ACTION_ASSIST), Intent("android.search.action.GLOBAL_SEARCH"))),
    QuickAction("settings", "Settings", Icons.Outlined.Settings, listOf(settings(Settings.ACTION_SETTINGS))),
).associateBy { it.id }

/** The first of the action's intents this TV can open, or null when none can (the icon is then left out). */
fun resolve(ctx: Context, a: QuickAction): Intent? =
    a.intents.firstOrNull { it.resolveActivity(ctx.packageManager) != null }?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

/** HDMI and other inputs, on TVs that have them. A box without inputs returns an empty list. */
fun tvInputs(ctx: Context): List<Pair<String, String>> = runCatching {
    val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
    tim.tvInputList
        .filter { it.isPassthroughInput && it.parentId == null || it.type == TvInputInfo.TYPE_HDMI }
        .map { it.id to (it.loadLabel(ctx)?.toString() ?: "Input") }
}.getOrElse { emptyList() }

fun openInput(ctx: Context, id: String) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Log.w(TAG, "input $id: $it") }
}
