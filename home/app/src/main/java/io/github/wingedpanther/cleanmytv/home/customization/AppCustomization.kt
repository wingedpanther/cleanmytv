package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.TvApp
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSlider(
            title = "Dock Size (First N Apps)",
            value = config.dockSize,
            min = 1, max = 20, unit = " apps",
            onChange = { ConfigStore.update(ctx, JSONObject().put("dock_size", it)) }
        )

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
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp))
                ) {
                    Text("Reset Order", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }

                Surface(
                    onClick = { ConfigStore.update(ctx, JSONObject().put("hidden", JSONArray())) },
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
