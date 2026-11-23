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

/** One app's day past its minutes: how many times it was on, its longest stretch, and when it last left the front. */
data class AppDay(val sessions: Int, val longest: Long, val last: Long)

/**
 * One day on the TV: milliseconds per app, per hour of the day (0 to 23) for all apps together, and each
 * app's [AppDay]. Days saved by Home 1.13.7 have no [stats].
 */
class DayUse(val day: LocalDate, val apps: Map<String, Long>, val hours: LongArray, val stats: Map<String, AppDay> = emptyMap()) {
    val total: Long get() = apps.values.sum()

    fun json(): JSONObject {
        val a = JSONObject()
        apps.forEach { (p, ms) -> if (ms >= 1000) a.put(p, ms / 1000) }
        val s = JSONObject()
        stats.forEach { (p, d) -> if (a.has(p)) s.put(p, JSONArray(listOf(d.sessions, d.longest / 1000, d.last / 1000))) }
        return JSONObject().put("apps", a).put("hours", JSONArray(hours.map { it / 1000 })).put("s", s)
    }

    companion object {
        /** Seconds in the file, milliseconds here. */
        fun from(day: LocalDate, d: JSONObject): DayUse {
            val a = d.getJSONObject("apps")
            val h = d.getJSONArray("hours")
            val s = d.optJSONObject("s") ?: JSONObject()
            return DayUse(
                day, a.keys().asSequence().associateWith { a.getLong(it) * 1000 }, LongArray(24) { h.optLong(it) * 1000 },
                s.keys().asSequence().associateWith { p -> s.getJSONArray(p).let { AppDay(it.optInt(0), it.optLong(1) * 1000, it.optLong(2) * 1000) } },
            )
        }
    }
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
    /** Home switched to an input: [Ev.pkg] is its tile's key. The next app to come to the front is showing it. */
    const val INPUT = 4

    /**
     * An activity came to the front ([START]), left it ([STOP]), or the screen went off or the TV shut down
     * ([OFF]). [cls] is the activity, so one of an app's screens leaving after the next one opened isn't
     * taken for the app leaving.
     */
    data class Ev(val time: Long, val pkg: String, val kind: Int, val cls: String = "")

    /** How far back the first look goes. Android has usually dropped anything older. */
    private const val FIRST_DAYS = 10L
    /** Saved days older than this go. A day is a few hundred bytes. */
    const val KEEP_DAYS = 400L
    private const val DAY_MS = 24 * 3600 * 1000L
    /** How soon after Home switches to an input the TV's player has to come to the front to count as showing it. */
    private const val INPUT_MS = 15_000L
    /** Back in the same app within this, as after a pop-up or the volume bar, is still the same time on. */
    private const val SAME_SESSION_MS = 60_000L

