package io.github.wingedpanther.cleanmytv.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Reads the TV's mixed output (audio session 0) as FFT data and turns it into bar heights.
 * The spike's question: does a given TV give real data here, or only zeros?
 */
class Visualiser(private val bars: Int = 32) {
    enum class State { NO_PERMISSION, WAITING, LIVE, SILENT, FAILED }

    private val _levels = MutableStateFlow(FloatArray(bars))
    val levels: StateFlow<FloatArray> = _levels
    private val _state = MutableStateFlow(State.WAITING)
    val state: StateFlow<State> = _state
    var error: String? = null
        private set

    private var viz: Visualizer? = null
    private var frames = 0
    private var nonZeroFrames = 0

    fun start(ctx: Context) {
        if (viz != null) return
        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.value = State.NO_PERMISSION
            return
        }
        try {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            val rate = min(Visualizer.getMaxCaptureRate(), 30_000) // millihertz: up to 30 updates a second
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
    }

    private fun onFft(fft: ByteArray) {
        // fft = [re0, reN/2, re1, im1, re2, im2, ...]; bands spaced on a log scale so bass doesn't
        // take one bar and treble twenty.
        val n = fft.size / 2
        val out = FloatArray(bars)
        var any = false
        for (b in 0 until bars) {
            val lo = max(1, (n * Math.pow(n.toDouble(), b.toDouble() / bars - 1)).toInt())
            val hi = max(lo + 1, (n * Math.pow(n.toDouble(), (b + 1).toDouble() / bars - 1)).toInt()).coerceAtMost(n)
            var peak = 0.0
            for (k in lo until hi) peak = max(peak, hypot(fft[2 * k].toDouble(), fft[2 * k + 1].toDouble()))
            out[b] = (ln(1 + peak) / ln(1 + 128.0)).toFloat().coerceIn(0f, 1f)
            // A floor, so the near-silent noise some TVs report doesn't count as music.
            if (out[b] > 0.15f) any = true
        }
        frames++
        if (any) nonZeroFrames++
        // Ease toward the new heights so the bars don't flicker.
        val prev = _levels.value
        _levels.value = FloatArray(bars) { i -> if (out[i] > prev[i]) out[i] else prev[i] * 0.85f + out[i] * 0.15f }
        if (frames % 30 == 0) {
            _state.value = if (nonZeroFrames > 0) State.LIVE else State.SILENT
            Log.i(TAG, "visualiser: $nonZeroFrames of $frames frames had sound")
        }
    }

    private companion object { const val TAG = "CMTVHome" }
}
