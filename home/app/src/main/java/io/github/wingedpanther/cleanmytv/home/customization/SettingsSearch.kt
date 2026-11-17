package io.github.wingedpanther.cleanmytv.home.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.customization.components.CUSTOMIZATION_BG
import io.github.wingedpanther.cleanmytv.home.customization.components.DIALOG_BG
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.PRIMARY_BUTTON_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.delay

/**
 * One setting the search can find: its name as the editor shows it, other words for it, and its section.
 * The name and words are English, and the keys for the TV's language, so a search works in either.
 */
data class Findable(val name: String, val words: String, val where: CustomizationCategory)

/** Every setting in the editor, with the words people use when they look for it. */
val SETTINGS_INDEX: List<Findable> = run {
    val L = CustomizationCategory.LAYOUT; val R = CustomizationCategory.ROWS; val K = CustomizationCategory.LOOK
    val T = CustomizationCategory.THEMES; val W = CustomizationCategory.WIDGETS; val A = CustomizationCategory.APPS
    val B = CustomizationCategory.BUTTONS; val P = CustomizationCategory.LOCK; val V = CustomizationCategory.VISUALIZER
    val F = CustomizationCategory.FIREPLACE; val M = CustomizationCategory.MORE
    listOf(
        Findable("Layout: Shelf, Split or Rail", "style arrangement sidebar left panel", L),
        Findable("Space above the apps", "top space gap margin dock position", L),
        Findable("Sections", "order show hide now playing recently used dock continue watching rows folders all apps", L),
        Findable("Recently used", "recent last apps history", L),
        Findable("Continue watching", "watch next resume films episodes series progress", L),
        Findable("Top bar icons", "quick settings shortcuts wifi bluetooth inputs settings icons bar sleep timer update", L),
        Findable("Rows", "categories groups row new rename", R),
        Findable("Folders", "group collection new rename", R),
        Findable("Font", "typeface text letters serif condensed casual", K),
        Findable("Darken the wallpaper", "dim dark brightness wallpaper", K),
        Findable("Shading", "shade gradient top bottom tint wash reach", K),
        Findable("Accent colour", "color accent highlight theme", K),
        Findable("Focus", "lift glow outline highlight selected tile", K),
        Findable("Tilt", "lean motion 3d", K),
        Findable("Keep the focused tile in place", "stationary still slide row under", K),
        Findable("Background takes the app's colour", "tint follow colour color wash", K),
        Findable("App icons", "banners colour mono duotone logo style", K),
        Findable("Tile shape", "square squircle round wide circle", K),
        Findable("Tile size", "dock size grid size bigger smaller scale", K),
        Findable("App names", "labels titles names under tiles", K),
        Findable("Themes", "theme look style colours", T),
        Findable("Each theme keeps its changes", "theme remember memory own look per theme", T),
        Findable("Save this look as a theme", "save theme new custom my own", T),
        Findable("Wallpaper", "background picture photo image", T),
        Findable("From your phone", "photo upload phone qr camera picture send", T),
        Findable("Bing picture of the day", "bing daily picture", T),
        Findable("Bing every day", "bing daily automatic wallpaper change", T),
        Findable("Snow", "snowflakes falling winter christmas december", T),
        Findable("Slideshow photos", "slides slideshow photo", T),
        Findable("Day and night", "schedule evening night morning time swap", T),
        Findable("Clock", "time date format", W),
        Findable("Weather", "location city temperature forecast", W),
        Findable("Temperature", "celsius fahrenheit unit", W),
        Findable("Hourly forecast", "weather hours hourly later next rain", W),
        Findable("Idle screen", "screensaver idle big clock photos ambient", W),
        Findable("Apps in the dock", "dock size favourites favorites", A),
        Findable("Apps per row", "columns grid", A),
        Findable("Hide apps", "hidden remove show order", A),
        Findable("Remote buttons", "button remote map key red green yellow blue shortcut", B),
        Findable("Sleep timer button", "sleep timer off button", B),
        Findable("App lock", "pin password lock code parental kids child", P),
        Findable("Locked apps", "pin lock apps parental", P),
        Findable("Daily limits", "time limit screen time minutes kids child parental", P),
        Findable("Customize Home lock", "pin editor lock", P),
        Findable("Change the PIN", "pin password code", P),
        Findable("Music bars", "visualiser visualizer equaliser equalizer audio sound bars", V),
        Findable("Music bars style and colour", "rainbow bars wave mirror height opacity sensitivity", V),
        Findable("Fireplace", "fire ambient cosy cozy log", F),
        Findable("Fireplace sound and timer", "crackle volume turn off timer", F),
        Findable("Greeting", "name hello good morning", M),
        Findable("Free memory", "ram memory clean", M),
        Findable("Live TV in Inputs", "tuner channels antenna hdmi inputs", M),
        Findable("Back to the input at power on", "hdmi input power on startup boot", M),
        Findable("Open at power on", "hdmi input power on startup boot monitor last input", M),
        Findable("Wait after waking", "power on delay seconds hdmi input", M),
        Findable("Keep my keyboard", "keyboard ime phone typing standby input method", M),
        Findable("Phone apps in their own folder", "other apps phone apps folder", M),
        Findable("Sunrise when the wallpaper changes", "intro animation sunrise", M),
        Findable("Names", "rename label top bar all apps title", M),
        Findable("Updates", "update version new release check what's new changelog", M),
    )
}

