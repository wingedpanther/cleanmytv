package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
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
import org.json.JSONObject

@Composable
fun LookCustomization(config: HomeConfig, ctx: Context) {
    var wallpaperStatus by remember { mutableStateOf("") }
    var showPickerModal by remember { mutableStateOf(false) }
    val hasWallpaper = ConfigStore.wallpaper(ctx).exists()

    val hexAccents = listOf(
        0xFFFFFFFF.toInt() to "White",
        0xFF4CAF50.toInt() to "Green",
        0xFF2196F3.toInt() to "Blue",
        0xFFFF9800.toInt() to "Orange",
        0xFFE91E63.toInt() to "Pink",
        0xFF9C27B0.toInt() to "Purple",
        0xFF00BCD4.toInt() to "Cyan"
    )

    fun checkAndOpenPicker() {
        showPickerModal = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- 1. Custom Wallpaper Picker Section ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Custom Wallpaper", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (hasWallpaper) "Current wallpaper: Custom image active" else "Current wallpaper: Default theme background",
                color = if (hasWallpaper) Color(0xFF4CAF50) else MUTED_COLOR,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvPrimaryButton(
                    text = "Browse Wallpapers",
                    onClick = { checkAndOpenPicker() },
                    icon = { Icon(imageVector = Icons.Outlined.FolderOpen, contentDescription = "Browse", modifier = Modifier.size(16.dp)) }
                )

                if (hasWallpaper) {
                    TvDestructiveButton(
                        text = "Remove Wallpaper",
                        onClick = {
                            runCatching {
                                ConfigStore.wallpaper(ctx).delete()
                                ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
                                ConfigStore.wallpaperChanged()
                                wallpaperStatus = "Wallpaper removed"
                            }.onFailure { wallpaperStatus = "Error removing wallpaper: ${it.message}" }
                        },
                        icon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp)) }
                    )
                }
            }

            if (wallpaperStatus.isNotEmpty()) {
                Text(wallpaperStatus, color = Color.White, fontSize = 12.sp)
            }
        }

        // --- 2. Wallpaper Dimming Overlay ---
        TvSlider(
            title = "Wallpaper Dimming Overlay",
            value = config.wallpaperDim,
            min = 0, max = 80, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("wallpaper_dim", it)) }
        )

        // --- 3. Accent Color Section ---
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Accent Color", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                hexAccents.forEach { (colorVal, name) ->
                    val isSelected = (config.accent and 0xFFFFFFFFL) == (colorVal.toLong() and 0xFFFFFFFFL)
                    Surface(
                        onClick = { ConfigStore.update(ctx, JSONObject().put("accent", String.format("#%06X", (0xFFFFFF and colorVal)))) },
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(colorVal),
                            focusedContainerColor = Color(colorVal)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.25f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border.None,
                            focusedBorder = Border(
                                border = BorderStroke(3.dp, Color.White),
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
                                    contentDescription = "Selected Accent",
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
            title = "Icon Style",
            options = listOf("banner" to "App Banner", "colour" to "Vector Brand Color", "mono" to "Monochrome", "duo" to "Duotone"),
            current = config.iconStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("icon_style", it)) }
        )

        // Side-by-Side App Labels Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TvSwitch(
                title = "Show App Names",
                subtitle = "Display titles under tiles",
                checked = config.showNames,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("show_names", it)) },
                modifier = Modifier.weight(1f)
            )

            TvSwitch(
                title = "Name on Focus Only",
                subtitle = "Only show title for focused app",
                checked = config.nameFocus,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("name_focus", it)) },
                modifier = Modifier.weight(1f)
            )
        }

        // --- 5. Tile Shape & Sizes ---
        TvRadioGroup(
            title = "Tile Shape",
            options = listOf("wide" to "Wide (16:9)", "square" to "Square (1:1)", "squircle" to "Squircle", "round" to "Round"),
            current = config.tileShape,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("tile_shape", it)) }
        )

        TvSlider(
            title = "Dock Tile Size",
            value = config.dockScale,
            min = 80, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_scale", it)) }
        )

        TvSlider(
            title = "Grid Tile Size",
            value = config.tileScale,
            min = 80, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("tile_scale", it)) }
        )

        // --- 6. Focus & Animation Effects ---
        TvRadioGroup(
            title = "Tile Focus Effect",
            options = listOf("lift" to "3D Lift", "glow" to "Glow Effect", "border" to "Border Outline", "none" to "None"),
            current = config.focus,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("focus", it)) }
        )

        TvSwitch(
            title = "Tile Tilt Effect",
            subtitle = "Leans focus tile toward D-pad movement",
            checked = config.tilt,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("tilt", it)) }
        )

        TvSwitch(
            title = "Stationary Focus (Pin Focus)",
            subtitle = "Row slides under centered focused tile",
            checked = config.pinFocus,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("pin_focus", it)) }
        )

        TvSwitch(
            title = "Dynamic App Color Tint",
            subtitle = "Tints launcher background to focused app logo color",
            checked = config.followColour,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("follow_colour", it)) }
        )
    }

    if (showPickerModal) {
        WallpaperPickerModal(
            ctx = ctx,
            onDismiss = { showPickerModal = false },
            onSelected = { file ->
                runCatching {
                    file.copyTo(ConfigStore.wallpaper(ctx), overwrite = true)
                    ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
                    ConfigStore.wallpaperChanged()
                    wallpaperStatus = "Wallpaper applied: ${file.name}"
                }.onFailure { wallpaperStatus = "Error applying wallpaper: ${it.message}" }
                showPickerModal = false
            }
        )
    }
}
