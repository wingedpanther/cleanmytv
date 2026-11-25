package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Text
import org.json.JSONArray
import org.json.JSONObject

/**
 * System updates often switch the TV's own apps back on. The desktop app tells Home what it switched off
 * ([HomeConfig.switchedOff]); when some of those are on again, Home says so once, and the desktop app's
 * Clean up screen offers to switch them off again.
 */
object CameBack {
    /** The packages from [switchedOff] the TV has on again. */
    fun find(switchedOff: List<String>, isOn: (String) -> Boolean): List<String> = switchedOff.filter(isOn)

    /** Still to tell the owner: back on, and not in the list they already saw. */
    fun news(back: List<String>, seen: List<String>): List<String> = back - seen.toSet()

    fun check(ctx: Context, switchedOff: List<String>): List<String> = find(switchedOff) { pkg ->
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
    }

    fun label(ctx: Context, pkg: String): String = runCatching {
        ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(pkg)

    /** The owner saw these; a later update that brings back others says so again. */
    fun seen(ctx: Context, back: List<String>) = ConfigStore.update(ctx, JSONObject().put("came_back_seen", JSONArray(back)))
}

@Composable
fun CameBackDialog(back: List<String>, accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val names = remember(back) { back.map { CameBack.label(ctx, it) }.sorted() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(620.dp).background(HomeColours.panel.copy(alpha = 0.95f), RoundedCornerShape(20.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Some apps came back"), color = Color(0xFFF0EBE5), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(
                    tr("{n} apps CleanMyTV switched off are on again, probably after a system update.", "n" to names.size.toString()),
                    color = Color(0xFFD7CFC4), fontSize = 15.sp
                )
                Text(names.take(8).joinToString(", ") + if (names.size > 8) ", …" else "", color = Color(0xFFD7CFC4), fontSize = 14.sp)
                Text(tr("To switch them off again, connect CleanMyTV on your computer and open Clean up."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                UpdateButton(tr("OK"), accent, Modifier.focusRequester(first).padding(top = 4.dp)) { onClose() }
            }
        }
    }
}
