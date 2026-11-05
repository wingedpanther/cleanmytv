package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import java.io.File

/**
 * The slideshow's pictures, which CleanMyTV uploads in one go (#56). They go into slides.upload/ and replace
 * slides/ only when the last one has arrived, as the theme pack does.
 */
object Slides {
    const val MAX = 12

    private fun dir(ctx: Context) = File(ctx.filesDir, "slides")
    private fun upload(ctx: Context) = File(ctx.filesDir, "slides.upload")

    fun count(ctx: Context): Int = (0 until MAX).count { File(dir(ctx), "$it.jpg").exists() }

    fun begin(ctx: Context) { upload(ctx).deleteRecursively(); upload(ctx).mkdirs() }

    fun target(ctx: Context, n: Int): File {
        require(n in 0 until MAX) { "bad slide $n" }
        return File(upload(ctx), "$n.jpg")
    }

    fun end(ctx: Context) {
        val old = File(ctx.filesDir, "slides.old")
        old.deleteRecursively()
        dir(ctx).renameTo(old)
        upload(ctx).renameTo(dir(ctx))
        old.deleteRecursively()
    }

    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }

    /** The next picture becomes the wallpaper, with the Sunrise. Home remembers which one it showed last. */
    fun next(ctx: Context) {
        val n = count(ctx)
        if (n == 0) return
        val prefs = ctx.getSharedPreferences("slides", Context.MODE_PRIVATE)
        val i = (prefs.getInt("at", -1) + 1) % n
        val tmp = ConfigStore.wallpaperUpload(ctx)
        File(dir(ctx), "$i.jpg").copyTo(tmp, overwrite = true)
        tmp.renameTo(ConfigStore.wallpaper(ctx))
        prefs.edit().putInt("at", i).apply()
        ConfigStore.wallpaperChanged()
    }
}
