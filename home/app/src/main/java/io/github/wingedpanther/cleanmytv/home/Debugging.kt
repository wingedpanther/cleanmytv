package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Text

/**
 * A quiet icon in the top bar while debugging is on, which CleanMyTV's Finish step turns off from the computer.
 * Keep it on hides the icon until debugging is turned off and on again.
 */
object Debugging {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("debugging", Context.MODE_PRIVATE)

    fun shown(ctx: Context): Boolean {
        val on = TvSettings.debuggingOn(ctx)
        if (!on) prefs(ctx).edit().remove("kept").apply()
        return on && !prefs(ctx).getBoolean("kept", false)
    }

    fun keep(ctx: Context) = prefs(ctx).edit().putBoolean("kept", true).apply()

    /** Android's own screen for it, where Home can't change the setting itself. */
    fun developerOptions(ctx: Context): Intent? =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .takeIf { it.resolveActivity(ctx.packageManager) != null }
}

@Composable
fun DebuggingDialog(accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    var stuck by remember { mutableStateOf(false) }
    // Again when Turn it off gives way to Open Developer options, so the focus stays in the dialog.
    LaunchedEffect(stuck) { runCatching { first.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            Column(Modifier.width(620.dp).background(Color(0xF22D3A2E), RoundedCornerShape(20.dp)).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Debugging is on"), color = Color(0xFFF0EBE5), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                Text(tr("While it's on, a computer on your network can ask to control this TV. CleanMyTV on a computer needs it to connect, so turn it off once you're done."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                Text(tr("To use CleanMyTV again later, turn it back on in Developer options."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                if (stuck) Text(tr("Home can't turn it off on this TV. Turn off USB debugging in Developer options."), color = accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val settings = Debugging.developerOptions(ctx)
                    if (stuck && settings != null) UpdateButton(tr("Open Developer options"), accent, Modifier.focusRequester(first)) { runCatching { ctx.startActivity(settings) }; onClose() }
                    else UpdateButton(tr("Turn it off"), accent, Modifier.focusRequester(first)) {
                        TvSettings.debuggingOff(ctx)
                        if (TvSettings.debuggingOn(ctx)) stuck = true else onClose()
                    }
                    UpdateButton(tr("Keep it on"), accent) { Debugging.keep(ctx); onClose() }
                }
            }
        }
    }
}
