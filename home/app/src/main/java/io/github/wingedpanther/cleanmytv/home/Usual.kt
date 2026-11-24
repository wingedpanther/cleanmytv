package io.github.wingedpanther.cleanmytv.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * The apps opened most around this hour on the last few days, from Android's usage events, for where the
 * focus starts when Home opens. The tiles keep their order. Needs usage access, as Recent and Screen time do.
 */
object Usual {
    private const val DAYS = 7L
    /** Opened around this hour on at least this many days, so one late film doesn't move the focus. */
    const val MIN_DAYS = 2

    fun apps(ctx: Context, shown: Set<String>): List<String> = runCatching {
        if (!Recent.allowed(ctx)) return emptyList()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        @Suppress("DEPRECATION")
        val opened = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        val opens = ArrayList<Pair<Long, String>>()
        for (e in ScreenTime.usageEvents(usm, now - DAYS * 24 * 3600 * 1000, now + 1)) {
            if (e.eventType == opened) opens.add(e.timeStamp to e.packageName)
        }
        ranked(opens, now, ZoneId.systemDefault(), shown - ctx.packageName)
    }.getOrDefault(emptyList())

    /**
     * Of [opens] (time, package), the apps in [shown] opened within an hour either side of [now]'s hour on at
     * least [MIN_DAYS] days, most days first. An app's many screens in one evening count as one day. A tie goes
     * to the one opened last.
     */
    fun ranked(opens: List<Pair<Long, String>>, now: Long, zone: ZoneId, shown: Set<String>): List<String> {
        val hour = Instant.ofEpochMilli(now).atZone(zone).hour
        val days = HashMap<String, HashSet<Long>>()
        val last = HashMap<String, Long>()
        for ((t, pkg) in opens) {
            if (pkg !in shown) continue
            val at = Instant.ofEpochMilli(t).atZone(zone)
            val off = Math.floorMod(at.hour - hour, 24)
            if (off > 1 && off < 23) continue
            days.getOrPut(pkg) { HashSet() }.add(at.toLocalDate().toEpochDay())
            last[pkg] = maxOf(last[pkg] ?: 0L, t)
        }
        return days.filterValues { it.size >= MIN_DAYS }.keys.sortedWith(compareByDescending<String> { days[it]!!.size }.thenByDescending { last[it] })
    }
}
