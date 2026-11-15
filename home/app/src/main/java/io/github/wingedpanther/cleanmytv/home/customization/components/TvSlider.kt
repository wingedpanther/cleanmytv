package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

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
    var localValue by remember(value) { mutableStateOf(value) }

    // Option A: Slider-level debounce.
    // Update local UI state instantly at 60fps without lag or disk I/O,
    // then commit to ConfigStore/HomeScreen 350ms after user stops tapping.
    LaunchedEffect(localValue) {
        if (localValue != value) {
            delay(350)
            onChange(localValue)
        }
    }

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
                            if (localValue > min) {
                                localValue = (localValue - 1).coerceAtLeast(min)
                            }
                            true // Always consume Left to prevent focus leaking
                        }
                        Key.DirectionRight -> {
                            if (localValue < max) {
                                localValue = (localValue + 1).coerceAtMost(max)
                            }
                            true // Always consume Right to prevent focus leaking
                        }
                        else -> false
                    }
                } else false
            }
            .semantics {
                contentDescription = "$title, value $localValue $unit"
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
                    text = "$localValue$unit",
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
                val pct = ((localValue - min).toFloat() / (max - min).toFloat()).coerceIn(0f, 1f)
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
