package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.Lock
import io.github.wingedpanther.cleanmytv.home.TvApp
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SecurityCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    var hasPin by remember { mutableStateOf(Lock.hasPin(ctx)) }
    var showPinModal by remember { mutableStateOf(false) }
    var pinMessage by remember { mutableStateOf("") }
    val lockedSet = remember(config.lockApps) { config.lockApps.toSet() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- PIN Status Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0x11FFFFFF))
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App Lock PIN Status:", color = MUTED_COLOR, fontSize = 12.sp)
                Text(
                    text = if (hasPin) "PIN SET & ACTIVE" else "NO PIN SET",
                    color = if (hasPin) Color(0xFF4CAF50) else Color(0xFFFFB23C),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { showPinModal = true },
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = PRIMARY_BUTTON_COLOR,
                        focusedContainerColor = Color.White,
                        contentColor = CUSTOMIZATION_BG,
                        focusedContentColor = CUSTOMIZATION_BG
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = if (hasPin) "Change PIN" else "Set App Lock PIN",
                        color = CUSTOMIZATION_BG,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (hasPin) {
                    Surface(
                        onClick = {
                            Lock.clearPin(ctx)
                            ConfigStore.update(ctx, JSONObject().put("lock_on", false))
                            hasPin = false
                            pinMessage = "PIN cleared and App Lock disabled"
                        },
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF882222)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "Clear PIN",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        if (pinMessage.isNotEmpty()) {
            Text(pinMessage, color = Color.White, fontSize = 12.sp)
        }

        TvSwitch(
            title = "Enable App Lock",
            subtitle = "Require PIN to launch selected apps",
            checked = config.lockOn,
            onCheckedChange = { on ->
                if (on && !hasPin) {
                    showPinModal = true
                } else {
                    ConfigStore.update(ctx, JSONObject().put("lock_on", on))
                }
            }
        )

        TvSwitch(
            title = "Lock System Settings",
            subtitle = "Require PIN for Android Settings, Play Store and Quick Settings",
            checked = config.lockSystem,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("lock_system", it)) }
        )

        // --- Locked Apps List / Grid ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Select Locked Apps (${config.lockApps.size} locked)", color = TEXT_COLOR, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Check an app to require PIN before launching.", color = MUTED_COLOR, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvSecondaryButton(
                    text = "Lock All",
                    onClick = {
                        val allPkgs = apps.map { it.pkg }
                        ConfigStore.update(ctx, JSONObject().put("lock_apps", JSONArray(allPkgs)))
                    }
                )

                TvSecondaryButton(
                    text = "Unlock All",
                    onClick = {
                        ConfigStore.update(ctx, JSONObject().put("lock_apps", JSONArray()))
                    }
                )
            }
        }

        // --- App Cards for Lock Selection ---
        val appRows = remember(apps) { apps.chunked(5) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            appRows.forEach { rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowApps.forEach { app ->
                        val isLocked = app.pkg in lockedSet
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = {
                                val nextLocked = lockedSet.toMutableSet()
                                if (isLocked) nextLocked.remove(app.pkg) else nextLocked.add(app.pkg)
                                ConfigStore.update(ctx, JSONObject().put("lock_apps", JSONArray(nextLocked.toList())))
                            },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isLocked) Color(0x44FFFFFF) else Color(0x11FFFFFF),
                                focusedContainerColor = Color.White,
                                focusedContentColor = CUSTOMIZATION_BG
                            ),
                            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isFocused = it.isFocused }
                        ) {
                            Column(
                                modifier = Modifier.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x22FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        bitmap = app.art,
                                        contentDescription = app.label,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = if (app.banner) ContentScale.Crop else ContentScale.Fit
                                    )

                                    if (isLocked) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0x99000000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Lock,
                                                contentDescription = "Locked",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = app.label,
                                    color = if (isFocused) CUSTOMIZATION_BG else TEXT_COLOR,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = if (isLocked) "LOCKED" else "UNLOCKED",
                                    color = if (isFocused) CUSTOMIZATION_BG else if (isLocked) Color(0xFF4CAF50) else MUTED_COLOR,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    repeat(5 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // --- Lock Duration & Visibility Options ---
        TvRadioGroup(
            title = "Unlock Duration",
            options = listOf("sleep" to "Until Sleep", "15" to "15 Minutes", "always" to "Always Ask"),
            current = config.lockFor,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("lock_for", it)) }
        )

        TvRadioGroup(
            title = "Lock Icon Visibility",
            options = listOf("badge" to "Show Lock Badge", "hide" to "Hide Locked Apps"),
            current = config.lockShow,
            onSelect = { ConfigStore.update(ctx, JSONObject().put("lock_show", it)) }
        )
    }

    if (showPinModal) {
        PinSetupModal(
            ctx = ctx,
            onDismiss = { showPinModal = false },
            onSuccess = {
                hasPin = true
                pinMessage = "PIN set successfully!"
                showPinModal = false
            }
        )
    }
}

@Composable
private fun PinSetupModal(
    ctx: Context,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(1) } // 1: Enter PIN, 2: Confirm PIN
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Enter a 4 to 6-digit PIN") }

    val currentPin = if (step == 1) pin1 else pin2

    fun type(digit: Char) {
        if (currentPin.length >= 6) return
        if (step == 1) {
            pin1 += digit
            note = ""
        } else {
            pin2 += digit
            note = ""
        }
    }

    fun deleteDigit() {
        if (step == 1) pin1 = pin1.dropLast(1) else pin2 = pin2.dropLast(1)
    }

    fun submit() {
        if (step == 1) {
            if (pin1.length < 4) {
                note = "PIN must be at least 4 digits"
                return
            }
            step = 2
            note = "Re-enter PIN to confirm"
        } else {
            if (pin2 != pin1) {
                note = "PINs do not match. Try again."
                step = 1
                pin1 = ""
                pin2 = ""
                return
            }
            Lock.setPin(ctx, pin1)
            ConfigStore.update(ctx, JSONObject().put("lock_on", true))
            onSuccess()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(380.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2836))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(imageVector = Icons.Outlined.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(
                    text = if (step == 1) "Set App Lock PIN" else "Confirm PIN",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                // PIN Digits Indicator
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(maxOf(4, currentPin.length)) { i ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (i < currentPin.length) Color.White else Color(0x33FFFFFF))
                        )
                    }
                }

                if (note.isNotEmpty()) {
                    Text(note, color = Color(0xFFFFB23C), fontSize = 13.sp)
                }

                // D-Pad Number Keys (1-9, Del, 0, OK)
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "del", "0", "ok")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { k ->
                                var isFocused by remember { mutableStateOf(false) }
                                Surface(
                                    onClick = {
                                        when (k) {
                                            "del" -> deleteDigit()
                                            "ok" -> submit()
                                            else -> type(k[0])
                                        }
                                    },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = if (k == "ok") Color(0xFF4CAF50) else Color(0x22FFFFFF),
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = CUSTOMIZATION_BG
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
                                    modifier = Modifier
                                        .size(width = 96.dp, height = 48.dp)
                                        .onFocusChanged { isFocused = it.isFocused }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        when (k) {
                                            "del" -> Icon(
                                                imageVector = Icons.Outlined.Backspace,
                                                contentDescription = "Delete",
                                                tint = if (isFocused) CUSTOMIZATION_BG else Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            "ok" -> Text(
                                                "OK",
                                                color = if (isFocused) CUSTOMIZATION_BG else Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            else -> Text(
                                                k,
                                                color = if (isFocused) CUSTOMIZATION_BG else Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvSecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss
                )
            }
        }
    }
}
