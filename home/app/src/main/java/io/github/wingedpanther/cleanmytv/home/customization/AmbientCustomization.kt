package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun VisualizerCustomization(config: HomeConfig, ctx: Context) {
    val isVisualizerActive = "visualizer" in config.sections

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = "Show Music Visualizer Section",
            subtitle = "Displays animated audio spectrum bars when playing music",
            checked = isVisualizerActive,
            onCheckedChange = { active ->
                val nextSections = if (active) {
                    if ("visualizer" in config.sections) config.sections else config.sections + "visualizer"
                } else {
                    config.sections - "visualizer"
                }
                ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(nextSections)))
            }
        )
    }
}

@Composable
fun FireplaceCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
