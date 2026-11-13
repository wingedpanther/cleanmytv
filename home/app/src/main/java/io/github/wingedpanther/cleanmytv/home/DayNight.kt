package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Day and night looks: the owner sets Home up the way they like it in the day, saves that as "day", does
 * the same for "night", and Home switches between them at the two times. A look is its colours, icons and
 * glass, plus the wallpaper, so any theme works, a shared one too. Home only switches when the time
 * crosses into the other half, so a theme picked by hand stays until then.
 */
object DayNight {
    val SLOTS = setOf("day", "night")

    private fun dir(ctx: Context, slot: String) = File(File(ctx.filesDir, "looks"), slot)

    fun saved(ctx: Context): List<String> = SLOTS.filter { File(dir(ctx, it), "look.json").exists() }.sorted()

    /** Keeps Home's look and wallpaper as it is now, as [slot]. */
    fun save(ctx: Context, slot: String) {
        require(slot in SLOTS) { "bad look $slot" }
        val d = dir(ctx, slot).apply { mkdirs() }
        val now = ConfigStore.config.value.toJson()
        val look = JSONObject()
        for (k in ThemePack.SAVED_LOOK_KEYS) if (now.has(k)) look.put(k, now.get(k))
        val wall = ConfigStore.wallpaper(ctx)
        if (wall.exists()) wall.copyTo(File(d, "wallpaper.jpg"), overwrite = true) else File(d, "wallpaper.jpg").delete()
        File(d, "look.json").writeText(look.toString())
        Log.i(TAG, "saved the $slot look")
    }

    /** Which half of the day [minute] (since midnight) falls in. Night may run past midnight, or not. */
    fun slotAt(minute: Int, dayAt: Int, nightAt: Int): String = when {
        dayAt == nightAt -> "day"
        dayAt < nightAt -> if (minute in dayAt until nightAt) "day" else "night"
        else -> if (minute in nightAt until dayAt) "night" else "day"
    }

    /** Puts on the look the clock asks for, once per change of half. Cheap enough to call every minute. */
    fun check(ctx: Context, minute: Int) {
        val cfg = ConfigStore.config.value
        if (!cfg.dayNight) return
        val slot = slotAt(minute, cfg.dayAt, cfg.nightAt)
        val prefs = ctx.getSharedPreferences("day_night", Context.MODE_PRIVATE)
        if (prefs.getString("applied", null) == slot) return
        if (apply(ctx, slot)) prefs.edit().putString("applied", slot).apply()
    }

    /** The owner turned the schedule on or changed a time: the next check puts the right look on again. */
    fun reset(ctx: Context) = ctx.getSharedPreferences("day_night", Context.MODE_PRIVATE).edit().remove("applied").apply()

    private fun apply(ctx: Context, slot: String): Boolean {
        val d = dir(ctx, slot)
        val look = runCatching { JSONObject(File(d, "look.json").readText()) }.getOrNull() ?: return false
        val wall = File(d, "wallpaper.jpg")
        if (wall.exists()) {
            val tmp = ConfigStore.wallpaperUpload(ctx)
            wall.copyTo(tmp, overwrite = true)
            tmp.renameTo(ConfigStore.wallpaper(ctx))
        }
        ConfigStore.update(ctx, look)
        if (wall.exists()) ConfigStore.wallpaperChanged()
        Log.i(TAG, "$slot look on")
        return true
    }
}
