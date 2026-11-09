package io.github.wingedpanther.cleanmytv.home.customization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.SwipeVertical
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
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
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

val CUSTOMIZATION_BG = Color(0xFF0B1D33)
val PANEL_BG = Color(0xFF1E2836)
val TEXT_COLOR = Color.White
val MUTED_COLOR = Color(0xFF9EAEC2)
val PRIMARY_BUTTON_COLOR = Color(0xFFA8C7FA)

enum class ListInteractionMode {
    NORMAL,
    REORDERING
}

@Composable
fun TvPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = PRIMARY_BUTTON_COLOR,
            focusedContainerColor = Color.White,
            contentColor = CUSTOMIZATION_BG,
            focusedContentColor = CUSTOMIZATION_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) icon()
            Text(
                text = text,
                color = CUSTOMIZATION_BG,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = CUSTOMIZATION_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) icon()
            Text(
                text = text,
                color = if (isFocused) CUSTOMIZATION_BG else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun TvDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFFD0342C),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color(0xFFD0342C)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) icon()
            Text(
                text = text,
                color = if (isFocused) Color(0xFFD0342C) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun TvSwitch(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { onCheckedChange(!checked) },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color.White,
            focusedContentColor = CUSTOMIZATION_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isFocused) CUSTOMIZATION_BG else TEXT_COLOR,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        color = if (isFocused) CUSTOMIZATION_BG.copy(alpha = 0.7f) else MUTED_COLOR,
                        fontSize = 12.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .width(44.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (checked) {
                            if (isFocused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR
                        } else {
                            if (isFocused) Color(0x330B1D33) else Color(0x33FFFFFF)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (checked) "ON" else "OFF",
                    color = if (checked) {
                        if (isFocused) Color.White else CUSTOMIZATION_BG
                    } else {
                        if (isFocused) CUSTOMIZATION_BG else MUTED_COLOR
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (value, label) ->
                val isSelected = value == current
                var isFocused by remember { mutableStateOf(false) }

                Surface(
                    onClick = { onSelect(value) },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (isSelected) PRIMARY_BUTTON_COLOR else Color(0x11FFFFFF),
                        focusedContainerColor = Color.White,
                        contentColor = if (isSelected) CUSTOMIZATION_BG else Color.White,
                        focusedContentColor = CUSTOMIZATION_BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isFocused || isSelected) CUSTOMIZATION_BG else MUTED_COLOR,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
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
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x1AFFFFFF),
            focusedContainerColor = Color.White,
            focusedContentColor = CUSTOMIZATION_BG
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown) {
                    when (e.key) {
                        Key.DirectionLeft -> {
                            if (value > min) {
                                onChange((value - 1).coerceAtLeast(min))
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (value < max) {
                                onChange((value + 1).coerceAtMost(max))
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = if (isFocused) CUSTOMIZATION_BG else TEXT_COLOR,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$value$unit",
                    color = if (isFocused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isFocused) Color(0x330B1D33) else Color(0x33FFFFFF)),
                contentAlignment = Alignment.CenterStart
            ) {
                val pct = ((value - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isFocused) CUSTOMIZATION_BG else Color.White)
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
    getId: (T) -> String = { it.toString() },
    getLabel: (T) -> String,
    onUpdateActive: (List<T>) -> Unit
) {
    // State machine as specified in docs/Order.md
    var mode by remember { mutableStateOf(ListInteractionMode.NORMAL) }
    var movingItemId by remember { mutableStateOf<String?>(null) }
    var originalOrder by remember { mutableStateOf<List<T>>(emptyList()) }
    var tempActiveItems by remember(activeItems) { mutableStateOf(activeItems) }

    LaunchedEffect(activeItems) {
        if (mode == ListInteractionMode.NORMAL) {
            tempActiveItems = activeItems
        }
    }

    val displayList = remember(tempActiveItems, allItems) {
        val activeSet = tempActiveItems.toSet()
        tempActiveItems + allItems.filter { it !in activeSet }
    }

    // Stable FocusRequesters keyed by item ID
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    displayList.forEach { item ->
        val id = getId(item)
        if (!focusRequesters.containsKey(id)) {
            focusRequesters[id] = FocusRequester()
        }
    }

    // ViewModel operations (as specified in docs/Order.md)
    fun moveItem(itemId: String, offset: Int) {
        val current = tempActiveItems.toMutableList()
        val fromIndex = current.indexOfFirst { getId(it) == itemId }
        if (fromIndex == -1) return
        val toIndex = (fromIndex + offset).coerceIn(0, current.size - 1)
        if (toIndex == fromIndex) return

        val moved = current.removeAt(fromIndex)
        current.add(toIndex, moved)
        tempActiveItems = current
    }

    fun startReordering(itemId: String) {
        originalOrder = tempActiveItems.toList()
        movingItemId = itemId
        mode = ListInteractionMode.REORDERING
    }

    fun confirmReordering() {
        val finalOrder = tempActiveItems.toList()
        movingItemId = null
        mode = ListInteractionMode.NORMAL
        onUpdateActive(finalOrder)
    }

    fun cancelReordering() {
        tempActiveItems = originalOrder
        movingItemId = null
        mode = ListInteractionMode.NORMAL
        onUpdateActive(originalOrder)
    }

    fun toggleSelection(item: T) {
        if (mode == ListInteractionMode.REORDERING) return
        val isActive = tempActiveItems.contains(item)
        val next = if (isActive) tempActiveItems - item else tempActiveItems + item
        tempActiveItems = next
        onUpdateActive(next)
    }

    // Keep focus locked on moving item's handle during reordering
    LaunchedEffect(movingItemId, tempActiveItems) {
        movingItemId?.let { id ->
            runCatching { focusRequesters[id]?.requestFocus() }
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (mode != ListInteractionMode.REORDERING) {
                return@onPreviewKeyEvent false
            }

            // Process only KeyUp events to prevent duplicate moves from held key repeat
            if (event.type != KeyEventType.KeyUp) {
                return@onPreviewKeyEvent true
            }

            val movingId = movingItemId ?: return@onPreviewKeyEvent false

            when (event.key) {
                Key.DirectionUp -> {
                    moveItem(movingId, -1)
                    true
                }
                Key.DirectionDown -> {
                    moveItem(movingId, 1)
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    confirmReordering()
                    true
                }
                Key.Back, Key.Escape -> {
                    cancelReordering()
                    true
                }
                else -> true // Consume all other keys in REORDERING mode
            }
        }
    ) {
        if (title.isNotEmpty()) {
            Text(title, color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        if (subtitle.isNotEmpty()) {
            Text(subtitle, color = MUTED_COLOR, fontSize = 12.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            displayList.forEach { item ->
                val id = getId(item)
                val isActive = tempActiveItems.contains(item)
                val isMoving = movingItemId == id
                val requester = focusRequesters[id] ?: remember { FocusRequester() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Checkbox + Item Title (Left Part) -> Conceptually: [Checkbox] [Item Title]
                    var isCardFocused by remember { mutableStateOf(false) }
                    Surface(
                        onClick = { toggleSelection(item) },
                        enabled = (mode == ListInteractionMode.NORMAL),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0x22FFFFFF),
                            focusedContainerColor = Color.White,
                            focusedContentColor = CUSTOMIZATION_BG,
                            disabledContainerColor = Color(0x22FFFFFF),
                            disabledContentColor = if (isActive) TEXT_COLOR else MUTED_COLOR.copy(alpha = 0.7f)
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties { canFocus = (mode == ListInteractionMode.NORMAL) }
                            .onFocusChanged { isCardFocused = it.isFocused }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Checkbox (Left)
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "Active",
                                    tint = if (isCardFocused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(
                                            1.5.dp,
                                            if (isCardFocused) CUSTOMIZATION_BG else MUTED_COLOR.copy(alpha = 0.4f),
                                            RoundedCornerShape(4.dp)
                                        )
                                )
                            }

                            // Item Title (Middle)
                            Text(
                                text = getLabel(item),
                                color = if (isCardFocused) CUSTOMIZATION_BG else (if (isActive) TEXT_COLOR else MUTED_COLOR.copy(alpha = 0.7f)),
                                fontSize = 13.sp,
                                fontWeight = if (isMoving || isActive) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 2. Dedicated Drag Handle (Right Part) -> Conceptually: [Checkbox] [Item Title] [Drag Handle]
                    if (isActive) {
                        var isHandleFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = {
                                if (isMoving) {
                                    confirmReordering()
                                } else {
                                    startReordering(id)
                                }
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isMoving) PRIMARY_BUTTON_COLOR else Color(0x22FFFFFF),
                                focusedContainerColor = if (isMoving) PRIMARY_BUTTON_COLOR else Color.White,
                                focusedContentColor = CUSTOMIZATION_BG,
                                disabledContainerColor = Color(0x22FFFFFF)
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            border = ClickableSurfaceDefaults.border(
                                border = Border.None,
                                focusedBorder = Border.None,
                                disabledBorder = Border.None
                            ),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                            modifier = Modifier
                                .focusRequester(requester)
                                .focusProperties { canFocus = (mode == ListInteractionMode.NORMAL || isMoving) }
                                .onFocusChanged { isHandleFocused = it.isFocused }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMoving) Icons.Outlined.SwapVert else Icons.Outlined.SwipeVertical,
                                    contentDescription = "Drag Handle",
                                    tint = if (isMoving || isHandleFocused) CUSTOMIZATION_BG else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
