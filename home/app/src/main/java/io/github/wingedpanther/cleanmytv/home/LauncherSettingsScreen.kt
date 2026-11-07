package io.github.wingedpanther.cleanmytv.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class SettingsCategory(val title: String) {
    LAYOUT("Layout & Rows"),
    LOOK("Look & Wallpaper"),
    ICONS("Icons & Tiles"),
    WIDGETS("Widgets & Header"),
    APPS("Apps & Organization"),
    VISUALIZER("Music Visualizer"),
    FIREPLACE("Fireplace"),
    REMOTE("Remote Buttons"),
    SECURITY("App Lock"),
    BACKUP("Backup & Restore")
}

private val SETTINGS_BG = Color(0x73000000)
private val PANEL_BG = Color(0x1AFFFFFF)
private val TEXT_COLOR = Color(0xFFFFFFFF)
private val MUTED_COLOR = Color(0xFFB0B0B0)
private val CUSTOMIZATION_BG = Color(0xFF0B1D33)

@Composable
fun LauncherSettingsScreen(
    config: HomeConfig,
    apps: List<TvApp>,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.LAYOUT) }
    var inContentPane by remember { mutableStateOf(false) }
    val firstFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var statusMessage by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SETTINGS_BG)
            .padding(24.dp)
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Back) {
                    if (inContentPane) {
                        inContentPane = false
                        true
                    } else {
                        onClose()
                        true
                    }
                } else false
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Launcher Customization",
                    color = TEXT_COLOR,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (statusMessage.isNotEmpty()) statusMessage else "Press Back to exit",
                    color = if (statusMessage.isNotEmpty()) Color.White else MUTED_COLOR,
                    fontSize = 14.sp
                )
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // --- Left Pane: Category Drawer ---
                Column(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .padding(end = 16.dp)
                ) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(SettingsCategory.values()) { index, cat ->
                            val isSelected = cat == selectedCategory
                            var isFocused by remember { mutableStateOf(false) }
                            Surface(
                                onClick = {
                                    selectedCategory = cat
                                    inContentPane = true
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color.White else Color.Transparent,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = CUSTOMIZATION_BG
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                            ) {
                                Text(
                                    text = cat.title,
                                    color = if (isFocused || isSelected) CUSTOMIZATION_BG else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                // --- Right Pane: Settings Panel Content ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PANEL_BG)
                        .padding(20.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(contentFocusRequester),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text(
                                text = selectedCategory.title,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        when (selectedCategory) {
                            SettingsCategory.LAYOUT -> item {
                                LayoutSettings(config, ctx)
                            }
                            SettingsCategory.LOOK -> item {
                                LookSettings(config, ctx)
                            }
                            SettingsCategory.ICONS -> item {
                                IconSettings(config, ctx)
                            }
                            SettingsCategory.WIDGETS -> item {
                                WidgetSettings(config, ctx)
                            }
                            SettingsCategory.APPS -> item {
                                AppSettings(config, ctx, apps)
                            }
                            SettingsCategory.VISUALIZER -> item {
                                VisualizerSettings(config, ctx)
                            }
                            SettingsCategory.FIREPLACE -> item {
                                FireplaceSettings(config, ctx)
                            }
                            SettingsCategory.REMOTE -> item {
                                RemoteSettings(config, ctx)
                            }
                            SettingsCategory.SECURITY -> item {
                                SecuritySettings(config, ctx)
                            }
                            SettingsCategory.BACKUP -> item {
                                BackupSettings(config, ctx, onMessage = { statusMessage = it })
                            }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================================================
// Sub-Panels & Controls
// =================================================================================================

@Composable
private fun LayoutSettings(config: HomeConfig, ctx: Context) {
    val allSections = listOf("nowplaying", "dock", "rows", "folders", "grid")
    val sectionLabels = mapOf(
        "nowplaying" to "Now playing",
        "dock" to "Dock",
        "rows" to "Rows",
        "folders" to "Folders",
        "grid" to "All apps"
    )

    val allQuick = listOf(
        "inputs", "wifi", "customize", "settings", "bluetooth",
        "display", "apps", "sleep", "search", "music", "panel", "themes", "fireplace"
    )
    val quickLabels = mapOf(
        "inputs" to "Inputs",
        "wifi" to "Wi-Fi",
        "customize" to "Customize",
        "settings" to "Settings",
        "bluetooth" to "Bluetooth",
        "display" to "Picture & sound",
        "apps" to "Apps",
        "sleep" to "Screensaver",
        "search" to "Search",
        "music" to "Music bars",
        "panel" to "Quick settings",
        "themes" to "Themes",
        "fireplace" to "Fireplace"
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvRadioGroup(
            title = "Layout Style",
            options = listOf("shelf" to "Shelf (Default)", "split" to "Split Sidebar", "rail" to "Vertical Rail"),
            current = config.layout,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("layout", it)) }
        )

        // --- Sections Reorderable List ---
        ReorderableCheckList(
            title = "Sections",
            subtitle = "Top to bottom. Untick one to hide it.",
            allItems = allSections,
            activeItems = config.sections,
            getLabel = { sectionLabels[it] ?: it },
            onUpdateActive = { next -> ConfigStore.update(ctx, JSONObject().put("sections", JSONArray(next))) }
        )

        // --- Quick Settings Reorderable List ---
        ReorderableCheckList(
            title = "Quick settings",
            subtitle = "Shortcuts in the top bar. Untick one to hide it.",
            allItems = allQuick,
            activeItems = config.quick,
            getLabel = { quickLabels[it] ?: it },
            onUpdateActive = { next -> ConfigStore.update(ctx, JSONObject().put("quick", JSONArray(next))) }
        )

        TvSlider(
            title = "Top Space (Shelf)",
            value = config.topSpace,
            min = 0, max = 40, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("top_space", it)) }
        )

        TvSlider(
            title = "Dock Scale",
            value = config.dockScale,
            min = 60, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_scale", it)) }
        )

        TvSlider(
            title = "Tile Scale",
            value = config.tileScale,
            min = 60, max = 150, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("tile_scale", it)) }
        )

        TvSlider(
            title = "Grid Columns",
            value = config.columns,
            min = 3, max = 8, unit = " cols",
            onChange = { ConfigStore.update(ctx, JSONObject().put("columns", it)) }
        )
    }
}

@Composable
private fun LookSettings(config: HomeConfig, ctx: Context) {
    val hexAccents = listOf(
        0xFF4CAF50 to "Green",
        0xFFFF3000 to "Red",
        0xFF2EC4B6 to "Teal",
        0xFFFFB23C to "Orange",
        0xFF7C5CFF to "Purple",
        0xFF3DDC84 to "Android",
        0xFFFFFFFF to "White"
    )

    var showPickerModal by remember { mutableStateOf(false) }
    var wallpaperStatus by remember { mutableStateOf("") }
    val hasWallpaper = remember { ConfigStore.wallpaper(ctx).exists() }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            showPickerModal = true
        } else {
            // Direct fallback to TV Settings App Permissions
            runCatching {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${ctx.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        }
    }

    val checkAndOpenPicker = {
        val hasPermission = permissionsToRequest.any { p ->
            ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
        }
        if (hasPermission) {
            showPickerModal = true
        } else {
            runCatching {
                permissionLauncher.launch(permissionsToRequest)
            }.onFailure {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:${ctx.packageName}")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
        }
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
                    shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                    modifier = Modifier
                        .height(36.dp)
                        .width(36.dp)
                        .then(if (isSelected) Modifier.border(3.dp, Color.White, CircleShape) else Modifier)
                ) {}
            }
        }

        TvRadioGroup(
            title = "Focus Effect",
            options = listOf("lift" to "Lift 3D", "glow" to "Glow", "outline" to "Outline", "none" to "None"),
            current = config.focus,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("focus", it)) }
        )

        TvSlider(
            title = "Wallpaper Dim",
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

@Composable
private fun IconSettings(config: HomeConfig, ctx: Context) {
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

@Composable
private fun WidgetSettings(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvRadioGroup(
            title = "Clock Format",
            options = listOf("time_date" to "Time & Date", "time" to "Time Only", "off" to "Off"),
            current = config.clock,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("clock", it)) }
        )

        TvRadioGroup(
            title = "Weather Unit",
            options = listOf("c" to "Celsius (°C)", "f" to "Fahrenheit (°F)"),
            current = config.weatherUnit,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("weather_unit", it)) }
        )

        TvSlider(
            title = "Idle Screen Timer",
            value = config.idleMinutes,
            min = 0, max = 60, unit = " min (0=Off)",
            onChange = { ConfigStore.update(ctx, JSONObject().put("idle_minutes", it)) }
        )
    }
}

