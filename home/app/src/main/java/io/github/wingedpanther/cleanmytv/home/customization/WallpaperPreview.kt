package io.github.wingedpanther.cleanmytv.home.customization

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/** A picture the focus is on in Themes and wallpaper, and what to call it under the preview. */
data class Previewed(val file: File, val name: String)

/**
 * Shows [p] in the preview while this button has the focus, then the wallpaper again. A button only clears
 * its own picture, whichever order the focus events come in.
 */
fun Modifier.previews(p: Previewed, preview: MutableState<Previewed?>): Modifier =
    onFocusChanged { if (it.isFocused) preview.value = p else if (preview.value == p) preview.value = null }

/**
 * The picture under the focus, beside Themes and wallpaper, so the owner sees it before choosing (Gathin
 * #3907). With no picture focused it shows the wallpaper on now. Each picture is decoded off the main thread
 * at about the preview's size, in RGB_565, a quarter of a megabyte for a 1080p one, and only once the focus
 * has rested on it for a moment, so moving along a row decodes nothing on the way. The few kept for going
 * back go when the editor closes.
 */
@Composable
fun WallpaperPreview(focused: Previewed?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val wallVersion by ConfigStore.wallpaperVersion.collectAsState()
    val now = remember(wallVersion) { ConfigStore.wallpaper(ctx).takeIf { it.exists() }?.let { Previewed(it, tr("The wallpaper now")) } }
    val shown = focused ?: now
    val cache = remember { object : LruCache<String, ImageBitmap>(3 * 1024 * 1024) { override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 2 } }
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shown?.file, wallVersion) {
        val f = shown?.file ?: run { picture = null; return@LaunchedEffect }
        val key = "${f.path}:${f.lastModified()}"
        cache.get(key)?.let { picture = it; return@LaunchedEffect }
        delay(150)
        picture = withContext(Dispatchers.IO) { small(f, 480) }?.also { cache.put(key, it) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.05f))) {
            Crossfade(picture, animationSpec = tween(180), label = "preview") { p ->
                if (p != null) Image(p, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Text(shown?.name ?: tr("No wallpaper"), color = MUTED_COLOR, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** [f] at least [width] pixels wide where it can be, in halves of its size. */
private fun small(f: File, width: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= width) sample *= 2
    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 })?.asImageBitmap()
}.getOrNull()
