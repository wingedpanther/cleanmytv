package io.github.wingedpanther.cleanmytv.home

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import org.json.JSONArray

/**
 * The Recently used row (#85). With usage access, which the desktop app grants over ADB when the row is on,
 * it's every app the TV opened, however it was opened: a remote button, an input, another app. Without it,
 * Home only knows the apps opened from Home itself.
 */
object Recent {
    const val SHOWN = 8
    private const val KEPT = 20
    private const val DAYS = 14L

    /** Packages, most recent first, that Home can show: launchable here, not a home screen, not Home itself. */
    fun apps(ctx: Context, shown: Set<String>): List<String> {
        val opened = if (allowed(ctx)) fromUsage(ctx) else emptyList()
        // The TV's own launcher shows up whenever Home is pressed with it kept on; it isn't an app people open.
        val homes = ctx.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }.toSet()
        return order(opened.ifEmpty { noted(ctx) }, shown - homes, ctx.packageName)
    }

    /** First appearance wins, so the list is newest first without repeats. */
    fun order(newestFirst: List<String>, shown: Set<String>, self: String, limit: Int = SHOWN): List<String> =
        newestFirst.asSequence().filter { it != self && it in shown }.distinct().take(limit).toList()

    /** Home opened [pkg]: the fallback when usage access isn't on. */
    fun note(ctx: Context, pkg: String) {
        val prefs = ctx.getSharedPreferences("recent", Context.MODE_PRIVATE)
        val next = (listOf(pkg) + noted(ctx)).distinct().take(KEPT)
        prefs.edit().putString("apps", JSONArray(next).toString()).apply()
    }

    private fun noted(ctx: Context): List<String> = runCatching {
        val a = JSONArray(ctx.getSharedPreferences("recent", Context.MODE_PRIVATE).getString("apps", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    /** Usage access, which the desktop app grants over ADB. Screen time needs it too. */
    fun allowed(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** When each of [pkgs] was last in front, from usage access; empty without it. */
    fun lastUsed(ctx: Context, pkgs: Collection<String>): Map<String, Long> = runCatching {
        if (!allowed(ctx)) return emptyMap()
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        usm.queryAndAggregateUsageStats(now - 60 * 24 * 3600 * 1000L, now)
            .filterKeys { it in pkgs }.mapValues { it.value.lastTimeUsed }.filterValues { it > 0 }
    }.getOrDefault(emptyMap())

    private fun fromUsage(ctx: Context): List<String> = runCatching {
        val usm = ctx.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val seen = ArrayList<String>()
        @Suppress("DEPRECATION")
        val opened = if (Build.VERSION.SDK_INT >= 29) UsageEvents.Event.ACTIVITY_RESUMED else UsageEvents.Event.MOVE_TO_FOREGROUND
        for (e in ScreenTime.usageEvents(usm, now - DAYS * 24 * 3600 * 1000, now + 1)) {
            if (e.eventType == opened) seen.add(e.packageName)
        }
        seen.asReversed()
    }.getOrDefault(emptyList())
}
