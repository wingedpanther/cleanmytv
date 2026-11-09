package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONArray
import org.json.JSONObject

private val SECTIONS_ALL = listOf("nowplaying", "dock", "rows", "folders", "grid")
private val QUICK_ALL = listOf("customize", "settings", "panel", "themes", "inputs", "wifi", "bluetooth", "display", "apps", "sleep", "search", "music", "fireplace")

@Composable
fun LayoutCustomization(config: HomeConfig, ctx: Context) {
    var activeSectionsExpanded by remember { mutableStateOf(true) }
    var quickShortcutsExpanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Layout Mode & Top Space ---
        TvRadioGroup(
            title = "Launcher Layout",
            options = listOf("shelf" to "Shelf", "split" to "Split Pane", "rail" to "Vertical Rail"),
            current = config.layout,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("layout", it)) }
        )

        TvSlider(
            title = "Top Space Above Apps",
            value = config.topSpace,
            min = 0, max = 100, unit = " dp",
            onChange = { ConfigStore.update(ctx, JSONObject().put("top_space", it)) }
        )

        // --- Unified Info Helper Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x18FFFFFF))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Info",
                tint = PRIMARY_BUTTON_COLOR,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Check items to enable · Select handle with OK to pick · ↑↓ Move · OK Confirm · Back Cancel",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )
        }

        // --- Side-by-Side Collapsible Cards ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Active Sections & Order
            CollapsibleCard(
                title = "Active Sections & Order",
                expanded = activeSectionsExpanded,
                onToggle = { activeSectionsExpanded = !activeSectionsExpanded },
                modifier = Modifier.weight(1f)
            ) {
                ReorderableCheckList(
                    title = "",
                    subtitle = "",
                    allItems = SECTIONS_ALL,
                    activeItems = config.sections,
                    getLabel = { id ->
                        when (id) {
                            "nowplaying" -> "Now Playing / Widgets"
                            "dock" -> "Dock Row"
                            "rows" -> "App Rows & Categories"
                            "folders" -> "Folders & Collections"
                            "grid" -> "All Apps Grid"
                            else -> id.replaceFirstChar { it.uppercase() }
                        }
                    },
                    onUpdateActive = { newList ->
                        ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(newList)))
                    }
                )
            }

            // Right Column: Quick Settings Shortcuts
            CollapsibleCard(
                title = "Quick Settings Shortcuts",
                expanded = quickShortcutsExpanded,
                onToggle = { quickShortcutsExpanded = !quickShortcutsExpanded },
                modifier = Modifier.weight(1f)
            ) {
                ReorderableCheckList(
                    title = "",
                    subtitle = "",
                    allItems = QUICK_ALL,
                    activeItems = config.quick,
                    getLabel = { id -> id.replaceFirstChar { it.uppercase() } },
                    onUpdateActive = { newList ->
                        ConfigStore.update(ctx, JSONObject().put("quick", JSONArray(newList)))
                    }
                )
            }
        }
    }
}

@Composable
private fun CollapsibleCard(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isHeaderFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x11FFFFFF))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            onClick = onToggle,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x22FFFFFF),
                focusedContainerColor = Color.White,
                focusedContentColor = CUSTOMIZATION_BG
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isHeaderFocused = it.isFocused }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isHeaderFocused) CUSTOMIZATION_BG else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = if (isHeaderFocused) CUSTOMIZATION_BG else Color.White
                )
            }
        }

        if (expanded) {
            Column(modifier = Modifier.padding(top = 2.dp)) {
                content()
            }
        }
    }
}
