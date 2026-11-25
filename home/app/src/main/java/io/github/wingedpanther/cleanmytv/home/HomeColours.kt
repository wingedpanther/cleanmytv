package io.github.wingedpanther.cleanmytv.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Home's panels and tiles take the theme's background instead of a fixed green (#150). ConfigStore keeps them
 * in step, and reads go through state, so a new theme recolours open panels at once. The default background
 * keeps the greens Home always had; a light one is darkened first, so the off-white text on top still reads.
 */
object HomeColours {
    private const val DEFAULT = 0xFF1C2A1FL

    /** The darkest surface: the Quick settings panel and empty picture frames. */
    var ground by mutableStateOf(Color(DEFAULT))
        private set
    /** Menus, dialogs and the Now playing card. */
    var panel by mutableStateOf(Color(0xFF2D3A2E))
        private set
    /** A focused tile, a step lighter than the panel. */
    var raised by mutableStateOf(Color(0xFF3A4A3B))
        private set

    fun follow(backgroundArgb: Long) {
        if (backgroundArgb == DEFAULT) {
            ground = Color(DEFAULT); panel = Color(0xFF2D3A2E); raised = Color(0xFF3A4A3B)
            return
        }
        val bg = Color(backgroundArgb).copy(alpha = 1f)
        ground = if (bg.luminance() < 0.2f) bg else mix(bg, Color.Black, 0.75f)
        panel = mix(ground, Color.White, 0.075f)
        raised = mix(ground, Color.White, 0.13f)
    }

    // Straight sRGB, like the hand-picked greens were.
    private fun mix(a: Color, b: Color, t: Float) =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)
}
