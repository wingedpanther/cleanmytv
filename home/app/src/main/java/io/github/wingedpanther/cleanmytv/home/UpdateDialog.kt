package io.github.wingedpanther.cleanmytv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.launch

private val UPDATE_PANEL: Color get() = HomeColours.panel.copy(alpha = 0.95f)
private val UPDATE_TEXT = Color(0xFFF0EBE5)
private val UPDATE_MUTED = Color(0xFFD7CFC4)

/**
 * A newer Home: what's new in the release, and Update, Later or Skip this version. The notes scroll with the
 * arrows; Update downloads, checks and hands the APK to Android's installer, which asks to confirm.
 */
@Composable
fun UpdateDialog(release: HomeRelease, accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by Updates.progress.collectAsState()
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { Updates.said(""); runCatching { first.requestFocus() } }
    // Looked at again while the dialog is open: the owner may have just allowed it in Settings.
    var allowed by remember { mutableStateOf(Updates.allowed(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = Updates.allowed(ctx)
            if (now && !allowed) Updates.said(tr("Allowed. Press Update."))
            allowed = now
            kotlinx.coroutines.delay(1000)
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(620.dp).background(UPDATE_PANEL, RoundedCornerShape(20.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CleanMyTV Home ${release.version}", color = UPDATE_TEXT, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(tr("You have {version}. Your settings, rows and wallpaper stay as they are.", "version" to Updates.installed(ctx)), color = UPDATE_MUTED, fontSize = 14.sp)
                Text(tr("What's new"), color = UPDATE_TEXT, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                // Focusable, so the arrows scroll the notes before they reach the buttons.
                Text(
                    release.notes.ifBlank { tr("No notes for this release.") },
                    color = UPDATE_MUTED, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().height(260.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .focusable().verticalScroll(rememberScrollState()).padding(14.dp)
                )
                if (progress.isNotEmpty()) Text(progress, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UpdateButton(if (allowed) tr("Update") else tr("Allow updates"), accent, Modifier.focusRequester(first), enabled = !busy) {
                        if (!Updates.allowed(ctx)) {
                            Updates.said(tr("Turn on CleanMyTV Home in the screen that opens, then come back and press Update."))
                            Updates.askToAllow(ctx)
                        } else {
                            busy = true
                            scope.launch { Updates.install(ctx, release); busy = false }
                        }
                    }
                    UpdateButton(tr("Later"), accent) { onClose() }
                    UpdateButton(tr("Skip this version"), accent) { Updates.skip(ctx, release); onClose() }
                }
            }
        }
    }
}

@Composable
internal fun UpdateButton(text: String, accent: Color, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f), focusedContainerColor = accent)
    ) {
        Box(Modifier.height(46.dp).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
            Text(text, color = UPDATE_TEXT.copy(alpha = if (enabled) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
