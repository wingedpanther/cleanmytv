package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

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
                        .semantics {
                            role = Role.RadioButton
                            selected = isSelected
                            contentDescription = "$label, ${if (isSelected) "selected" else "not selected"}"
                        }
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
