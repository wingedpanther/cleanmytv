package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import java.io.File

@Composable
fun WallpaperPickerModal(
    ctx: Context,
    onDismiss: () -> Unit,
    onSelected: (File) -> Unit
) {
    val imageFiles = remember {
        val found = mutableListOf<File>()
        val validExts = setOf("jpg", "jpeg", "png", "webp", "bmp")

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
                            if (f.exists() && f.isFile && f.length() > 0) found.add(f)
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
            File("/sdcard"),
            File("/sdcard/Download"),
            File("/sdcard/Pictures"),
            File("/sdcard/DCIM"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Pictures"),
            File("/storage/emulated/0")
        )

        scanRoots.forEach { root ->
            if (root.exists() && root.isDirectory) {
                runCatching {
                    root.walkTopDown().maxDepth(4).filter { f ->
                        f.isFile && f.extension.lowercase() in validExts && f.length() > 0
                    }.forEach { found.add(it) }
                }
            }
        }

        found.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(560.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222823))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select Wallpaper Image", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                if (imageFiles.isEmpty()) {
                    Text(
                        text = "No image files (.jpg, .png, .webp) found on TV storage.\n\nPlace an image file in the Download or Pictures folder on your TV. If storage access was blocked, click below to allow Storage permission in TV Settings.",
                        color = MUTED_COLOR,
                        fontSize = 13.sp
                    )

                    Surface(
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ctx.startActivity(intent)
                            }
                        },
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF7C5CFF)),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Open TV Settings > Permissions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                    }
                } else {
                    Text(
                        text = "Found ${imageFiles.size} image(s) on TV storage:",
                        color = MUTED_COLOR,
                        fontSize = 12.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .height(300.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(imageFiles) { file ->
                            val sizeKb = file.length() / 1024
                            val parentName = file.parentFile?.name ?: ""
                            Surface(
                                onClick = { onSelected(file) },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = Color(0x22FFFFFF),
                                    focusedContainerColor = Color(0x66FFFFFF)
                                ),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (parentName.isNotEmpty()) {
                                            Text("in $parentName/", color = MUTED_COLOR, fontSize = 11.sp)
                                        }
                                    }
                                    Text("${sizeKb} KB", color = MUTED_COLOR, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                Surface(
                    onClick = onDismiss,
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
        }
    }
}
