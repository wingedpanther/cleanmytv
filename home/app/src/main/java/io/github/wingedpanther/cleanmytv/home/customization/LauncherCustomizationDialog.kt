package io.github.wingedpanther.cleanmytv.home.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.TvApp

@Composable
fun LauncherCustomizationDialog(
    config: HomeConfig,
    apps: List<TvApp>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var selectedCategory by remember { mutableStateOf(CustomizationCategory.LAYOUT) }
    var inContentPane by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val firstFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { firstFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x73000000))
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && e.key == Key.Back) {
                        if (inContentPane) {
                            inContentPane = false
                            runCatching { firstFocusRequester.requestFocus() }
                            true
                        } else {
                            onDismiss()
                            true
                        }
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Launcher Customization",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CleanMyTV On-Device Customization Panel",
                            color = MUTED_COLOR,
                            fontSize = 13.sp
                        )
                    }

                    if (statusMessage.isNotEmpty()) {
                        Text(
                            text = statusMessage,
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Main 2-Pane Layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // --- Left Pane: Category Drawer ---
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .padding(end = 16.dp)
                    ) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(CustomizationCategory.values()) { index, cat ->
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
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // --- Right Pane: Customization Panel Content ---
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
                                CustomizationCategory.LAYOUT -> item {
                                    LayoutCustomization(config, ctx)
                                }
                                CustomizationCategory.LOOK -> item {
                                    LookCustomization(config, ctx)
                                }
                                CustomizationCategory.ICONS -> item {
                                    IconCustomization(config, ctx)
                                }
                                CustomizationCategory.WIDGETS -> item {
                                    WidgetCustomization(config, ctx)
                                }
                                CustomizationCategory.APPS -> item {
                                    AppCustomization(config, ctx, apps)
                                }
                                CustomizationCategory.VISUALIZER -> item {
                                    VisualizerCustomization(config, ctx)
                                }
                                CustomizationCategory.FIREPLACE -> item {
                                    FireplaceCustomization(config, ctx)
                                }
                                CustomizationCategory.REMOTE -> item {
                                    RemoteCustomization(config, ctx)
                                }
                                CustomizationCategory.SECURITY -> item {
                                    SecurityCustomization(config, ctx, apps)
                                }
                                CustomizationCategory.BACKUP -> item {
                                    BackupCustomization(config, ctx, onMessage = { statusMessage = it })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
