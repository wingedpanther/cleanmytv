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
 */
object PowerOn {
    /** How long after the screen comes on to look: the TV puts its own screen up first. */
    const val SETTLE_MS = 2000L

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
        val app = prefs(ctx).getString("app", null)
        if (app == null) {
            if (ConfigStore.config.value.powerOnInput) Log.i(TAG, "power on: nothing to go back to, the screen went off away from an input")
            return
        }
        prefs(ctx).edit().remove("app").apply()
        val why = when {
            !ConfigStore.config.value.powerOnInput -> "switched off since the screen went off"
            front == app -> "the TV is already on $app"
            else -> null
        }
        if (why != null) { Log.i(TAG, "power on: $why"); return }
        val pm = ctx.packageManager
        val open = pm.getLeanbackLaunchIntentForPackage(app) ?: pm.getLaunchIntentForPackage(app)
        if (open == null) { Log.w(TAG, "power on: $app has no screen to open"); return }
        runCatching { ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            // Android can still drop a start from the background without an error; ActivityTaskManager logs that.
            .onSuccess { Log.i(TAG, "power on: opening $app") }
            .onFailure { Log.w(TAG, "power on: $it") }
    }
}
