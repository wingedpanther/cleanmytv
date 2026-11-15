package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wallpapers the on-TV editor can set without a computer: Bing's picture of the day, a theme's picture, one of
 * the slideshow's photos, or none. Pictures from a USB drive or the computer still come from the desktop app,
 * which shrinks them for the TV; Home asks for no storage permission.
 */
object Wallpapers {
    private const val BING = "https://www.bing.com"
    private const val MAX_BYTES = 4 * 1024 * 1024

    /** [src] becomes the wallpaper, and the new one comes in with the Sunrise. */
    fun use(ctx: Context, src: File, from: String = "") {
        val tmp = ConfigStore.wallpaperUpload(ctx)
        src.copyTo(tmp, overwrite = true)
        tmp.renameTo(ConfigStore.wallpaper(ctx))
        ConfigStore.update(ctx, JSONObject().put("wallpaper_from", from))
        ConfigStore.wallpaperChanged()
    }

    fun remove(ctx: Context) {
        ConfigStore.wallpaper(ctx).delete()
        ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
        ConfigStore.wallpaperChanged()
    }

    /** Today's Bing picture at 1920x1080, as the desktop app's button gets it. Null when it worked, or why not. */
    suspend fun bing(ctx: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val list = JSONObject(String(get("$BING/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"), Charsets.UTF_8))
            val base = list.optJSONArray("images")?.optJSONObject(0)?.optString("urlbase").orEmpty()
            require(base.startsWith("/")) { "Bing has no picture today" }
            val bytes = get("$BING${base}_1920x1080.jpg")
            val f = File(ctx.cacheDir, "bing.jpg").apply { writeBytes(bytes) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            require(bounds.outWidth > 0) { "Bing sent something that isn't a picture" }
            use(ctx, f, from = "bing")
            f.delete()
            ctx.getSharedPreferences("bing", Context.MODE_PRIVATE).edit().putString("day", today()).apply()
            null
        }.getOrElse { it.message ?: "Bing didn't answer" }
    }

    private fun today() = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /** With "Bing every day" on: today's picture, once a day. Home calls it while it's on screen. */
    suspend fun daily(ctx: Context) {
        if (!ConfigStore.config.value.bingDaily) return
        if (ctx.getSharedPreferences("bing", Context.MODE_PRIVATE).getString("day", null) == today()) return
        bing(ctx)?.let { android.util.Log.i(TAG, "Bing every day: $it") }
    }

    private fun get(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "CleanMyTV-Home github.com/wingedpanther/cleanmytv")
            require(conn.responseCode == 200) { "Bing answered ${conn.responseCode}" }
            // Only Bing itself: a redirect to another host would put someone else's file on the TV.
            require(conn.url.host == URL(BING).host) { "Bing sent the picture somewhere else" }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    require(out.size() <= MAX_BYTES) { "Bing's picture is over 4 MB" }
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }
}
