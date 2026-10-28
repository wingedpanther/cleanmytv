package io.github.wingedpanther.cleanmytv.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Reads the TV's mixed output (audio session 0) as FFT data and turns it into bar heights from 0 to 1.
 *
 * Music puts most of its energy in the bass, so raw levels max out the left bars and leave the right
 * ones flat. Three things even it out: bands spaced on a log scale, a tilt that lifts the treble, and
 * an automatic gain that follows how loud the song is, so quiet and loud tracks both fill the range.
 */
class Visualiser {
    enum class State { NO_PERMISSION, WAITING, LIVE, SILENT, FAILED }

    private val _levels = MutableStateFlow(FloatArray(0))
    /** Target heights, 0..1, updated at the capture rate. The UI eases toward them every frame. */
    val levels: StateFlow<FloatArray> = _levels
    private val _state = MutableStateFlow(State.WAITING)
    val state: StateFlow<State> = _state
    var error: String? = null
        private set

    @Volatile var bars = 32
    @Volatile var sensitivity = 1f

    private var viz: Visualizer? = null
    private var audio: AudioManager? = null
    private var musicCheckedAt = 0
    private var musicActive = false
    private var gain = 0.0 // running reference level for the automatic gain
    private var frames = 0
    private var soundFrames = 0

    fun start(ctx: Context) {
        if (viz != null) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.NO_PERMISSION
            return
        }
        audio = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val rate = min(Visualizer.getMaxCaptureRate(), 30_000) // millihertz
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer, w: ByteArray, r: Int) {}
                override fun onFftDataCapture(v: Visualizer, fft: ByteArray, r: Int) = onFft(fft)
            }, rate, false, true)
            v.enabled = true
            viz = v
            _state.value = State.WAITING
            Log.i(TAG, "visualiser on: capture ${v.captureSize}, rate ${rate / 1000}/s")
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
            _state.value = State.FAILED
            Log.w(TAG, "visualiser failed: $error")
        }
    }

    fun stop() {
        viz?.runCatching { enabled = false; release() }
        viz = null
        _levels.value = FloatArray(0)
    }

    private fun onFft(fft: ByteArray) {
        // The remote's click sounds reach the output mix too. Android tells music and video apart from
        // those system sounds, so the bars only move while something is actually playing.
        if (frames - musicCheckedAt >= 10 || frames == 0) {
            musicCheckedAt = frames
            musicActive = audio?.isMusicActive ?: true
        }
        if (!musicActive) {
            frames++
            if (_levels.value.any { it > 0f }) _levels.value = FloatArray(bars)
            return
        }
        // fft = [re0, reN/2, re1, im1, re2, im2, ...]. Skip bin 0 (DC) and the top fifth (little music there).
        val bins = fft.size / 2
        val top = (bins * 0.8).toInt()
        val n = bars
        val raw = DoubleArray(n)
        var loudest = 0.0
        for (b in 0 until n) {
            val lo = max(1, top.toDouble().pow(b.toDouble() / n).toInt())
            val hi = max(lo + 1, top.toDouble().pow((b + 1).toDouble() / n).toInt()).coerceAtMost(top)
            var sum = 0.0
            for (k in lo until hi) sum += hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble())
            val mean = sum / (hi - lo)
            // Treble tilt: up to 3x at the right edge.
            raw[b] = mean * (1.0 + 2.0 * b / (n - 1).coerceAtLeast(1))
            loudest = max(loudest, raw[b])
        }
        // Automatic gain: jump up to a louder song at once, ease down over a few seconds.
        gain = if (loudest > gain) loudest else gain * 0.985 + loudest * 0.015
        val ref = max(gain, 6.0) // below this it is noise, not music
        val out = FloatArray(n)
        var any = false
        for (b in 0 until n) {
            // sqrt for loudness as heard; 0.85 leaves headroom so peaks stand out.
            val v = (sqrt(raw[b] / ref) * 0.85 * sensitivity).toFloat().coerceIn(0f, 1f)
            out[b] = v
            if (v > 0.15f) any = true
        }
        frames++
        if (any) soundFrames++
        _levels.value = out
        if (frames % 30 == 0) {
            _state.value = if (soundFrames > 0) State.LIVE else State.SILENT
            soundFrames = 0
        }
    }

    private companion object { const val TAG = "CMTVHome" }
}
