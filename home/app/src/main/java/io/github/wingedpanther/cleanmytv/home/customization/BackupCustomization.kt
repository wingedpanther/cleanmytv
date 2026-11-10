package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.customization.components.Alert
import io.github.wingedpanther.cleanmytv.home.customization.components.AlertType
import org.json.JSONObject
import java.io.File
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun BackupCustomization(config: HomeConfig, ctx: Context, onMessage: (String) -> Unit) {
    var inlineMessage by remember { mutableStateOf("") }
    
    fun getBackupFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(dir, "cleanmytv-config.json")
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Export Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Export Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Saves your complete layout, colors, and settings to app backup storage (cleanmytv-config.json).",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvSecondaryButton(
                    text = "Export Config to File",
                    onClick = {
                        runCatching {
                            val jsonStr = config.toJson().toString(2)
                            val targetFile = getBackupFile(ctx)
                            targetFile.parentFile?.mkdirs()
                            targetFile.writeText(jsonStr)
                            inlineMessage = "Exported successfully to app backup storage"
                        }.onFailure { inlineMessage = "Export failed: ${it.message}" }
                    }
                )
                
                if (inlineMessage.startsWith("Export")) {
                    Alert(
                        text = inlineMessage,
                        type = if (inlineMessage.startsWith("Export failed")) AlertType.ERROR else AlertType.SUCCESS
                    )
                }
            }
        }

        // --- Import Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Import Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Loads and applies settings from app backup storage.",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvSecondaryButton(
                    text = "Import Config from File",
                    onClick = {
                        runCatching {
                            val targetFile = getBackupFile(ctx)
                            if (!targetFile.exists()) {
                                inlineMessage = "Import failed: Backup configuration file not found"
                                return@TvSecondaryButton
                            }
                            val jsonStr = targetFile.readText()
                            ConfigStore.update(ctx, JSONObject(jsonStr))
                            inlineMessage = "Settings restored successfully!"
                            onMessage("Settings restored successfully!") // also send to top header for toast fallback
                        }.onFailure { inlineMessage = "Import failed: ${it.message}" }
                    }
                )
                
                if (inlineMessage.startsWith("Import") || inlineMessage.startsWith("Settings")) {
                    Alert(
                        text = inlineMessage,
                        type = if (inlineMessage.startsWith("Import failed")) AlertType.ERROR else AlertType.SUCCESS
                    )
                }
            }
        }

        // --- Reset Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reset Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Resets all customization settings back to factory default state.",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TvDestructiveButton(
                    text = "Reset All Settings to Default",
                    onClick = {
                        runCatching {
                            ConfigStore.reset(ctx)
                            inlineMessage = "All settings reset to default successfully!"
                            onMessage("All settings reset to default successfully!")
                        }.onFailure { inlineMessage = "Reset failed: ${it.message}" }
                    }
                )

                if (inlineMessage.startsWith("All settings reset") || inlineMessage.startsWith("Reset failed")) {
                    Alert(
                        text = inlineMessage,
                        type = if (inlineMessage.startsWith("Reset failed")) AlertType.ERROR else AlertType.SUCCESS
                    )
                }
            }
        }
    }
}
