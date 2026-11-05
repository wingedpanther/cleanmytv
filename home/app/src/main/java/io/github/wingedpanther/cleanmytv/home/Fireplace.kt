package io.github.wingedpanther.cleanmytv.home

import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.service.dreams.DreamService
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The fireplace: a looping video and a separate, longer crackle, which CleanMyTV puts in Home's own folder
 * on the TV's shared storage (Android/data/<package>/files/fireplace). ADB can write there and Home can
 * read it without any storage permission. Neither file ships inside the app.
 */
object Fire {
    private val _version = MutableStateFlow(0)
    /** Bumped when CleanMyTV has put new files on the TV, so Home looks again. */
    val version: StateFlow<Int> = _version

    fun dir(ctx: Context): File? = ctx.getExternalFilesDir("fireplace")
    fun video(ctx: Context): File? = dir(ctx)?.let { File(it, "fireplace.mp4") }
    fun sound(ctx: Context): File? = dir(ctx)?.let { File(it, "crackle.m4a") }
    fun ready(ctx: Context): Boolean = video(ctx)?.let { it.length() > 0 } == true
    fun changed() { _version.value++ }
}

/**
 * Plays the fireplace on a TextureView, so Compose can draw the dim and the rows over it. The picture fills
 * the screen and is cropped, never stretched, if the screen isn't 16:9. [volume] is 0 for no sound.
 */
class FireView(ctx: Context, private val volume: () -> Float) : TextureView(ctx), TextureView.SurfaceTextureListener {
    private var video: MediaPlayer? = null
    private var audio: MediaPlayer? = null
    private var surface: Surface? = null
    private var want = false

    init { surfaceTextureListener = this }

    /** Play or hold still: Home holds it while an app is open or the TV's animations are off. */
    fun play(on: Boolean) {
        want = on
        runCatching { video?.let { if (on && !it.isPlaying) it.start() else if (!on && it.isPlaying) it.pause() } }
        runCatching { audio?.let { if (on && volume() > 0f && !it.isPlaying) it.start() else if ((!on || volume() <= 0f) && it.isPlaying) it.pause() } }
    }

    fun setVolume(v: Float) {
        runCatching { audio?.setVolume(v, v) }
        play(want)
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val file = Fire.video(context)?.takeIf { it.length() > 0 } ?: return
        surface = Surface(st)
        video = runCatching {
            MediaPlayer().apply {
                setDataSource(file.path)
                setSurface(surface)
                isLooping = true
                setVolume(0f, 0f)
                setOnVideoSizeChangedListener { _, vw, vh -> fit(vw, vh) }
                setOnPreparedListener { if (want) it.start() }
                setOnErrorListener { _, what, extra -> Log.w(TAG, "fireplace video: $what/$extra"); true }
                prepareAsync()
            }
        }.onFailure { Log.w(TAG, "fireplace video: $it") }.getOrNull()
        val crackle = Fire.sound(context)?.takeIf { it.length() > 0 } ?: return
        audio = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                setDataSource(crackle.path)
                isLooping = true
                volume().let { setVolume(it, it) }
                setOnPreparedListener { if (want && volume() > 0f) it.start() }
                setOnErrorListener { _, what, extra -> Log.w(TAG, "fireplace sound: $what/$extra"); true }
                prepareAsync()
            }
        }.onFailure { Log.w(TAG, "fireplace sound: $it") }.getOrNull()
    }

    /** Fill the view and crop the overflow, centred. */
    private fun fit(vw: Int, vh: Int) {
        if (vw <= 0 || vh <= 0 || width <= 0 || height <= 0) return
        val scale = maxOf(width / vw.toFloat(), height / vh.toFloat())
        val sx = vw * scale / width
        val sy = vh * scale / height
        setTransform(Matrix().apply { setScale(sx, sy, width / 2f, height / 2f) })
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        video?.let { runCatching { fit(it.videoWidth, it.videoHeight) } }
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        release()
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    fun release() {
        runCatching { video?.release() }; video = null
        runCatching { audio?.release() }; audio = null
        surface?.release(); surface = null
    }
}

/** What shows when the files aren't on the TV yet. */
private fun notYet(ctx: Context) = TextView(ctx).apply {
    text = "Put the fireplace on the TV first: CleanMyTV, Home screen step, Look & wallpaper tab."
    setTextColor(0xFFBBBBBB.toInt())
    textSize = 20f
    gravity = Gravity.CENTER
    setBackgroundColor(0xFF000000.toInt())
}

private fun fireVolume(sound: Boolean) = if (sound) ConfigStore.config.value.fireVolume / 100f else 0f

/** The fireplace full screen, from the Fireplace quick-settings icon or a remote button. Back closes it. */
class FireplaceActivity : Activity() {
    private var view: FireView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Fire.ready(this)) view = FireView(this) { fireVolume(ConfigStore.config.value.fireSound) }.also { setContentView(it) }
        else setContentView(notYet(this))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onResume() { super.onResume(); view?.play(true) }
    override fun onPause() { view?.play(false); super.onPause() }
}

/** The fireplace as the TV's screensaver, listed as "CleanMyTV Fireplace". */
class FireplaceDream : DreamService() {
    private var view: FireView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ConfigStore.load(this)
        isInteractive = false
        isFullscreen = true
        if (Fire.ready(this)) view = FireView(this) { fireVolume(ConfigStore.config.value.fireSound) }.also { setContentView(it) }
        else setContentView(notYet(this))
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); view?.play(true) }

    override fun onDreamingStopped() {
        view?.play(false)
        view?.release()
        super.onDreamingStopped()
    }
}
