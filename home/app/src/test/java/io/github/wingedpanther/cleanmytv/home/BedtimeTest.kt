package io.github.wingedpanther.cleanmytv.home

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BedtimeTest {
    private val zone = ZoneId.of("Europe/Dublin")
    // Saturday 21 November 2026.
    private fun at(day: Int, h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(2026, 11, day, h, m, s, 0, zone)
    private val half11 = 23 * 60 + 30

    @Test fun `tonight when the note is still ahead`() {
        assertEquals(at(21, 23, 30), Bedtime.next(at(21, 20, 0), half11, (1..7).toList()))
    }

    @Test fun `inside the last minute it waits for the next night`() {
        assertEquals(at(22, 23, 30), Bedtime.next(at(21, 23, 29, 30), half11, (1..7).toList()))
    }

    @Test fun `only on the picked days`() {
        // Saturday evening, school nights only: Sunday's bedtime is next.
        assertEquals(at(22, 23, 30), Bedtime.next(at(21, 20, 0), half11, listOf(1, 2, 3, 4, 7)))
        assertEquals(at(23, 23, 30), Bedtime.next(at(21, 20, 0), half11, (1..5).toList()))
    }

    @Test fun `no days, no bedtime`() {
        assertNull(Bedtime.next(at(21, 20, 0), half11, emptyList()))
    }
}
