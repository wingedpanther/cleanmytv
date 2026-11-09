package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.Fire
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun VisualizerCustomization(config: HomeConfig, ctx: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TvSwitch(
            title = "Show Music Visualizer Bars",
            subtitle = "Displays animated audio spectrum bars when playing music",
            checked = config.visualiser,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("visualiser", it)) }
        )

        TvRadioGroup(
            title = "Visualizer Style",
            options = listOf("bars" to "Spectrum Bars", "mirror" to "Mirrored Bars", "wave" to "Waveform Line"),
            current = config.vizStyle,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_style", it)) }
        )

        TvRadioGroup(
            title = "Visualizer Color",
            options = listOf("accent" to "Accent Color", "rainbow" to "Rainbow", "white" to "White"),
            current = config.vizColour,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("viz_colour", it)) }
        )

        TvSlider(
            title = "Bar Height Scale",
            value = config.vizHeight,
            min = 4, max = 32, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_height", it)) }
        )

        TvSlider(
            title = "Opacity",
            value = config.vizOpacity,
            min = 10, max = 100, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_opacity", it)) }
        )

        TvSlider(
            title = "Audio Sensitivity",
            value = config.vizSensitivity,
            min = 10, max = 300, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("viz_sensitivity", it)) }
        )
    }
}

@Composable
fun FireplaceCustomization(config: HomeConfig, ctx: Context) {
    var isDownloading by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf("") }
    var isReady by remember { mutableStateOf(Fire.ready(ctx)) }
    val scope = rememberCoroutineScope()

    fun startDownload() {
        if (isDownloading) return
        isDownloading = true
        downloadMessage = "Downloading Fireplace Video & Sound (11 MB)..."

        scope.launch(Dispatchers.IO) {
            runCatching {
                val dir = ctx.getExternalFilesDir("fireplace") ?: throw Exception("Storage unavailable")
                dir.mkdirs()

                val filesToDownload = listOf(
                    "fireplace.mp4" to "https://github.com/wingedpanther/cleanmytv/releases/download/fireplace-1/fireplace.mp4",
                    "crackle.m4a" to "https://github.com/wingedpanther/cleanmytv/releases/download/fireplace-1/crackle.m4a"
                )

                filesToDownload.forEachIndexed { idx, (fileName, urlStr) ->
                    withContext(Dispatchers.Main) {
                        downloadMessage = "Downloading $fileName (${idx + 1}/2)..."
                    }

                    val targetFile = File(dir, fileName)
                    val tmpFile = File(dir, "$fileName.tmp")

                    fun openStream(targetUrl: String): java.io.InputStream {
                        val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15_000
                            readTimeout = 30_000
                            instanceFollowRedirects = true
                        }
                        val code = conn.responseCode
                        if (code in 300..399) {
                            val loc = conn.getHeaderField("Location") ?: throw Exception("Redirect missing location")
                            return openStream(loc)
                        }
                        if (code != 200) throw Exception("HTTP $code")
                        return conn.inputStream
                    }

                    openStream(urlStr).use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }

                    if (tmpFile.length() > 0) {
                        tmpFile.renameTo(targetFile)
                    }
                }

                Fire.changed()
                withContext(Dispatchers.Main) {
                    isReady = Fire.ready(ctx)
                    isDownloading = false
                    downloadMessage = "Fireplace media downloaded successfully!"
                }
            }.onFailure { err ->
                withContext(Dispatchers.Main) {
                    isDownloading = false
                    downloadMessage = "Download failed: ${err.message}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // --- Fireplace Media Status Card ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                    Text("Fireplace Storage Status:", color = MUTED_COLOR, fontSize = 12.sp)
                    Text(
                        text = if (isReady) "MEDIA READY (VIDEO & CRACKLE INSTALLED)" else "NO MEDIA ON TV",
                        color = if (isReady) Color(0xFF4CAF50) else Color(0xFFFFB23C),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!isDownloading) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isReady) {
                            TvDestructiveButton(
                                text = "Delete Media",
                                onClick = {
                                    val dir = ctx.getExternalFilesDir("fireplace")
                                    if (dir != null) {
                                        File(dir, "fireplace.mp4").delete()
                                        File(dir, "crackle.m4a").delete()
                                    }
                                    Fire.changed()
                                    isReady = false
                                    downloadMessage = "Fireplace media deleted from storage"
                                },
                                icon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp)) }
                            )

                            TvSecondaryButton(
                                text = "Re-download",
                                onClick = { startDownload() }
                            )
                        } else {
                            TvPrimaryButton(
                                text = "Download Fireplace (11 MB)",
                                onClick = { startDownload() }
                            )
                        }
                    }
                }
            }

            if (downloadMessage.isNotEmpty()) {
                Text(downloadMessage, color = Color.White, fontSize = 12.sp)
            }
        }

        TvSwitch(
            title = "Fireplace Home Wallpaper",
            subtitle = "Show live fireplace video behind Home screen",
            checked = config.fireWall,
            onCheckedChange = { on ->
                if (on && !isReady) {
                    startDownload()
                }
                ConfigStore.update(ctx, JSONObject().put("fire_wall", on))
            }
        )

        TvSwitch(
            title = "Crackling Sound in Fullscreen",
            subtitle = "Play crackling audio during full screen fireplace",
            checked = config.fireSound,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("fire_sound", it)) }
        )

        TvSlider(
            title = "Volume",
            value = config.fireVolume,
            min = 0, max = 100, unit = "%",
            onChange = { ConfigStore.update(ctx, JSONObject().put("fire_volume", it)) }
        )

        TvSlider(
            title = "Auto-Off Timer",
            value = config.fireTimer,
            min = 0, max = 240, unit = " min (0=Off)",
            onChange = { ConfigStore.update(ctx, JSONObject().put("fire_timer", it)) }
        )
    }
}
