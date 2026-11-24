package io.github.wingedpanther.cleanmytv.home

import android.content.Context

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
