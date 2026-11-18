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
        captionFile(ctx).delete()
        ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
        ConfigStore.wallpaperChanged()
    }

    /** Today's Bing picture at 1920x1080, as the desktop app's button gets it. Null when it worked, or why not. */
    suspend fun bing(ctx: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            val list = JSONObject(String(get("$BING/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US"), Charsets.UTF_8))
            val image = list.optJSONArray("images")?.optJSONObject(0)
            val base = image?.optString("urlbase").orEmpty()
            require(base.startsWith("/")) { tr("Bing has no picture today") }
            val bytes = get("$BING${base}_1920x1080.jpg")
            val f = File(ctx.cacheDir, "bing.jpg").apply { writeBytes(bytes) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            require(bounds.outWidth > 0) { tr("Bing sent something that isn't a picture") }
            // Before the picture goes in, so Home finds the line when it loads the new wallpaper.
            saveCaption(ctx, captionFrom(image?.optString("copyright").orEmpty()), bytes)
            use(ctx, f, from = "bing")
            f.delete()
            ctx.getSharedPreferences("bing", Context.MODE_PRIVATE).edit().putString("day", today()).apply()
            null
        }.getOrElse { it.message ?: tr("Bing didn't answer") }
    }

    /** Where Bing's picture was taken, as Bing words it. */
    data class Caption(val place: String)

    /**
     * Bing's "copyright" line, like "Boardwalk through the forest, Mount Revelstoke National Park, British
     * Columbia, Canada (© Photographer/Agency)", without the credit. Null when it's empty.
     */
    fun captionFrom(copyright: String): Caption? {
        val line = copyright.replace(Regex("\\p{Cntrl}"), " ").trim()
        val at = line.lastIndexOf("(©")
        val place = (if (at < 0) line else line.substring(0, at)).trim().trimEnd(',').trim().take(160)
        return if (place.isEmpty()) null else Caption(place)
    }

    /**
     * The caption kept with the picture it belongs to, by a checksum of the picture: any other wallpaper, from
     * the desktop app, a theme, the slideshow or a phone, doesn't match it and gets no caption. A picture from
     * before Home kept captions has none.
     */
    private fun captionFile(ctx: Context) = File(ctx.filesDir, "wallpaper.caption.json")

    private fun saveCaption(ctx: Context, c: Caption?, picture: ByteArray) {
        if (c == null) { captionFile(ctx).delete(); return }
        runCatching {
            captionFile(ctx).writeText(JSONObject().put("place", c.place).put("crc", crc(picture)).toString())
        }
    }

    /** The caption for the wallpaper on screen, or null when it has none. Reads the picture: call it off the main thread. */
    fun caption(ctx: Context): Caption? {
        val f = captionFile(ctx)
        val pic = ConfigStore.wallpaper(ctx)
        if (!f.exists() || !pic.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val place = o.optString("place").trim()
            if (place.isEmpty() || o.optLong("crc", -1) != crc(pic.readBytes())) null else Caption(place)
        }.getOrNull()
    }

    private fun crc(b: ByteArray) = java.util.zip.CRC32().apply { update(b) }.value

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
            require(conn.responseCode == 200) { tr("Bing answered {code}", "code" to conn.responseCode) }
            // Only Bing itself: a redirect to another host would put someone else's file on the TV.
            require(conn.url.host == URL(BING).host) { tr("Bing sent the picture somewhere else") }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    require(out.size() <= MAX_BYTES) { tr("Bing's picture is over 4 MB") }
                }
            }
            return out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }
}
