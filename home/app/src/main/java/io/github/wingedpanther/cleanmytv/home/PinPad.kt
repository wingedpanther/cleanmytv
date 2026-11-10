package io.github.wingedpanther.cleanmytv.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PAD_TEXT = Color(0xFFF2EEE7)
private val PAD_MUTED = Color(0xFFC8C0B3)
private val PAD_PANEL = Color(0xF21A1C22)

private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)

/**
 * The PIN pad: the remote's number keys type straight in, or move over the pad with the D-pad. OK checks;
 * a sixth digit checks by itself. [what] is the package (or Lock.PANEL) that unlocks.
 */
@Composable
fun PinPad(title: String, what: String, accent: Color, onUnlocked: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var wait by remember { mutableIntStateOf(Lock.waitSeconds()) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(wait) { if (wait > 0) { note = "Too many tries. Wait $wait s"; delay(1000); wait = Lock.waitSeconds(); if (wait == 0) note = "" } }
    fun submit() {
        if (pin.length < 4) { note = "At least 4 digits"; return }
        if (Lock.check(ctx, pin, what)) { onUnlocked(); return }
        pin = ""
        wait = Lock.waitSeconds()
        if (wait == 0) note = "Wrong PIN"
        scope.launch { for (x in listOf(14f, -12f, 8f, -4f, 0f)) shake.animateTo(x, tween(45)) }
    }
    fun type(c: Char) {
        if (wait > 0 || pin.length >= 6) return
        pin += c; note = ""
        if (pin.length == 6) submit()
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).onPreviewKeyEvent { e ->
            val d = DIGIT_KEYS[e.key] ?: return@onPreviewKeyEvent false
            if (e.type == KeyEventType.KeyDown) type(d)
            true
        },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.width(360.dp).background(PAD_PANEL, RoundedCornerShape(20.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(Icons.Outlined.Lock, null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(accent))
            Spacer(Modifier.height(10.dp))
            Text(title, color = PAD_TEXT, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).background(if (i < pin.length) accent else Color.White.copy(alpha = 0.18f), CircleShape))
                }
            }
            Text(note, color = PAD_MUTED, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp).height(18.dp))
            Spacer(Modifier.height(8.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { k ->
                            Surface(
                                onClick = { when (k) { "del" -> pin = pin.dropLast(1); "ok" -> submit(); else -> type(k[0]) } },
                                modifier = (if (k == "1") Modifier.focusRequester(first) else Modifier).size(width = 88.dp, height = 52.dp),
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (k == "ok") accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.07f),
                                    focusedContainerColor = accent
                                )
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    when (k) {
                                        "del" -> Image(Icons.Outlined.Backspace, "Delete", Modifier.size(22.dp), colorFilter = ColorFilter.tint(PAD_TEXT))
                                        "ok" -> Text("OK", color = PAD_TEXT, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                        else -> Text(k, color = PAD_TEXT, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Over a locked app, however it was opened: by voice, from Settings, from another app. The key service
 * starts it when a locked app comes to the front. Back goes Home rather than into the app.
 */
class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConfigStore.load(this)
        val pkg = intent.getStringExtra(EXTRA_PKG) ?: return finish()
        val launch: Intent? = intent.getParcelableExtra(EXTRA_LAUNCH)
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        setContent {
            PinPad("Enter the PIN for $label", pkg, Color(ConfigStore.config.value.accent)) {
                launch?.let { runCatching { startActivity(it) }.onFailure { e -> Log.w(TAG, "launch $pkg: $e") } }
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun goHome() {
        intent.getStringExtra(EXTRA_PKG)?.let { Lock.backOut(it) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LAUNCH = "launch"
        /** Shown at most once at a time: the guard sees the app again while this covers it. */
        @Volatile var showing: String? = null

        fun intent(ctx: android.content.Context, pkg: String, launch: Intent? = null): Intent =
            Intent(ctx, LockActivity::class.java).putExtra(EXTRA_PKG, pkg).apply { launch?.let { putExtra(EXTRA_LAUNCH, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }

    // Already open for another app: start over with this one, so the title and the unlock are for it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    override fun onResume() { super.onResume(); showing = intent.getStringExtra(EXTRA_PKG) }
    override fun onPause() { super.onPause(); showing = null }
}
