package io.github.wingedpanther.cleanmytv.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextTest {
    private fun row(vararg cells: Pair<String, Any?>): WatchItem? = WatchNext.fromRow(mapOf(*cells)::get)

    private fun item(pkg: String, title: String, engaged: Long, key: String = "$pkg:$title") =
        WatchItem(id = engaged, pkg = pkg, title = title, engaged = engaged, key = key)

    @Test fun `a provider row becomes an item`() {
        val it = row(
            "_id" to 7L, "package_name" to "org.jellyfin.androidtv", "title" to " The Expanse ", "season_display_number" to "2",
            "episode_display_number" to "5", "episode_title" to "Home", "poster_art_uri" to "https://x/p.jpg", "poster_art_aspect_ratio" to 0L,
            "intent_uri" to "jellyfin://item/42", "last_playback_position_millis" to 600_000L, "duration_millis" to 2_400_000L,
            "last_engagement_time_utc_millis" to 1_700_000_000_000L, "content_id" to "42", "browsable" to 1L,
        )!!
        assertEquals(7L, it.id)
        assertEquals("The Expanse", it.title)
        assertEquals("https://x/p.jpg", it.image)
        assertEquals("jellyfin://item/42", it.intentUri)
        assertEquals(0.25f, it.progress!!, 0.0001f)
        assertEquals("org.jellyfin.androidtv:42", it.key)
        assertEquals("S2 E5 · Home", WatchNext.episodeLine(it))
    }

    @Test fun `rows without an app or a title, or taken off the list, are skipped`() {
        assertNull(row("title" to "Film"))
        assertNull(row("package_name" to "a.b", "title" to "  "))
        assertNull(row("package_name" to "a.b", "title" to "Film", "browsable" to 0L))
    }

    @Test fun `no progress bar without a position and a length`() {
        assertNull(row("package_name" to "a.b", "title" to "Film")!!.progress)
        assertNull(row("package_name" to "a.b", "title" to "Film", "last_playback_position_millis" to 5_000L)!!.progress)
        assertNull(row("package_name" to "a.b", "title" to "Film", "duration_millis" to 5_000L)!!.progress)
        assertEquals(1f, row("package_name" to "a.b", "title" to "Film", "last_playback_position_millis" to 9_000L, "duration_millis" to 5_000L)!!.progress!!, 0f)
    }

    @Test fun `a portrait poster gives way to the thumbnail, when there is one`() {
        val poster = "content://a.b/poster"; val thumb = "content://a.b/thumb"
        assertEquals(thumb, row("package_name" to "a.b", "title" to "F", "poster_art_uri" to poster, "poster_art_aspect_ratio" to 4L, "thumbnail_uri" to thumb)!!.image)
        assertEquals(poster, row("package_name" to "a.b", "title" to "F", "poster_art_uri" to poster, "poster_art_aspect_ratio" to 4L)!!.image)
        assertEquals(poster, row("package_name" to "a.b", "title" to "F", "poster_art_uri" to poster, "poster_art_aspect_ratio" to 0L, "thumbnail_uri" to thumb)!!.image)
        assertEquals(thumb, row("package_name" to "a.b", "title" to "F", "thumbnail_uri" to thumb)!!.image)
    }

    @Test fun `the same film is told apart by its content id, then its link, then its title`() {
        assertEquals("a.b:9", row("package_name" to "a.b", "title" to "F", "content_id" to "9", "intent_uri" to "x://1")!!.key)
        assertEquals("a.b:x://1", row("package_name" to "a.b", "title" to "F", "intent_uri" to "x://1")!!.key)
        assertEquals("a.b:F", row("package_name" to "a.b", "title" to "F")!!.key)
    }

    @Test fun `episode line takes what the app gave`() {
        val base = WatchItem(id = 1, pkg = "a.b", title = "Show")
        assertNull(WatchNext.episodeLine(base))
        assertEquals("E3", WatchNext.episodeLine(base.copy(episode = "3")))
        assertEquals("S1", WatchNext.episodeLine(base.copy(season = "1")))
        assertEquals("Pilot", WatchNext.episodeLine(base.copy(episodeTitle = "Pilot")))
    }

    @Test fun `newest first, at most ten, apps on Home only`() {
        val items = (1..14).map { item("a.b", "F$it", engaged = it.toLong()) } + item("gone.app", "Old", 100)
        val shown = WatchNext.pick(items, setOf("a.b"), HomeConfig())
        assertEquals(WatchNext.SHOWN, shown.size)
        assertEquals((14 downTo 5).map { "F$it" }, shown.map { it.title })
    }

    @Test fun `the same film twice shows once, the newest`() {
        val items = listOf(item("a.b", "Old copy", 1, key = "a.b:42"), item("a.b", "New copy", 2, key = "a.b:42"))
        assertEquals(listOf("New copy"), WatchNext.pick(items, setOf("a.b"), HomeConfig()).map { it.title })
    }

    @Test fun `hidden apps stay out`() {
        val items = listOf(item("a.b", "Mine", 1), item("c.d", "Hidden", 2))
        assertEquals(listOf("Mine"), WatchNext.pick(items, setOf("a.b", "c.d"), HomeConfig(hidden = setOf("c.d"))).map { it.title })
    }

    @Test fun `locked apps show with a badge, or not at all with Hide locked apps`() {
        val items = listOf(item("a.b", "Open", 1), item("c.d", "Locked", 2))
        val badge = HomeConfig(lockOn = true, lockApps = listOf("c.d"), lockShow = "badge")
        assertEquals(listOf("Locked", "Open"), WatchNext.pick(items, setOf("a.b", "c.d"), badge).map { it.title })
        assertEquals(listOf("Open"), WatchNext.pick(items, setOf("a.b", "c.d"), badge.copy(lockShow = "hide")).map { it.title })
        // The lock switched off: the hide setting no longer applies.
        assertEquals(2, WatchNext.pick(items, setOf("a.b", "c.d"), badge.copy(lockShow = "hide", lockOn = false)).size)
    }

    @Test fun `pictures shrink by powers of two and never below the tile`() {
        assertEquals(1, WatchNext.sampleSize(400, 392))
        assertEquals(2, WatchNext.sampleSize(1000, 392))
        assertEquals(4, WatchNext.sampleSize(1920, 392))
        assertEquals(1, WatchNext.sampleSize(200, 392))
    }

    @Test fun `Continue watching is on for a new TV, after the dock`() {
        assertEquals(listOf("nowplaying", "dock", "watchnext", "rows", "folders", "grid"), HomeConfig().sections)
        assertEquals(listOf("watchnext", "grid"), HomeConfig.fromJson(JSONObject("""{"sections":["watchnext","grid"]}""")).sections)
    }

    @Test fun `settings from an older Home get Continue watching once`() {
        // Saved by 1.12: no "sections_known".
        assertEquals(listOf("recent", "dock", "watchnext", "grid"), HomeConfig.addNewSections(listOf("recent", "dock", "grid"), null))
        assertEquals(listOf("watchnext", "grid", "rows"), HomeConfig.addNewSections(listOf("grid", "rows"), null))
        assertEquals(listOf("nowplaying", "watchnext", "grid"), HomeConfig.addNewSections(listOf("nowplaying", "grid"), null))
        // Saved by this Home, with it turned off: it stays off.
        val known = HomeConfig.SECTIONS
        assertEquals(listOf("dock", "grid"), HomeConfig.addNewSections(listOf("dock", "grid"), known))
        assertEquals(known.toList(), HomeConfig().toJson().getJSONArray("sections_known").let { a -> (0 until a.length()).map { a.getString(it) } })
    }
}
