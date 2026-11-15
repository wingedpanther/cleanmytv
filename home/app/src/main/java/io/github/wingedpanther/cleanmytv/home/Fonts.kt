package io.github.wingedpanther.cleanmytv.home

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.tv.material3.LocalTextStyle

/**
 * Home's typeface, from the ones every Android TV carries, so nothing is downloaded or bundled. The clock
 * keeps its own. A TV without one of them shows the usual one instead.
 */
fun homeFont(id: String): FontFamily = when (id) {
    "condensed" -> FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
    "serif" -> FontFamily.Serif
    "casual" -> FontFamily(Typeface.create("casual", Typeface.NORMAL))
    else -> FontFamily.Default
}

@Composable
fun HomeFont(id: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = homeFont(id)), content = content)
}
