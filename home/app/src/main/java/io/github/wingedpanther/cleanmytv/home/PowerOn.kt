package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.util.Log

/**
 * Back to the input at power on (#94). Some TVs come back on the input that was on when they went to sleep;
 * with CleanMyTV Home as Home, others start on Home every time. KeyService notes which app was in front
 * when the screen went off, and if it was the app that shows the TV's inputs, opens that app again when the
 * screen comes on. The TV's input app opens on the last input it showed.
 *
 * The owner can pick one input instead ([HomeConfig.powerOnTarget]), for a TV used as a monitor or one that
 * should start on its channels: that input opens at every power on, whatever the TV slept on.
 */
object PowerOn {
    /** How long after the screen comes on to look: the TV puts its own screen up first. */
    const val SETTLE_MS = 2000L

    /** What power on does, and why, so a recorded log says where it stopped. */
    sealed interface Wake {
        data class Skip(val why: String) : Wake
        data class App(val pkg: String) : Wake
        data class Input(val id: String) : Wake
    }

    /**
     * [noted] is the input app in front at screen off, if any; [front] is what the TV put up on waking;
     * [inputApps] are the apps that show inputs; [inputs] the input ids the TV has.
     */
    fun decide(on: Boolean, target: String, noted: String?, front: String?, inputApps: Set<String>, inputs: Set<String>): Wake = when {
        !on -> Wake.Skip("switched off")
        target.isNotEmpty() && target !in inputs -> Wake.Skip("the TV has no input $target")
        // The TV, or a device on HDMI, picked an input already.
        target.isNotEmpty() && front != null && front in inputApps -> Wake.Skip("the TV is already on an input in $front")
        target.isNotEmpty() -> Wake.Input(target)
        noted == null -> Wake.Skip("nothing to go back to, the screen went off away from an input")
        front == noted -> Wake.Skip("the TV is already on $noted")
        else -> Wake.App(noted)
    }

    /** How long after the screen comes on to act. */
    fun delayMs(cfg: HomeConfig): Long = SETTLE_MS + cfg.powerOnWait * 1000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("power_on", Context.MODE_PRIVATE)

    /** The apps that open an HDMI port's picture or the tuner's channels, minus launchers. */
    fun inputApps(ctx: Context): Set<String> = runCatching {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptySet()
        val uris = tim.tvInputList.map {
            if (it.isPassthroughInput) TvContract.buildChannelUriForPassthroughInput(it.id) else TvContract.buildChannelsUriForInput(it.id)
        } + TvContract.Channels.CONTENT_URI
        val pm = ctx.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }.toSet()
        uris.flatMap { u -> pm.queryIntentActivities(Intent(Intent.ACTION_VIEW, u), 0).map { it.activityInfo.packageName } }.toSet() - launchers - ctx.packageName
    }.getOrDefault(emptySet())

    private fun inputIds(ctx: Context): Set<String> = runCatching {
        (ctx.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager)?.tvInputList?.map { it.id }?.toSet()
    }.getOrNull() ?: emptySet()

    /**
     * The inputs the owner can pick: the TV's own tuner, HDMI and the other ports, named as the Inputs menu
     * names them. The tuner is there whatever the Live TV switch says, for a TV that should start on its
     * channels (#128).
     */
    fun choices(ctx: Context): List<Pair<String, String>> = tvInputs(ctx, liveTv = true)

    /**
     * The screen went off with [front] in front. The log line says what was in front and whether it counted
     * as an input, so a recorded log shows where a TV that wakes on Home lost the input (#94).
     */
    fun asleep(ctx: Context, front: String?) {
        val on = ConfigStore.config.value.powerOnInput
        val inputs = if (on) inputApps(ctx) else emptySet()
        val app = front?.takeIf { on && it in inputs }
        prefs(ctx).edit().putString("app", app).apply()
        if (on) Log.i(TAG, "power on: screen off with $front in front, ${if (app != null) "an input" else "not an input"}; input apps: ${inputs.joinToString().ifEmpty { "none" }}")
    }

    /** The screen is on, or the TV started; [front] is what the TV put up. */
    fun awake(ctx: Context, front: String?) {
        val noted = prefs(ctx).getString("app", null)
        prefs(ctx).edit().remove("app").apply()
        val cfg = ConfigStore.config.value
        if (!cfg.powerOnInput && noted == null) return
        val target = cfg.powerOnTarget
        val wake = decide(cfg.powerOnInput, target, noted, front, if (target.isEmpty()) emptySet() else inputApps(ctx), if (target.isEmpty()) emptySet() else inputIds(ctx))
        when (wake) {
            is Wake.Skip -> Log.i(TAG, "power on: ${wake.why}")
            is Wake.Input -> {
                Log.i(TAG, "power on: opening input ${wake.id}")
                openInput(ctx, wake.id)
            }
            is Wake.App -> {
                val pm = ctx.packageManager
                val open = pm.getLeanbackLaunchIntentForPackage(wake.pkg) ?: pm.getLaunchIntentForPackage(wake.pkg)
                if (open == null) { Log.w(TAG, "power on: ${wake.pkg} has no screen to open"); return }
                runCatching { ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    // Android can still drop a start from the background without an error; ActivityTaskManager logs that.
                    .onSuccess { Log.i(TAG, "power on: opening ${wake.pkg}") }
                    .onFailure { Log.w(TAG, "power on: $it") }
            }
        }
    }
}
