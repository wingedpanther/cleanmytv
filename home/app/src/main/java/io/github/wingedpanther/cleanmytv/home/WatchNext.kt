package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * One film or episode from the TV's Watch Next list, the one Google TV's launcher shows as "Continue
 * watching". [key] tells two rows for the same thing apart from two different things.
 */
data class WatchItem(
    val id: Long,
    val pkg: String,
    val title: String,
    val season: String? = null,
    val episode: String? = null,
    val episodeTitle: String? = null,
    val image: String? = null,
    val intentUri: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
    val engaged: Long = 0,
    val key: String = "$pkg:$title",
) {
    /** How far through, 0 to 1, or null when the app didn't say. */
    val progress: Float? get() = if (position > 0 && duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
}

/**
 * The Continue watching row. Apps put what people are part way through into Android's TV provider; Google
 * TV's launcher reads it, and with that launcher switched off nothing did. Reading other apps' entries
 * takes READ_TV_LISTINGS, a runtime permission the desktop app grants over ADB, or the TV asks for from the
 * editor. Without it the provider returns only Home's own entries, which is none, and the row stays away.
 */
object WatchNext {
    const val SHOWN = 10
    const val STALE_DAYS = 30
    /** Watched this far, it's finished: the credits are often all that's left. */
    private const val DONE = 0.95f
    const val PERMISSION = "android.permission.READ_TV_LISTINGS"
    val URI: Uri by lazy { Uri.parse("content://android.media.tv/watch_next_program") }

    private val COLUMNS = arrayOf(
        "_id", "package_name", "title", "season_display_number", "episode_display_number", "episode_title",
        "poster_art_uri", "poster_art_aspect_ratio", "thumbnail_uri", "intent_uri", "last_playback_position_millis",
        "duration_millis", "last_engagement_time_utc_millis", "content_id", "browsable",
    )
    // TvContract's poster aspect ratio for 16:9; the tiles are that shape.
    private const val WIDE = 0

    /**
     * A provider row, by column name, to an item. Null for a row with no app or title, or one the owner took
     * off the list in Google TV ("browsable" 0).
     */
    fun fromRow(get: (String) -> Any?): WatchItem? {
        fun text(c: String) = (get(c) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        fun long(c: String) = when (val v = get(c)) { is Number -> v.toLong(); is String -> v.toLongOrNull(); else -> null }
        val pkg = text("package_name") ?: return null
        val title = text("title") ?: return null
        if (long("browsable") == 0L) return null
        // A poster that isn't 16:9 is cropped hard in a wide tile: the app's thumbnail, a frame from the
        // video, fits better when there is one.
        val poster = text("poster_art_uri")
        val thumb = text("thumbnail_uri")
        val ratio = long("poster_art_aspect_ratio")
        val image = if (poster != null && (ratio == null || ratio == WIDE.toLong() || thumb == null)) poster else thumb ?: poster
        val intentUri = text("intent_uri")
        return WatchItem(
            id = long("_id") ?: 0,
            pkg = pkg,
            title = title,
            season = text("season_display_number"),
            episode = text("episode_display_number"),
            episodeTitle = text("episode_title"),
            image = image,
            intentUri = intentUri,
            position = long("last_playback_position_millis")?.coerceAtLeast(0) ?: 0,
            duration = long("duration_millis")?.coerceAtLeast(0) ?: 0,
            engaged = long("last_engagement_time_utc_millis") ?: 0,
            key = "$pkg:" + (text("content_id") ?: intentUri ?: title),
        )
    }

    /**
     * What the row shows: items from apps on Home and not hidden, nor locked with "Hide locked apps", newest
     * first, one per film or episode, at most [SHOWN]. Some apps never take an entry off the list, so Home
     * leaves out what nobody has watched for [STALE_DAYS], what's all but finished, and what the owner
     * [removed] here, until it's watched again after that.
     */
    fun pick(items: List<WatchItem>, apps: Set<String>, cfg: HomeConfig, now: Long = System.currentTimeMillis(), removed: Map<String, Long> = emptyMap()): List<WatchItem> {
        val hideLocked = cfg.lockOn && cfg.lockShow == "hide"
        val since = now - STALE_DAYS * 24 * 3600 * 1000L
        return items.asSequence()
            .filter { it.pkg in apps && it.pkg !in cfg.hidden && !(hideLocked && it.pkg in cfg.lockApps) }
            .filter { (it.engaged == 0L || it.engaged >= since) && (it.progress ?: 0f) < DONE && (removed[it.key]?.let { at -> it.engaged > at } ?: true) }
            .sortedByDescending { it.engaged }
            .distinctBy { it.key }
            .take(SHOWN)
            .toList()
    }

    /** "S2 E5 · The Long Night", or whichever of those the app gave; null with none. */
    fun episodeLine(it: WatchItem): String? {
        val number = when {
            it.season != null && it.episode != null -> tr("S{season} E{episode}", "season" to it.season, "episode" to it.episode)
            it.episode != null -> tr("E{episode}", "episode" to it.episode)
            it.season != null -> tr("S{season}", "season" to it.season)
            else -> null
        }
        return listOfNotNull(number, it.episodeTitle).joinToString(" · ").ifEmpty { null }
    }

    private fun removedPrefs(ctx: Context) = ctx.getSharedPreferences("watch_removed", Context.MODE_PRIVATE)

    /** Items the owner took off the row, by key, with when; older than [STALE_DAYS] they'd be left out anyway. */
    fun removed(ctx: Context): Map<String, Long> {
        val p = removedPrefs(ctx)
        val since = System.currentTimeMillis() - STALE_DAYS * 24 * 3600 * 1000L
        val all = p.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()
        all.filterValues { it < since }.keys.takeIf { it.isNotEmpty() }?.let { old -> p.edit().apply { old.forEach { remove(it) } }.apply() }
        return all.filterValues { it >= since }
    }

    /** Hold OK, Remove: off the row until the app says it was watched again. */
    fun remove(ctx: Context, item: WatchItem) = removedPrefs(ctx).edit().putLong(item.key, System.currentTimeMillis()).apply()

    fun allowed(ctx: Context): Boolean = ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Every entry the provider lets Home see. The provider refuses a selection from Home, so it filters here. */
    fun read(ctx: Context): List<WatchItem> = runCatching {
        ctx.contentResolver.query(URI, COLUMNS, null, null, null)?.use { c ->
            val out = ArrayList<WatchItem>(c.count)
            while (c.moveToNext()) fromRow { name -> value(c, name) }?.let(out::add)
            out
        }.orEmpty()
    }.onFailure { Log.i(TAG, "watch next: $it") }.getOrDefault(emptyList())

    private fun value(c: Cursor, name: String): Any? {
        val i = c.getColumnIndex(name)
        if (i < 0) return null
        return when (c.getType(i)) {
            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
            Cursor.FIELD_TYPE_STRING -> c.getString(i)
            else -> null
        }
    }

    /** Calls [changed] on the main thread when an app changes the list; the returned function stops it. */
    fun watch(ctx: Context, changed: () -> Unit): () -> Unit {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = changed()
        }
        val ok = runCatching { ctx.contentResolver.registerContentObserver(URI, true, obs) }
            .onFailure { Log.i(TAG, "watch next observer: $it") }.isSuccess
        return { if (ok) runCatching { ctx.contentResolver.unregisterContentObserver(obs) } }
    }

    /**
     * The item's own link, kept to the app that wrote it: an entry may only open its own app, never another
     * app's screen or one of Home's, and gets no access to files. Null when it has no link that app can open.
     */
    fun intent(ctx: Context, item: WatchItem): Intent? {
        val uri = item.intentUri ?: return null
        val i = runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME or Intent.URI_ANDROID_APP_SCHEME) }.getOrNull() ?: return null
        i.selector = null
        i.clipData = null
        val target = i.component?.packageName ?: i.`package`
        if (target != null && target != item.pkg) return null
        if (i.component == null) i.setPackage(item.pkg)
        val grants = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        i.flags = (i.flags and grants.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        return i.takeIf { runCatching { ctx.packageManager.resolveActivity(it, 0) }.getOrNull() != null }
    }

    // ---- Pictures ---------------------------------------------------------------------------------

    // Decoded pictures stay for the next time Home comes back, up to about 6 MB; a 2 GB box has little to spare.
    private val pictures by lazy {
        object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
        }
    }
    // A picture that didn't load isn't tried again for a while: the TV may simply have been offline.
    private val failed = HashMap<String, Long>()
    private const val RETRY_MS = 10 * 60_000L
    // Two at a time, so ten posters don't all decode at once while the row scrolls.
    private val gate = Semaphore(2)
    private const val MAX_BYTES = 4 * 1024 * 1024

    fun cached(uri: String): ImageBitmap? = pictures.get(uri)

    /**
     * The picture at [uri], scaled down to about [width] pixels wide, off the main thread. Pictures come from
     * the app's own provider or its servers, as they do for Google TV's launcher.
     */
    suspend fun picture(ctx: Context, uri: String, width: Int): ImageBitmap? {
        pictures.get(uri)?.let { return it }
        val now = android.os.SystemClock.elapsedRealtime()
        if (synchronized(failed) { failed[uri]?.let { now - it < RETRY_MS } == true }) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                pictures.get(uri) ?: runCatching { load(ctx, uri, width) }
                    .onFailure { Log.i(TAG, "watch next picture: $it") }.getOrNull()
                    .also { b -> if (b != null) pictures.put(uri, b) else synchronized(failed) { failed[uri] = now } }
            }
        }
    }

    private fun load(ctx: Context, uri: String, width: Int): ImageBitmap? {
        val bytes = when (Uri.parse(uri).scheme?.lowercase()) {
            "http", "https" -> {
                val conn = URL(uri).openConnection() as HttpURLConnection
                conn.connectTimeout = 5_000
                conn.readTimeout = 8_000
                conn.instanceFollowRedirects = true
                try { if (conn.responseCode == 200) conn.inputStream.use { capped(it) } else null } finally { conn.disconnect() }
            }
            "content", "android.resource" -> ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { capped(it) }
            else -> null
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, width) }
        val big = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val out = if (big.width > width) Bitmap.createScaledBitmap(big, width, (big.height.toLong() * width / big.width).toInt().coerceAtLeast(1), true).also { if (it !== big) big.recycle() } else big
        return out.asImageBitmap()
    }

    /** The largest power of two that keeps the picture at least [target] wide. */
    fun sampleSize(width: Int, target: Int): Int {
        var n = 1
        while (width / (n * 2) >= target) n *= 2
        return n
    }

    private fun capped(input: InputStream): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > MAX_BYTES) return null
        }
        return buf.toByteArray()
    }
}

/** Hold OK on a Continue watching tile: take it off the row, for apps that never do. */
@Composable
fun WatchMenu(item: WatchItem, accent: Color, onRemove: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(560.dp).background(HomeColours.panel.copy(alpha = 0.95f), RoundedCornerShape(20.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.title, color = Color(0xFFF0EBE5), fontSize = 22.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(tr("It comes back if you watch it again."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UpdateButton(tr("Remove from Continue watching"), accent, Modifier.focusRequester(first)) { onRemove() }
                    UpdateButton(tr("Cancel"), accent) { onClose() }
                }
            }
        }
    }
}