    /**
     * Adds up the stretches in [events] (oldest first) by day and hour. A stretch still open at the end runs
     * to [to]. An app in front before the first event isn't known, so callers start the events a day early
     * and drop the days before the ones they want.
     *
     * After an [INPUT], the TV's player counts as that input (PS5, not "Live TV") until it leaves the front.
     * Home's own screens ([self]) in between, like the tile's hand-off, don't take it. An input picked from
     * the TV's own menu while the player is up isn't seen, so that time stays with the input Home opened.
     * Time on Home itself isn't counted: the TV sitting on Home isn't time spent watching anything.
     */
    fun tally(events: List<Ev>, to: Long, zone: ZoneId, self: String = ""): Map<LocalDate, DayUse> {
        val apps = HashMap<LocalDate, HashMap<String, Long>>()
        val hours = HashMap<LocalDate, LongArray>()
        val pieces = ArrayList<Triple<String, Long, Long>>()
        fun add(pkg: String, from: Long, until: Long) {
            if (self.isNotEmpty() && pkg == self) return
            if (until > from) pieces.add(Triple(pkg, from, until))
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
        var front: String? = null // what the time counts for: the app, or the input it's showing
        var frontPkg: String? = null // the app actually in front
        var frontCls = ""
        var since = 0L
        var input: Pair<String, Long>? = null
        for (e in events) {
            if (e.time > to) break
            when (e.kind) {
                INPUT -> input = e.pkg to e.time
                START -> {
                    front?.let { add(it, since, e.time) }
                    val i = input
                    front = when {
                        i != null && e.pkg != self && e.time - i.second <= INPUT_MS -> i.first.also { input = null }
                        // The player moving between its own screens is still on the same input.
                        front?.startsWith(InputTiles.PREFIX) == true && e.pkg == frontPkg -> front
                        else -> e.pkg
                    }
                    frontPkg = e.pkg; frontCls = e.cls; since = e.time
                }
                STOP -> if (frontPkg == e.pkg && (e.cls == frontCls || e.cls.isEmpty())) { front?.let { add(it, since, e.time) }; front = null; frontPkg = null }
                OFF -> { front?.let { add(it, since, e.time) }; front = null; frontPkg = null }
            }
        }
        front?.let { add(it, since, to) }
        // Sessions: the same app's stretches with only a short gap between them, counted on each day they cover.
        val stats = HashMap<LocalDate, HashMap<String, AppDay>>()
        fun session(pkg: String, from: Long, until: Long) {
            var t = from
            while (t < until) {
                val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), zone)
                val next = minOf(until, at.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
                val day = stats.getOrPut(at.toLocalDate()) { HashMap() }
                val was = day[pkg] ?: AppDay(0, 0, 0)
                day[pkg] = AppDay(was.sessions + 1, maxOf(was.longest, next - t), maxOf(was.last, next))
                t = next
            }
        }
        var open: Triple<String, Long, Long>? = null
        for (p in pieces) {
            val o = open
            open = if (o != null && o.first == p.first && p.second - o.third <= SAME_SESSION_MS) Triple(o.first, o.second, p.third)
            else { o?.let { session(it.first, it.second, it.third) }; p }
        }
        open?.let { session(it.first, it.second, it.third) }
        return apps.mapValues { (day, a) -> DayUse(day, a, hours[day] ?: LongArray(24), stats[day] ?: emptyMap()) }
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
        for (e in usageEvents(usm, begin, end)) {
            // The same numbers on every Android Home runs on: resumed or moved to the front (1), paused or moved
            // back (2), the screen going off (16, Android 9+) and the TV shutting down (26).
            val kind = when (e.eventType) { 1 -> START; 2 -> STOP; 16, 26 -> OFF; else -> continue }
            list.add(Ev(e.timeStamp, e.packageName ?: "", kind, e.className ?: ""))
        }
        list.addAll(marks(ctx).filter { it.time in begin..end })
        list.sortBy { it.time }
        return tally(list, end, zone, ctx.packageName)
    }

    /**
     * Android's usage events from [begin] to before [end], oldest first. Asked for up to far ahead: a TV that starts
     * with its clock months ahead (a Sony BRAVIA did, June 2027) files the rest of the day under that date, and Android
     * leaves that file out of any question that ends now. The events in it keep their real times.
     */
    fun usageEvents(usm: UsageStatsManager, begin: Long, end: Long): List<UsageEvents.Event> {
        val out = ArrayList<UsageEvents.Event>()
        runCatching {
            val events = usm.queryEvents(begin, Long.MAX_VALUE / 4)
            while (events.hasNextEvent()) {
                val e = UsageEvents.Event()
                events.getNextEvent(e)
                if (e.timeStamp in begin until end) out.add(e)
            }
        }.onFailure { Log.w(TAG, "screen time: $it") }
        out.sortBy { it.timeStamp }
        return out
    }

