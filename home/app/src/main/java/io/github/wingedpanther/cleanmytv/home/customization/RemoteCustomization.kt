package io.github.wingedpanther.cleanmytv.home.customization

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowDropUp
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SettingsRemote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import io.github.wingedpanther.cleanmytv.home.ButtonMap
import io.github.wingedpanther.cleanmytv.home.ConfigStore
import io.github.wingedpanther.cleanmytv.home.HomeConfig
import io.github.wingedpanther.cleanmytv.home.TvApp
import org.json.JSONArray
import org.json.JSONObject

val AVAILABLE_BUTTON_ACTIONS = listOf(
    "settings" to "System Settings",
    "wifi" to "Wi-Fi Settings",
    "inputs" to "TV Inputs",
    "bluetooth" to "Bluetooth Settings",
    "display" to "Picture & Sound",
    "apps" to "All Apps",
    "sleep" to "Screensaver / Sleep",
    "search" to "Voice / Search",
    "music" to "Music Bars",
    "panel" to "Quick Settings Panel",
    "themes" to "Themes Carousel",
    "next_wallpaper" to "Next Wallpaper",
    "home" to "Go to Home Screen",
    "back" to "Back Key",
    "none" to "Disable / Swallow Key",
    "app" to "Launch Specific App..."
)

fun getButtonLabel(b: ButtonMap, apps: List<TvApp>): String {
    if (b.action == "app" && !b.app.isNullOrEmpty()) {
        val found = apps.find { it.pkg == b.app }
        return "Launch: ${found?.label ?: b.app}"
    }
    return AVAILABLE_BUTTON_ACTIONS.find { it.first == b.action }?.second ?: b.action.replaceFirstChar { it.uppercase() }
}

fun formatKeyCodeName(code: Int): String {
    val name = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "").replace("_", " ")
    return "$name (Code $code)"
}

@Composable
fun RemoteCustomization(config: HomeConfig, ctx: Context, apps: List<TvApp>) {
    var showCaptureModal by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvPrimaryButton(
                text = "Remap Remote Key",
                onClick = { showCaptureModal = true },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }

        TvSwitch(
            title = "Enable Remote Key Remapping",
            subtitle = "Intercept configured remote keys and trigger assigned actions",
            checked = config.buttonsOn,
            onCheckedChange = { ConfigStore.update(ctx, JSONObject().put("buttons_on", it)) }
        )

        // --- Configured Remote Keys List ---
        Text("Configured Key Mappings (${config.buttons.size})", color = TEXT_COLOR, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

        if (config.buttons.isEmpty()) {
            Text(
                text = "No custom remote keys configured yet. Click 'Remap Remote Key' above and press any key on your remote control to assign an action.",
                color = MUTED_COLOR,
                fontSize = 13.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                config.buttons.forEach { button ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x11FFFFFF))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatKeyCodeName(button.code),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getButtonLabel(button, apps),
                                color = PRIMARY_BUTTON_COLOR,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TvDestructiveButton(
                            text = "Remove",
                            onClick = {
                                val nextList = config.buttons.filter { it.code != button.code }
                                val jsonArr = JSONArray()
                                nextList.forEach { item ->
                                    val obj = JSONObject().put("code", item.code).put("action", item.action)
                                    if (item.action == "app" && !item.app.isNullOrEmpty()) obj.put("app", item.app)
                                    jsonArr.put(obj)
                                }
                                ConfigStore.update(ctx, JSONObject().put("buttons", jsonArr))
                            },
                            icon = { Icon(imageVector = Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp)) }
                        )
                    }
                }
            }
        }
    }

    if (showCaptureModal) {
        KeyCaptureModal(
            apps = apps,
            onDismiss = { showCaptureModal = false },
            onSaveMapping = { code, action, pkg ->
                val nextButtons = config.buttons.filter { it.code != code }.toMutableList()
                nextButtons.add(ButtonMap(code, action, if (action == "app") pkg else null))

                val jsonArr = JSONArray()
                nextButtons.forEach { item ->
                    val obj = JSONObject().put("code", item.code).put("action", item.action)
                    if (item.action == "app" && !item.app.isNullOrEmpty()) obj.put("app", item.app)
                    jsonArr.put(obj)
                }

                ConfigStore.update(ctx, JSONObject().put("buttons", jsonArr).put("buttons_on", true))
                showCaptureModal = false
            }
        )
    }
}

