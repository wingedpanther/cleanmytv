package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtrasTest {
    private val gb = 1024L * 1024 * 1024

    @Test fun `day runs from the day time to the night time`() {
        assertEquals("night", DayNight.slotAt(6 * 60 + 59, 7 * 60, 19 * 60))
        assertEquals("day", DayNight.slotAt(7 * 60, 7 * 60, 19 * 60))
        assertEquals("day", DayNight.slotAt(18 * 60 + 59, 7 * 60, 19 * 60))
        assertEquals("night", DayNight.slotAt(19 * 60, 7 * 60, 19 * 60))
        assertEquals("night", DayNight.slotAt(0, 7 * 60, 19 * 60))
    }

    @Test fun `a night owl's day can start after midnight and still work`() {
        // Day from 10:00, night from 02:00: night is 02:00 to 10:00.
        assertEquals("day", DayNight.slotAt(23 * 60, 10 * 60, 2 * 60))
        assertEquals("day", DayNight.slotAt(60, 10 * 60, 2 * 60))
        assertEquals("night", DayNight.slotAt(3 * 60, 10 * 60, 2 * 60))
        assertEquals("day", DayNight.slotAt(3 * 60, 8 * 60, 8 * 60))
    }

    @Test fun `memory reads as the box is sold`() {
        assertEquals("1.4 of 2 GB free", Memory.line(MemoryNow(free = (1.4 * gb).toLong(), total = (1.85 * gb).toLong())))
        assertEquals("820 MB of 2 GB free", Memory.line(MemoryNow(free = 820L * 1024 * 1024, total = (1.85 * gb).toLong())))
        assertEquals("512 MB of 4 GB free", Memory.line(MemoryNow(free = 512L * 1024 * 1024, total = (3.6 * gb).toLong())))
        assertEquals("0 MB", Memory.size(0L))
    }

    @Test fun `keys are only filtered while a button is mapped or the finder listens (#87)`() {
        assertFalse(KeyService.needsKeys(HomeConfig(), listening = false))
        assertFalse(KeyService.needsKeys(HomeConfig(buttonsOn = true), listening = false))
        assertFalse(KeyService.needsKeys(HomeConfig(buttonsOn = false, buttons = listOf(ButtonMap(183, "inputs"))), listening = false))
        assertTrue(KeyService.needsKeys(HomeConfig(buttonsOn = true, buttons = listOf(ButtonMap(183, "inputs"))), listening = false))
        assertTrue(KeyService.needsKeys(HomeConfig(), listening = true))
    }

    @Test fun `the new settings round-trip and stay in range`() {
        val c = HomeConfig.fromJson(JSONObject("""{"tile_glass":90,"memory":true,"day_night":true,"day_at":1500,"night_at":1200,"quick":["memory","wifi"]}"""))
        assertEquals(60, c.tileGlass)
        assertTrue(c.memory)
        assertTrue(c.dayNight)
        assertEquals(24 * 60 - 1, c.dayAt)
        assertEquals(1200, c.nightAt)
        assertEquals(listOf("memory", "wifi"), c.quick)
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
        assertTrue("memory" in HomeConfig.BUTTON_ACTIONS)
    }
}
