package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import org.json.JSONObject

/**
 * Newly installed apps land where the owner asked (new_apps_to): at the end of a row, or in the dock for a
 * day with a New badge. An app counts by its first install time, which an update keeps, so updates never
 * count. Home looks each time it reloads its apps, which an install sets off (MainActivity's receiver), so an
 * app put on while Home wasn't running still lands when Home next opens.
 */
object NewApps {
    const val DOCK = "dock"
    const val DAY_MS = 24 * 60 * 60_000L
    /** As Config.fromJson keeps a group. */
    private const val MAX_IN_ROW = 60

    /**
     * Where new apps go after the rows change from [before] to [after], from either editor: a renamed row
     * keeps them (same place, same number of rows), a removed one sends them back to All apps.
     */
    fun follow(to: String, before: List<Group>, after: List<Group>): String {
        if (to.isEmpty() || to == DOCK || after.any { it.name == to }) return to
        val i = before.indexOfFirst { it.name == to }
        return when {
            i < 0 -> to
            before.size == after.size -> after[i].name
            else -> ""
        }
    }

    /** The apps first installed after [seen], oldest first. */
    fun since(installed: Map<String, Long>, seen: Long): List<String> =
        installed.entries.filter { it.value > seen }.sortedBy { it.value }.map { it.key }

    /**
     * The rows with [fresh] added to the end of the row new apps go to, or null when nothing changes. An app
     * the owner already put in a row or folder, or hid, stays where it is.
     */
    fun intoRow(cfg: HomeConfig, fresh: List<String>): List<Group>? {
        val i = cfg.rows.indexOfFirst { it.name == cfg.newAppsTo }
        if (cfg.newAppsTo == DOCK || i < 0) return null
        val placed = (cfg.rows + cfg.folders).flatMap { it.apps }.toSet() + cfg.hidden
        val add = fresh.filter { it !in placed }.distinct()
        if (add.isEmpty()) return null
        return cfg.rows.mapIndexed { j, g -> if (j == i) g.copy(apps = (g.apps + add).take(MAX_IN_ROW)) else g }
    }

    /** Installed less than a day before [now]: the New badge, and the dock's extra spot. */
    fun isNew(installed: Long, now: Long): Boolean = installed > 0 && now - installed in 0 until DAY_MS

    /** With new apps going to the dock: the new ones that aren't in a folder, newest first. */
    fun forDock(cfg: HomeConfig, installed: Map<String, Long>, now: Long): List<String> {
        if (cfg.newAppsTo != DOCK) return emptyList()
        val inFolders = cfg.folders.flatMap { it.apps }.toSet()
        return installed.entries.filter { isNew(it.value, now) && it.key !in inFolders && it.key !in cfg.hidden }
            .sortedByDescending { it.value }.map { it.key }
    }

    /**
     * After each app-list load: puts apps installed since the last look into their row, then remembers the
     * newest install. The first look only remembers, so the apps already on the TV stay where they are.
     */
    fun check(ctx: Context, apps: List<TvApp>) {
        val newest = apps.maxOfOrNull { it.installed }?.takeIf { it > 0 } ?: return
        val prefs = ctx.getSharedPreferences("new_apps", Context.MODE_PRIVATE)
        val seen = prefs.getLong("seen", -1L)
        if (newest > seen) prefs.edit().putLong("seen", newest).apply()
        if (seen < 0) return
        val rows = intoRow(ConfigStore.config.value, since(apps.associate { it.pkg to it.installed }, seen)) ?: return
        ConfigStore.update(ctx, JSONObject().put("rows", HomeConfig.groupsJson(rows)), remember = false)
    }
}
