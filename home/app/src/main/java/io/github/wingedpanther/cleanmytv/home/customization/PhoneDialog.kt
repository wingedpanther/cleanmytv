package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import io.github.wingedpanther.cleanmytv.home.PhoneUpload
import io.github.wingedpanther.cleanmytv.home.customization.components.DIALOG_BG
import io.github.wingedpanther.cleanmytv.home.customization.components.MUTED_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.PRIMARY_BUTTON_COLOR
import io.github.wingedpanther.cleanmytv.home.customization.components.TvSecondaryButton
import io.github.wingedpanther.cleanmytv.home.tr

/** The QR code a phone scans to send a photo; Home listens only while this is open. */
@Composable
fun PhoneDialog(ctx: Context, onDismiss: () -> Unit) {
    val url = remember { PhoneUpload.start(ctx) }
    DisposableEffect(Unit) { onDispose { PhoneUpload.stop() } }
    val note by PhoneUpload.note.collectAsState()
    val code = remember(url) { url?.let { runCatching { QRCodeWriter().encode(it, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull() } }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.Center) {
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(DIALOG_BG).padding(28.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (code != null) Box(Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)) { Qr(code) }
                Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Send a photo from your phone"), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    if (url == null) {
                        Text(tr("The TV has no network address right now. Check it's on Wi-Fi or a cable."), color = MUTED_COLOR, fontSize = 14.sp)
                    } else {
                        Text(tr("Scan the code with your phone's camera. Your phone has to be on the same Wi-Fi as the TV."), color = MUTED_COLOR, fontSize = 14.sp)
                        Text(tr("Or open {url}", "url" to url), color = MUTED_COLOR, fontSize = 12.sp)
                        Text(tr("The address stops working when you close this, or after 10 minutes."), color = MUTED_COLOR, fontSize = 12.sp)
                        if (note.isNotEmpty()) Text(note, color = PRIMARY_BUTTON_COLOR, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TvSecondaryButton(text = tr("Close"), onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Qr(m: BitMatrix) {
    Canvas(Modifier.fillMaxSize()) {
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
