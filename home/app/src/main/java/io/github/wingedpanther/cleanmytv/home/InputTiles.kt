package io.github.wingedpanther.cleanmytv.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.text.TextUtils
import android.text.TextPaint
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Inputs as tiles: each HDMI port (and the tuner) becomes an "app" the owner can put in the dock, a row or a
 * folder, named as the Inputs menu names it, like "PS5". Its stand-in package name fits everywhere a package
 * goes, in Home and in the desktop app, and one press switches to the input.
 */
object InputTiles {
    const val PREFIX = "cmtv_input."

    /** `com.mediatek.tvinput/.hdmi.HDMIInputService/HW1` -> `cmtv_input.com_mediatek_tvinput__hdmi_hdmiinputservice_hw1`. */
    fun key(id: String) = PREFIX + id.lowercase().replace(Regex("[^a-z0-9]"), "_")

    fun isInput(pkg: String?) = pkg?.startsWith(PREFIX) == true

    /** The input a tile stands for, while the TV still has it. */
    fun idFor(ctx: Context, pkg: String): String? = tvInputs(ctx).firstOrNull { key(it.first) == pkg }?.first

    fun apps(ctx: Context): List<TvApp> = tvInputs(ctx).map { (id, name) ->
        TvApp(key(id), name, banner(name).asImageBitmap(), banner = true, launch = intent(ctx, id), colour = TILE.toInt())
    }

    /** For the desktop app's editor: the same tiles, names only. */
    fun list(ctx: Context): List<Pair<String, String>> = tvInputs(ctx).map { (id, name) -> key(id) to name }

    fun intent(ctx: Context, id: String): Intent =
        Intent(ctx, InputActivity::class.java).putExtra(InputActivity.INPUT, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** A remote button's "open an app" job on an input tile. */
    fun open(ctx: Context, pkg: String) { idFor(ctx, pkg)?.let { openInput(ctx, it) } }

    private const val TILE = 0xFF37474FL

    /** A TV-sized banner: a port glyph and the input's name, so it sits with the apps' own banners. */
    private fun banner(name: String): Bitmap {
        val w = 320; val h = 180
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(TILE.toInt())
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f; strokeJoin = Paint.Join.ROUND }
        // An HDMI plug: the wide top, narrowing at the bottom corners.
        val path = android.graphics.Path().apply {
            moveTo(130f, 46f); lineTo(190f, 46f); lineTo(190f, 66f); lineTo(182f, 78f); lineTo(138f, 78f); lineTo(130f, 66f); close()
        }
        c.drawPath(path, line)
        c.drawRoundRect(RectF(144f, 56f, 176f, 64f), 2f, 2f, Paint(line).apply { style = Paint.Style.FILL })
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 34f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
        val fit = TextUtils.ellipsize(name, text, w - 40f, TextUtils.TruncateAt.END).toString()
        c.drawText(fit, w / 2f, 132f, text)
        return b
    }
}

/** Switches to the input in its extra and closes: input tiles open through it, so the PIN pad can gate them like apps. */
class InputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(INPUT)?.let { openInput(this, it) }
        finish()
    }

    companion object { const val INPUT = "input" }
}
