package io.github.wingedpanther.cleanmytv.home

import android.app.Activity
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The fireplace: a looping video and a separate, longer crackle. CleanMyTV sends them through
 * ConfigProvider into Home's private storage, which works on every TV; Fire OS, for one, won't let ADB
 * write to Android/data (#70). Home 1.6 to 1.7.2 took them in Android/data/<package>/files/fireplace, so
 * files already there still count. Neither file ships inside the app.
 */
object Fire {
    private val _version = MutableStateFlow(0)
    /** Bumped when CleanMyTV has put new files on the TV, so Home looks again. */
    val version: StateFlow<Int> = _version
    val NAMES = setOf("fireplace.mp4", "crackle.m4a")

    /** Where uploads go now. */
    fun dir(ctx: Context): File = File(ctx.filesDir, "fireplace").apply { mkdirs() }
    private fun file(ctx: Context, name: String): File? =
        File(dir(ctx), name).takeIf { it.length() > 0 }
            ?: ctx.getExternalFilesDir("fireplace")?.let { File(it, name) }?.takeIf { it.length() > 0 }
    fun video(ctx: Context): File? = file(ctx, "fireplace.mp4")
    fun sound(ctx: Context): File? = file(ctx, "crackle.m4a")
    /** Deletes both copies, new place and old. */
    fun clear(ctx: Context) {
        for (n in NAMES) { File(dir(ctx), n).delete(); ctx.getExternalFilesDir("fireplace")?.let { File(it, n).delete() } }
    }
    fun ready(ctx: Context): Boolean = video(ctx)?.let { it.length() > 0 } == true
    fun changed(ctx: Context) { syncDream(ctx); _version.value++ }

    /**
     * The fireplace screensaver is listed only while the fireplace is on the TV (#116). Taking it off also
     * moves the TV's screensaver back to its own, if the fireplace was it.
     */
    fun syncDream(ctx: Context) = runCatching {
        val dream = android.content.ComponentName(ctx, FireplaceDream::class.java)
        val on = ready(ctx)
        val want = if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val pm = ctx.packageManager
        if (pm.getComponentEnabledSetting(dream) != want) pm.setComponentEnabledSetting(dream, want, android.content.pm.PackageManager.DONT_KILL_APP)
        if (on) return@runCatching
        val r = ctx.contentResolver
        val saver = android.provider.Settings.Secure.getString(r, "screensaver_components").orEmpty()
        if (saver.split(",").none { it == dream.flattenToString() || it == dream.flattenToShortString() }) return@runCatching
        val back = android.provider.Settings.Secure.getString(r, "screensaver_default_component")?.takeIf { it.isNotBlank() }
        if (back != null) android.provider.Settings.Secure.putString(r, "screensaver_components", back)
        else android.provider.Settings.Secure.putInt(r, "screensaver_enabled", 0)
    }.onFailure { Log.w(TAG, "fireplace screensaver: $it") }
}

/**
 * Plays the fireplace on a TextureView, so Compose can draw the dim and the rows over it. The picture fills
 * the screen and is cropped, never stretched, if the screen isn't 16:9. [volume] is 0 for no sound.
 */
class FireView(ctx: Context, private val volume: () -> Float) : TextureView(ctx), TextureView.SurfaceTextureListener {
    private var video: MediaPlayer? = null
    private var audio: Crackle? = null
    private var surface: Surface? = null
    private var want = false

    init { surfaceTextureListener = this }

    /** Play or hold still: Home holds it while an app is open or the TV's animations are off. */
    fun play(on: Boolean) {
        want = on
        runCatching { video?.let { if (on && !it.isPlaying) it.start() else if (!on && it.isPlaying) it.pause() } }
        audio?.play(on && volume() > 0f)
    }

    fun setVolume(v: Float) {
        audio?.setVolume(v)
        play(want)
    }

    /** The fire dies down: picture and crackle fade together over [ms], then [done] runs. */
    fun fadeOut(ms: Long, done: () -> Unit) {
        val start = volume()
        animate().alpha(0f).setDuration(ms).setUpdateListener { a ->
            audio?.setVolume(start * (1f - a.animatedFraction))
        }.withEndAction { play(false); done() }.start()
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
        audio = Crackle(crackle, volume()).also { it.play(want && volume() > 0f) }
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
        audio?.release(); audio = null
        surface?.release(); surface = null
    }
}

/**
 * The crackle, decoded here and played as PCM through an AudioTrack, half a second at a time.
 *
 * MediaPlayer sends a sound with no picture, 60 s or longer, in a format the TV's audio chip takes, to the
 * chip's offload output as it is, undecoded. On some TVs that output is silent: the 66.8 s AAC crackle never
 * played on a TCL 65QM63K (#118). AOSP's NuPlayer skips offload when the speed isn't exactly 1.0, so a
 * MediaPlayer at 1.001 would also do, but that rule lives in the player, and TV chip makers ship their own
 * players. A PCM AudioTrack can't go to offload at all: offload has to be asked for when the track is built,
 * so it goes through the TV's mixer like every other app's sound, whatever player the TV has. Decoding it
 * here also lets the loop run with no gap: the encoder's lead-in and tail are cut at every seam ([Seam]),
 * where MediaPlayer, looping on its own, leaves a short silence.
 */
private class Crackle(private val file: File, volume: Float) {
    private val lock = Object()
    @Volatile private var alive = true
    private var want = false
    private var volume = volume
    private var track: AudioTrack? = null

    init { Thread(::run, "crackle").apply { isDaemon = true }.start() }

    fun play(on: Boolean) = synchronized(lock) {
        if (want == on) return@synchronized
        want = on
        runCatching { track?.let { if (on) it.play() else it.pause() } }
        lock.notifyAll()
    }

    fun setVolume(v: Float) = synchronized(lock) {
        volume = v
        runCatching { track?.setVolume(v) }
    }

    /** Stopping the track also wakes the decoder if it's waiting on a full buffer; it then cleans up. */
    fun release() = synchronized(lock) {
        alive = false
        runCatching { track?.stop() }
        lock.notifyAll()
    }

    private fun run() {
        // Audio priority, as Android's own audio threads have, so a busy TV doesn't leave the track empty.
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
        var ex: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            ex = MediaExtractor().apply { setDataSource(file.path) }
            val index = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: run { Log.w(TAG, "fireplace sound: no audio in ${file.name}"); return }
            ex.selectTrack(index)
            val format = ex.getTrackFormat(index)
            val delay = format.intOr("encoder-delay")
            val padding = format.intOr("encoder-padding")
            // The decoder would cut these only once, at the very start; the loop cuts them at every seam instead.
            format.setInteger("encoder-delay", 0)
            format.setInteger("encoder-padding", 0)
            codec = decoder(format.getString(MediaFormat.KEY_MIME)!!).apply { configure(format, null, null, 0); start() }
            Log.i(TAG, "fireplace sound: ${codec.name}, encoder delay $delay, padding $padding")
            val info = MediaCodec.BufferInfo()
            var pass = 0L
            var seam: Seam? = null
            var chunk = ByteArray(0)
            while (alive) {
                synchronized(lock) { while (alive && !want) lock.wait() }
                if (!alive) break
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val into = codec.getInputBuffer(i)!!
                    var n = ex.readSampleData(into, 0)
                    if (n < 0) {
                        ex.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        pass++
                        n = ex.readSampleData(into, 0)
                        if (n < 0) { Log.w(TAG, "fireplace sound: ${file.name} is empty"); return }
                    }
                    // Each pass gets its own stretch of time stamps, so the output says which pass it belongs to.
                    codec.queueInputBuffer(i, 0, n, ex.sampleTime + pass * PASS_US, 0)
                    ex.advance()
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED || (o >= 0 && seam == null)) seam = open(codec.outputFormat, delay, padding)
                if (o < 0) continue
                val out = codec.getOutputBuffer(o)
                val s = seam
                var n = 0
                if (out != null && s != null && info.size > 0) {
                    if (chunk.size < info.size) chunk = ByteArray(info.size)
                    out.position(info.offset)
                    out.get(chunk, 0, info.size)
                    n = s.take(Math.floorDiv(info.presentationTimeUs + PASS_US / 2, PASS_US), chunk, info.size)
                }
                codec.releaseOutputBuffer(o, false)
                val t = synchronized(lock) { track }
                if (n > 0 && t != null && t.write(s!!.out, 0, n) < 0 && alive) {
                    // The audio server lost the track, after an output change say: build a new one.
                    Log.w(TAG, "fireplace sound: track lost, opening a new one")
                    seam = open(codec.outputFormat, delay, padding)
                }
            }
        } catch (e: Exception) {
            if (alive) Log.w(TAG, "fireplace sound: $e")
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { ex?.release() }
            synchronized(lock) { runCatching { track?.release() }; track = null }
        }
    }

    /** A track for the decoder's output, with half a second of room: 96 KB for 48 kHz stereo. */
    private fun open(f: MediaFormat, delay: Int, padding: Int): Seam {
        val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val encoding = f.intOr(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
        val mask = when (channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            4 -> AudioFormat.CHANNEL_OUT_QUAD
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
            else -> error("$channels channels")
        }
        val frame = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask).setEncoding(encoding).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(rate, mask, encoding) * 2, rate / 2 * frame))
            .build()
        synchronized(lock) {
            runCatching { track?.release() }
            t.setVolume(volume)
            if (want) t.play()
            track = t
        }
        return Seam(delay, padding, frame)
    }

    /** Android's own software decoder where there is one: it acts the same on every TV, and AAC costs next to nothing. */
    private fun decoder(mime: String): MediaCodec {
        val own = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.firstOrNull { c ->
            !c.isEncoder && (c.name.startsWith("c2.android.") || c.name.startsWith("OMX.google.")) && c.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
        return if (own != null) MediaCodec.createByCodecName(own.name) else MediaCodec.createDecoderByType(mime)
    }

    private fun MediaFormat.intOr(key: String, or: Int = 0) = if (containsKey(key)) getInteger(key) else or

    private companion object {
        /** Four hours of time stamps for each pass: far more than any crackle is long. */
        const val PASS_US = 4L * 3600 * 1_000_000
    }
}

