package io.github.wingedpanther.cleanmytv.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * The music bars. The visualiser sends new heights about 20 times a second; this eases toward them on
 * every frame so the motion is smooth, lets them fall slower than they rise, and keeps a peak dot per
 * bar that drifts down. Nothing is drawn while the TV is silent.
 */
@Composable
fun Bars(visualiser: Visualiser, cfg: HomeConfig, accent: Color, modifier: Modifier) {
    val target by visualiser.levels.collectAsState()
    val screenH = LocalConfiguration.current.screenHeightDp
    val state = remember { BarState() }
    val tick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            // Silent: check four times a second instead of drawing every frame.
            if (state.quiet() && visualiser.levels.value.all { it < 0.01f }) { last = 0L; delay(250); continue }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1e9f).coerceIn(0.001f, 0.1f)
                last = now
                if (state.step(visualiser.levels.value, dt)) tick.longValue = now
            }
        }
    }

    Canvas(modifier.height((screenH * cfg.vizHeight / 100f).dp)) {
        tick.longValue // redraw when the bars move
        if (target.isEmpty() && state.quiet()) return@Canvas
        val alpha = cfg.vizOpacity / 100f
        when (cfg.vizStyle) {
            "wave" -> wave(state.shown, cfg, accent, alpha)
            else -> bars(state, cfg, accent, alpha, mirror = cfg.vizStyle == "mirror")
        }
    }
}

private class BarState {
    var shown = FloatArray(0)
    var peaks = FloatArray(0)

    /** Moves the shown heights toward the target; true when anything changed enough to redraw. */
    fun step(target: FloatArray, dt: Float): Boolean {
        if (shown.size != target.size && target.isNotEmpty()) {
            shown = FloatArray(target.size)
            peaks = FloatArray(target.size)
        }
        var moved = false
        for (i in shown.indices) {
            val t = target.getOrElse(i) { 0f }
            val v = shown[i]
            // Rise fast, fall slow.
            val k = if (t > v) 1f - Math.pow(0.0005, dt.toDouble()).toFloat() else 1f - Math.pow(0.08, dt.toDouble()).toFloat()
            val next = v + (t - v) * k
            if (kotlin.math.abs(next - v) > 0.001f) moved = true
            shown[i] = next
            val p = max(next, peaks[i] - 0.5f * dt)
            if (p != peaks[i]) moved = true
            peaks[i] = p
        }
        return moved
    }

    fun quiet() = shown.all { it < 0.03f } && peaks.all { it < 0.06f }
}

private fun colourFor(i: Int, n: Int, cfg: HomeConfig, accent: Color): Color = when (cfg.vizColour) {
    "rainbow" -> Color.hsv(300f * i / max(1, n - 1), 0.75f, 1f)
    "white" -> Color.White
    else -> accent
}

private fun DrawScope.bars(state: BarState, cfg: HomeConfig, accent: Color, alpha: Float, mirror: Boolean) {
    val n = state.shown.size
    if (n == 0) return
    val gap = size.width / n * 0.22f
    val w = (size.width - gap * (n + 1)) / n
    val base = if (mirror) size.height / 2 else size.height
    val span = if (mirror) size.height / 2 else size.height
    val radius = CornerRadius(minOf(w / 3, 4.dp.toPx()))
    for (i in 0 until n) {
        val c = colourFor(i, n, cfg, accent)
        val h = state.shown[i] * span
        val x = gap + i * (w + gap)
        // Below 3% it's room noise, not music: draw nothing rather than a row of hairlines.
        if (state.shown[i] > 0.03f) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.35f)), startY = base - h, endY = base),
                topLeft = Offset(x, base - h), size = Size(w, h), cornerRadius = radius
            )
            if (mirror) drawRoundRect(
                brush = Brush.verticalGradient(listOf(c.copy(alpha = alpha * 0.35f), c.copy(alpha = 0f)), startY = base, endY = base + h),
                topLeft = Offset(x, base), size = Size(w, h), cornerRadius = radius
            )
        }
        if (cfg.vizPeaks && state.peaks[i] > 0.06f) {
            val py = base - state.peaks[i] * span - 4.dp.toPx()
            drawRoundRect(c.copy(alpha = alpha), Offset(x, py), Size(w, 3.dp.toPx()), radius)
        }
    }
}

private fun DrawScope.wave(shown: FloatArray, cfg: HomeConfig, accent: Color, alpha: Float) {
    val n = shown.size
    if (n < 2 || shown.all { it < 0.03f }) return
    val step = size.width / (n - 1)
    val line = Path()
    for (i in 0 until n) {
        val x = i * step
        val y = size.height - shown[i] * size.height * 0.9f
        if (i == 0) line.moveTo(x, y) else {
            val px = (i - 1) * step
            val py = size.height - shown[i - 1] * size.height * 0.9f
            line.cubicTo(px + step / 2, py, x - step / 2, y, x, y)
        }
    }
    val fill = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
    val colours = if (cfg.vizColour == "rainbow") (0..6).map { Color.hsv(50f * it, 0.75f, 1f) } else listOf(colourFor(0, 1, cfg, accent), colourFor(0, 1, cfg, accent))
    drawPath(fill, Brush.verticalGradient(listOf(colours[0].copy(alpha = alpha * 0.45f), Color.Transparent)))
    drawPath(line, Brush.horizontalGradient(colours.map { it.copy(alpha = alpha) }), style = Stroke(width = 3.dp.toPx()))
}
