package io.github.wingedpanther.cleanmytv.home

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android only lists other apps' media sessions to a notification listener. This one reads no
 * notifications; it exists so Home may ask. CleanMyTV switches it on over ADB with the Now playing section.
 */
class NowPlayingListener : NotificationListenerService()

data class Track(val title: String, val artist: String, val app: String, val art: ImageBitmap?, val playing: Boolean)

/** What any music or video app is playing, from its media session, while Home is on screen. */
object NowPlaying {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track
    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null

    private val sessions = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() = pick(manager?.let { active(it) }.orEmpty())
    }

    private fun component(ctx: Context) = ComponentName(ctx, NowPlayingListener::class.java)

    private fun active(m: MediaSessionManager): List<MediaController> =
        appContext?.let { runCatching { m.getActiveSessions(component(it)) }.getOrNull() }.orEmpty()

    fun start(ctx: Context) {
        appContext = ctx.applicationContext
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        try {
            m.addOnActiveSessionsChangedListener(sessions, component(ctx), main)
            manager = m
            pick(m.getActiveSessions(component(ctx)))
        } catch (e: SecurityException) {
            // The listener isn't switched on: the Now playing section stays empty.
            Log.i(TAG, "now playing: no access ($e)")
            _track.value = null
        }
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessions) }
        controller?.unregisterCallback(callback)
        controller = null
        manager = null
    }

    /** Follow the session that is playing, or the first one that has something loaded. */
    private fun pick(list: List<MediaController>) {
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null }
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || md == null || title.isNullOrBlank()) { _track.value = null; return }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val pm = appContext?.packageManager
        val app = runCatching { pm!!.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault(c.packageName)
        val bmp: Bitmap? = md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        // Cover art can arrive at full size; Home only needs a thumbnail.
        val art = bmp?.let { b -> runCatching { (if (b.width > 256) b.scale(256, 256 * b.height / b.width) else b).asImageBitmap() }.getOrNull() }
        _track.value = Track(title, artist, app, art, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    fun playPause() {
        val t = controller?.transportControls ?: return
        if (_track.value?.playing == true) t.pause() else t.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
}
