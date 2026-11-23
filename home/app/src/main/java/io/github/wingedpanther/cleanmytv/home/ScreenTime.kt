package io.github.wingedpanther.cleanmytv.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** One day on the TV: milliseconds per app, and per hour of the day (0 to 23) for all apps together. */
class DayUse(val day: LocalDate, val apps: Map<String, Long>, val hours: LongArray) {
    val total: Long get() = apps.values.sum()
}

/**
 * Screen time: how long each app was in front, day by day. Android's usage events say when an app came to
 * the front and when it left, but keep only about a week of them. So Home saves each finished day's totals
 * in a small file, and the Screen time section and dashboard can show months. Today is always worked out
 * fresh from the events. It needs usage access, which the desktop app grants while the section is on.
 */
object ScreenTime {
    const val START = 1
    const val STOP = 2
    const val OFF = 3

    /**
     * An activity came to the front ([START]), left it ([STOP]), or the screen went off or the TV shut down
     * ([OFF]). [cls] is the activity, so one of an app's screens leaving after the next one opened isn't
     * taken for the app leaving.
     */
    data class Ev(val time: Long, val pkg: String, val kind: Int, val cls: String = "")

    /** How far back the first look goes. Android has usually dropped anything older. */
    private const val FIRST_DAYS = 10L
    /** Saved days older than this go. A day is a few hundred bytes. */
    private const val KEEP_DAYS = 400L
    private const val DAY_MS = 24 * 3600 * 1000L

    /**
     * Adds up the stretches in [events] (oldest first) by day and hour. A stretch still open at the end runs
     * to [to]. An app in front before the first event isn't known, so callers start the events a day early
     * and drop the days before the ones they want.
     */
    fun tally(events: List<Ev>, to: Long, zone: ZoneId): Map<LocalDate, DayUse> {
        val apps = HashMap<LocalDate, HashMap<String, Long>>()
        val hours = HashMap<LocalDate, LongArray>()
        fun add(pkg: String, from: Long, until: Long) {
            var t = from
            while (t < until) {
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone)
                val next = minOf(until, at.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli())
                val day = at.toLocalDate()
                apps.getOrPut(day) { HashMap() }.merge(pkg, next - t, Long::plus)
                hours.getOrPut(day) { LongArray(24) }[at.hour] += next - t
                t = next
            }
        }
        var front: String? = null
        var frontCls = ""
        var since = 0L
        for (e in events) {
            if (e.time > to) break
            when (e.kind) {
                START -> { front?.let { add(it, since, e.time) }; front = e.pkg; frontCls = e.cls; since = e.time }
                STOP -> if (front == e.pkg && (e.cls == frontCls || e.cls.isEmpty())) { add(e.pkg, since, e.time); front = null }
                OFF -> { front?.let { add(it, since, e.time) }; front = null }
            }
        }
        front?.let { add(it, since, to) }
        return apps.mapValues { (day, a) -> DayUse(day, a, hours[day] ?: LongArray(24)) }
    }

    /** Days from [from] to [until], oldest first, with an empty day where nothing was on. */
    fun range(saved: Map<LocalDate, DayUse>, from: LocalDate, until: LocalDate): List<DayUse> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(until) }
            .map { saved[it] ?: DayUse(it, emptyMap(), LongArray(24)) }.toList()

    // ---- on the TV -----------------------------------------------------------------------------------

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private fun file(ctx: Context) = File(ctx.filesDir, "screentime.json")

    /** Saved days, and the last day saved. */
    private var cache: Pair<MutableMap<LocalDate, DayUse>, LocalDate?>? = null

    /**
     * The days from [from] to today, oldest first, today counted up to now. Null without usage access.
     * Finished days not saved yet are saved first. Slow-ish: call it off the main thread.
     */
    @Synchronized
    fun days(ctx: Context, from: LocalDate): List<DayUse>? {
        if (!Recent.allowed(ctx)) return null
        val today = LocalDate.now(zone)
        val (saved, through) = load(ctx)
        val start = through?.plusDays(1) ?: today.minusDays(FIRST_DAYS)
        if (start.isBefore(today)) {
            val fresh = read(ctx, start, today)
            for ((day, use) in fresh) if (!day.isBefore(start) && day.isBefore(today)) saved[day] = use
            saved.keys.removeAll { it.isBefore(today.minusDays(KEEP_DAYS)) }
            cache = saved to today.minusDays(1)
            store(ctx)
        }
        val now = read(ctx, today, null)[today]
        return range(saved + listOfNotNull(now?.let { today to it }), from, today)
    }

    /** Forgets every saved day. Android's own week of events stays, so the last days come back on the next look. */
    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
        cache = null
    }

    /** The usage events from a day before [from] up to the start of [until] (null: now), tallied. */
    private fun read(ctx: Context, from: LocalDate, until: LocalDate?): Map<LocalDate, DayUse> {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val begin = from.atStartOfDay(zone).toInstant().toEpochMilli() - DAY_MS
        val end = until?.atStartOfDay(zone)?.toInstant()?.toEpochMilli() ?: System.currentTimeMillis()
        val list = ArrayList<Ev>()
        runCatching {
            val events = usm.queryEvents(begin, end)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                // The same numbers on every Android Home runs on: resumed or moved to the front (1), paused or moved
                // back (2), the screen going off (16, Android 9+) and the TV shutting down (26).
                val kind = when (e.eventType) { 1 -> START; 2 -> STOP; 16, 26 -> OFF; else -> continue }
                list.add(Ev(e.timeStamp, e.packageName ?: "", kind, e.className ?: ""))
            }
        }.onFailure { Log.w(TAG, "screen time: $it") }
        return tally(list, end, zone)
    }

    private fun load(ctx: Context): Pair<MutableMap<LocalDate, DayUse>, LocalDate?> = cache ?: run {
        val days = HashMap<LocalDate, DayUse>()
        var through: LocalDate? = null
        runCatching {
            val o = JSONObject(file(ctx).readText())
            through = o.optString("through").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            val all = o.getJSONObject("days")
            for (key in all.keys()) {
                val d = all.getJSONObject(key)
                val a = d.getJSONObject("apps")
                val h = d.getJSONArray("hours")
                val day = LocalDate.parse(key)
                days[day] = DayUse(day, a.keys().asSequence().associateWith { a.getLong(it) * 1000 }, LongArray(24) { h.optLong(it) * 1000 })
            }
        }
        (days to through).also { cache = it }
    }

    /** Seconds, not milliseconds, to keep the file small. Written whole, then swapped in. */
    private fun store(ctx: Context) {
        val (days, through) = cache ?: return
        val all = JSONObject()
        for ((day, use) in days) {
            val a = JSONObject()
            use.apps.forEach { (p, ms) -> if (ms >= 1000) a.put(p, ms / 1000) }
            all.put(day.toString(), JSONObject().put("apps", a).put("hours", JSONArray(use.hours.map { it / 1000 })))
        }
        val o = JSONObject().put("v", 1).put("through", through?.toString() ?: "").put("days", all)
        runCatching {
            val tmp = File(ctx.filesDir, "screentime.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file(ctx))
        }.onFailure { Log.w(TAG, "screen time: $it") }
    }

    /** "3 h 20 min", "45 min", "0 min": how the section and the dashboard say a length of time. */
    fun words(ms: Long): String = Limits.words(((ms + 30_000) / 60_000).toInt())
}
