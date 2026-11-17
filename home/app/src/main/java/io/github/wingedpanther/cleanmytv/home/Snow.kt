package io.github.wingedpanther.cleanmytv.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

/**
 * Snow over the wallpaper, behind the apps. A few dozen flakes, moved about 24 times a second rather than
 * every frame, on their own layer so the rest of Home isn't redrawn with them. While [running] is false they
 * hold still: an app or the editor is over Home, a menu is open, or the screen is off.
 */
@Composable
fun Snow(running: Boolean, modifier: Modifier = Modifier) {
    val flakes = remember { Flakes() }
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                flakes.step(if (last == 0L) 0f else (now - last) / 1e9f)
                last = now
                tick.longValue = now
            }
            delay(Flakes.FRAME_MS)
        }
    }
    Canvas(modifier.fillMaxSize().graphicsLayer()) {
        tick.longValue // redraw when the flakes move
        val unit = 1.dp.toPx()
        for (i in 0 until flakes.count) {
            drawCircle(Color.White.copy(alpha = flakes.alpha[i]), flakes.size[i] * unit, Offset(flakes.x(i) * size.width, flakes.y[i] * size.height))
        }
    }
}

/**
 * Where each flake is, as fractions of the screen, so the same snow fits any TV. Near flakes are bigger,
 * brighter and faster than far ones, and each sways a little as it falls.
 */
class Flakes(val count: Int = 60, seed: Int = 25) {
    private val r = Random(seed)
    val y = FloatArray(count) { r.nextFloat() }
    private val x0 = FloatArray(count) { r.nextFloat() }
    private val near = FloatArray(count) { r.nextFloat().let { it * it } }
    val size = FloatArray(count) { 2f + near[it] * 4.6f } // dp, big enough to see across the room
    val alpha = FloatArray(count) { 0.35f + near[it] * 0.5f }
    private val fall = FloatArray(count) { 0.03f + near[it] * 0.06f + r.nextFloat() * 0.01f } // screens a second
    private val sway = FloatArray(count) { 0.004f + r.nextFloat() * 0.008f }
    private val phase = FloatArray(count) { r.nextFloat() * 6.28f }
    private val swaySpeed = FloatArray(count) { 0.4f + r.nextFloat() * 0.6f }

    fun x(i: Int): Float = x0[i] + sway[i] * sin(phase[i])

    /** Moves every flake on by [dt] seconds. One that falls off the bottom starts again above the top. */
    fun step(dt: Float) {
        // A long gap (Home was away) doesn't throw the flakes about.
        val t = dt.coerceIn(0f, 0.1f)
        for (i in 0 until count) {
            y[i] += fall[i] * t
            phase[i] += swaySpeed[i] * t
            if (y[i] > 1.02f) {
                y[i] -= 1.04f
                x0[i] = r.nextFloat()
            }
        }
    }

    companion object {
        /** The pause after each frame: about 24 frames a second, plenty for snow this slow. */
        const val FRAME_MS = 25L
    }
}
