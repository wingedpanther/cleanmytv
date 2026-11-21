package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.DayNight
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.PackTheme
import io.github.wingedpanther.cleanmytv.home.Slides
import io.github.wingedpanther.cleanmytv.home.ThemePack
import io.github.wingedpanther.cleanmytv.home.Wallpapers
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TextPrompt
import io.github.wingedpanther.cleanmytv.home.customization.components.TvPrimaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSwitch
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun clock(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

/**
 * Themes, wallpaper and the day and night looks, as the desktop app's Look tab has them. Pictures from the
 * computer or a USB drive still come through the desktop app, which shrinks them for the TV.
 */
@Composable
fun ThemesCustomization(config: HomeConfig, ctx: Context, preview: MutableState<Previewed?>) {
    val packVersion by ThemePack.version.collectAsState()
    // Read off the main thread, so drawing the section doesn't hold up the remote (#133).
    val pack by produceState<Pair<List<PackTheme>, Set<String>>?>(null, packVersion, config) {
        value = withContext(Dispatchers.IO) { ThemePack.list(ctx).let { it to ThemePack.changed(ctx, it) } }
    }
    val (themes, changed) = pack ?: return
    var naming by remember { mutableStateOf(false) }
    var saveNote by remember { mutableStateOf("") }
    val slides = remember { Slides.count(ctx) }
    var saved by remember { mutableStateOf(DayNight.saved(ctx)) }
    var bing by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hasWallpaper = ConfigStore.wallpaper(ctx).exists()

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ---- Themes ----
        Text(tr("Themes"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (themes.isEmpty()) {
            Text(tr("No themes on the TV yet. The desktop app puts them here when you apply one from its Look tab."), color = MUTED_COLOR, fontSize = 13.sp)
        } else {
            themes.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t.name + if (t.id in changed) tr(" (your changes)") else "", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    val picture = { v: Int -> Previewed(ThemePack.picture(ctx, t.id, v), "${t.name} · ${tr("Picture {n}", "n" to v + 1)}") }
                    TvPrimaryButton(text = tr("Use theme"), onClick = { ThemePack.apply(ctx, t, 0) }, modifier = Modifier.previews(picture(0), preview))
                    // Only the picture: the colours and icons stay as they are.
                    for (v in 0 until t.pictures) {
                        TvSecondaryButton(text = tr("Picture {n}", "n" to v + 1), onClick = { ThemePack.apply(ctx, t, v, withLook = false) }, modifier = Modifier.previews(picture(v), preview))
                    }
                    if (t.id in changed) TvSecondaryButton(text = tr("Forget changes"), onClick = { ThemePack.forget(ctx, t.id) })
                }
            }
        }
        TvSwitch(
            title = tr("Each theme keeps its changes"),
            subtitle = tr("Change the look while a theme is on, and the theme keeps it: colours, sizes, glass, shading, spacing and music bars. Rows, folders, App lock and buttons stay the same for every theme."),
            checked = config.themeMemory,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("theme_memory", it)) }
        )
        TvSecondaryButton(text = tr("Save this look as a theme"), onClick = { saveNote = ""; naming = true })
        if (saveNote.isNotEmpty()) Text(saveNote, color = MUTED_COLOR, fontSize = 13.sp)

        // ---- Wallpaper ----
        Text(tr("Wallpaper"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvPrimaryButton(text = tr("From your phone"), onClick = { phone = true })
            TvSecondaryButton(text = tr("Bing picture of the day"), onClick = {
                bing = tr("Getting today's picture…")
                scope.launch { bing = Wallpapers.bing(ctx)?.let { tr("That didn't work: {why}.", "why" to it) } ?: "" }
            })
            if (hasWallpaper) TvSecondaryButton(text = tr("No wallpaper"), onClick = { Wallpapers.remove(ctx) })
        }
        if (bing.isNotEmpty()) Text(bing, color = MUTED_COLOR, fontSize = 13.sp)
        TvSwitch(
            title = tr("Snow"),
            subtitle = tr("Gentle snow over the wallpaper, behind the apps. The Christmas theme turns it on."),
            checked = config.snow,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("snow", it)) }
        )
        TvSwitch(
            title = tr("Bing every day"),
            subtitle = tr("Bing's new picture becomes the wallpaper each day. Picking another wallpaper doesn't stop it; this switch does."),
            checked = config.bingDaily,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("bing_daily", it)) }
        )
        TvSwitch(
            title = tr("Show where it was taken"),
            subtitle = tr("A small line in the bottom corner of Home says where Bing's picture was taken."),
            checked = config.bingCaption,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("bing_caption", it)) }
        )
        if (slides > 0) {
            Text(tr("The slideshow's photos"), color = MUTED_COLOR, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in 0 until slides) {
                    val photo = Previewed(Slides.file(ctx, n), "${tr("The slideshow's photos")} · ${n + 1}")
                    TvSecondaryButton(text = "${n + 1}", onClick = { Wallpapers.use(ctx, Slides.file(ctx, n)) }, modifier = Modifier.previews(photo, preview))
                }
            }
        }
        Alert(text = tr("From your phone sends a photo as the wallpaper or into the slideshow. Pictures on your computer go through the desktop app."), modifier = Modifier.fillMaxWidth())

        // ---- Day and night ----
        Text(tr("Day and night"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(tr("Set Home up for the day and save it, then the same for the night. Home swaps them at these times."), color = MUTED_COLOR, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (slot in listOf("day", "night")) {
                TvSecondaryButton(
                    text = (if (slot == "day") tr("Save this look as day") else tr("Save this look as night")) + (if (slot in saved) tr(" (saved)") else ""),
                    onClick = { DayNight.save(ctx, slot); saved = DayNight.saved(ctx) }
                )
            }
        }
        TvSwitch(
            title = tr("Swap looks at these times"),
            subtitle = if (saved.size < 2) tr("Save a day look and a night look first") else null,
            checked = config.dayNight,
            onCheckedChange = { on ->
                if (!on || saved.size == 2) { ConfigStore.update(ctx, JSONObject().put("day_night", on)); DayNight.reset(ctx) }
            }
        )
        for ((key, label, at) in listOf(Triple("day_at", tr("Day from"), config.dayAt), Triple("night_at", tr("Night from"), config.nightAt))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$label ${clock(at)}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                val move = { by: Int -> ConfigStore.update(ctx, JSONObject().put(key, Math.floorMod(at + by, 24 * 60))); DayNight.reset(ctx) }
                TvSecondaryButton(text = tr("Earlier"), onClick = { move(-30) })
                TvSecondaryButton(text = tr("Later"), onClick = { move(30) })
            }
        }
    }
    if (phone) PhoneDialog(ctx) { phone = false }
    if (naming) TextPrompt(
        title = tr("A name for this theme"),
        hint = tr("The look and the wallpaper on now. A theme with the same name takes this look instead."),
        maxLength = 40,
        onDismiss = { naming = false },
        onDone = { name ->
            naming = false
            saveNote = runCatching { ThemePack.saveCurrent(ctx, name); tr("Saved as {name}.", "name" to name) }.getOrElse { tr("Not saved: {why}.", "why" to it.message.toString()) }
        }
    )
}
