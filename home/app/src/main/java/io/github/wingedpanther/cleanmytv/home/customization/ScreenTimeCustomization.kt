package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import io.github.wingedpanther.cleanmytv.home.Recent
import io.github.wingedpanther.cleanmytv.home.ScreenTime
import io.github.wingedpanther.cleanmytv.home.ScreenTimeApps
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.AlertType
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvDestructiveButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvRadioGroup
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSlider
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import io.github.wingedpanther.cleanmytv.home.customization.components.findable
import io.github.wingedpanther.cleanmytv.home.tr
import org.json.JSONArray
import org.json.JSONObject

/** The card's colour choices here; the desktop app takes any colour. Empty is the default. */
private fun cardBg() = listOf("" to tr("Black"), "#2B2B2B" to tr("Charcoal"), "#16213A" to tr("Navy"), "#2D3A2E" to tr("Green"), "#FFFFFF" to tr("White"))
private fun cardText() = listOf("" to tr("Auto"), "#FFFFFF" to tr("White"), "#F0EBE5" to tr("Cream"), "#111111" to tr("Black"))
private fun cardBars() = listOf("" to tr("Accent"), "#FFFFFF" to tr("White"), "#FFB23C" to tr("Amber"), "#2EC4B6" to tr("Teal"), "#E4572E" to tr("Red"))

/** The same goals the desktop app offers, in minutes; 0 is none. */
private val GOALS = listOf(0, 60, 90, 120, 150, 180, 240, 300, 360)

/**
 * Screen time from the remote: the card on Home, the daily goal, the PIN for the dashboard and the history.
 * With a PIN set, turning the PIN off or clearing the history asks for it, so the remote alone can't hide the hours.
 */
@Composable
fun ScreenTimeCustomization(config: HomeConfig, ctx: Context) {
    // Looked at again when the editor comes back from Android's usage access screen.
    var allowed by remember { mutableStateOf(Recent.allowed(ctx)) }
    val owner = ctx as? androidx.activity.ComponentActivity
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = Recent.allowed(ctx) }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }
    var pickingGoal by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    var afterPin by remember { mutableStateOf<(() -> Unit)?>(null) }
    val guarded = { act: () -> Unit -> if (Lock.hasPin(ctx)) afterPin = act else act() }
    val set = { change: JSONObject -> ConfigStore.update(ctx, change); KeyService.ensureOn(ctx) }
    val usageScreen = remember { Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).takeIf { it.resolveActivity(ctx.packageManager) != null } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = tr("Show Screen time on Home"),
            subtitle = tr("A card with today's time, the week and the top apps. OK on it opens the dashboard."),
            checked = "screentime" in config.sections,
            onCheckedChange = { on ->
                // In after Now playing and Recently used, where it's seen without scrolling.
                val list = config.sections.filter { it != "screentime" }.toMutableList()
                if (on) list.add(list.indexOfFirst { it != "nowplaying" && it != "recent" }.let { if (it < 0) list.size else it }, "screentime")
                ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(list)))
            }
        )
        if ("screentime" in config.sections) {
            Text(tr("The card on Home"), color = Color.White, fontSize = 15.sp, modifier = Modifier.findable(tr("The card on Home")))
            TvRadioGroup(tr("Background"), cardBg(), config.stBg) { ConfigStore.update(ctx, JSONObject().put("st_bg", it)) }
            TvSlider(tr("Background opacity"), config.stOpacity, 0, 100, "%") { ConfigStore.update(ctx, JSONObject().put("st_opacity", it)) }
            TvRadioGroup(tr("Text"), cardText(), config.stText) { ConfigStore.update(ctx, JSONObject().put("st_text", it)) }
            TvRadioGroup(tr("Bars"), cardBars(), config.stBars) { ConfigStore.update(ctx, JSONObject().put("st_bars", it)) }
        }
        if (!allowed) {
            // Some TVs have Android's own screen for it; the others leave it to the computer.
            Alert(if (usageScreen != null) tr("Screen time needs usage access. Press Allow and turn on CleanMyTV Home in the list, or tick Screen time in CleanMyTV on a computer.")
                else tr("Screen time needs usage access. In CleanMyTV on a computer, tick Screen time under CleanMyTV Home, Layout, Sections."), Modifier.fillMaxWidth(), AlertType.WARNING)
            usageScreen?.let { i -> TvPrimaryButton(tr("Allow"), onClick = { runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }) }
        }
        TvSecondaryButton(text = tr("Open the dashboard"), onClick = { ScreenTimeApps.open(ctx) })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val goal = if (config.screenGoal > 0) Limits.words(config.screenGoal) else tr("No goal")
            Text("${tr("Daily goal")}: $goal", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TvSecondaryButton(text = tr("Change"), onClick = { pickingGoal = true }, modifier = Modifier.findable(tr("Daily goal")))
        }
        Text(tr("Once the TV has been on this long in a day, a note says so over whatever is on, once a day."), color = MUTED_COLOR, fontSize = 13.sp)
        if (config.screenGoal > 0) ServiceNote(tr("the note doesn't show"))

        if (Lock.hasPin(ctx) && config.lockOn) TvSwitch(
            title = tr("Ask for the PIN"),
            subtitle = tr("Screen time's dashboard asks for the PIN before it opens."),
            checked = config.lockScreenTime,
            onCheckedChange = { on -> if (on) set(JSONObject().put("lock_screentime", true)) else guarded { set(JSONObject().put("lock_screentime", false)) } }
        ) else Alert(tr("To keep the dashboard behind a PIN, set a PIN and turn on App lock first."), Modifier.fillMaxWidth())
        Text(tr("Each app's daily limit is on its page in the dashboard, and under App lock."), color = MUTED_COLOR, fontSize = 13.sp)

        TvDestructiveButton(text = tr("Clear the history"), onClick = { confirmClear = true }, modifier = Modifier.findable(tr("Clear the history")))
        Text(tr("Home forgets the days it saved. Android keeps about a week of its own, so the last few days come back."), color = MUTED_COLOR, fontSize = 13.sp)
        if (cleared) Alert(tr("Cleared."), Modifier.fillMaxWidth(), AlertType.SUCCESS)
    }

    if (pickingGoal) Choose(tr("Daily goal"), GOALS.map { "$it" to if (it == 0) tr("No goal") else Limits.words(it) }, onDismiss = { pickingGoal = false }) { m ->
        pickingGoal = false
        set(JSONObject().put("screen_goal", m.toInt()))
    }
    if (confirmClear) Choose(tr("Clear the screen time history?"), listOf("yes" to tr("Clear it"), "no" to tr("Keep it")), onDismiss = { confirmClear = false }) { pick ->
        confirmClear = false
        if (pick == "yes") guarded { ScreenTime.clear(ctx); cleared = true }
    }
    afterPin?.let { act ->
        Dialog(onDismissRequest = { afterPin = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PinPad(tr("Enter the PIN for Screen time"), Lock.SCREENTIME, Color(config.accent)) { afterPin = null; act() }
        }
    }
}