@Composable
private fun KeyCaptureModal(
    apps: List<TvApp>,
    onDismiss: () -> Unit,
    onSaveMapping: (code: Int, action: String, pkg: String?) -> Unit
) {
    var capturedCode by remember { mutableIntStateOf(-1) }
    var selectedAction by remember { mutableStateOf("settings") }
    var selectedAppPkg by remember { mutableStateOf(apps.firstOrNull()?.pkg ?: "") }
    var showAppDropdown by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF0000000))
                .onPreviewKeyEvent { e ->
                    if (capturedCode == -1) {
                        val nativeCode = (e.nativeKeyEvent as? KeyEvent)?.keyCode ?: -1
                        if (nativeCode > 0) {
                            if (e.type == KeyEventType.KeyDown) {
                                if (nativeCode == KeyEvent.KEYCODE_BACK || nativeCode == KeyEvent.KEYCODE_ESCAPE) {
                                    onDismiss()
                                } else {
                                    capturedCode = nativeCode
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                    }
                    false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(540.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E2836))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(imageVector = Icons.Outlined.SettingsRemote, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))

                if (capturedCode == -1) {
                    Text("Press Remote Button Now", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Press any button on your TV remote control (e.g. Red, Green, Yellow, Blue, Guide, Netflix, Prime) to map its action.", color = MUTED_COLOR, fontSize = 13.sp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Listening for key press...", color = Color(0xFFFFB23C), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }

                    Surface(
                        onClick = onDismiss,
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0x33FFFFFF)),
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                    ) {
                        Text("Cancel", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                } else {
                    Text("Key Captured: ${formatKeyCodeName(capturedCode)}", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Select the action to trigger when this button is pressed:", color = MUTED_COLOR, fontSize = 13.sp)

                    // Actions Selector List
                    LazyColumn(
                        modifier = Modifier
                            .height(if (selectedAction == "app" && showAppDropdown) 280.dp else 220.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(AVAILABLE_BUTTON_ACTIONS) { (actionKey, label) ->
                            val isSelected = selectedAction == actionKey
                            var isFocused by remember { mutableStateOf(false) }

                            Surface(
                                onClick = {
                                    selectedAction = actionKey
                                    if (actionKey == "app") showAppDropdown = true
                                },
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) Color.White else Color(0x11FFFFFF),
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = CUSTOMIZATION_BG
                                ),
                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Text(
                                    text = label,
                                    color = if (isFocused || isSelected) CUSTOMIZATION_BG else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                )
                            }
                        }

                        if (selectedAction == "app") {
                            item {
                                Text("Select Target App to Launch:", color = TEXT_COLOR, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                                
                                val selectedApp = apps.find { it.pkg == selectedAppPkg } ?: apps.firstOrNull()
                                Surface(
                                    onClick = { showAppDropdown = !showAppDropdown },
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = PRIMARY_BUTTON_COLOR,
                                        focusedContainerColor = Color.White,
                                        contentColor = CUSTOMIZATION_BG,
                                        focusedContentColor = CUSTOMIZATION_BG
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Target App: ${selectedApp?.label ?: selectedAppPkg}",
                                            color = CUSTOMIZATION_BG,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            imageVector = if (showAppDropdown) Icons.Outlined.ArrowDropUp else Icons.Outlined.ArrowDropDown,
                                            contentDescription = "Expand App List",
                                            tint = CUSTOMIZATION_BG
                                        )
                                    }
                                }
                            }

                            if (showAppDropdown) {
                                items(apps) { app ->
                                    val isSelectedApp = selectedAppPkg == app.pkg
                                    var isFocusedApp by remember { mutableStateOf(false) }

                                    Surface(
                                        onClick = {
                                            selectedAppPkg = app.pkg
                                            showAppDropdown = false
                                        },
                                        colors = ClickableSurfaceDefaults.colors(
                                            containerColor = if (isSelectedApp) Color(0x44FFFFFF) else Color(0x11FFFFFF),
                                            focusedContainerColor = Color.White,
                                            focusedContentColor = CUSTOMIZATION_BG
                                        ),
                                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(6.dp)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp)
                                            .onFocusChanged { isFocusedApp = it.isFocused }
                                    ) {
                                        Text(
                                            text = app.label,
                                            color = if (isFocusedApp) CUSTOMIZATION_BG else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelectedApp) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        TvSecondaryButton(
                            text = "Cancel",
                            onClick = onDismiss
                        )

                        TvPrimaryButton(
                            text = "Save Mapping",
                            onClick = {
                                onSaveMapping(capturedCode, selectedAction, if (selectedAction == "app") selectedAppPkg else null)
                            }
                        )
                    }
                }
            }
        }
    }
}
