package io.github.wingedpanther.cleanmytv.home.customization

import androidx.compose.material.icons.outlined.Search

import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.tv.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.delay

@Composable
fun CustomizationDialog(
    config: HomeConfig,
    apps: List<TvApp>,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf(CustomizationCategory.LAYOUT) }
    var inContentPane by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    // A new search remounts the section, so its controls look for the setting even when the section is already open.
    var found by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    val categoryFocusRequesters = remember {
        CustomizationCategory.values().associateWith { FocusRequester() }
    }
    val contentFocusRequester = remember { FocusRequester() }
    val headerBackFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { categoryFocusRequesters[CustomizationCategory.LAYOUT]?.requestFocus() }
    }

    LaunchedEffect(inContentPane, selectedCategory) {
        if (inContentPane) {
            delay(150) // Ensure Right Pane is laid out and AnimatedVisibility is mounted
            runCatching {
                // Request focus directly on the content root wrapper.
                // The focusGroup modifier handles automatically delegating to the true first actionable child.
                contentFocusRequester.requestFocus()
            }
        } else {
            delay(50) // Ensure the Left Menu is fully mounted in the DOM before requesting focus
            runCatching { categoryFocusRequesters[selectedCategory]?.requestFocus() }
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        if (inContentPane) {
            inContentPane = false
        } else {
            onDismiss()
        }
    }

    // Direct Full-Screen Overlay with 7% transparent PANEL_BG
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PANEL_BG.copy(alpha = 0.93f))
            .onPreviewKeyEvent { e ->
                if (e.key == Key.Back) {
                    if (e.type == KeyEventType.KeyDown) {
                        if (inContentPane) {
                            inContentPane = false
                        } else {
                            onDismiss()
                        }
                    }
                    true // Consume both KeyDown and KeyUp for Back
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
                Row(
                    modifier = Modifier.height(40.dp), // Prevents height fluctuation
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedVisibility(
                        visible = inContentPane,
                        enter = fadeIn(animationSpec = tween(250)) + expandHorizontally(animationSpec = tween(250)) + slideInHorizontally(initialOffsetX = { -it / 2 }),
                        exit = fadeOut(animationSpec = tween(200)) + shrinkHorizontally(animationSpec = tween(200)) + slideOutHorizontally(targetOffsetX = { -it / 2 })
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = {
                                    inContentPane = false
                                },
                                modifier = Modifier.focusRequester(headerBackFocusRequester),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0x33FFFFFF),
                                    focusedContainerColor = Color.White,
                                    contentColor = Color.White,
                                    focusedContentColor = CUSTOMIZATION_BG
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = tr("Back"),
                                        modifier = Modifier.padding(end = 6.dp).size(18.dp)
                                    )
                                    Text(
                                        text = tr("Categories"),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // Moved the spacing inside the animated layout so it shrinks to 0!
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                        }
                    }

                    AnimatedContent(
                        targetState = if (inContentPane) selectedCategory.label else tr("Customize Home"),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + slideInVertically { -it / 2 }) togetherWith
                            (fadeOut(animationSpec = tween(180)) + slideOutVertically { it / 2 }) using
                            androidx.compose.animation.SizeTransform(clip = false)
                        },
                        label = "headerTitle"
                    ) { title ->
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(
                        visible = inContentPane,
                        enter = fadeIn(animationSpec = tween(250)),
                        exit = fadeOut(animationSpec = tween(200))
                    ) {
                        Alert(text = tr("Press BACK to go back"))
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                val totalWidth = maxWidth
                val drawerWidth = 280.dp

                // Slide left and right horizontally without resizing right pane width
                val slideOffset by animateDpAsState(
                    targetValue = if (inContentPane) -drawerWidth else 0.dp,
                    animationSpec = tween(320, easing = FastOutSlowInEasing),
                    label = "slideOffset"
                )

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        // Ensure the row is wide enough to hold both the drawer and the fixed-width right panel
                        .width(totalWidth + drawerWidth)
                        .offset(x = slideOffset)
                ) {
                    // --- Left Pane: Category Drawer ---
                    if (!inContentPane || slideOffset > -drawerWidth) {
                        Box(
                            modifier = Modifier
                                .width(drawerWidth)
                                .fillMaxHeight()
                                .padding(end = 16.dp)
                        ) {
                            // User-requested Grey Background Box for the Left Menu without inner padding
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    // Above the sections: finds a setting by name or by the words people use for it.
                                    item {
                                        var focused by remember { mutableStateOf(false) }
                                        Surface(
                                            onClick = { searching = true },
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = Color.White.copy(alpha = 0.08f),
                                                focusedContainerColor = Color.White,
                                                focusedContentColor = CUSTOMIZATION_BG
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).onFocusChanged { focused = it.isFocused }
                                        ) {
                                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Search, contentDescription = null, tint = if (focused) CUSTOMIZATION_BG else MUTED_COLOR, modifier = Modifier.size(18.dp))
                                                Text(tr("Search settings"), color = if (focused) CUSTOMIZATION_BG else MUTED_COLOR, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
                                            }
                                        }
                                    }
                                    itemsIndexed(CustomizationCategory.values()) { _, cat ->
                                        val isSelected = cat == selectedCategory
                                        var isFocused by remember { mutableStateOf(false) }
                                        Surface(
                                            onClick = {
                                                selectedCategory = cat
                                                inContentPane = true
                                            },
                                            colors = ClickableSurfaceDefaults.colors(
                                                containerColor = if (isSelected) PRIMARY_BUTTON_COLOR else Color.Transparent,
                                                focusedContainerColor = Color.White,
                                                focusedContentColor = CUSTOMIZATION_BG,
                                                contentColor = if (isSelected) Color.Black else Color.White
                                            ),
                                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(categoryFocusRequesters[cat]!!)
                                                .onFocusChanged { state ->
                                                    isFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        selectedCategory = cat // Instant live preview on focus!
                                                    }
                                                }
                                                .onPreviewKeyEvent { e ->
                                                    if (e.type == KeyEventType.KeyDown && 
                                                        (e.key == Key.DirectionRight || e.key == Key.DirectionCenter || e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                                                        selectedCategory = cat
                                                        inContentPane = true
                                                        true
                                                    } else false
                                                }
                                        ) {
                                            Text(
                                                text = cat.label,
                                                color = if (isFocused) CUSTOMIZATION_BG else if (isSelected) Color.Black else Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Completely removed from the DOM! We just leave an empty spacer of the exact same width 
                        // so the Right Pane doesn't instantly snap/jump to the left.
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(drawerWidth))
                    }

                    // --- Right Pane: Settings Panel Content ---
                    // Hardcoded to fixed width, simply moving left without expanding!
                    Box(
                        modifier = Modifier
                            .width(totalWidth - drawerWidth)
                            .fillMaxHeight()
                    ) {
                        // Use key(selectedCategory) instead of AnimatedContent!
                        // This forcefully destroys the old view and scroll state, resolving random focus jumps!
                        key(selectedCategory, found) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Box(modifier = Modifier.focusGroup().focusRequester(contentFocusRequester)) {
                                        when (selectedCategory) {
                                            CustomizationCategory.LAYOUT -> LayoutCustomization(config, ctx)
                                            CustomizationCategory.LOOK -> LookCustomization(config, ctx)
                                            CustomizationCategory.WIDGETS -> WidgetCustomization(config, ctx)
                                            CustomizationCategory.APPS -> AppCustomization(config, ctx, apps)
                                            CustomizationCategory.VISUALIZER -> VisualizerCustomization(config, ctx)
                                            CustomizationCategory.FIREPLACE -> FireplaceCustomization(config, ctx)
                                            CustomizationCategory.ROWS -> RowsCustomization(config, ctx, apps)
                                            CustomizationCategory.THEMES -> ThemesCustomization(config, ctx)
                                            CustomizationCategory.BUTTONS -> ButtonsCustomization(config, ctx, apps)
                                            CustomizationCategory.LOCK -> LockCustomization(config, ctx, apps)
                                            CustomizationCategory.MORE -> MoreCustomization(config, ctx)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (searching) SettingsSearchDialog(onDismiss = { searching = false }) { f ->
        searching = false
        Find.target = tr(f.name)
        found++
        selectedCategory = f.where
        inContentPane = true
    }
}
