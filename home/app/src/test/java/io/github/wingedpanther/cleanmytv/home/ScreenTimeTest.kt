package io.github.wingedpanther.cleanmytv.home

import io.github.wingedpanther.cleanmytv.home.ScreenTime.Ev
import io.github.wingedpanther.cleanmytv.home.ScreenTime.OFF
import io.github.wingedpanther.cleanmytv.home.ScreenTime.START
import io.github.wingedpanther.cleanmytv.home.ScreenTime.STOP
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ScreenTimeTest {
    private val zone = ZoneId.of("Europe/Dublin")
    private fun at(s: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(s), zone).toInstant().toEpochMilli()
    private val min = 60_000L
    private val day = LocalDate.parse("2026-11-20")

    @Test fun `each app counts from coming to the front until the next one does`() {
        val d = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T19:00"), "youtube", START),
            Ev(at("2026-11-20T19:40"), "netflix", START),
            Ev(at("2026-11-20T21:10"), "netflix", OFF),
        ), at("2026-11-20T23:00"), zone)[day]!!
        assertEquals(40 * min, d.apps["youtube"])
        assertEquals(90 * min, d.apps["netflix"])
        assertEquals(130 * min, d.total)
        assertEquals(60 * min, d.hours[19])
        assertEquals(60 * min, d.hours[20])
        assertEquals(10 * min, d.hours[21])
    }

    @Test fun `a stretch past midnight is split between the two days`() {
        val all = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T23:30"), "netflix", START),
            Ev(at("2026-11-21T00:45"), "netflix", STOP),
        ), at("2026-11-21T12:00"), zone)
        assertEquals(30 * min, all[day]!!.apps["netflix"])
        assertEquals(45 * min, all[day.plusDays(1)]!!.apps["netflix"])
        assertEquals(30 * min, all[day]!!.hours[23])
    }

    @Test fun `one screen of an app leaving after its next screen opened doesn't stop the app`() {
        val d = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T10:00"), "youtube", START, "Main"),
            Ev(at("2026-11-20T10:20"), "youtube", START, "Player"),
            Ev(at("2026-11-20T10:21"), "youtube", STOP, "Main"),
            Ev(at("2026-11-20T11:00"), "youtube", STOP, "Player"),
        ), at("2026-11-20T12:00"), zone)[day]!!
        assertEquals(60 * min, d.apps["youtube"])
    }

    @Test fun `the screen going off stops the count, and an app still in front counts until now`() {
        val d = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T08:00"), "news", START),
            Ev(at("2026-11-20T08:30"), "", OFF),
            Ev(at("2026-11-20T18:00"), "home", START),
        ), at("2026-11-20T18:15"), zone)[day]!!
        assertEquals(30 * min, d.apps["news"])
        assertEquals(15 * min, d.apps["home"])
    }

    @Test fun `events after the end are left for the next look`() {
        val d = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T08:00"), "news", START),
            Ev(at("2026-11-20T10:00"), "home", START),
        ), at("2026-11-20T09:00"), zone)[day]!!
        assertEquals(60 * min, d.apps["news"])
        assertEquals(null, d.apps["home"])
    }

    @Test fun `a range fills the days nothing was on`() {
        val saved = mapOf(day to DayUse(day, mapOf("a" to min), LongArray(24)))
        val r = ScreenTime.range(saved, day.minusDays(1), day.plusDays(1))
        assertEquals(listOf(day.minusDays(1), day, day.plusDays(1)), r.map { it.day })
        assertEquals(listOf(0L, min, 0L), r.map { it.total })
    }

    @Test fun `sessions join short gaps, count on each day they cover, and keep the longest and the last`() {
        val all = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T20:00"), "youtube", START),
            Ev(at("2026-11-20T20:30"), "youtube", STOP),
            // Back after 40 seconds, as after the volume bar: the same session.
            Ev(at("2026-11-20T20:30").plus(40_000), "youtube", START),
            Ev(at("2026-11-20T21:00"), "youtube", STOP),
            Ev(at("2026-11-20T22:00"), "youtube", START),
            Ev(at("2026-11-21T00:20"), "", OFF),
        ), at("2026-11-21T12:00"), zone)
        assertEquals(AppDay(2, 120 * min, at("2026-11-21T00:00")), all[day]!!.stats["youtube"])
        assertEquals(AppDay(1, 20 * min, at("2026-11-21T00:20")), all[day.plusDays(1)]!!.stats["youtube"])
    }

    @Test fun `a day keeps its minutes, hours and sessions through the file`() {
        val d = ScreenTime.tally(listOf(
            Ev(at("2026-11-20T19:00"), "youtube", START),
            Ev(at("2026-11-20T19:40"), "netflix", START),
            Ev(at("2026-11-20T21:10"), "netflix", OFF),
        ), at("2026-11-20T23:00"), zone)[day]!!
        val back = DayUse.from(day, org.json.JSONObject(d.json().toString()))
        assertEquals(d.apps, back.apps)
        assertEquals(d.hours.toList(), back.hours.toList())
        assertEquals(d.stats, back.stats)
        // Home 1.13.7 saved days without sessions.
        assertEquals(emptyMap<String, AppDay>(), DayUse.from(day, org.json.JSONObject("""{"apps":{"a":60},"hours":[]}""")).stats)
    }
}
