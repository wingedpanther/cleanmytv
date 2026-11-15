package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily limits: an app gets so many minutes a day, then it needs the PIN, like a locked app. Home's button
 * service counts the time from which app is in front, so no usage permission is needed. A right PIN gives
 * that app the rest of the day. The count starts again at midnight.
 */
object Limits {
    private var front: String? = null
    private var since = 0L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("limits", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    /** Yesterday's counts go when the day changes. */
    private fun fresh(ctx: Context) {
        val p = prefs(ctx)
        if (p.getString("day", null) != today()) p.edit().clear().putString("day", today()).apply()
    }

    /** [pkg] came to the front (null: the screen went off). The app before it stops counting. */
    @Synchronized
    fun front(ctx: Context, pkg: String?) {
        val now = SystemClock.elapsedRealtime()
        front?.let { add(ctx, it, now - since) }
        front = pkg
        since = now
    }

    private fun add(ctx: Context, pkg: String, ms: Long) {
        if (ms <= 0 || pkg !in ConfigStore.config.value.limits) return
        fresh(ctx)
        val p = prefs(ctx)
        p.edit().putLong("used:$pkg", p.getLong("used:$pkg", 0) + ms).apply()
    }

    /** Milliseconds [pkg] has been in front today, counting the stretch it's in now. */
    @Synchronized
    fun used(ctx: Context, pkg: String): Long {
        fresh(ctx)
        val now = if (front == pkg) SystemClock.elapsedRealtime() - since else 0
        return prefs(ctx).getLong("used:$pkg", 0) + now
    }

    /** Time left today, or null when [pkg] has no limit or the PIN already gave it the day. */
    fun left(ctx: Context, pkg: String): Long? {
        val minutes = ConfigStore.config.value.limits[pkg] ?: return null
        if (extended(ctx, pkg)) return null
        return (minutes * 60_000L - used(ctx, pkg)).coerceAtLeast(0)
    }

    fun over(ctx: Context, pkg: String): Boolean = left(ctx, pkg) == 0L

    fun extended(ctx: Context, pkg: String): Boolean { fresh(ctx); return prefs(ctx).getBoolean("more:$pkg", false) }

    /** The PIN was right: [pkg] has no limit for the rest of today. */
    fun extend(ctx: Context, pkg: String) { fresh(ctx); prefs(ctx).edit().putBoolean("more:$pkg", true).apply() }

    /** "1 h 20 min", "45 min": for the editor's list. */
    fun words(minutes: Int): String = when {
        minutes < 60 -> "$minutes min"
        minutes % 60 == 0 -> "${minutes / 60} h"
        else -> "${minutes / 60} h ${minutes % 60} min"
    }
}
