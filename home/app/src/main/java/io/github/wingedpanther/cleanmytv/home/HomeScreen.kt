package io.github.wingedpanther.cleanmytv.home

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val TEXT = Color(0xFFF2F5F7)
private val MUTED = Color(0xFFB8C7D1)

@Composable
fun HomeScreen(visualiser: Visualiser, appsVersion: Int) {
    val ctx = LocalContext.current
    val cfg by ConfigStore.config.collectAsState()
    val wallVersion by ConfigStore.wallpaperVersion.collectAsState()
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    LaunchedEffect(appsVersion) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx.packageManager, ctx.packageName) }
    }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(wallVersion) {
        wallpaper = withContext(Dispatchers.IO) {
            val f = ConfigStore.wallpaper(ctx)
            if (f.exists()) runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull() else null
        }
    }
    LaunchedEffect(cfg.vizBars, cfg.vizSensitivity, cfg.visualiser) {
        visualiser.bars = cfg.vizBars
        visualiser.sensitivity = cfg.vizSensitivity / 100f
        if (cfg.visualiser) visualiser.start(ctx) else visualiser.stop()
    }

    val accent = Color(cfg.accent)
    val shown = arrange(apps, cfg)
    val dock = shown.take(cfg.dockSize)
    val grid = shown.drop(cfg.dockSize)
    val first = remember { FocusRequester() }
    LaunchedEffect(shown.isNotEmpty()) { if (shown.isNotEmpty()) runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize().background(Color(cfg.background))) {
        wallpaper?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = cfg.wallpaperDim / 100f)))
        }
        // A soft shade at the top keeps the clock readable on bright wallpapers.
        Box(Modifier.fillMaxWidth().height(200.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))

        // A 1080p TV is 960x540 dp. The header stays put; the apps scroll under it. Rows keep 16 dp
        // of room on each side so a focused tile's glow and zoom aren't clipped.
        Column(Modifier.fillMaxSize()) {
            Header(cfg, Modifier.padding(start = 48.dp, end = 48.dp, top = 28.dp))
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val gap = 16.dp
                val side = 16.dp
                val gridW: Dp = (maxWidth - 64.dp - side * 2 - gap * (cfg.columns - 1)) / cfg.columns
                val dockW: Dp = minOf(gridW * 1.5f, 220.dp)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = 4.dp, bottom = 96.dp)) {
                    if (dock.isNotEmpty()) {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(gap), contentPadding = PaddingValues(horizontal = side, vertical = 14.dp)) {
                                items(dock, key = { it.pkg }) { app ->
                                    Tile(app, cfg, accent, Modifier.width(dockW), if (app == shown.first()) Modifier.focusRequester(first) else Modifier, big = true)
                                }
                            }
                        }
                    }
                    if (grid.isNotEmpty()) {
                        item { Text(if (dock.isEmpty()) "Apps" else "All apps", color = MUTED, fontSize = 13.sp, modifier = Modifier.padding(start = side, top = 6.dp)) }
                        items(grid.chunked(cfg.columns)) { row ->
                            Row(Modifier.padding(horizontal = side, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                for (app in row) Tile(app, cfg, accent, Modifier.width(gridW), if (app == shown.first()) Modifier.focusRequester(first) else Modifier, big = false)
                            }
                        }
                    }
                }
            }
        }

        if (cfg.visualiser) {
            Bars(visualiser, cfg, accent, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
        }
    }
}

@Composable
private fun Header(cfg: HomeConfig, modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    Row(modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        if (cfg.greeting.isNotBlank()) {
            val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
            val part = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
            Text("$part, ${cfg.greeting}", color = TEXT, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        if (cfg.clock != "off") {
            Column(horizontalAlignment = Alignment.End) {
                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = TEXT, fontSize = 32.sp, fontWeight = FontWeight.Light)
                if (cfg.clock == "time_date") Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), color = MUTED, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Tile(app: TvApp, cfg: HomeConfig, accent: Color, size: Modifier, focus: Modifier, big: Boolean) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(if (big) 10.dp else 8.dp)
    Column(size) {
        Surface(
            onClick = { runCatching { ctx.startActivity(app.launch) }.onFailure { Log.w(TAG, "launch ${app.pkg}: $it") } },
            modifier = focus.fillMaxWidth().aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (cfg.focus == "none") 1f else 1.08f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = if (cfg.focus == "glow") Glow(accent.copy(alpha = 0.75f), 14.dp) else Glow.None),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = if (cfg.focus == "outline") Border(BorderStroke(3.dp, accent), shape = shape) else Border.None
            ),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xCC15303F), focusedContainerColor = Color(0xFF1F4256))
        ) {
            if (app.banner) {
                Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Image(app.art, null, Modifier.size(if (big) 40.dp else 32.dp))
                    if (!cfg.showNames) {
                        Spacer(Modifier.height(4.dp))
                        Text(app.label, color = TEXT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (cfg.showNames) {
            Text(
                app.label, color = MUTED, fontSize = if (big) 13.sp else 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        }
    }
}
