package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.KeyService
import org.json.JSONObject

@Composable
fun RemoteCustomization(config: HomeConfig, ctx: Context) {
    val serviceActive = remember { KeyService.isEnabled(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Accessibility & Remote Service", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x11FFFFFF))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("KeyService Accessibility Status:", color = MUTED_COLOR, fontSize = 12.sp)
                Text(
                    text = if (serviceActive) "ACTIVE & INTERCEPTING KEYS" else "DISABLED IN SYSTEM SETTINGS",
                    color = if (serviceActive) Color(0xFF4CAF50) else Color(0xFFFFB23C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                onClick = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                },
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color(0x33FFFFFF),
                    focusedContainerColor = Color.White,
                    focusedContentColor = CUSTOMIZATION_BG
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Open Accessibility Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        TvSwitch(
            title = "Enable Remote Button Remapping",
            subtitle = "Intercept remote keys and Home button press",
            checked = config.buttonsOn,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("buttons_on", it)) }
        )
    }
}
