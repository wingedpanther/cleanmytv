package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key

/**
 * The first-use tip about holding OK (#146: the owner couldn't find how to move apps). Shown once a day on the
 * first three days Home is used, since Home comes back into view many times a day, and never again once the
 * owner has held OK on an app.
 */
object Hints {
    private const val TIMES = 3
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("hints", Context.MODE_PRIVATE)

    /** True when the tip is due now; counts it as shown. */
    fun take(ctx: Context): Boolean {
        val p = prefs(ctx)
        val n = p.getInt("hold_ok", 0)
        val today = System.currentTimeMillis() / 86_400_000L
        if (n >= TIMES || p.getLong("hold_ok_day", -1L) == today) return false
        p.edit().putInt("hold_ok", n + 1).putLong("hold_ok_day", today).apply()
        return true
    }

    /** The owner held OK on an app: they know. */
    fun done(ctx: Context) = prefs(ctx).edit().putInt("hold_ok", TIMES).apply()
}

/**
 * Whether this remote has number keys and colour keys. Both are off until Home sees one pressed, then stay on:
 * until then Teletext hides its line numbers and no hint asks for a number or a colour.
 */
object RemoteKeys {
    var digits by mutableStateOf(false); private set
    var colours by mutableStateOf(false); private set
    private var loaded = false
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("hints", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        prefs(ctx).let { digits = it.getBoolean("digit_keys", false); colours = it.getBoolean("colour_keys", false) }
    }

    fun seen(ctx: Context, k: Key) {
        if (!digits && digitKey(k) != null) { digits = true; prefs(ctx).edit().putBoolean("digit_keys", true).apply() }
        if (!colours && colourKey(k) != null) { colours = true; prefs(ctx).edit().putBoolean("colour_keys", true).apply() }
    }
}