    private fun load(ctx: Context): Pair<MutableMap<LocalDate, DayUse>, LocalDate?> = cache ?: run {
        val days = HashMap<LocalDate, DayUse>()
        var through: LocalDate? = null
        runCatching {
            val o = JSONObject(file(ctx).readText())
            // Before 3, a TV whose clock started ahead saved its days empty; before 4, days counted the time on
            // Home itself. Those are read again from Android.
            if (o.optInt("v") < 4) return@runCatching
            through = o.optString("through").takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) }
            val all = o.getJSONObject("days")
            for (key in all.keys()) LocalDate.parse(key).let { day -> days[day] = DayUse.from(day, all.getJSONObject(key)) }
        }
        (days to through).also { cache = it }
    }

    /** Seconds, not milliseconds, to keep the file small. Written whole, then swapped in. */
    private fun store(ctx: Context) {
        val (days, through) = cache ?: return
        val all = JSONObject()
        for ((day, use) in days) all.put(day.toString(), use.json())
        val o = JSONObject().put("v", 4).put("through", through?.toString() ?: "").put("days", all)
        runCatching {
            val tmp = File(ctx.filesDir, "screentime.json.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file(ctx))
        }.onFailure { Log.w(TAG, "screen time: $it") }
    }

    // ---- inputs -------------------------------------------------------------------------------------
    // Android only sees the TV's player app, not which port it shows, so Home notes each input it opens.

    private fun marksFile(ctx: Context) = File(ctx.filesDir, "screentime-inputs.json")
    private const val MARKS_DAYS = 30L

    private fun readMarks(ctx: Context): JSONObject = runCatching { JSONObject(marksFile(ctx).readText()) }.getOrDefault(JSONObject())

    private fun marks(ctx: Context): List<Ev> = runCatching {
        val a = readMarks(ctx).getJSONArray("marks")
        (0 until a.length()).map { a.getJSONArray(it).let { m -> Ev(m.getLong(0), m.getString(1), INPUT) } }
    }.getOrDefault(emptyList())

    /** Home is switching to input [id]: noted with its name, for the time the player shows it. */
    @Synchronized
    fun inputOpened(ctx: Context, id: String) {
        val key = InputTiles.key(id)
        val name = runCatching { tvInputs(ctx, liveTv = true).firstOrNull { it.first == id }?.second }.getOrNull() ?: id
        val o = readMarks(ctx)
        val now = System.currentTimeMillis()
        val old = o.optJSONArray("marks") ?: JSONArray()
        val keep = JSONArray()
        for (i in 0 until old.length()) old.optJSONArray(i)?.let { if (it.optLong(0) > now - MARKS_DAYS * DAY_MS) keep.put(it) }
        keep.put(JSONArray(listOf(now, key)))
        o.put("marks", keep).put("names", (o.optJSONObject("names") ?: JSONObject()).put(key, name))
        runCatching { marksFile(ctx).writeText(o.toString()) }.onFailure { Log.w(TAG, "screen time input: $it") }
    }

    /** The names Home noted for inputs, by their tile keys. */
    fun inputNames(ctx: Context): Map<String, String> = runCatching {
        val n = readMarks(ctx).getJSONObject("names")
        n.keys().asSequence().associateWith { n.getString(it) }
    }.getOrDefault(emptyMap())

    /** The first day Home has anything for, or null before the first look. */
    @Synchronized
    fun firstDay(ctx: Context): LocalDate? = load(ctx).first.filterValues { it.total > 0 }.keys.minOrNull()

    /**
     * For the desktop app: up to [PAGE] days starting [back] days before today, with today and the first day
     * Home has. A page at a time, since a binder answer has a size limit.
     */
    fun page(ctx: Context, back: Long): JSONObject {
        val today = LocalDate.now(zone)
        val from = today.minusDays(back.coerceIn(0, KEEP_DAYS))
        val days = days(ctx, from) ?: return JSONObject().put("allowed", false)
        val list = JSONArray()
        days.take(PAGE).forEach { d -> list.put(d.json().put("day", d.day.toString())) }
        val names = JSONObject()
        inputNames(ctx).forEach { (k, v) -> names.put(k, v) }
        return JSONObject().put("allowed", true).put("today", today.toString()).put("first", firstDay(ctx)?.toString() ?: today.toString()).put("days", list).put("names", names)
    }
    const val PAGE = 60

    /** "3 h 20 min", "45 min", "0 min": how the section and the dashboard say a length of time. */
    fun words(ms: Long): String = Limits.words(((ms + 30_000) / 60_000).toInt())
}
