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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import io.github.wingedpanther.cleanmytv.home.WatchNext
import io.github.wingedpanther.cleanmytv.home.canOpen
import io.github.wingedpanther.cleanmytv.home.quickActions
import io.github.wingedpanther.cleanmytv.home.tr
import io.github.wingedpanther.cleanmytv.home.tvInputs
import org.json.JSONArray
import org.json.JSONObject

private val SECTIONS_ALL = listOf("nowplaying", "recent", "screentime", "dock", "watchnext", "rows", "folders", "grid")
private val QUICK_ALL = listOf("inputs", "wifi", "bluetooth", "display", "apps", "sleep", "search", "music", "panel", "themes", "fireplace", "memory", "timer", "screentime", "guest", "find", "allapps", "customize", "settings")

@Composable
fun LayoutCustomization(config: HomeConfig, ctx: Context) {
    var activeSectionsExpanded by remember { mutableStateOf(true) }
    var quickShortcutsExpanded by remember { mutableStateOf(true) }
    // Looked at again when the editor comes back from Android's permission question.
    var watchAllowed by remember { mutableStateOf(WatchNext.allowed(ctx)) }
    val owner = ctx as? androidx.activity.ComponentActivity
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) watchAllowed = WatchNext.allowed(ctx) }
        owner?.lifecycle?.addObserver(obs)
        onDispose { owner?.lifecycle?.removeObserver(obs) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Layout Mode & Top Space ---
        TvRadioGroup(
            title = tr("Layout"),
            options = listOf("shelf" to tr("Shelf"), "split" to tr("Split"), "rail" to tr("Rail"), "bau" to "Bauhaus", "glass" to tr("Glass"), "teletext" to "Teletext"),
            current = config.layout,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("layout", it)) }
        )

        if (config.layout == "glass") {
            TvRadioGroup(
                title = tr("Backdrop"),
                options = listOf("slate" to tr("Glass slate"), "wallpaper" to tr("My wallpaper")),
                current = config.glassBackdrop,
                onSelect = { ConfigStore.update(ctx, JSONObject().put("glass_backdrop", it)) }
            )
            TvSwitch(
                title = tr("Big app name"),
                subtitle = tr("Off: only things to continue show up there"),
                checked = config.glassHeadline,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("glass_headline", it)) }
            )
            TvSwitch(
                title = tr("Banners in the Glass dock"),
                subtitle = tr("Off: app names on glass, white when focused"),
                checked = config.glassBanners,
                onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("glass_banners", it)) }
            )
        }

        // Glass and Teletext place their own rows, so they leave out the space and these sections (W5).
        val simple = config.layout == "glass" || config.layout == "teletext"
        if (!simple) TvSlider(
            title = tr("Space above the apps"),
            value = config.topSpace,
            min = 0, max = 80, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("top_space", it)) }
        )
        if (config.layout == "shelf") TvSlider(
            title = tr("Glass behind the dock"),
            value = config.dockGlass,
            min = 0, max = 60, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_glass", it)) }
        )

        // --- Unified Info Helper Bar ---
        Alert(
            text = tr("Check items to enable · Select handle with OK to pick · ↑↓ Move · OK Confirm · Back Cancel"),
            modifier = Modifier.fillMaxWidth()
        )

        // --- Side-by-Side Collapsible Cards ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Active Sections & Order
            CollapsibleCard(
                title = tr("Sections"),
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
                        val name = when (id) {
                            "nowplaying" -> tr("Now playing")
                            "recent" -> tr("Recently used")
                            "screentime" -> tr("Screen time")
                            "dock" -> tr("Dock")
                            "watchnext" -> tr("Continue watching")
                            "rows" -> tr("Rows")
                            "folders" -> tr("Folders")
                            "grid" -> config.names["all_apps"]?.ifEmpty { null } ?: tr("All apps")
                            else -> id.replaceFirstChar { it.uppercase() }
                        }
                        if (simple && id in setOf("recent", "rows", "folders", "grid")) tr("{name}, not used by this layout", "name" to name) else name
                    },
                    onUpdateActive = { newList ->
                        val turnedOn = "watchnext" in newList && "watchnext" !in config.sections
                        ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(newList)))
                        nowPlayingAccess(ctx, "nowplaying" in newList)
                        if (turnedOn && !watchAllowed) askWatchNext(ctx)
                    }
                )
                // The desktop app grants this when it installs Home; a Home that updated itself asks here.
                if ("watchnext" in config.sections && !watchAllowed) {
                    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Alert(tr("Continue watching needs your OK to see what you're watching in other apps."), Modifier.fillMaxWidth(), AlertType.WARNING)
                        TvPrimaryButton(tr("Allow"), onClick = { askWatchNext(ctx) })
                    }
                }
            }

            // Right Column: Quick Settings Shortcuts
            CollapsibleCard(
                title = tr("Top bar icons"),
                expanded = quickShortcutsExpanded,
                onToggle = { quickShortcutsExpanded = !quickShortcutsExpanded },
                modifier = Modifier.weight(1f)
            ) {
                ReorderableCheckList(
                    title = "",
                    subtitle = "",
                    // App shortcuts the owner added from the desktop app stay in the list, so editing here keeps them.
                    allItems = QUICK_ALL.filter { it in config.quick || canShow(ctx, it) } + config.quick.filter { HomeConfig.quickApp(it) != null },
                    activeItems = config.quick,
                    getLabel = { id -> quickLabel(ctx, config, id) },
                    onUpdateActive = { newList ->
                        ConfigStore.update(ctx, JSONObject().put("quick", JSONArray(newList)))
                    }
                )
            }
        }
    }
}

/** Android's own question for Continue watching's permission, over the editor. */
private fun askWatchNext(ctx: Context) {
    (ctx as? android.app.Activity)?.requestPermissions(arrayOf(WatchNext.PERMISSION), 1)
}

/** Home's own names for the top bar icons, or the app's name for an app shortcut. */
private fun quickLabel(ctx: Context, config: HomeConfig, id: String): String {
    HomeConfig.quickApp(id)?.let { pkg ->
        return runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
    }
    return config.names[id] ?: quickActions()[id]?.label ?: id
}

/** Leaves out icons this TV can't open, as the top bar does (Inputs without inputs, Clean memory on Android 14). */
private fun canShow(ctx: Context, id: String): Boolean = when (id) {
    "customize" -> true
    else -> quickActions()[id]?.let { canOpen(ctx, it, tvInputs(ctx).isNotEmpty()) } ?: false
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
                    contentDescription = if (expanded) tr("Collapse") else tr("Expand"),
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