/**
 * Cuts the encoder's lead-in ([delay] frames) and tail ([padding] frames) off every pass of a loop, so the
 * end of the sound runs straight into its start. The last [padding] frames are held back until the next
 * pass begins and shows they were the tail. [take] puts what's ready to play at the start of [out].
 */
internal class Seam(private val delay: Int, private val padding: Int, private val frameBytes: Int) {
    var out = ByteArray(0)
        private set
    private val held = ByteArray(padding * frameBytes)
    private var heldLen = 0
    private var pass = Long.MIN_VALUE
    private var seen = 0L

    /** Takes [len] bytes of [pass] and returns how many bytes of [out] to play. */
    fun take(pass: Long, pcm: ByteArray, len: Int): Int {
        if (pass != this.pass) { this.pass = pass; seen = 0; heldLen = 0 }
        val frames = len / frameBytes
        val skip = (delay - seen).coerceIn(0, frames.toLong()).toInt() * frameBytes
        seen += frames
        val total = heldLen + len - skip
        if (out.size < total) out = ByteArray(total)
        System.arraycopy(held, 0, out, 0, heldLen)
        System.arraycopy(pcm, skip, out, heldLen, len - skip)
        val ready = maxOf(0, total - held.size)
        heldLen = total - ready
        System.arraycopy(out, ready, held, 0, heldLen)
        return ready
    }
}

