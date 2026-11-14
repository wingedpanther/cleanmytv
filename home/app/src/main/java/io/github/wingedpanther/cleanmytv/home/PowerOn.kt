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

    /** The screen went off with [front] in front. */
    fun asleep(ctx: Context, front: String?) {
        val app = front?.takeIf { ConfigStore.config.value.powerOnInput && it in inputApps(ctx) }
        prefs(ctx).edit().putString("app", app).apply()
    }

    /** The screen is on, or the TV started; [front] is what the TV put up. */
    fun awake(ctx: Context, front: String?) {
        val app = prefs(ctx).getString("app", null) ?: return
        prefs(ctx).edit().remove("app").apply()
        if (!ConfigStore.config.value.powerOnInput || front == app) return
        val pm = ctx.packageManager
        val open = pm.getLeanbackLaunchIntentForPackage(app) ?: pm.getLaunchIntentForPackage(app) ?: return
        runCatching { ctx.startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { Log.i(TAG, "power on: back to $app") }
            .onFailure { Log.w(TAG, "power on: $it") }
    }
}
