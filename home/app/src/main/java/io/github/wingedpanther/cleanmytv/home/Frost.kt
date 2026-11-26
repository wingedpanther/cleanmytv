package io.github.wingedpanther.cleanmytv.home

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/** The wallpaper, blurred, for frosted glass panels; null when there is no picture to frost. */
internal val LocalFrost = staticCompositionLocalOf<ImageBitmap?> { null }

/**
 * Frosted glass on any Android version (#151): the wallpaper is a still picture, so it is blurred once when it
 * changes, and a panel draws its own part of that copy. RenderEffect would need Android 12, and most TVs are older.
 */
internal object Frost {
    /** An eighth of the size, box-blurred three times: smooth once it is stretched back over the screen. */
    fun of(src: ImageBitmap): ImageBitmap {
        val b = src.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        val w = max(8, b.width / 8)
        val h = max(8, b.height / 8)
        val small = Bitmap.createScaledBitmap(b, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { blur(px, w, h, 3) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    /** One box blur of [r] pixels each way, across then down. */
    private fun blur(px: IntArray, w: Int, h: Int, r: Int) {
        val out = IntArray(px.size)
        pass(px, out, w, h, r, across = true)
        pass(out, px, w, h, r, across = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, across: Boolean) {
        val lines = if (across) h else w
        val len = if (across) w else h
        for (line in 0 until lines) {
            fun at(i: Int) = src[if (across) line * w + i.coerceIn(0, len - 1) else i.coerceIn(0, len - 1) * w + line]
            var rs = 0; var gs = 0; var bs = 0
            for (i in -r..r) { val c = at(i); rs += c shr 16 and 0xFF; gs += c shr 8 and 0xFF; bs += c and 0xFF }
            val n = 2 * r + 1
            for (i in 0 until len) {
                dst[if (across) line * w + i else i * w + line] = (0xFF shl 24) or ((rs / n) shl 16) or ((gs / n) shl 8) or (bs / n)
                val out = at(i - r); val into = at(i + r + 1)
                rs += (into shr 16 and 0xFF) - (out shr 16 and 0xFF)
                gs += (into shr 8 and 0xFF) - (out shr 8 and 0xFF)
                bs += (into and 0xFF) - (out and 0xFF)
            }
        }
    }
}

/**
 * A pane of frosted glass behind this element, [inset] in from its edges, with corners of [radius]. [amount] is
 * the percent of white, as `tile_glass` uses; [dim] darkens the frosted picture as the wallpaper is darkened.
 * A light line on the top edge catches the light. Draws behind only, so the layout and the focus zoom don't move.
 */
internal fun Modifier.frosted(amount: Int, radius: Dp, insetX: Dp = 0.dp, insetY: Dp = 0.dp, dim: Float = 0f): Modifier = composed {
    val frost = LocalFrost.current
    var at by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    onGloballyPositioned { at = it.positionInRoot(); screen = it.findRootCoordinates().size }.drawBehind {
        val left = insetX.toPx(); val top = insetY.toPx()
        val pane = Size(size.width - 2 * left, size.height - 2 * top)
        if (pane.width <= 0f || pane.height <= 0f) return@drawBehind
        val r = CornerRadius(radius.toPx())
        val path = Path().apply { addRoundRect(RoundRect(left, top, left + pane.width, top + pane.height, r)) }
        clipPath(path) {
            frost?.let { f ->
                // Laid out as the wallpaper is, cropped to fill the screen, then moved so this pane shows its own part.
                val scale = max(screen.width.toFloat() / f.width, screen.height.toFloat() / f.height)
                val dw = f.width * scale; val dh = f.height * scale
                drawImage(f, IntOffset.Zero, IntSize(f.width, f.height),
                    IntOffset(((screen.width - dw) / 2 - at.x).roundToInt(), ((screen.height - dh) / 2 - at.y).roundToInt()),
                    IntSize(dw.roundToInt(), dh.roundToInt()), filterQuality = FilterQuality.Low)
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
            drawRect(Color.White.copy(alpha = amount / 100f))
            drawRect(Color.White.copy(alpha = (amount / 100f + 0.25f).coerceAtMost(1f)), Offset(left, top), Size(pane.width, 1.dp.toPx()))
        }
        drawRoundRect(Color.White.copy(alpha = 0.12f), Offset(left, top), pane, r, style = Stroke(1.dp.toPx()))
    }
}
