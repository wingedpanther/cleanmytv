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
 *   apps                       the apps Home can show, with the names the TV uses
 *   keys                       the last remote buttons the TV received (for the button finder)
 *   wallpaper_read <offset>    a piece of the wallpaper in use, so any computer can save the look with it
 *   lock_pin <base64 JSON>     set the app lock PIN ({"pin":"1234"}); it is stored hashed and never read back
 *   lock_clear                 forget the PIN and turn the lock off
 *   pack_begin, pack_meta <base64 JSON>, pack_file_begin <base64 "id:n">, pack_file_chunk <base64>,
 *   pack_file_end <md5>, pack_end <base64 JSON order>, pack_clear
 *                              the built-in themes and their pictures, for switching themes on the TV
 *   slides_begin, slide_file_begin <n>, slide_file_chunk <base64>, slide_file_end <md5>, slides_end, slides_clear
 *                              the slideshow's pictures (#56)
 *   fire_dir                   where CleanMyTV puts the fireplace files (Home's folder on shared storage)
 *   fire_ready, fire_clear     the files have arrived, or are gone; Home looks again
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
        if (method == "keys") {
            out.putString("keys", KeyLog.json().toString())
            return out
        }
        if (method == "fire_dir") {
            out.putString("dir", Fire.dir(ctx)?.path ?: "")
            return out
        }
        if (method == "apps") {
            out.putString("apps", listApps(ctx.packageManager, ctx.packageName).toString())
            return out
        }
        if (method == "wallpaper_read") {
            // Bundles cross a binder limit, so the picture comes back in pieces; the caller asks until done.
            val f = ConfigStore.wallpaper(ctx)
            if (!f.exists()) { out.putLong("size", 0); return out }
            val bytes = f.readBytes()
            val at = (arg?.toIntOrNull() ?: 0).coerceIn(0, bytes.size)
            out.putLong("size", bytes.size.toLong())
            out.putString("data", Base64.encodeToString(bytes, at, minOf(READ_PIECE, bytes.size - at), Base64.NO_WRAP))
            return out
        }
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
                ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
                ConfigStore.wallpaperChanged()
            }
            "lock_pin" -> {
                val pin = json(String(Base64.decode(arg ?: "", Base64.DEFAULT), Charsets.UTF_8)).optString("pin")
                Lock.setPin(ctx, pin)
            }
            "lock_clear" -> {
                Lock.clearPin(ctx)
                ConfigStore.update(ctx, JSONObject().put("lock_on", false))
            }
            "pack_begin" -> ThemePack.begin(ctx)
            "pack_meta" -> ThemePack.meta(ctx, json(String(Base64.decode(arg ?: "", Base64.DEFAULT), Charsets.UTF_8)))
            "pack_file_begin" -> {
                packFile = ThemePack.target(ctx, String(Base64.decode(arg ?: "", Base64.DEFAULT), Charsets.UTF_8))
                packFile!!.writeBytes(ByteArray(0))
            }
            "pack_file_chunk" -> {
                val f = packFile ?: throw IllegalStateException("pack_file_begin first")
                val bytes = Base64.decode(arg ?: "", Base64.DEFAULT)
                if (f.length() + bytes.size > MAX_WALLPAPER) throw IllegalArgumentException("picture is over 4 MB")
                f.appendBytes(bytes)
            }
            "pack_file_end" -> {
                val f = packFile ?: throw IllegalStateException("pack_file_begin first")
                val got = MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
                if (got != arg?.lowercase()) { f.delete(); throw IllegalArgumentException("picture upload is damaged (md5 $got)") }
                packFile = null
            }
            "pack_end" -> ThemePack.end(ctx, runCatching { org.json.JSONArray(String(Base64.decode(arg ?: "", Base64.DEFAULT), Charsets.UTF_8)) }
                .getOrElse { throw IllegalArgumentException("arg must be a JSON list of theme ids") })
            "pack_clear" -> ThemePack.clear(ctx)
            "slides_begin" -> Slides.begin(ctx)
            "slide_file_begin" -> {
                packFile = Slides.target(ctx, arg?.toIntOrNull() ?: -1)
                packFile!!.writeBytes(ByteArray(0))
            }
            "slide_file_chunk" -> {
                val f = packFile ?: throw IllegalStateException("slide_file_begin first")
                val bytes = Base64.decode(arg ?: "", Base64.DEFAULT)
                if (f.length() + bytes.size > MAX_WALLPAPER) throw IllegalArgumentException("picture is over 4 MB")
                f.appendBytes(bytes)
            }
            "slide_file_end" -> {
                val f = packFile ?: throw IllegalStateException("slide_file_begin first")
                val got = MessageDigest.getInstance("MD5").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
                if (got != arg?.lowercase()) { f.delete(); throw IllegalArgumentException("picture upload is damaged (md5 $got)") }
                packFile = null
            }
            "slides_end" -> Slides.end(ctx)
            "slides_clear" -> {
                Slides.clear(ctx)
                ConfigStore.update(ctx, JSONObject().put("slide_minutes", 0))
            }
            "fire_ready" -> Fire.changed()
            "fire_clear" -> {
                Fire.video(ctx)?.delete(); Fire.sound(ctx)?.delete()
                ConfigStore.update(ctx, JSONObject().put("fire_wall", false))
                Fire.changed()
            }
            else -> throw IllegalArgumentException("unknown method: $method")
        }
        val config = ConfigStore.config.value.toJson()
            .put("has_wallpaper", ConfigStore.wallpaper(ctx).exists())
            .put("configured", ConfigStore.configured(ctx))
            .put("quick_available", org.json.JSONArray(availableQuick(ctx)))
            .put("lock_pin_set", Lock.hasPin(ctx))
            .put("themes_on_tv", ThemePack.list(ctx).size)
            .put("fire_on_tv", Fire.ready(ctx))
            .put("slides_on_tv", Slides.count(ctx))
            .put("can_change_settings", TvSettings.canWriteSecure(ctx))
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
        const val READ_PIECE = 192 * 1024
        /** The picture being uploaded for the theme pack, between pack_file_begin and pack_file_end. */
        @Volatile var packFile: java.io.File? = null
    }
}
