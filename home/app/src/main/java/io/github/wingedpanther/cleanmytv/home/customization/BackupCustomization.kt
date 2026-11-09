package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import io.github.wingedpanther.cleanmytv.home.customization.components.*
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

            TvSecondaryButton(
                text = "Export Config to File",
                onClick = {
                    runCatching {
                        val jsonStr = config.toJson().toString(2)
                        val targetFile = getBackupFile(ctx)
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeText(jsonStr)
                        onMessage("Exported successfully to app backup storage")
                    }.onFailure { onMessage("Export failed: ${it.message}") }
                }
            )
        }

        // --- Import Configuration Section ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Import Configuration", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "Loads and applies settings from app backup storage.",
                color = MUTED_COLOR,
                fontSize = 12.sp
            )

            TvSecondaryButton(
                text = "Import Config from File",
                onClick = {
                    runCatching {
                        val targetFile = getBackupFile(ctx)
                        if (!targetFile.exists()) {
                            onMessage("Backup configuration file not found")
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
