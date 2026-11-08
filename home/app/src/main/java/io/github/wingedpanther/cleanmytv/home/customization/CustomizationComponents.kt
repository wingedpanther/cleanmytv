package io.github.wingedpanther.cleanmytv.home.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

val CUSTOMIZATION_BG = Color(0xFF0B1D33)
val PANEL_BG = Color(0xFF1E2836)
val TEXT_COLOR = Color.White
val MUTED_COLOR = Color(0xFF9EAEC2)

@Composable
fun TvSwitch(
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
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
fun TvRadioGroup(
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
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
fun TvSlider(
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
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
fun <T : Any> ReorderableCheckList(
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
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
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
                            var upFocused by remember { mutableStateOf(false) }
                            var downFocused by remember { mutableStateOf(false) }

                            // Move Up Button
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
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                modifier = Modifier
                                    .padding(2.dp)
                                    .onFocusChanged { upFocused = it.isFocused }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconTint = if (!canMoveUp) {
                                        MUTED_COLOR.copy(alpha = 0.3f)
                                    } else if (upFocused) {
                                        CUSTOMIZATION_BG
                                    } else {
                                        Color.White
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowUpward,
                                        contentDescription = "Move Up",
                                        tint = iconTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Move Down Button
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
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
                                modifier = Modifier
                                    .padding(2.dp)
                                    .onFocusChanged { downFocused = it.isFocused }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val iconTint = if (!canMoveDown) {
                                        MUTED_COLOR.copy(alpha = 0.3f)
                                    } else if (downFocused) {
                                        CUSTOMIZATION_BG
                                    } else {
                                        Color.White
                                    }
                                    Icon(
                                        imageVector = Icons.Outlined.ArrowDownward,
                                        contentDescription = "Move Down",
                                        tint = iconTint,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
