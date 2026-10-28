package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val TAG = "CMTVHome"

data class TvApp(val pkg: String, val label: String, val art: ImageBitmap, val banner: Boolean, val launch: Intent)

class MainActivity : ComponentActivity() {
    private val visualiser = Visualiser()
    private val lastKey = mutableStateOf("")
    private var created = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        created = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        setContent { Home(visualiser, lastKey.value) }
        window.decorView.post { Log.i(TAG, "first frame ${SystemClock.uptimeMillis() - created} ms after onCreate") }
    }

    override fun onStart() {
        super.onStart()
        if (ConfigStore.config.value.visualiser) visualiser.start(this)
    }

    override fun onStop() {
        visualiser.stop()
        super.onStop()
    }

    // Spike: log every key so we can see what a TV's remote sends (#26). Nothing is swallowed.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val name = KeyEvent.keyCodeToString(event.keyCode)
            Log.i(TAG, "key ${event.keyCode} $name scan=${event.scanCode}")
            lastKey.value = "${event.keyCode} $name"
        }
        return super.dispatchKeyEvent(event)
    }

    // Pressing Home while already home: stay put instead of rebuilding.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.i(TAG, "home pressed while home")
    }
}

/** TV apps first (with their banners), then phone apps that only have an icon. */
private fun loadApps(pm: PackageManager, self: String): List<TvApp> {
    fun query(category: String) = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(category), 0
    )
    val seen = HashSet<String>()
    val out = ArrayList<TvApp>()
    for ((category, isTv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
        for (ri in query(category)) {
            val ai = ri.activityInfo
            if (ai.packageName == self || !seen.add(ai.packageName)) continue
            val banner: Drawable? = if (isTv) ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) else null
            val art = (banner ?: ai.loadIcon(pm)).toBitmap(if (banner != null) 320 else 128, if (banner != null) 180 else 128)
            val launch = Intent(Intent.ACTION_MAIN).addCategory(category)
                .setClassName(ai.packageName, ai.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            out += TvApp(ai.packageName, ri.loadLabel(pm).toString(), art.asImageBitmap(), banner != null, launch)
        }
    }
    return out
}

@Composable
private fun Home(visualiser: Visualiser, lastKey: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val config by ConfigStore.config.collectAsState()
    val accent = Color(config.accent)
    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        val t = SystemClock.uptimeMillis()
        apps = withContext(Dispatchers.Default) { loadApps(ctx.packageManager, ctx.packageName) }
        Log.i(TAG, "${apps.size} apps loaded in ${SystemClock.uptimeMillis() - t} ms")
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) runCatching { first.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1E2A), Color(0xFF02080C))))
    ) {
        Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 32.dp, end = 48.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("CleanMyTV Home", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Clock()
            }
            Spacer(Modifier.height(48.dp))
            Text("Apps", color = Color(0xFFB8C7D1), fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    Tile(
                        app, config.focus, accent,
                        if (app == apps.first()) Modifier.focusRequester(first) else Modifier
                    ) { runCatching { ctx.startActivity(app.launch) }.onFailure { Log.w(TAG, "launch ${app.pkg}: $it") } }
                }
            }
            Spacer(Modifier.weight(1f))
            Status(visualiser, lastKey)
        }
        if (config.visualiser) Bars(visualiser, accent, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Tile(app: TvApp, focus: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 224.dp, height = 126.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (focus == "none") 1f else 1.1f),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = if (focus == "glow") androidx.tv.material3.Glow(accent.copy(alpha = 0.7f), 18.dp) else androidx.tv.material3.Glow.None
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = if (focus == "outline") androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, accent), shape = shape)
            else androidx.tv.material3.Border.None
        ),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF15303F), focusedContainerColor = Color(0xFF1C3D50))
    ) {
        if (app.banner) {
            Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(app.art, null, Modifier.size(52.dp))
                Spacer(Modifier.height(8.dp))
                Text(app.label, color = Color.White, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(1000L - System.currentTimeMillis() % 1000) } }
    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), color = Color.White, fontSize = 22.sp)
}

@Composable
private fun Status(visualiser: Visualiser, lastKey: String) {
    val state by visualiser.state.collectAsState()
    val audio = when (state) {
        Visualiser.State.NO_PERMISSION -> "no permission"
        Visualiser.State.WAITING -> "waiting"
        Visualiser.State.LIVE -> "live"
        Visualiser.State.SILENT -> "silent (play some music)"
        Visualiser.State.FAILED -> "failed: ${visualiser.error}"
    }
    Text("spike · audio: $audio · last key: ${lastKey.ifEmpty { "none" }}", color = Color(0xFF6E8796), fontSize = 12.sp, modifier = Modifier.padding(bottom = 72.dp))
}

/** Equaliser bars along the bottom edge, coloured with the accent. */
@Composable
private fun Bars(visualiser: Visualiser, accent: Color, modifier: Modifier) {
    val levels by visualiser.levels.collectAsState()
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val n = levels.size
        val gap = 4.dp.toPx()
        val w = (size.width - gap * (n + 1)) / n
        for (i in 0 until n) {
            val h = (levels[i] * size.height).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.25f)), startY = size.height - h, endY = size.height),
                topLeft = Offset(gap + i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
        }
    }
}