/** Best first: names that start with the words, then names that hold them, then the other words. */
fun searchSettings(query: String, index: List<Findable> = SETTINGS_INDEX): List<Findable> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    fun score(f: Findable): Int {
        val names = listOf(f.name, tr(f.name)).map { it.lowercase() }
        val all = "${names.joinToString(" ")} ${f.words} ${tr(f.words).lowercase()} ${f.where.title.lowercase()} ${f.where.label.lowercase()}"
        if (!words.all { it in all }) return 0
        return when {
            names.any { it.startsWith(words.first()) } -> 3
            words.all { w -> names.any { w in it } } -> 2
            else -> 1
        }
    }
    return index.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
}

/** The search box and its results; picking one opens its section. */
@Composable
fun SettingsSearchDialog(onDismiss: () -> Unit, onPick: (Findable) -> Unit) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val input = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(250); runCatching { input.requestFocus(); keyboard?.show() } }
    val results = remember(query) { searchSettings(query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.padding(top = 40.dp).width(560.dp).clip(RoundedCornerShape(16.dp)).background(DIALOG_BG).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(tr("Search settings"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (focused) Color.White else Color(0x22FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    if (query.isEmpty()) Text(tr("Try font, wallpaper, PIN, sleep, weather"), color = if (focused) CUSTOMIZATION_BG.copy(alpha = 0.6f) else MUTED_COLOR, fontSize = 14.sp)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(40) },
                        textStyle = TextStyle(color = if (focused) CUSTOMIZATION_BG else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(if (focused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); if (results.isNotEmpty()) runCatching { firstResult.requestFocus() } }),
                        // A one-line field keeps Down for itself on some TVs: Down goes to the results instead.
                        modifier = Modifier.fillMaxWidth().focusRequester(input).onFocusChanged { focused = it.isFocused; if (it.isFocused) keyboard?.show() }
                            .onPreviewKeyEvent { e ->
                                if (e.key == Key.DirectionDown && results.isNotEmpty()) {
                                    if (e.type == KeyEventType.KeyDown) { keyboard?.hide(); runCatching { firstResult.requestFocus() } }
                                    true
                                } else false
                            }
                    )
                }
                if (query.isNotBlank() && results.isEmpty()) Text(tr("Nothing called that. Try another word."), color = MUTED_COLOR, fontSize = 13.sp)
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results.size) { i ->
                        val f = results[i]
                        TvSecondaryButton(text = "${tr(f.name)}  ·  ${f.where.label}", onClick = { keyboard?.hide(); onPick(f) }, modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(firstResult) else Modifier))
                    }
                }
            }
        }
    }
}