@Composable
private fun AppSettings(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    val orderedApps = remember(config.order, apps) {
        val byPkg = apps.associateBy { it.pkg }
        val orderedList = mutableListOf<TvApp>()
        val seen = mutableSetOf<String>()

        config.order.forEach { pkg ->
            byPkg[pkg]?.let { app ->
                orderedList.add(app)
                seen.add(pkg)
            }
        }
        apps.filter { it.pkg !in seen }
            .sortedBy { it.label.lowercase() }
            .forEach { orderedList.add(it) }

        orderedList
    }

    val hiddenSet = remember(config.hidden) { config.hidden.toSet() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSlider(
            title = "Dock Row Size",
            value = config.dockSize,
            min = 0, max = 8, unit = " apps",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_size", it)) }
        )

        // --- Action Header Bar ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App Order & Visibility (${apps.size} apps)", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Use ← and → on card to reorder. Highlighted green tiles belong to the Dock.", color = MUTED_COLOR, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { ConfigStore.update(ctx, JSONObject().put("order", JSONArray())) },
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Text("Reset Order", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }

                Surface(
                    onClick = { ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray())) },
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Text("Show All", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }

        // --- App Grid Cards (5 Columns) ---
        val appRows = remember(orderedApps) { orderedApps.chunked(5) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appRows.forEachIndexed { rowIndex, rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowApps.forEachIndexed { colIndex, app ->
                        val globalIndex = rowIndex * 5 + colIndex
                        val isHidden = app.pkg in hiddenSet
                        val isDock = globalIndex < config.dockSize && !isHidden

                        AppTileControlCard(
                            app = app,
                            isDock = isDock,
                            isHidden = isHidden,
                            canMoveLeft = globalIndex > 0,
                            canMoveRight = globalIndex < orderedApps.size - 1,
                            onMove = { direction ->
                                val currentOrderList = orderedApps.map { it.pkg }.toMutableList()
                                val targetIdx = globalIndex + direction
                                if (targetIdx in 0 until currentOrderList.size) {
                                    val tmp = currentOrderList[globalIndex]
                                    currentOrderList[globalIndex] = currentOrderList[targetIdx]
                                    currentOrderList[targetIdx] = tmp
                                    ConfigStore.update(ctx, JSONObject().put("order", JSONArray(currentOrderList)))
                                }
                            },
                            onToggleHide = {
                                val nextHidden = hiddenSet.toMutableSet()
                                if (isHidden) nextHidden.remove(app.pkg) else nextHidden.add(app.pkg)
                                ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray(nextHidden.toList())))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(5 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTileControlCard(
    app: TvApp,
    isDock: Boolean,
    isHidden: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMove: (Int) -> Unit,
    onToggleHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var leftFocused by remember { mutableStateOf(false) }
    var hideFocused by remember { mutableStateOf(false) }
    var rightFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x1AFFFFFF))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- Tile Banner / Card ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x22FFFFFF))
                .then(if (isDock) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Image(
                bitmap = app.art,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit
            )

            if (isHidden) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xEE000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("HIDDEN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- App Name ---
        Text(
            text = app.label,
            color = if (isHidden) MUTED_COLOR.copy(alpha = 0.5f) else TEXT_COLOR,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // --- Controls Bar: ← | Hide/Show | → ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Move Left Button
            Surface(
                onClick = { onMove(-1) },
                enabled = canMoveLeft,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x22FFFFFF),
                    focusedContainerColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
                    .onFocusChanged { leftFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    val iconTint = if (!canMoveLeft) {
                        MUTED_COLOR.copy(alpha = 0.3f)
                    } else if (leftFocused) {
                        CUSTOMIZATION_BG
                    } else {
                        Color.White
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Move Left",
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Hide / Unhide Button
            Surface(
                onClick = onToggleHide,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isHidden) Color(0x66FFFFFF) else Color(0x22FFFFFF),
                    focusedContainerColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1.8f)
                    .padding(horizontal = 2.dp)
                    .onFocusChanged { hideFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    val hideTextColor = if (hideFocused) CUSTOMIZATION_BG else Color.White
                    Text(
                        text = if (isHidden) "Show" else "Hide",
                        color = hideTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Move Right Button
            Surface(
                onClick = { onMove(1) },
                enabled = canMoveRight,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x22FFFFFF),
                    focusedContainerColor = Color.White
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
                    .onFocusChanged { rightFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), contentAlignment = Alignment.Center) {
                    val iconTint = if (!canMoveRight) {
                        MUTED_COLOR.copy(alpha = 0.3f)
                    } else if (rightFocused) {
                        CUSTOMIZATION_BG
                    } else {
                        Color.White
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = "Move Right",
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VisualizerSettings(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = "Enable Music Visualizer",
            subtitle = "Audio reactive bars on home screen",
            checked = config.visualiser,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("visualiser", it)) }
        )

        TvRadioGroup(
            title = "Visualizer Style",
            options = listOf("bars" to "Bars", "mirror" to "Mirrored", "wave" to "Wave"),
            current = config.vizStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_style", it)) }
        )

        TvRadioGroup(
            title = "Color Style",
            options = listOf("accent" to "Accent Color", "rainbow" to "Rainbow", "white" to "White"),
            current = config.vizColour,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_colour", it)) }
        )

        TvSlider(
            title = "Bar Count",
            value = config.vizBars,
            min = 8, max = 64, unit = " bars",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_bars", it)) }
        )

        TvSlider(
            title = "Bar Height",
            value = config.vizHeight,
            min = 4, max = 40, unit = "% screen",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_height", it)) }
        )

        TvSlider(
            title = "Opacity",
            value = config.vizOpacity,
            min = 20, max = 100, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_opacity", it)) }
        )

        TvSlider(
            title = "Sensitivity",
            value = config.vizSensitivity,
            min = 25, max = 300, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_sensitivity", it)) }
        )
    }
}

@Composable
private fun FireplaceSettings(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = "Fireplace Background",
            subtitle = "Render live fireplace wallpaper behind Home",
            checked = config.fireWall,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("fire_wall", it)) }
        )

        TvSwitch(
            title = "Fire Audio Crackle",
            subtitle = "Enable fire crackle sound in screensaver",
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
            min = 0, max = 240, unit = " min",
            onChange = { ConfigStore.update(ctx, JSONObject().put("fire_timer", it)) }
        )
    }
}

@Composable
private fun RemoteSettings(config: HomeConfig, ctx: Context) {
    val serviceActive = remember { KeyService.isEnabled(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Accessibility & Remote Service", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.Bold)

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
                Text("KeyService Accessibility Status:", color = MUTED_COLOR, fontSize = 12.sp)
                Text(
                    text = if (serviceActive) "ACTIVE & INTERCEPTING KEYS" else "DISABLED IN SYSTEM SETTINGS",
                    color = if (serviceActive) Color(0xFF4CAF50) else Color(0xFFFFB23C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                onClick = {
                    runCatching {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color.White,
                    focusedContentColor = CUSTOMIZATION_BG
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Open Accessibility Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        TvSwitch(
            title = "Enable Remote Button Remapping",
            subtitle = "Intercept remote keys and Home button press",
            checked = config.buttonsOn,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("buttons_on", it)) }
        )
    }
}

@Composable
private fun SecuritySettings(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSwitch(
            title = "Enable App Lock",
            subtitle = "Require PIN to launch selected apps",
            checked = config.lockOn,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("lock_on", it)) }
        )

        TvSwitch(
            title = "Lock System Settings",
            subtitle = "Require PIN for Android Settings and Play Store",
            checked = config.lockSystem,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("lock_system", it)) }
        )

        TvRadioGroup(
            title = "Unlock Duration",
            options = listOf("sleep" to "Until Sleep", "15" to "15 Minutes", "always" to "Always Ask"),
            current = config.lockFor,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("lock_for", it)) }
        )

        TvRadioGroup(
            title = "Lock Icon Visibility",
            options = listOf("badge" to "Show Lock Badge", "hide" to "Hide Locked Apps"),
            current = config.lockShow,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("lock_show", it)) }
        )
    }
}

