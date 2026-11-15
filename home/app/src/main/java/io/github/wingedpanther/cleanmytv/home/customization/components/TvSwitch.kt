package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

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
            .semantics {
                role = Role.Switch
                stateDescription = if (checked) "On" else "Off"
                contentDescription = "$title, ${if (checked) "On" else "Off"}"
            }
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
