package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONObject
import java.io.File

@Composable
fun BackupCustomization(config: HomeConfig, ctx: Context, onMessage: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Export Configuration", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Saves your complete layout, colors, and settings to TV Downloads folder (/sdcard/Download/cleanmytv-config.json).", color = MUTED_COLOR, fontSize = 13.sp)

        Surface(
            onClick = {
                runCatching {
                    val jsonStr = config.toJson().toString(2)
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadDir.mkdirs()
                    val targetFile = File(downloadDir, "cleanmytv-config.json")
                    targetFile.writeText(jsonStr)
                    onMessage("Exported to Download/cleanmytv-config.json")
                }.onFailure { onMessage("Export failed: ${it.message}") }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Color.White,
                focusedContentColor = CUSTOMIZATION_BG
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text("Export Config to File", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("Import Configuration", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Loads and applies settings from Download/cleanmytv-config.json.", color = MUTED_COLOR, fontSize = 13.sp)

        Surface(
            onClick = {
                runCatching {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadDir, "cleanmytv-config.json")
                    if (!targetFile.exists()) {
                        onMessage("File Download/cleanmytv-config.json not found")
                        return@Surface
                    }
                    val jsonStr = targetFile.readText()
                    ConfigStore.update(ctx, JSONObject(jsonStr))
                    onMessage("Settings restored successfully!")
                }.onFailure { onMessage("Import failed: ${it.message}") }
            },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33FFFFFF),
                focusedContainerColor = Color.White,
                focusedContentColor = CUSTOMIZATION_BG
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
        ) {
            Text("Import Config from File", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}