@Composable
private fun BackupSettings(config: HomeConfig, ctx: Context, onMessage: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Export Configuration", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Saves your complete layout, colors, and settings to TV Downloads folder (/sdcard/Download/cleanmytv-config.json).", color = MUTED_COLOR, fontSize = 13.sp)

        Surface(
            onClick = {
                runCatching {
                    val jsonStr = config.toJson().toString(2)
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadDir.mkdirs()
                    val targetFile = File(downloadDir, "cleanmytv-config.json")
                    targetFile.writeText(jsonStr)
                    onMessage("Exported to Download/cleanmytv-config.json")
                }.onFailure { onMessage("Export failed: ${it.message}") }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Color.White,
                focusedContentColor = CUSTOMIZATION_BG
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text("Export Config to File", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Import Configuration", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Loads and applies settings from Download/cleanmytv-config.json.", color = MUTED_COLOR, fontSize = 13.sp)

        Surface(
            onClick = {
                runCatching {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadDir, "cleanmytv-config.json")
                    if (!targetFile.exists()) {
                        onMessage("File Download/cleanmytv-config.json not found")
                        return@Surface
                    }
                    val jsonStr = targetFile.readText()
                    ConfigStore.update(ctx, JSONObject(jsonStr))
                    onMessage("Settings restored successfully!")
                }.onFailure { onMessage("Import failed: ${it.message}") }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Color.White,
                focusedContentColor = CUSTOMIZATION_BG
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text("Import Config from File", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

// =================================================================================================
// Reusable D-Pad Components
// =================================================================================================

@Composable
private fun TvSlider(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    unit: String = "",
    onChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = TEXT_COLOR, fontSize = 14.sp)
            Text("$value$unit", color = MUTED_COLOR, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        Surface(
            onClick = {},
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x22FFFFFF),
                focusedContainerColor = Color(0x44FFFFFF)
            ),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown) {
                        when (e.key) {
                            Key.DirectionLeft -> {
                                val next = (value - 1).coerceAtLeast(min)
                                if (next != value) onChange(next)
                                true
                            }
                            Key.DirectionRight -> {
                                val next = (value + 1).coerceAtMost(max)
                                if (next != value) onChange(next)
                                true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val pct = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White)
                )
            }
        }
    }
}

