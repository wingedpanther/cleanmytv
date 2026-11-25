package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.tr
import org.json.JSONObject

@Composable
fun LookCustomization(config: HomeConfig, ctx: Context) {
    val hexAccents = listOf(
        0xFFFFFFFF.toInt() to tr("White"),
        0xFF4CAF50.toInt() to tr("Green"),
        0xFF2196F3.toInt() to tr("Blue"),
        0xFFFF9800.toInt() to tr("Orange"),
        0xFFE91E63.toInt() to tr("Pink"),
        0xFF9C27B0.toInt() to tr("Purple"),
        0xFF00BCD4.toInt() to tr("Cyan")
    )

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Teletext draws its own black pages in one monospace face.
        if (config.layout == "teletext") Alert(text = tr("Teletext has its own colours and type. The font, wallpaper and accent here apply to the other layouts."), modifier = Modifier.fillMaxWidth())
        // Glass keeps the system sans, as its design has it.
        if (config.layout == "glass") Alert(text = tr("Glass keeps the TV's usual sans. The font here applies to the other layouts."), modifier = Modifier.fillMaxWidth())
        TvRadioGroup(
            title = tr("Font"),
            options = listOf("default" to tr("Usual"), "condensed" to tr("Condensed"), "serif" to tr("Serif"), "casual" to tr("Casual")),
            current = config.font,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("font", it)) }
        )

        // --- 1. Wallpaper Dimming Overlay ---
        TvSlider(
            title = tr("Darken the wallpaper"),
            value = config.wallpaperDim,
            min = 0, max = 80, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("wallpaper_dim", it)) }
        )

        // Shading, as on the desktop app's Look tab: strength and how far each shade reaches.
        val shades = buildList {
            if (config.layout == "shelf") {
                add(Triple(tr("Top shade"), "shade_top", config.shadeTop)); add(Triple(tr("Top shade reach"), "shade_top_reach", config.shadeTopReach))
            }
            add(Triple(tr("Bottom shade"), "shade_bottom", config.shadeBottom)); add(Triple(tr("Bottom shade reach"), "shade_bottom_reach", config.shadeBottomReach))
            if (config.followColour) {
                add(Triple(tr("App colour"), "wash", config.wash)); add(Triple(tr("App colour reach"), "wash_reach", config.washReach))
            }
        }
        for ((title, key, value) in shades) {
            TvSlider(
                title = title,
                value = value,
                min = if (key == "wash_reach") 10 else 0, max = 100, unit = "%",
                onChange = { ConfigStore.update(ctx, JSONObject().put(key, it)) }
            )
        }

        // --- 3. Accent Color Section ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Accent"), color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                hexAccents.forEach { (colorVal, name) ->
                    val isSelected = (config.accent and 0xFFFFFFFFL) == (colorVal.toLong() and 0xFFFFFFFFL)
                    val focusedBorderColor = if (colorVal == 0xFFFFFFFF.toInt()) PRIMARY_BUTTON_COLOR else Color.White
                    Surface(
                        onClick = { ConfigStore.update(ctx, JSONObject().put("accent", String.format("#%06X", (0xFFFFFF and colorVal)))) },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(colorVal),
                            focusedContainerColor = Color(colorVal)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(4.dp, Color(colorVal)),
                                shape = CircleShape
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(4.dp, focusedBorderColor),
                                shape = CircleShape
                            )
                        ),
                        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                        modifier = Modifier
                            .height(36.dp)
                            .width(36.dp)
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = tr("Selected Accent"),
                                    tint = Color.Black,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 4. Icon Style & App Labels ---
        TvRadioGroup(
            title = tr("App icons"),
            options = listOf("banner" to tr("App Banner"), "colour" to tr("Vector Brand Color"), "mono" to tr("Monochrome"), "duo" to tr("Duotone")),
            current = config.iconStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("icon_style", it)) }
        )

        // Side-by-Side App Labels Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvSwitch(
                title = tr("App names"),
                subtitle = tr("Names under the tiles"),
                checked = config.showNames,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("show_names", it)) },
                modifier = Modifier.weight(1f)
            )

            TvSwitch(
                title = tr("Only the focused app's name"),
                subtitle = tr("The other names stay hidden"),
                checked = config.nameFocus,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("name_focus", it)) },
                modifier = Modifier.weight(1f)
            )
        }

        // --- 5. Tile Shape & Sizes ---
        TvRadioGroup(
            title = tr("Tile shape"),
            options = listOf("wide" to tr("Wide (16:9)"), "square" to tr("Square (1:1)"), "squircle" to tr("Squircle"), "round" to tr("Round")),
            current = config.tileShape,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("tile_shape", it)) }
        )

        TvSlider(
            title = tr("Dock tile size"),
            value = config.dockScale,
            min = 60, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_scale", it)) }
        )

        TvSlider(
            title = tr("Tile size"),
            value = config.tileScale,
            min = 60, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("tile_scale", it)) }
        )

        // --- 6. Focus & Animation Effects ---
        TvRadioGroup(
            title = tr("Focus"),
            options = listOf("lift" to tr("3D Lift"), "glow" to tr("Glow Effect"), "outline" to tr("Border Outline"), "none" to tr("None")),
            current = config.focus,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("focus", it)) }
        )

        TvSwitch(
            title = tr("Tilt"),
            subtitle = tr("The tile leans toward the move"),
            checked = config.tilt,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("tilt", it)) }
        )

        TvSwitch(
            title = tr("Keep the focused tile in place"),
            subtitle = tr("The row slides under it"),
            checked = config.pinFocus,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("pin_focus", it)) }
        )

        TvSwitch(
            title = tr("Start on the app I usually open"),
            subtitle = tr("Home opens with the focus on the app you open most at this time of day. The order stays."),
            checked = config.focusUsual,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("focus_usual", it)) }
        )

        TvSwitch(
            title = tr("Background takes the app's colour"),
            subtitle = tr("A tint of the focused app's colour"),
            checked = config.followColour,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("follow_colour", it)) }
        )
    }
}
