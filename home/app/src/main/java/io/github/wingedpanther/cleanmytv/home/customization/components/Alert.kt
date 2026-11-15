package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

enum class AlertType {
    INFO, SUCCESS, WARNING, ERROR
}

@Composable
fun Alert(
    text: String,
    modifier: Modifier = Modifier,
    type: AlertType = AlertType.INFO
) {
    val icon: ImageVector
    val iconTint: Color
    val bgColor: Color

    when (type) {
        AlertType.INFO -> {
            icon = Icons.Outlined.Info
            iconTint = Color(0xFFA8C7FA) // PRIMARY_BUTTON_COLOR
            bgColor = Color(0x18FFFFFF) // Shared semi-transparent background
        }
        AlertType.SUCCESS -> {
            icon = Icons.Outlined.CheckCircle
            iconTint = Color(0xFF4CAF50) // Green
            bgColor = Color(0xFF4CAF50).copy(alpha = 0.1f) // Green with 10% opacity
        }
        AlertType.WARNING -> {
            icon = Icons.Outlined.Warning
            iconTint = Color(0xFFFFA000) // Amber
            bgColor = Color(0x18FFFFFF)
        }
        AlertType.ERROR -> {
            icon = Icons.Outlined.ErrorOutline
            iconTint = Color(0xFFE53935) // Red
            bgColor = Color(0x18FFFFFF)
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = type.name,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            color = Color(0xFFB0BEC5), // MUTED_COLOR
            fontSize = 12.sp
        )
    }
}
