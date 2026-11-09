package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.io.File

fun loadWallpaperPreview(file: File): ImageBitmap? = runCatching {
    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
    val bmp = BitmapFactory.decodeFile(file.absolutePath, options)
    bmp?.asImageBitmap()
}.getOrNull()

@Composable
fun WallpaperPickerModal(
    ctx: Context,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit
) {
    val firstFocusRequester = remember { FocusRequester() }

    val imageFiles = remember {
        val found = mutableListOf<File>()
        val validExts = setOf("jpg", "jpeg", "png", "webp", "bmp")

        fun isIgnoredPath(f: File): Boolean {
            val pathLower = f.absolutePath.lowercase()
            if (pathLower.contains(".thumbnail") || pathLower.contains(".cache") || pathLower.contains(".trash") || pathLower.contains(".pending")) return true
            return f.path.split(File.separatorChar).any { it.startsWith(".") && it.length > 1 }
        }

        // 1. Query Android MediaStore
        runCatching {
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = ctx.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
            )
            cursor?.use { c ->
                val dataIdx = c.getColumnIndex(MediaStore.Images.Media.DATA)
                if (dataIdx >= 0) {
                    while (c.moveToNext()) {
                        val path = c.getString(dataIdx)
                        if (!path.isNullOrBlank()) {
                            val f = File(path)
                            if (f.exists() && f.isFile && f.length() > 0 && !isIgnoredPath(f)) found.add(f)
                        }
                    }
                }
            }
        }

        // 2. Recursive filesystem scan across common storage directories
        val scanRoots = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStorageDirectory(),
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            File("/sdcard/DCIM")
        )

        scanRoots.forEach { root ->
            if (root.exists() && root.isDirectory) {
                runCatching {
                    root.walkTopDown().maxDepth(4).filter { f ->
                        f.isFile && f.extension.lowercase() in validExts && f.length() > 0 && !isIgnoredPath(f)
                    }.forEach { found.add(it) }
                }
            }
        }

        // Deduplicate using canonical path to collapse symlinks/duplicate mounts (/sdcard vs /storage/emulated/0)
        found.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
            .sortedByDescending { it.lastModified() }
    }

    var focusedFile by remember(imageFiles) { mutableStateOf(imageFiles.firstOrNull()) }

    LaunchedEffect(imageFiles) {
        if (imageFiles.isNotEmpty()) {
            runCatching { firstFocusRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(840.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2836))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Select Custom Wallpaper Image", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (imageFiles.isNotEmpty()) "Found ${imageFiles.size} image(s) on TV storage" else "No images found",
                            color = MUTED_COLOR,
                            fontSize = 13.sp
                        )
                    }

                    TvSecondaryButton(text = "Close", onClick = onDismiss)
                }

                if (imageFiles.isEmpty()) {
                    Text(
                        text = "No image files (.jpg, .png, .webp) found on TV storage.\n\nPlace an image file in the Download or Pictures folder on your TV. If storage access was blocked, click below to allow Storage permission in TV Settings.",
                        color = MUTED_COLOR,
                        fontSize = 13.sp
                    )

                    TvPrimaryButton(
                        text = "Open TV Settings > Permissions",
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // Left Pane: File List
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(imageFiles) { index, file ->
                                val sizeKb = file.length() / 1024
                                val parentName = file.parentFile?.name ?: ""
                                var isFocused by remember { mutableStateOf(false) }

                                Surface(
                                    onClick = { onSelected(file) },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color(0x22FFFFFF),
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = CUSTOMIZATION_BG
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged {
                                            isFocused = it.isFocused
                                            if (it.isFocused) focusedFile = file
                                        }
                                        .then(if (index == 0) Modifier.focusRequester(firstFocusRequester) else Modifier)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                file.name,
                                                color = if (isFocused) CUSTOMIZATION_BG else Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (parentName.isNotEmpty()) {
                                                Text(
                                                    "in $parentName/",
                                                    color = if (isFocused) CUSTOMIZATION_BG.copy(alpha = 0.7f) else MUTED_COLOR,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Text(
                                            "${sizeKb} KB",
                                            color = if (isFocused) CUSTOMIZATION_BG else MUTED_COLOR,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Right Pane: Live Image Preview
                        Box(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x15FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val activeFile = focusedFile
                            if (activeFile != null) {
                                val previewBmp = remember(activeFile) { loadWallpaperPreview(activeFile) }
                                if (previewBmp != null) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Image(
                                            bitmap = previewBmp,
                                            contentDescription = activeFile.name,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .align(Alignment.BottomCenter)
                                                .background(Color(0xEE0B1D33))
                                                .padding(10.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = activeFile.name,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${activeFile.length() / 1024} KB · ${activeFile.parentFile?.name ?: ""}",
                                                    color = MUTED_COLOR,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Text("Preview Unavailable", color = MUTED_COLOR, fontSize = 13.sp)
                                }
                            } else {
                                Text("Select an Image to Preview", color = MUTED_COLOR, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