/** What shows when the files aren't on the TV yet. */
private fun notYet(ctx: Context) = TextView(ctx).apply {
    text = tr("Put the fireplace on the TV first: CleanMyTV, Home screen step, Look & wallpaper tab.")
    setTextColor(0xFFBBBBBB.toInt())
    textSize = 20f
    gravity = Gravity.CENTER
    setBackgroundColor(0xFF000000.toInt())
}

private fun fireVolume(sound: Boolean) = if (sound) ConfigStore.config.value.fireVolume / 100f else 0f

/**
 * The fireplace full screen, from the Fireplace quick-settings icon or a remote button. Back closes it.
 * OK steps the timer through off, 30 min, 1 h and 2 h. When it runs out the fire fades over 30 seconds and
 * the TV goes to sleep, or, with the remote-button service off, Home comes back and the TV's own sleep takes over.
 */
class FireplaceActivity : Activity() {
    private var view: FireView? = null
    private var note: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val out = Runnable { burnOut() }
    private val hideNote = Runnable { note?.animate()?.alpha(0f)?.setDuration(400)?.start() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Fire.ready(this)) {
            val fire = FireView(this) { fireVolume(ConfigStore.config.value.fireSound) }
            view = fire
            note = TextView(this).apply {
                setTextColor(0xFFF0EBE5.toInt())
                textSize = 18f
                setBackgroundColor(0x99000000.toInt())
                setPadding(36, 18, 36, 18)
                alpha = 0f
            }
            setContentView(FrameLayout(this).apply {
                addView(fire, FrameLayout.LayoutParams(-1, -1))
                addView(note, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = 60 })
            })
        } else setContentView(notYet(this))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onResume() {
        super.onResume()
        view?.play(true)
        arm(ConfigStore.config.value.fireTimer, quiet = ConfigStore.config.value.fireTimer == 0)
    }

    override fun onPause() { handler.removeCallbacks(out); view?.play(false); super.onPause() }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (view != null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) && event.repeatCount == 0) {
            val now = ConfigStore.config.value.fireTimer
            val next = TIMES.firstOrNull { it > now } ?: 0
            ConfigStore.update(this, org.json.JSONObject().put("fire_timer", next))
            arm(next, quiet = false)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun arm(minutes: Int, quiet: Boolean) {
        handler.removeCallbacks(out)
        if (minutes > 0) handler.postDelayed(out, minutes * 60_000L)
        if (quiet) return
        note?.text = when {
            minutes == 0 -> tr("The fire burns until you close it. OK sets a timer.")
            minutes < 60 -> tr("The fire goes out in {n} min, then the TV sleeps.", "n" to minutes)
            else -> tr("The fire goes out in {n} h, then the TV sleeps.", "n" to minutes / 60)
        }
        note?.animate()?.alpha(1f)?.setDuration(200)?.start()
        handler.removeCallbacks(hideNote)
        handler.postDelayed(hideNote, 3500)
    }

    private fun burnOut() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view?.fadeOut(30_000) {
            if (!KeyService.sleep(this)) Log.i(TAG, "fireplace timer: remote-button service is off, leaving sleep to the TV")
            finish()
        }
    }

    private companion object { val TIMES = listOf(30, 60, 120) }
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
