package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
    var movingPkg by remember { mutableStateOf<String?>(null) }
    var pinOk by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf<(() -> Unit)?>(null) }
    val withPin = { action: () -> Unit ->
        if (pinOk || !config.lockOn || config.lockEditor || !Lock.hasPin(ctx)) action() else waiting = action
    }

    fun stepApp(pkg: String, offset: Int) {
        val currentOrderList = orderedApps.map { it.pkg }.toMutableList()
        val fromIdx = currentOrderList.indexOf(pkg)
        if (fromIdx == -1) return
        val toIdx = (fromIdx + offset).coerceIn(0, currentOrderList.size - 1)
        if (toIdx == fromIdx) return

        val moved = currentOrderList.removeAt(fromIdx)
        currentOrderList.add(toIdx, moved)
        withPin {
            ConfigStore.update(ctx, JSONObject().put("order", JSONArray(currentOrderList)))
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.onPreviewKeyEvent { event ->
            val moving = movingPkg ?: return@onPreviewKeyEvent false
            if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent true

            when (event.key) {
                Key.DirectionLeft -> {
                    stepApp(moving, -1)
                    true
                }
                Key.DirectionRight -> {
                    stepApp(moving, 1)
                    true
                }
                Key.DirectionUp -> {
                    stepApp(moving, -5)
                    true
                }
                Key.DirectionDown -> {
                    stepApp(moving, 5)
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                    movingPkg = null
                    true
                }
                else -> true
            }
        }
    ) {
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
                Text(
                    text = if (movingPkg != null) tr("MOVING ACTIVE: Use ← → ↑ ↓ to shift app position · OK to confirm") else tr("Select Move to activate D-pad grid movement · Select Hide to toggle visibility"),
                    color = if (movingPkg != null) PRIMARY_BUTTON_COLOR else MUTED_COLOR,
                    fontSize = 12.sp,
                    fontWeight = if (movingPkg != null) FontWeight.Bold else FontWeight.Normal
                )
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

        // --- 5-Column App Cards Grid with 2-Button Controls (Hide & Move) ---
        val appRows = remember(orderedApps) { orderedApps.chunked(5) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appRows.forEachIndexed { rowIndex, rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowApps.forEachIndexed { colIndex, app ->
                        val globalIndex = rowIndex * 5 + colIndex
                        val isHidden = app.pkg in hiddenSet
                        val isMoving = movingPkg == app.pkg

                        AppTile2ButtonCard(
                            app = app,
                            isHidden = isHidden,
                            isMoving = isMoving,
                            onToggleMove = {
                                movingPkg = if (isMoving) null else app.pkg
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
private fun AppTile2ButtonCard(
    app: TvApp,
    isHidden: Boolean,
    isMoving: Boolean,
    onToggleMove: () -> Unit,
    onToggleHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hideFocused by remember { mutableStateOf(false) }
    var moveFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isMoving) PRIMARY_BUTTON_COLOR.copy(alpha = 0.25f) else Color(0x1AFFFFFF))
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

        // --- 2-Button Control Row: [ Hide / Show ] and [ Move / Done ] ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Button 1: Hide / Show
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
                    .weight(1f)
                    .height(26.dp)
                    .onFocusChanged { hideFocused = it.isFocused }
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isHidden) tr("Show") else tr("Hide"),
                        color = if (hideFocused) CUSTOMIZATION_BG else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Button 2: Move / Done (Triggers 4-Way D-Pad Grid Movement)
            Surface(
                onClick = onToggleMove,
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isMoving) PRIMARY_BUTTON_COLOR else Color(0x22FFFFFF),
                    focusedContainerColor = if (isMoving) PRIMARY_BUTTON_COLOR else Color.White,
                    contentColor = if (isMoving) CUSTOMIZATION_BG else Color.White,
                    focusedContentColor = CUSTOMIZATION_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(
                    border = Border.None,
                    focusedBorder = Border.None,
                    disabledBorder = Border.None
                ),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                modifier = Modifier
                    .weight(1.2f)
                    .height(26.dp)
                    .onFocusChanged { moveFocused = it.isFocused }
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenWith,
                        contentDescription = tr("Move"),
                        tint = if (isMoving || moveFocused) CUSTOMIZATION_BG else Color.White,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(13.dp)
                    )
                    Text(
                        text = if (isMoving) tr("Done") else tr("Move"),
                        color = if (isMoving || moveFocused) CUSTOMIZATION_BG else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
