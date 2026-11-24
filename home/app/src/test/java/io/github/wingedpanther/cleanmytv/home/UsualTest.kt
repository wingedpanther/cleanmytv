package io.github.wingedpanther.cleanmytv.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class UsualTest {
    private val zone = ZoneId.of("Europe/Dublin")
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
    private val shown = setOf("youtube", "netflix", "plex")

    @Test fun `the app opened around this hour on the most days wins`() {
        val opens = listOf(at(18, 20) to "netflix", at(19, 21) to "netflix", at(20, 20, 30) to "youtube", at(21, 9) to "youtube", at(22, 9) to "youtube")
        assertEquals("netflix", Usual.ranked(opens, at(24, 20, 45), zone, shown).firstOrNull())
        assertEquals("youtube", Usual.ranked(opens, at(24, 9), zone, shown).firstOrNull())
    }

    @Test fun `many screens of one app in one evening count once`() {
        val opens = (0 until 10).map { at(22, 20, it) to "plex" } + listOf(at(20, 20) to "netflix", at(21, 20) to "netflix")
        assertEquals("netflix", Usual.ranked(opens, at(24, 20), zone, shown).firstOrNull())
    }

    @Test fun `one day is not a habit, and apps not on Home don't count`() {
        assertNull(Usual.ranked(listOf(at(22, 20) to "netflix"), at(24, 20), zone, shown).firstOrNull())
        assertNull(Usual.ranked(listOf(at(21, 20) to "settings", at(22, 20) to "settings"), at(24, 20), zone, shown).firstOrNull())
    }

    @Test fun `the hour either side of midnight counts`() {
        val opens = listOf(at(20, 23, 30) to "plex", at(22, 0, 15) to "plex")
        assertEquals("plex", Usual.ranked(opens, at(24, 0, 10), zone, shown).firstOrNull())
    }

    @Test fun `the rest follow, most days first`() {
        val opens = listOf(at(20, 20) to "plex", at(21, 20) to "plex", at(22, 20) to "plex", at(21, 20) to "netflix", at(22, 20) to "netflix", at(23, 20) to "youtube")
        assertEquals(listOf("plex", "netflix"), Usual.ranked(opens, at(24, 20), zone, shown))
    }

    @Test fun `a tie goes to the one opened last`() {
        val opens = listOf(at(20, 20) to "netflix", at(21, 20) to "youtube", at(22, 20) to "netflix", at(23, 20) to "youtube")
        assertEquals("youtube", Usual.ranked(opens, at(24, 20), zone, shown).firstOrNull())
    }
}
