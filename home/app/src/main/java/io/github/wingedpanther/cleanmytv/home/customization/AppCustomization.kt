package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.Lock
import io.github.wingedpanther.cleanmytv.home.PinPad
import io.github.wingedpanther.cleanmytv.home.TvApp
import io.github.wingedpanther.cleanmytv.home.tr
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AppCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
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
        apps.forEach { app ->
            if (app.pkg !in seen) orderedList.add(app)
        }
        orderedList
    }

    val hiddenSet = remember(config.hidden) { config.hidden.toSet() }
    // With App lock on, the PIN goes first before apps the owner hid come back or the order resets. Once per
    // visit; an editor behind the PIN has asked already.
    var pinOk by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val withPin = { action: () -> Unit ->
        if (pinOk || !config.lockOn || config.lockEditor || !Lock.hasPin(ctx)) action() else waiting = action
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSlider(
            title = tr("Apps in the dock"),
            value = config.dockSize,
            min = 0, max = 8, unit = if (config.dockSize == 0) tr(" apps (off)") else tr(" apps"),
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_size", it)) }
        )

        TvSlider(
            title = tr("Apps per row"),
            value = config.columns.coerceIn(3, 8),
            min = 3, max = 8, unit = tr(" apps"),
            onChange = { ConfigStore.update(ctx, JSONObject().put("columns", it)) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tr("App Order & Visibility ({n} apps)", "n" to apps.size), color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(tr("Use ← and → on card to reorder. Border outlines indicate Dock tiles."), color = MUTED_COLOR, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSecondaryButton(
                    text = tr("Reset Order"),
                    onClick = { withPin { ConfigStore.update(ctx, JSONObject().put("order", JSONArray())) } }
                )

                TvSecondaryButton(
                    text = tr("Show All"),
                    onClick = { withPin { ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray())) } }
                )
            }
        }

        // --- App Grid Cards (Fixed 5 Columns for Settings Page) ---
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
                                val hide: () -> Unit = {
                                    val nextHidden = hiddenSet.toMutableSet()
                                    if (isHidden) nextHidden.remove(app.pkg) else nextHidden.add(app.pkg)
                                    ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray(nextHidden.toList())))
                                }
                                if (isHidden) withPin(hide) else hide()
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

    waiting?.let { action ->
        Dialog(onDismissRequest = { waiting = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PinPad(tr("Enter the PIN to change hidden apps"), Lock.PANEL, Color(config.accent)) { waiting = null; pinOk = true; action() }
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
                .background(Color(0x22FFFFFF)),
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
                    Icon(
                        imageVector = Icons.Outlined.VisibilityOff,
                        contentDescription = tr("Hidden"),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
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
                onClick = { if (canMoveLeft) onMove(-1) },
                enabled = canMoveLeft,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x22FFFFFF),
                    focusedContainerColor = Color.White,
                    disabledContainerColor = Color(0x11FFFFFF)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None,
                    disabledBorder = Border.None
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .padding(end = 2.dp)
                    .focusProperties { canFocus = canMoveLeft }
                    .onFocusChanged { leftFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val iconTint = if (!canMoveLeft) {
                        MUTED_COLOR.copy(alpha = 0.3f)
                    } else if (leftFocused) {
                        CUSTOMIZATION_BG
                    } else {
                        Color.White
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = tr("Move Left"),
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None,
                    disabledBorder = Border.None
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1.8f)
                    .height(24.dp)
                    .padding(horizontal = 2.dp)
                    .onFocusChanged { hideFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val hideTextColor = if (hideFocused) CUSTOMIZATION_BG else Color.White
                    Text(
                        text = if (isHidden) tr("Show") else tr("Hide"),
                        color = hideTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Move Right Button
            Surface(
                onClick = { if (canMoveRight) onMove(1) },
                enabled = canMoveRight,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x22FFFFFF),
                    focusedContainerColor = Color.White,
                    disabledContainerColor = Color(0x11FFFFFF)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None,
                    disabledBorder = Border.None
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
                    .padding(start = 2.dp)
                    .focusProperties { canFocus = canMoveRight }
                    .onFocusChanged { rightFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val iconTint = if (!canMoveRight) {
                        MUTED_COLOR.copy(alpha = 0.3f)
                    } else if (rightFocused) {
                        CUSTOMIZATION_BG
                    } else {
                        Color.White
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = tr("Move Right"),
                        tint = iconTint,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
