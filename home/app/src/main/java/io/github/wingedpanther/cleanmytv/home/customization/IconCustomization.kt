package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONObject

@Composable
fun IconCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvRadioGroup(
            title = "Icon Style",
            options = listOf("banner" to "App Banner", "colour" to "Vector Brand Color", "mono" to "Monochrome", "duo" to "Duotone"),
            current = config.iconStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("icon_style", it)) }
        )

        TvRadioGroup(
            title = "Tile Shape",
            options = listOf("wide" to "Wide (16:9)", "square" to "Square (1:1)", "squircle" to "Squircle", "round" to "Round"),
            current = config.tileShape,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("tile_shape", it)) }
        )

        TvSwitch(
            title = "Show App Names",
            subtitle = "Display app titles under tiles",
            checked = config.showNames,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("show_names", it)) }
        )

        TvSwitch(
            title = "Name on Focus Only",
            subtitle = "Only show title for the currently focused app",
            checked = config.nameFocus,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("name_focus", it)) }
        )
    }
}
