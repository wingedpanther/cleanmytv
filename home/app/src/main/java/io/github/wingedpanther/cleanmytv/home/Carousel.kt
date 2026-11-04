package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val C_TEXT = Color(0xFFF0EBE5)
private val C_MUTED = Color(0xFFD7CFC4)

/** Decoded pictures, small: the centre card at half size, the neighbours at a quarter. About 6 MB at most. */
private val thumbs = object : LruCache<String, ImageBitmap>(6 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

@Composable
private fun picture(ctx: Context, id: String, v: Int, sample: Int): ImageBitmap? {
    val key = "$id:$v:$sample"
    val img by produceState(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(ThemePack.picture(ctx, id, v).path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()?.also { thumbs.put(key, it) }
        }
    }
    return img
}

/**
 * Themes on the TV, browsed with the remote: left and right for the theme, with its neighbours either side,
 * up and down for its pictures (the dots under the name). Nothing changes until OK.
 */
@Composable
fun ThemeCarousel(accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val themes = remember { ThemePack.list(ctx) }
    val now = ConfigStore.config.value.wallpaperFrom.split(":")
    var t by remember { mutableIntStateOf(themes.indexOfFirst { it.id == now.getOrNull(0) }.coerceAtLeast(0)) }
    var v by remember { mutableIntStateOf(if (themes.getOrNull(t)?.id == now.getOrNull(0)) now.getOrNull(1)?.toIntOrNull() ?: 0 else 0) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xF20B0F0C)).focusRequester(focus).focusable().onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown || themes.isEmpty()) return@onKeyEvent false
                val n = themes[t].pictures
                when (e.key) {
                    Key.DirectionLeft -> { t = (t - 1 + themes.size) % themes.size; v = 0; true }
                    Key.DirectionRight -> { t = (t + 1) % themes.size; v = 0; true }
                    Key.DirectionUp -> { v = (v - 1 + n) % n; true }
                    Key.DirectionDown -> { v = (v + 1) % n; true }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { ThemePack.apply(ctx, themes[t], v); onClose(); true }
                    else -> false
                }
            },
            contentAlignment = Alignment.Center
        ) {
            if (themes.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No themes on this TV yet", color = C_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text("In CleanMyTV: Home screen → Look & wallpaper → Themes → Put themes on the TV.", color = C_MUTED, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                }
                return@Box
            }
            // Two rings of neighbours, smaller and dimmer the further out, so more themes show at once.
            for (d in listOf(-2, 2, -1, 1)) {
                val i = ((t + d) % themes.size + themes.size) % themes.size
                if (themes.size < 3 && kotlin.math.abs(d) == 2) continue
                if (themes.size < 2) continue
                val w = if (kotlin.math.abs(d) == 1) 300.dp else 200.dp
                Card(ctx, themes[i], 0, w, 4, Modifier.offset(x = (if (kotlin.math.abs(d) == 1) 330.dp else 520.dp) * (if (d < 0) -1 else 1), y = (-24).dp).graphicsLayer { alpha = if (kotlin.math.abs(d) == 1) 0.6f else 0.3f })
            }
            val cur = themes[t]
            val pop by animateFloatAsState(1f, tween(200), label = "pop")
            Card(ctx, cur, v, 520.dp, 2, Modifier.offset(y = (-24).dp).graphicsLayer { scaleX = pop; scaleY = pop }.border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(14.dp)))
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 27.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(cur.name, color = C_TEXT, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                if (cur.pictures > 1) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(cur.pictures) { Box(Modifier.size(7.dp).background(if (it == v) accent else Color.White.copy(alpha = 0.25f), CircleShape)) }
                }
                Text("◀ ▶ themes   ▲ ▼ pictures   OK to use it   Back to close", color = C_MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}

@Composable
private fun Card(ctx: Context, theme: PackTheme, v: Int, w: Dp, sample: Int, modifier: Modifier) {
    val img = picture(ctx, theme.id, v, sample)
    Box(modifier.size(w, w * 9 / 16).clip(RoundedCornerShape(14.dp)).background(Color(0xFF1C2A1F))) {
        img?.let { Image(it, theme.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
