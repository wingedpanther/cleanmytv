package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.PathParser
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import kotlin.math.abs

/**
 * An app's logo on its own, for the icon styles. [plate] is the colour the logo sat on, when it was a
 * colourful one (YouTube's red, Spotify's green); [drawn] says it came from the drawn set. [picture]: the
 * icon is a full picture with no logo to lift off it, which only the Colour style can show.
 */
class Glyph(val art: ImageBitmap, val plate: Int?, val drawn: Boolean, val picture: Boolean = false)

/**
 * Every app gets a logo, in this order: the drawn set (Simple Icons, CC0, matched by package name), the
 * logo layer of the app's adaptive icon, then its flat icon split into backing and logo. Null means
 * nothing usable came out, and the tile shows the app's first letter.
 */
object IconArt {
    private const val SIZE = 128
    private var drawn: Map<String, Pair<String, Int>>? = null

    private fun drawnSet(ctx: Context): Map<String, Pair<String, Int>> = drawn ?: runCatching {
        val o = JSONObject(ctx.assets.open("drawn_icons.json").bufferedReader().use { it.readText() })
        o.keys().asSequence().filter { !it.startsWith("_") }.associateWith { k ->
            val e = o.getJSONObject(k)
            e.getString("path") to (0xFF000000.toInt() or e.getString("hex").removePrefix("#").toInt(16))
        }
    }.getOrDefault(emptyMap()).also { drawn = it }

    fun glyph(ctx: Context, pkg: String, icon: Drawable): Glyph? = runCatching {
        drawnSet(ctx)[pkg]?.let { (path, hex) -> fromPath(path, hex) }
            ?: (icon as? AdaptiveIconDrawable)?.let { fromAdaptive(it) }
            ?: fromFlat(icon.toBitmap(SIZE, SIZE))
    }.getOrNull()

    private fun fromPath(data: String, hex: Int): Glyph? {
        val path = PathParser.createPathFromPathData(data)
        val bmp = createBitmap(SIZE, SIZE)
        Canvas(bmp).apply {
            concat(Matrix().apply { setScale(SIZE / 24f, SIZE / 24f) })
            drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hex })
        }
        return crop(bmp)?.let { Glyph(it.asImageBitmap(), hex, drawn = true) }
    }

    /** Android 8+ icons hand over the logo as its own layer; the background layer gives the plate. */
    private fun fromAdaptive(a: AdaptiveIconDrawable): Glyph? {
        val fg = a.foreground ?: return null
        // The layers are 108 dp with the middle 72 dp visible: draw at 1.5x and keep the middle.
        val big = createBitmap(SIZE * 3 / 2, SIZE * 3 / 2)
        fg.setBounds(0, 0, big.width, big.height)
        fg.draw(Canvas(big))
        val inner = Bitmap.createBitmap(big, SIZE / 4, SIZE / 4, SIZE, SIZE)
        val px = IntArray(SIZE * SIZE).also { inner.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        // A foreground that covers nearly everything is a picture, not a logo: treat the icon as flat.
        if (px.count { alpha(it) > 200 } > SIZE * SIZE * 0.85) return fromFlat(a.toBitmap(SIZE, SIZE))
        val plate = a.background?.let { bg -> average(bg.toBitmap(16, 16)) }?.takeIf { saturation(it) > 0.25f }
        return crop(inner)?.let { Glyph(it.asImageBitmap(), plate, drawn = false) }
    }

    /** A flat icon: find the shape behind the logo (by its edge, or by its most common colour) and lift the logo off it. */
    private fun fromFlat(bmp: Bitmap): Glyph? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        val ring = ArrayList<Int>(); var ringAll = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (x in 5 until w - 5 && y in 5 until h - 5) continue
            ringAll++
            if (alpha(px[y * w + x]) > 200) ring += px[y * w + x]
        }
        var plate: Int? = null
        val opaque = px.count { alpha(it) > 200 }
        if (ring.size > ringAll * 0.7) {
            val (c, f) = common(ring); if (f > 0.6f) plate = c
        } else {
            val (c, f) = common(px.filter { alpha(it) > 200 })
            if (f > 0.6f && f < 0.97f) plate = c
        }
        // Opaque edge to edge with no one colour behind: a picture, not a logo on a backing.
        if (plate == null && opaque > w * h * 0.8) return Glyph(bmp.asImageBitmap(), null, drawn = false, picture = true)
        val out = px.copyOf()
        for (i in out.indices) if (alpha(out[i]) < 40 || (plate != null && near(out[i], plate))) out[i] = 0
        if (out.count { it != 0 } < w * h * 0.015) return null
        val logo = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        return crop(logo)?.let { Glyph(it.asImageBitmap(), plate?.takeIf { p -> saturation(p) > 0.25f }, drawn = false) }
    }

    /** Trims the empty edges, so every logo sits the same size on its tile. */
    private fun crop(bmp: Bitmap): Bitmap? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h).also { bmp.getPixels(it, 0, w, 0, 0, w, h) }
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (alpha(px[y * w + x]) > 8) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y }
        if (x1 < 0 || (x1 - x0) * (y1 - y0) < 16) return null
        return Bitmap.createBitmap(bmp, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
    }

    private fun alpha(c: Int) = c ushr 24
    private fun red(c: Int) = (c shr 16) and 255
    private fun green(c: Int) = (c shr 8) and 255
    private fun blue(c: Int) = c and 255
    private fun near(p: Int, q: Int) = abs(red(p) - red(q)) + abs(green(p) - green(q)) + abs(blue(p) - blue(q)) < 60
    private fun saturation(c: Int): Float { val mx = maxOf(red(c), green(c), blue(c)); val mn = minOf(red(c), green(c), blue(c)); return if (mx == 0) 0f else (mx - mn) / mx.toFloat() }

    /** The most common colour, in coarse buckets, and how much of [list] it covers. */
    private fun common(list: List<Int>): Pair<Int, Float> {
        if (list.isEmpty()) return 0 to 0f
        val counts = HashMap<Int, IntArray>()
        for (c in list) counts.getOrPut((red(c) shr 5 shl 6) or (green(c) shr 5 shl 3) or (blue(c) shr 5)) { intArrayOf(c, 0) }[1]++
        val best = counts.values.maxBy { it[1] }
        return best[0] to best[1] / list.size.toFloat()
    }

    private fun average(bmp: Bitmap): Int {
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) { val c = bmp.getPixel(x, y); if (alpha(c) < 128) continue; rs += red(c); gs += green(c); bs += blue(c); n++ }
        if (n == 0) return 0
        return 0xFF000000.toInt() or ((rs / n).toInt() shl 16) or ((gs / n).toInt() shl 8) or (bs / n).toInt()
    }

    /** Duotone: each pixel's lightness picks a colour between [dark] and [light], as one colour-matrix pass. */
    fun duotone(dark: androidx.compose.ui.graphics.Color, light: androidx.compose.ui.graphics.Color): ColorMatrix {
        fun row(a: Float, b: Float): FloatArray {
            val span = (b - a) * 255f * 0.65f / 255f
            return floatArrayOf(span * 0.2126f, span * 0.7152f, span * 0.0722f, 0f, (a + (b - a) * 0.35f) * 255f)
        }
        return ColorMatrix(row(dark.red, light.red) + row(dark.green, light.green) + row(dark.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
    }
}
