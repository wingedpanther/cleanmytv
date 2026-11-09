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
fun SetupCustomization(config: HomeConfig, ctx: Context) {
    val serviceActive = remember { KeyService.isEnabled(ctx) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Default Launcher Card ---
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
                Text("Default Launcher Status:", color = MUTED_COLOR, fontSize = 12.sp)
                Text(
                    text = "CLEANMYTV HOME ACTIVE",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            TvSecondaryButton(
                text = "Set Default Home App",
                onClick = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_HOME_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }.onFailure {
                        runCatching {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(intent)
                        }
                    }
                }
            )
        }

        // --- Accessibility KeyService Card ---
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

            TvSecondaryButton(
                text = "Open Accessibility Settings",
                onClick = {
                    runCatching {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }
                }
            )
        }

        TvSwitch(
            title = "Home Button Interception",
            subtitle = "Intercept Home remote key to bypass stock launcher re-launch lag",
            checked = config.buttonsOn || serviceActive,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("buttons_on", it)) }
        )
    }
}
