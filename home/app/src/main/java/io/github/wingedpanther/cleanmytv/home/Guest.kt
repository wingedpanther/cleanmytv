package io.github.wingedpanther.cleanmytv.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import io.github.wingedpanther.cleanmytv.home.customization.Qr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * For guests: the home Wi-Fi as a QR code their phones join from, typed once in the desktop app, and guest
 * mode, where Home shows only the apps the owner picked until the TV next goes to sleep.
 */
object Guest {
    private val _on = MutableStateFlow(false)
    val on: StateFlow<Boolean> = _on
    /** For the PIN pad, when App lock has a PIN: ending guest mode asks for it. */
    const val LOCK = "guest"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("guest", Context.MODE_PRIVATE)

    fun load(ctx: Context) { _on.value = prefs(ctx).getBoolean("on", false) }

    fun start(ctx: Context) {
        prefs(ctx).edit().putBoolean("on", true).apply()
        _on.value = true
    }

    /** The TV went to sleep, restarted, or the owner ended it. */
    fun end(ctx: Context) {
        if (!_on.value && !prefs(ctx).getBoolean("on", false)) return
        prefs(ctx).edit().putBoolean("on", false).apply()
        _on.value = false
    }

    /** Quick-settings icons that stay in guest mode. */
    val QUICK = setOf("guest", "inputs")

    /**
     * What a phone camera reads as a Wi-Fi network: WIFI:T:WPA;S:<name>;P:<password>;; with \ ; , : and "
     * escaped. An open network has T:nopass and no password.
     */
    fun wifiCode(name: String, password: String, security: String): String {
        fun esc(s: String) = s.replace(Regex("""([\;,:"])"""), """\\$1""")
        return if (security == "nopass") "WIFI:T:nopass;S:${esc(name)};;" else "WIFI:T:$security;S:${esc(name)};P:${esc(password)};;"
    }
}

/** The Guest Wi-Fi icon's dialog: the QR code, the name and password to type, and guest mode. */
@Composable
fun GuestDialog(cfg: HomeConfig, accent: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val on by Guest.on.collectAsState()
    val first = remember { FocusRequester() }
    var askPin by remember { mutableStateOf(false) }
    val wifi = cfg.wifiName.isNotBlank()
    val code = remember(cfg.wifiName, cfg.wifiPassword, cfg.wifiSecurity) {
        if (!wifi) null else runCatching { QRCodeWriter().encode(Guest.wifiCode(cfg.wifiName, cfg.wifiPassword, cfg.wifiSecurity), BarcodeFormat.QR_CODE, 0, 0) }.getOrNull()
    }
    LaunchedEffect(askPin) { if (!askPin) runCatching { first.requestFocus() } }
    if (askPin) {
        Dialog(onDismissRequest = { askPin = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            PinPad(tr("Enter the PIN to end guest mode"), Guest.LOCK, accent) { askPin = false; Guest.end(ctx); onClose() }
        }
        return
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)), contentAlignment = Alignment.Center) {
            Row(Modifier.background(Color(0xF22D3A2E), RoundedCornerShape(20.dp)).padding(26.dp), horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
                if (code != null) Box(Modifier.size(230.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)) { Qr(code) }
                Column(Modifier.width(340.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Guest Wi-Fi"), color = Color(0xFFF0EBE5), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    if (!wifi) {
                        Text(tr("Type the Wi-Fi name and password once in CleanMyTV on a computer: Home screen step, Widgets tab."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                    } else {
                        Text(tr("Point your phone's camera at the code to join."), color = Color(0xFFD7CFC4), fontSize = 15.sp)
                        Text(cfg.wifiName, color = Color(0xFFF0EBE5), fontSize = 18.sp)
                        if (cfg.wifiSecurity != "nopass") Text(tr("Password: {password}", "password" to cfg.wifiPassword), color = Color(0xFFF0EBE5), fontSize = 18.sp)
                    }
                    if (on) Text(tr("Guest mode is on: Home shows only the guest apps until the TV goes to sleep."), color = accent, fontSize = 14.sp)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        UpdateButton(tr("Close"), accent, Modifier.focusRequester(first)) { onClose() }
                        if (on) UpdateButton(tr("End guest mode"), accent) {
                            if (cfg.lockOn && Lock.hasPin(ctx)) askPin = true else { Guest.end(ctx); onClose() }
                        } else if (cfg.guestApps.isNotEmpty()) UpdateButton(tr("Guest mode"), accent) { Guest.start(ctx); onClose() }
                    }
                }
            }
        }
    }
}
