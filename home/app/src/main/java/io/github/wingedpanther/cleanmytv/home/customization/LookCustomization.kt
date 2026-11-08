package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
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
        0xFF4CAF50 to "Green",
        0xFF2196F3 to "Blue",
        0xFFFF9800 to "Orange",
        0xFFE91E63 to "Pink",
        0xFF9C27B0 to "Purple",
        0xFF00BCD4 to "Cyan"
    )

    fun checkAndOpenPicker() {
        showPickerModal = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Custom Wallpaper Picker Section ---
        Text("Custom Wallpaper", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (hasWallpaper) "Current wallpaper: Custom image active" else "Current wallpaper: Default theme background",
            color = if (hasWallpaper) Color(0xFF4CAF50) else MUTED_COLOR,
            fontSize = 13.sp
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                onClick = { checkAndOpenPicker() },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color.White,
                    focusedContentColor = CUSTOMIZATION_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
            ) {
                Text("Select Wallpaper from TV Storage", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
            }

            if (hasWallpaper) {
                Surface(
                    onClick = {
                        runCatching {
                            ConfigStore.wallpaper(ctx).delete()
                            ConfigStore.update(ctx, JSONObject().put("wallpaper_from", ""))
                            ConfigStore.wallpaperChanged()
                            wallpaperStatus = "Wallpaper removed"
                        }.onFailure { wallpaperStatus = "Error removing wallpaper: ${it.message}" }
                    },
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF882222)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text("Remove Wallpaper", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            }
        }

        if (wallpaperStatus.isNotEmpty()) {
            Text(wallpaperStatus, color = Color.White, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Accent Picker
        Text("Accent Color", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            hexAccents.forEach { (colorVal, name) ->
                val isSelected = config.accent == colorVal
                Surface(
                    onClick = { ConfigStore.update(ctx, JSONObject().put("accent", String.format("#%06X", (0xFFFFFF and colorVal.toInt())))) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(colorVal),
                        focusedContainerColor = Color(colorVal)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    modifier = Modifier
                        .height(36.dp)
                        .width(36.dp)
                ) {
                    if (isSelected) {
                        Text("✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        TvRadioGroup(
            title = "Tile Focus Effect",
            options = listOf("lift" to "3D Lift", "glow" to "Glow Effect", "border" to "Border Outline", "none" to "None"),
            current = config.focus,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("focus", it)) }
        )

        TvSlider(
            title = "Wallpaper Dimming Overlay",
            value = config.wallpaperDim,
            min = 0, max = 80, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("wallpaper_dim", it)) }
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
