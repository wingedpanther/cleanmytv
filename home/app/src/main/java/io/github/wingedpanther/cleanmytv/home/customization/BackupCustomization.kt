package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import org.json.JSONObject
import java.io.File

@Composable
fun BackupCustomization(config: HomeConfig, ctx: Context, onMessage: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Export Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Export Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Saves your complete layout, colors, and settings to TV Downloads folder (/sdcard/Download/cleanmytv-config.json).",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            TvSecondaryButton(
                text = "Export Config to File",
                onClick = {
                    runCatching {
                        val jsonStr = config.toJson().toString(2)
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        downloadDir.mkdirs()
                        val targetFile = File(downloadDir, "cleanmytv-config.json")
                        targetFile.writeText(jsonStr)
                        onMessage("Exported to Download/cleanmytv-config.json")
                    }.onFailure { onMessage("Export failed: ${it.message}") }
                }
            )
        }

        // --- Import Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Import Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Loads and applies settings from Download/cleanmytv-config.json.",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            TvSecondaryButton(
                text = "Import Config from File",
                onClick = {
                    runCatching {
                        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        val targetFile = File(downloadDir, "cleanmytv-config.json")
                        if (!targetFile.exists()) {
                            onMessage("File Download/cleanmytv-config.json not found")
                            return@TvSecondaryButton
                        }
                        val jsonStr = targetFile.readText()
                        ConfigStore.update(ctx, JSONObject(jsonStr))
                        onMessage("Settings restored successfully!")
                    }.onFailure { onMessage("Import failed: ${it.message}") }
                }
            )
        }
    }
}