@Composable
private fun TvRadioGroup(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = TEXT_COLOR, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (key, label) ->
                val isSelected = current == key
                var isFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = { onSelect(key) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) Color.White else Color(0x11FFFFFF),
                        focusedContainerColor = Color.White,
                        focusedContentColor = CUSTOMIZATION_BG
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier.onFocusChanged { isFocused = it.isFocused }
                ) {
                    Text(
                        text = label,
                        color = if (isFocused || isSelected) CUSTOMIZATION_BG else MUTED_COLOR,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x11FFFFFF),
            focusedContainerColor = Color(0x33FFFFFF)
        ),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TEXT_COLOR, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) {
                    Text(subtitle, color = MUTED_COLOR, fontSize = 12.sp)
                }
            }
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (checked) Color(0xFF4CAF50) else Color(0xFF888888),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun <T : Any> ReorderableCheckList(
    title: String,
    subtitle: String,
    allItems: List<T>,
    activeItems: List<T>,
    getLabel: (T) -> String,
    onUpdateActive: (List<T>) -> Unit
) {
    val displayList = remember(activeItems, allItems) {
        val activeSet = activeItems.toSet()
        activeItems + allItems.filter { it !in activeSet }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MUTED_COLOR, fontSize = 12.sp)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            displayList.forEach { item ->
                val isActive = activeItems.contains(item)
                val activeIndex = activeItems.indexOf(item)
                val canMoveUp = isActive && activeIndex > 0
                val canMoveDown = isActive && activeIndex >= 0 && activeIndex < activeItems.size - 1

                Surface(
                    onClick = {
                        val next = if (isActive) {
                            activeItems - item
                        } else {
                            activeItems + item
                        }
                        onUpdateActive(next)
                    },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isActive) Color(0x22FFFFFF) else Color(0x0AFFFFFF),
                        focusedContainerColor = Color(0x44FFFFFF)
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown) {
                                when (e.key) {
                                    Key.DirectionLeft -> {
                                        if (canMoveUp) {
                                            val next = activeItems.toMutableList()
                                            val tmp = next[activeIndex]
                                            next[activeIndex] = next[activeIndex - 1]
                                            next[activeIndex - 1] = tmp
                                            onUpdateActive(next)
                                            true
                                        } else false
                                    }
                                    Key.DirectionRight -> {
                                        if (canMoveDown) {
                                            val next = activeItems.toMutableList()
                                            val tmp = next[activeIndex]
                                            next[activeIndex] = next[activeIndex + 1]
                                            next[activeIndex + 1] = tmp
                                            onUpdateActive(next)
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Checkbox + Label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isActive) "[✓]" else "[  ]",
                                color = if (isActive) Color(0xFF4CAF50) else MUTED_COLOR,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getLabel(item),
                                color = if (isActive) TEXT_COLOR else MUTED_COLOR.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        // Position arrows Up / Down
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                onClick = {
                                    if (canMoveUp) {
                                        val next = activeItems.toMutableList()
                                        val tmp = next[activeIndex]
                                        next[activeIndex] = next[activeIndex - 1]
                                        next[activeIndex - 1] = tmp
                                        onUpdateActive(next)
                                    }
                                },
                                enabled = canMoveUp,
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = Color.White
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "↑",
                                    color = if (canMoveUp) TEXT_COLOR else MUTED_COLOR.copy(alpha = 0.3f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }

                            Surface(
                                onClick = {
                                    if (canMoveDown) {
                                        val next = activeItems.toMutableList()
                                        val tmp = next[activeIndex]
                                        next[activeIndex] = next[activeIndex + 1]
                                        next[activeIndex + 1] = tmp
                                        onUpdateActive(next)
                                    }
                                },
                                enabled = canMoveDown,
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = Color.White
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "↓",
                                    color = if (canMoveDown) TEXT_COLOR else MUTED_COLOR.copy(alpha = 0.3f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallpaperPickerModal(
    ctx: Context,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit
) {
    val imageFiles = remember {
        val found = mutableListOf<File>()
        val validExts = setOf("jpg", "jpeg", "png", "webp", "bmp")

        // 1. Query Android MediaStore
        runCatching {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                if (dataIdx >= 0) {
                    while (c.moveToNext()) {
                        val path = c.getString(dataIdx)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.isFile && f.length() > 0) found.add(f)
                        }
                    }
                }
            }
        }

        // 2. Recursive filesystem scan across common storage directories
        val scanRoots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStorageDirectory(),
            File("/sdcard"),
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            File("/sdcard/DCIM"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Pictures"),
            File("/storage/emulated/0")
        )

        scanRoots.forEach { root ->
            if (root.exists() && root.isDirectory) {
                runCatching {
                    root.walkTopDown().maxDepth(4).filter { f ->
                        f.isFile && f.extension.lowercase() in validExts && f.length() > 0
                    }.forEach { found.add(it) }
                }
            }
        }

        found.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222823))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Wallpaper Image", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                if (imageFiles.isEmpty()) {
                    Text(
                        text = "No image files (.jpg, .png, .webp) found on TV storage.\n\nPlace an image file in the Download or Pictures folder on your TV. If storage access was blocked, click below to allow Storage permission in TV Settings.",
                        color = MUTED_COLOR,
                        fontSize = 13.sp
                    )

                    Surface(
                        onClick = {
                            runCatching {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.parse("package:${ctx.packageName}")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            }
                        },
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF7C5CFF)),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Open TV Settings > Permissions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                } else {
                    Text(
                        text = "Found ${imageFiles.size} image(s) on TV storage:",
                        color = MUTED_COLOR,
                        fontSize = 12.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(imageFiles) { file ->
                            val sizeKb = file.length() / 1024
                            val parentName = file.parentFile?.name ?: ""
                            Surface(
                                onClick = { onSelected(file) },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = Color(0x66FFFFFF)
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (parentName.isNotEmpty()) {
                                            Text("in $parentName/", color = MUTED_COLOR, fontSize = 11.sp)
                                        }
                                    }
                                    Text("${sizeKb} KB", color = MUTED_COLOR, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Surface(
                    onClick = onDismiss,
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel", color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
    }
}
