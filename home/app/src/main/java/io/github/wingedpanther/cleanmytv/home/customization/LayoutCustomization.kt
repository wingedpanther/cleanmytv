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

val SECTIONS_ALL = listOf("clock", "quick", "widgets", "dock", "grid", "visualizer")
val QUICK_ALL = listOf("customize", "settings", "panel", "themes", "inputs", "wifi", "bluetooth", "display", "apps", "sleep", "search", "music", "fireplace")

@Composable
fun LayoutCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvRadioGroup(
            title = "Launcher Layout",
            options = listOf("shelf" to "Shelf", "split" to "Split", "rail" to "Rail"),
            current = config.layout,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("layout", it)) }
        )

        ReorderableCheckList(
            title = "Active Sections & Order",
            subtitle = "Check sections to enable, use ← and → or ↑ and ↓ to reorder.",
            allItems = SECTIONS_ALL,
            activeItems = config.sections,
            getLabel = { id ->
                when (id) {
                    "clock" -> "Clock & Weather"
                    "quick" -> "Quick Settings Bar"
                    "widgets" -> "Widgets (Now Playing)"
                    "dock" -> "Dock Row"
                    "grid" -> "Apps Grid"
                    "visualizer" -> "Music Visualizer Bar"
                    else -> id
                }
            },
            onUpdateActive = { newList ->
                ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(newList)))
            }
        )

        ReorderableCheckList(
            title = "Quick Settings Shortcuts",
            subtitle = "Shortcuts displayed in the Quick Settings bar.",
            allItems = QUICK_ALL,
            activeItems = config.quick,
            getLabel = { id -> id.replaceFirstChar { it.uppercase() } },
            onUpdateActive = { newList ->
                ConfigStore.update(ctx, JSONObject().put("quick", JSONArray(newList)))
            }
        )

        TvSlider(
            title = "Top Space Above Apps",
            value = config.topSpace,
            min = 0, max = 100, unit = " dp",
            onChange = { ConfigStore.update(ctx, JSONObject().put("top_space", it)) }
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
    }
}
