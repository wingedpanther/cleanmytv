package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.Fire
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun VisualizerCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvSwitch(
            title = "Show Music Visualizer Bars",
            subtitle = "Displays animated audio spectrum bars when playing music",
            checked = config.visualiser,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("visualiser", it)) }
        )

        TvRadioGroup(
            title = "Visualizer Style",
            options = listOf("bars" to "Spectrum Bars", "mirror" to "Mirrored Bars", "wave" to "Waveform Line"),
            current = config.vizStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_style", it)) }
        )

        TvRadioGroup(
            title = "Visualizer Color",
            options = listOf("accent" to "Accent Color", "rainbow" to "Rainbow", "white" to "White"),
            current = config.vizColour,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_colour", it)) }
        )

        TvSlider(
            title = "Bar Height Scale",
            value = config.vizHeight,
            min = 4, max = 32, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_height", it)) }
        )

        TvSlider(
            title = "Opacity",
            value = config.vizOpacity,
            min = 10, max = 100, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_opacity", it)) }
        )

        TvSlider(
            title = "Audio Sensitivity",
            value = config.vizSensitivity,
            min = 10, max = 300, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_sensitivity", it)) }
        )
    }
}

@Composable
fun FireplaceCustomization(config: HomeConfig, ctx: Context) {
    val isReady = remember { Fire.ready(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Fireplace Media Status Card ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x11FFFFFF))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fireplace Storage Status:", color = MUTED_COLOR, fontSize = 12.sp)
                Text(
                    text = if (isReady) "MEDIA READY (VIDEO & CRACKLE INSTALLED)" else "NO MEDIA ON TV (UPLOAD FROM DESKTOP APP)",
                    color = if (isReady) Color(0xFF4CAF50) else Color(0xFFFFB23C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        TvSwitch(
            title = "Fireplace Home Wallpaper",
            subtitle = "Show live fireplace video behind Home screen",
            checked = config.fireWall,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("fire_wall", it)) }
        )

        TvSwitch(
            title = "Crackling Sound in Fullscreen",
            subtitle = "Play crackling audio during full screen fireplace",
            checked = config.fireSound,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("fire_sound", it)) }
        )

        TvSlider(
            title = "Volume",
            value = config.fireVolume,
            min = 0, max = 100, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("fire_volume", it)) }
        )

        TvSlider(
            title = "Auto-Off Timer",
            value = config.fireTimer,
            min = 0, max = 240, unit = " min (0=Off)",
            onChange = { ConfigStore.update(ctx, JSONObject().put("fire_timer", it)) }
        )
    }
}
