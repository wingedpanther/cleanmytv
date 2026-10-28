package io.github.wingedpanther.cleanmytv.home

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest

/**
 * The desktop app's way in, over ADB (`content call --uri content://io.github.wingedpanther.cleanmytv.home.config`):
 *   get                        the settings, plus whether a wallpaper is set
 *   set64 <base64 JSON>        merge settings (base64, so no text needs shell quoting)
 *   wallpaper_begin            start a new wallpaper upload
 *   wallpaper_chunk <base64>   append a piece of the JPEG
 *   wallpaper_end <md5>        check it and swap it in
 *   wallpaper_remove           back to the plain background
 * Only the shell user (ADB) and root may call it, so other apps on the TV can't restyle Home.
 */
class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val uid = Binder.getCallingUid()
        if (uid != SHELL_UID && uid != ROOT_UID && uid != Process.myUid()) {
            throw SecurityException("only ADB may change CleanMyTV Home")
        }
        val ctx = requireNotNull(context)
        val out = Bundle()
        when (method) {
            "get" -> ConfigStore.load(ctx)
            "set" -> ConfigStore.update(ctx, json(arg))
            "set64" -> ConfigStore.update(ctx, json(String(Base64.decode(arg ?: "", Base64.DEFAULT), Charsets.UTF_8)))
            "wallpaper_begin" -> ConfigStore.wallpaperUpload(ctx).writeBytes(ByteArray(0))
            "wallpaper_chunk" -> {
                val f = ConfigStore.wallpaperUpload(ctx)
                val bytes = Base64.decode(arg ?: "", Base64.DEFAULT)
                if (f.length() + bytes.size > MAX_WALLPAPER) throw IllegalArgumentException("wallpaper is over 4 MB")
                f.appendBytes(bytes)
            }
            "wallpaper_end" -> {
                val f = ConfigStore.wallpaperUpload(ctx)
                val got = MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
                if (got != arg?.lowercase()) throw IllegalArgumentException("wallpaper upload is damaged (md5 $got)")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, bounds)
                if (bounds.outWidth <= 0) throw IllegalArgumentException("that is not a picture")
                f.renameTo(ConfigStore.wallpaper(ctx))
                ConfigStore.wallpaperChanged()
            }
            "wallpaper_remove" -> {
                ConfigStore.wallpaper(ctx).delete()
                ConfigStore.wallpaperChanged()
            }
            else -> throw IllegalArgumentException("unknown method: $method")
        }
        val config = ConfigStore.config.value.toJson().put("has_wallpaper", ConfigStore.wallpaper(ctx).exists())
        out.putString("config", config.toString())
        return out
    }

    private fun json(s: String?): JSONObject =
        runCatching { JSONObject(s ?: "") }.getOrElse { throw IllegalArgumentException("arg must be a JSON object") }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0

    private companion object {
        const val SHELL_UID = 2000
        const val ROOT_UID = 0
        const val MAX_WALLPAPER = 4 * 1024 * 1024
    }
}
