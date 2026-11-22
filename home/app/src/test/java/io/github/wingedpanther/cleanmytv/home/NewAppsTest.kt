package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppsTest {
    private val day = NewApps.DAY_MS
    private val now = 1_800_000_000_000L
    private val rows = listOf(Group("Watch", listOf("com.netflix.ninja")), Group("Music", listOf("com.spotify.tv.android")))

    @Test fun `only apps first installed after the last look are new, oldest first`() {
        val installed = mapOf("a.old" to 100L, "b.new" to 300L, "c.newer" to 200L, "d.same" to 150L)
        assertEquals(listOf("c.newer", "b.new"), NewApps.since(installed, 150L))
    }

    @Test fun `an update keeps its first install time, so it's never new`() {
        // Updated today, installed last month: the first install time is all Home reads.
        assertEquals(emptyList<String>(), NewApps.since(mapOf("com.netflix.ninja" to now - 30 * day), now - day))
    }

    @Test fun `new apps go to the end of the chosen row`() {
        val cfg = HomeConfig(rows = rows, newAppsTo = "Watch")
        val next = NewApps.intoRow(cfg, listOf("com.disney.disneyplus", "tv.twitch.android.app"))!!
        assertEquals(listOf("com.netflix.ninja", "com.disney.disneyplus", "tv.twitch.android.app"), next[0].apps)
        assertEquals(rows[1], next[1])
    }

    @Test fun `nothing changes when off, for the dock, or for a row that's gone`() {
        val fresh = listOf("com.disney.disneyplus")
        assertNull(NewApps.intoRow(HomeConfig(rows = rows), fresh))
        assertNull(NewApps.intoRow(HomeConfig(rows = rows, newAppsTo = NewApps.DOCK), fresh))
        assertNull(NewApps.intoRow(HomeConfig(rows = rows, newAppsTo = "Games"), fresh))
        assertNull(NewApps.intoRow(HomeConfig(rows = rows, newAppsTo = "Watch"), emptyList()))
    }

    @Test fun `an app the owner already placed or hid stays where it is`() {
        val cfg = HomeConfig(rows = rows, folders = listOf(Group("Kids", listOf("a.kids"))), hidden = setOf("a.hidden"), newAppsTo = "Watch")
        assertNull(NewApps.intoRow(cfg, listOf("com.spotify.tv.android", "a.kids", "a.hidden", "com.netflix.ninja")))
    }

    @Test fun `a full row takes no more`() {
        val full = Group("Watch", (1..60).map { "a.app$it" })
        assertEquals(full.apps, NewApps.intoRow(HomeConfig(rows = listOf(full), newAppsTo = "Watch"), listOf("a.extra"))!![0].apps)
    }

    @Test fun `the dock shows apps from the last day, newest first, outside folders`() {
        val cfg = HomeConfig(folders = listOf(Group("Kids", listOf("a.kids"))), hidden = setOf("a.hidden"), newAppsTo = NewApps.DOCK)
        val installed = mapOf("a.hour" to now - 3_600_000L, "a.minute" to now - 60_000L, "a.yesterday" to now - day - 1, "a.kids" to now - 1L, "a.hidden" to now - 1L)
        assertEquals(listOf("a.minute", "a.hour"), NewApps.forDock(cfg, installed, now))
        assertEquals(emptyList<String>(), NewApps.forDock(cfg.copy(newAppsTo = "Watch"), installed, now))
    }

    @Test fun `new means less than a day old, and an input tile never is`() {
        assertTrue(NewApps.isNew(now - day + 1, now))
        assertFalse(NewApps.isNew(now - day, now))
        assertFalse(NewApps.isNew(0L, now))
        assertFalse(NewApps.isNew(now + 60_000L, now))
    }

    @Test fun `a renamed row keeps the new apps, a removed one sends them back to All apps`() {
        val cfg = HomeConfig(rows = rows, newAppsTo = "Watch")
        fun after(vararg names: String) = HomeConfig.fromJson(JSONObject().put("rows", HomeConfig.groupsJson(names.map { Group(it, emptyList()) })), cfg).newAppsTo
        assertEquals("Films", after("Films", "Music"))
        assertEquals("Watch", after("Music", "Watch"))
        assertEquals("", after("Music"))
        // Both in one change: the owner's pick wins.
        assertEquals("Music", HomeConfig.fromJson(JSONObject().put("rows", HomeConfig.groupsJson(listOf(rows[1]))).put("new_apps_to", "Music"), cfg).newAppsTo)
        assertEquals("dock", NewApps.follow("dock", rows, emptyList()))
        assertEquals("Games", NewApps.follow("Games", rows, rows.take(1)))
    }

    @Test fun `the setting is kept as the desktop app sends it`() {
        assertEquals("Watch", HomeConfig.fromJson(JSONObject().put("new_apps_to", " Watch\n")).newAppsTo)
        assertEquals("dock", HomeConfig.fromJson(JSONObject().put("new_apps_to", "dock")).newAppsTo)
        assertEquals("", HomeConfig.fromJson(JSONObject().put("new_apps_to", ""), HomeConfig(newAppsTo = "dock")).newAppsTo)
        assertEquals("dock", HomeConfig.fromJson(JSONObject(), HomeConfig(newAppsTo = "dock")).newAppsTo)
        assertEquals("Watch", HomeConfig.fromJson(HomeConfig(newAppsTo = "Watch").toJson()).newAppsTo)
    }
}
