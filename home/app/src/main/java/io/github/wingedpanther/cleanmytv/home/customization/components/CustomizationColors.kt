package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The editor wears the theme Home has on: its accent for what's picked, and its background for the panel and
 * for text on light surfaces. Read through these getters, so a theme picked in the editor recolours it at once.
 */
object EditorTheme {
    private val FIXED = Color(0xFFA8C7FA)
    var accent by mutableStateOf(FIXED)
        private set
    var ground by mutableStateOf(Color(0xFF0B1D33))
        private set
    var panel by mutableStateOf(Color(0xFF1E2836))
        private set

    fun follow(accentArgb: Long, backgroundArgb: Long) {
        val a = Color(accentArgb)
        val bg = Color(backgroundArgb)
        // Picked items carry dark text, so a dark accent is lifted toward white until that text reads on it.
        // Focus is white, so a white or near-white accent would look focused everywhere: the fixed blue instead.
        accent = when {
            minOf(a.red, a.green, a.blue) > 0.75f -> FIXED
            a.luminance() > 0.36f -> a
            else -> lerp(a, Color.White, 0.5f)
        }
        // Dark text on white and on the accent; a light background goes most of the way to black first.
        ground = if (bg.luminance() < 0.04f) bg else lerp(bg, Color.Black, 0.75f)
        panel = if (bg.luminance() < 0.2f) bg else lerp(bg, Color.Black, 0.75f)
    }
}

val CUSTOMIZATION_BG: Color get() = EditorTheme.ground
val PANEL_BG: Color get() = EditorTheme.panel
val TEXT_COLOR = Color.White
val MUTED_COLOR = Color(0xFF9EAEC2)
val PRIMARY_BUTTON_COLOR: Color get() = EditorTheme.accent
/** Dialogs sit a step lighter than the panel, so they stand off it. */
val DIALOG_BG: Color get() = lerp(EditorTheme.panel, Color.White, 0.1f)
