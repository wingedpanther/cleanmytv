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

    @Test fun `an unmapped volume press stops the filter for a moment (#87)`() {
        val mapped = HomeConfig(buttonsOn = true, buttons = listOf(ButtonMap(183, "inputs")))
        assertTrue(KeyService.needsKeys(mapped, listening = false, volumePaused = false))
        assertFalse(KeyService.needsKeys(mapped, listening = false, volumePaused = true))
        // The button finder still hears everything.
        assertTrue(KeyService.needsKeys(mapped, listening = true, volumePaused = true))
    }

    @Test fun `only a launcher's home screen is sent back, not its other screens`() {
        val homes = setOf("com.amazon.tv.launcher.ui.HomeActivity_vNext")
        assertTrue(KeyService.sendsBack("com.amazon.tv.launcher.ui.HomeActivity_vNext", homes, activity = true))
        assertFalse(KeyService.sendsBack("com.amazon.tv.launcher.ui.SettingsActivity", homes, activity = true))
        // A dialog or an unnamed window keeps the old behaviour, as does a launcher whose home can't be read.
        assertTrue(KeyService.sendsBack("android.widget.FrameLayout", homes, activity = false))
        assertTrue(KeyService.sendsBack(null, homes, activity = false))
        assertTrue(KeyService.sendsBack("com.amazon.tv.launcher.ui.SettingsActivity", emptySet(), activity = true))
    }

    @Test fun `the service runs for the redirect and the power-on input too`() {
        assertFalse(KeyService.needsService(HomeConfig()))
        assertTrue(KeyService.needsService(HomeConfig(stockRedirect = "com.google.android.apps.tv.launcherx")))
        assertTrue(KeyService.needsService(HomeConfig(powerOnInput = true)))
        assertTrue(KeyService.needsService(HomeConfig(lockOn = true)))
    }

    @Test fun `stock_redirect takes a package name or nothing`() {
        val c = HomeConfig.fromJson(JSONObject("""{"stock_redirect":"com.amazon.tv.launcher","power_on_input":true}"""))
        assertEquals("com.amazon.tv.launcher", c.stockRedirect)
        assertTrue(c.powerOnInput)
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
        assertEquals("com.amazon.tv.launcher", HomeConfig.fromJson(JSONObject("""{"stock_redirect":"rm -rf /"}"""), c).stockRedirect)
        assertEquals("", HomeConfig.fromJson(JSONObject("""{"stock_redirect":""}"""), c).stockRedirect)
    }

    @Test fun `moving an app from the remote shifts it and stops at the ends`() {
        val shown = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "a", "c", "d"), Reorder.move(shown, "a", 1))
        assertEquals(listOf("a", "b", "c", "d"), Reorder.move(shown, "a", -1))
        assertEquals(listOf("a", "b", "d", "c"), Reorder.move(shown, "c", 4))
        assertEquals(listOf("c", "a", "b", "d"), Reorder.move(shown, "c", -6))
        assertEquals(shown, Reorder.move(shown, "x", 1))
    }

    @Test fun `a move pins what is on screen and keeps the rest of the saved order`() {
        // "f" sits in a folder: it stays in the saved order, after the apps on screen.
        assertEquals(listOf("b", "a", "c", "f"), Reorder.order(listOf("a", "f"), listOf("a", "b", "c"), "b", -1))
        // A row keeps an app it holds but doesn't show (hidden or uninstalled) at the end.
        assertEquals(listOf("b", "a", "gone"), Reorder.row(listOf("a", "gone", "b"), listOf("a", "b"), "a", 1))
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

    @Test fun `shading keeps today's look until someone moves a slider`() {
        val d = HomeConfig()
        assertEquals(listOf(60, 40, 80, 65, 50, 100), listOf(d.shadeTop, d.shadeTopReach, d.shadeBottom, d.shadeBottomReach, d.wash, d.washReach))
        val c = HomeConfig.fromJson(JSONObject("""{"shade_top":0,"shade_top_reach":130,"shade_bottom":-5,"shade_bottom_reach":30,"wash":75,"wash_reach":0}"""))
        assertEquals(listOf(0, 100, 0, 30, 75, 10), listOf(c.shadeTop, c.shadeTopReach, c.shadeBottom, c.shadeBottomReach, c.wash, c.washReach))
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
    }

    @Test fun `recently used is newest first, once each, and only apps Home shows`() {
        val opened = listOf("netflix", "self", "youtube", "netflix", "gone", "plex", "youtube")
        assertEquals(listOf("netflix", "youtube", "plex"), Recent.order(opened, setOf("netflix", "youtube", "plex", "self"), "self"))
        assertEquals(listOf("netflix"), Recent.order(opened, setOf("netflix", "youtube"), "self", limit = 1))
        assertEquals(listOf("recent", "dock", "grid"), HomeConfig.fromJson(JSONObject("""{"sections":["recent","dock","grid"]}""")).sections)
    }

    @Test fun `limits, font, idle style and Bing every day round-trip and stay sane`() {
        val c = HomeConfig.fromJson(JSONObject("""{"limits":{"com.google.android.youtube.tv":60,"bad name":30,"com.netflix.ninja":2},"font":"serif","idle_style":"photos","bing_daily":true,"lock_editor":true}"""))
        assertEquals(mapOf("com.google.android.youtube.tv" to 60), c.limits)
        assertEquals("serif", c.font)
        assertEquals("photos", c.idleStyle)
        assertTrue(c.bingDaily)
        assertTrue(c.lockEditor)
        assertEquals(c, HomeConfig.fromJson(c.toJson()))
        assertEquals("default", HomeConfig.fromJson(JSONObject("""{"font":"comic"}""")).font)
        assertTrue(KeyService.needsService(HomeConfig(limits = mapOf("a.b" to 30))))
        assertTrue("timer" in HomeConfig.BUTTON_ACTIONS)
    }

    @Test fun `time reads the way people say it`() {
        assertEquals("45 min", Limits.words(45))
        assertEquals("2 h", Limits.words(120))
        assertEquals("1 h 30 min", Limits.words(90))
    }

    @Test fun `a release is newer by its numbers, and a test build counts as the version it leads to`() {
        assertTrue(Updates.newer("1.10.0", "1.9.0"))
        assertFalse(Updates.newer("1.9.0", "1.10.0-test.2"))
        assertFalse(Updates.newer("1.10.0", "1.10.0-test.2"))
        assertTrue(Updates.newer("1.10.1", "1.10.0-test.2"))
        assertFalse(Updates.newer("1.9.0", "1.9.0"))
    }

    @Test fun `release notes lose their tables and marks for the TV`() {
        val md = "**New**\n- **Recently used:** a row (#85)\n\n| | |\n|---|---|\n| Mac | `x.dmg` |\n\n**Full Changelog**: https://x\n[Discord](https://discord.gg/a)"
        assertEquals("New\n• Recently used: a row (#85)\n\nDiscord", Updates.plain(md))
    }

    @Test fun `release notes stop at the downloads`() {
        val md = "Smoother Home.\n\n**Updating**\nPress Update.\n\n**Downloads**\n| | |\n|---|---|\n\nYour browser may warn. Questions: [Discord](https://discord.gg/a)."
        assertEquals("Smoother Home.\n\nUpdating\nPress Update.", Updates.plain(md))
    }

    @Test fun `the keyboard guard undoes only the phone keyboard, right after a restart or wake, once`() {
        val bridge = "com.google.android.tv.remote.service/.ImeBridgeService"
        val tcl = "com.tcl.inputmethod.international/.T_IME"
        val hour = 3_600_000L
        assertTrue(Keyboard.shouldUndo(bridge, hour, 10_000, false, true))
        assertTrue(Keyboard.shouldUndo(bridge, 60_000, hour, false, true))
        assertFalse(Keyboard.shouldUndo(bridge, hour, hour, false, true))
        assertFalse(Keyboard.shouldUndo(bridge, hour, 10_000, true, true))
        assertFalse(Keyboard.shouldUndo(bridge, hour, 10_000, false, false))
        assertFalse(Keyboard.shouldUndo(tcl, hour, 10_000, false, true))
        assertFalse(Keyboard.shouldUndo(null, hour, 10_000, false, true))
    }

    @Test fun `a search lands on the control with that name`() {
        val find = io.github.wingedpanther.cleanmytv.home.customization.components.Find
        assertTrue(find.matches("Layout: Shelf, Split or Rail", "Layout"))
        assertTrue(find.matches("Darken the wallpaper", "darken the wallpaper"))
        assertFalse(find.matches("Darken the wallpaper", "Font"))
        assertFalse(find.matches(null, "Font"))
        assertFalse(find.matches("Font", "  "))
    }

    @Test fun `search finds a setting by its name or the words people use, best first`() {
        val s = io.github.wingedpanther.cleanmytv.home.customization.searchSettings("font")
        assertEquals("Font", s.first().name)
        assertEquals(io.github.wingedpanther.cleanmytv.home.customization.CustomizationCategory.LOCK,
            io.github.wingedpanther.cleanmytv.home.customization.searchSettings("password").first().where)
        assertTrue(io.github.wingedpanther.cleanmytv.home.customization.searchSettings("sleep timer").isNotEmpty())
        assertTrue(io.github.wingedpanther.cleanmytv.home.customization.searchSettings("zzz").isEmpty())
        assertTrue(io.github.wingedpanther.cleanmytv.home.customization.searchSettings("  ").isEmpty())
    }
}
