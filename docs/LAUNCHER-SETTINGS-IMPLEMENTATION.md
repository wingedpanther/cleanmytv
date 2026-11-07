# On-Device Launcher Settings: Technical Implementation Plan

This document details the architecture, design, and implementation specification for building an **on-device Settings & Customization interface** directly inside **CleanMyTV Home** (`/home`). 

Currently, full customization (layout, themes, icon styles, widgets, app lock, remote key mapping) is managed primarily via the desktop Electron app over ADB. Adding an on-device settings screen enables TV owners to customize their launcher directly using only their TV remote control (D-pad).

---

## 1. Executive Summary & Possibility Assessment

### Is it possible?
**Yes, 100% possible.** The Android TV launcher app (`/home`) already possesses the full foundation needed:
* **Reactive Configuration Store (`ConfigStore.kt`)**: Reads and writes `HomeConfig` directly to Android `SharedPreferences` (`cleanmytv_home_config`) and exposes a reactive `StateFlow<HomeConfig>`. Any call to `ConfigStore.update(context, jsonObject)` immediately updates state and triggers recomposition across the Jetpack Compose TV UI.
* **Compose for TV Foundations (`HomeScreen.kt`, `Panel.kt`, `Carousel.kt`)**: The app already renders TV-native overlays, side panels, and focusable surfaces using `androidx.tv.material3` components.
* **Hardware Remote Input (`KeyService.kt`)**: Remote keys and D-pad events are already captured and handled natively.

Adding an on-device settings screen requires no architectural redesign—only creating new Jetpack Compose TV settings screen composables and wiring them to `ConfigStore`.

---

## 2. System Architecture & Data Flow

```
+-----------------------------------------------------------------------------------+
|                            CleanMyTV Home TV Launcher                             |
|                                                                                   |
|  +--------------------------------+          +---------------------------------+  |
|  |     Desktop App (via ADB)      |          |    On-Device Settings UI      |  |
|  |  `content call ... set64 ...`  |          | (LauncherSettingsScreen.kt)     |  |
|  +---------------+----------------+          +----------------+----------------+  |
|                  |                                            |                   |
|                  +---------------------+----------------------+                   |
|                                        v                                          |
|                       +----------------------------------+                        |
|                       |  ConfigStore.kt (SharedPreferences) |                        |
|                       +----------------+-----------------+                        |
|                                        |                                          |
|                                        v                                          |
|                        StateFlow<HomeConfig> Emission                             |
|                                        |                                          |
|                                        v                                          |
|                       +----------------------------------+                        |
|                       |      HomeScreen.kt (Compose)     |                        |
|                       |    Recomposes UI in Real Time    |                        |
|                       +----------------------------------+                        |
+-----------------------------------------------------------------------------------+
```

### Key Principles:
1. **Single Source of Truth**: `ConfigStore` remains the sole authority for launcher state.
2. **Zero Desktop Reliance**: Mutating `ConfigStore` on the TV immediately persists changes locally to `SharedPreferences`.
3. **Seamless Desktop Sync**: When the desktop Electron app connects later via ADB `content call ... get`, it receives the updated `HomeConfig` set on the TV.

---

## 3. UX & Navigation Design (Remote-First D-Pad UI)

### Screen Layout: Two-Pane Category Navigation
```
+-----------------------------------------------------------------------------------+
|  SETTINGS                                                            [Back / Esc] |
+-----------------------+-----------------------------------------------------------+
|                       |                                                           |
|  > Layout & Rows      |  Layout Style                                             |
|    Look & Wallpaper   |  [  Shelf (Default)  ]   [   Split   ]   [   Rail   ]     |
|    Icons & Tiles      |                                                           |
|    Widgets & Clock    |  Top Space Above Apps                                     |
|    App Lock           |  [---o------------------------]  25%                      |
|    Remote Buttons     |                                                           |
|    Music Visualizer   |  Quick Settings Items                                     |
|    Fireplace          |  [x] Inputs  [x] Wi-Fi  [x] Settings  [ ] Bluetooth       |
|                       |                                                           |
+-----------------------+-----------------------------------------------------------+
```

### Navigation Rules:
* **Opening Settings**: Triggered via the **`Customize`** Quick Settings icon (`QuickAction("customize", "Customize", ...)`), Quick Settings panel shortcut (`quickActions()["panel"]`), or long-pressing Home/Menu on the remote control.
* **Left Pane (Categories)**: Vertical list of category headers. Navigating up/down moves focus between categories. Pressing `D-pad Right` moves focus into the right pane's controls.
* **Right Pane (Controls)**: Category-specific settings controls (Radio Selectors, Sliders, Switches, Color Pickers).
* **Closing / Back**: Pressing `Back` on the remote returns focus from controls to the left pane, or closes the settings screen if focused on the category pane.

---

## 4. Settings Modules & Options

The on-device settings screen will cover all options supported by `HomeConfig`:

| Module | Features & Controls | UI Control Component |
| :--- | :--- | :--- |
| **1. Layout & Rows** | • Layout style (`shelf`, `split`, `rail`)<br>• Top space (0–40%)<br>• Section order & section toggle (`nowplaying`, `dock`, `rows`, `folders`, `grid`)<br>• Dock scale & tile scale sliders (60–150%)<br>• Grid columns (3–8 columns) | Segmented Button, D-pad Slider, Reorderable List |
| **2. Look & Wallpaper** | • Theme selector (Switch preset built-in themes)<br>• Accent color palette picker<br>• Background color picker<br>• Focus animation style (`lift`, `glow`, `outline`, `none`)<br>• Wallpaper dim level (0–80%)<br>• Focus motion effects (`tilt`, `pin_focus`, `follow_colour`) | Theme Swatch Grid, Color Picker Modal, Toggle Switch |
| **3. Icons & Tiles** | • Icon style (`banner`, `colour`, `mono`, `duo`)<br>• Tile shape (`wide`, `square`, `squircle`, `round`)<br>• App name display (`always`, `focus_only`, `off`) | Image Preview Tile Group, Toggle Switch |
| **4. Widgets & Header** | • Clock format (`time_date`, `time`, `off`)<br>• Personalized greeting text<br>• Live Weather location & temperature unit (°C / °F)<br>• Idle screen timer (0–60 mins) | Selection Group, TV On-Screen Keyboard / Quick Presets |
| **5. Apps & Organization** | • Dock size slider (0–8 apps)<br>• App visibility (hide/show installed apps)<br>• Row & folder management | Focusable App Grid, Group Editor Dialog |
| **6. Music Visualizer** | • Toggle visualizer on/off<br>• Visualizer style (`bars`, `mirror`, `wave`)<br>• Visualizer height, bar count, opacity, sensitivity<br>• Color style (`accent`, `rainbow`, `white`) | D-pad Sliders, Segmented Button |
| **7. Fireplace Ambient** | • Fireplace placement (full screen, screensaver, background wallpaper)<br>• Sound toggle & volume slider<br>• Auto-off timer | Selection List, Slider |
| **8. App Lock & Security** | • PIN setup & verification modal (4–6 digits)<br>• Lock toggle for launcher & system settings<br>• Unlock duration (`sleep`, `15 mins`, `always`)<br>• Lock visibility (`badge`, `hide`) | On-Screen PinPad (`PinPad.kt`), Selection Group |
| **9. Remote Buttons** | • Key code detection listener<br>• Action mapper (Inputs, Settings, App Launch, Home, Back, Next Wallpaper) | Key Detection Overlay, Action Picker |
| **10. Backup & Restore** | • **Export Config**: Save current launcher settings as JSON file to TV storage (`/sdcard/Download/cleanmytv-config.json`) or copy base64 config string<br>• **Import Config**: Load and apply JSON config file from local storage to restore complete layout, colors, app order, widgets & shortcuts | Action Buttons, File Picker Dialog / Modal |

---

## 5. Technical Implementation Steps

### Phase 1: Core Components & Layout (`home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/`)

Create the following new Kotlin files in `/home`:

1. **`LauncherSettingsScreen.kt`**:
   * Root full-screen overlay component rendered inside `Dialog` or `Box` on top of `HomeScreen`.
   * Manages current category selection state and focus delegation between Left Pane and Right Pane.

2. **`components/SettingsControls.kt`**:
   * Reusable TV D-pad optimized control components:
     * `TvSlider`: Left/Right arrow keys adjust slider values with visual progress bar and percentage readout.
     * `TvRadioGroup`: Horizontal/Vertical selectable options with focus highlight.
     * `TvColorPicker`: Color swatch grid with D-pad navigation.
     * `TvSwitchRow`: Label + status chip toggle activated via D-pad `OK / Select`.

3. **`components/AppPickerModal.kt`**:
   * D-pad accessible grid overlay for selecting apps to include in dock, rows, folders, or app lock lists.

---

### Phase 2: Code Blueprint & Sample Implementation

#### A. Updating `ConfigStore.kt` Helper Function
Ensure `ConfigStore` provides a direct mutation helper for local launcher UI components:

```kotlin
// ConfigStore.kt
fun updateKey(ctx: Context, key: String, value: Any) {
    val currentJson = JSONObject(load(ctx).toJson().toString())
    currentJson.put(key, value)
    update(ctx, currentJson)
}
```

#### B. Component Blueprint (`LauncherSettingsScreen.kt`)

```kotlin
// File: LauncherSettingsScreen.kt
// Location: home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/LauncherSettingsScreen.kt

enum class SettingsCategory(val title: String) {
    LAYOUT("Layout & Rows"),
    LOOK("Look & Wallpaper"),
    ICONS("Icons & Tiles"),
    WIDGETS("Widgets & Header"),
    APPS("Apps & Folders"),
    VISUALIZER("Music Visualizer"),
    FIREPLACE("Fireplace"),
    REMOTE("Remote Buttons"),
    SECURITY("App Lock"),
    BACKUP("Backup & Restore")
}

@Composable
fun LauncherSettingsScreen(
    config: HomeConfig,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.LAYOUT) }
    val accentColor = Color(config.accent)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5101712)) // Dark translucent background
            .padding(32.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // --- Left Pane: Category Navigation ---
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight()
                    .padding(end = 24.dp)
            ) {
                Text(
                    text = "Launcher Settings",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(SettingsCategory.values()) { _, cat ->
                        val isSelected = cat == selectedCategory
                        Surface(
                            onClick = { selectedCategory = cat },
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = if (isSelected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                focusedContainerColor = accentColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = cat.title,
                                color = if (isSelected) accentColor else Color.White,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // --- Right Pane: Category Content Controls ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0x33FFFFFF))
                    .padding(24.dp)
            ) {
                when (selectedCategory) {
                    SettingsCategory.LAYOUT -> LayoutSettingsPanel(config, ctx)
                    SettingsCategory.LOOK -> LookSettingsPanel(config, ctx)
                    SettingsCategory.ICONS -> IconSettingsPanel(config, ctx)
                    SettingsCategory.WIDGETS -> WidgetSettingsPanel(config, ctx)
                    SettingsCategory.VISUALIZER -> VisualizerSettingsPanel(config, ctx)
                    SettingsCategory.SECURITY -> AppLockSettingsPanel(config, ctx)
                    else -> Text(text = "Category details coming soon", color = Color.White)
                }
            }
        }
    }
}
```

---

### Phase 3: Integration into `HomeScreen.kt`

In `HomeScreen.kt`, add state control to toggle `LauncherSettingsScreen` overlay when opened via Quick Settings bar or D-pad shortcut:

```kotlin
// Inside HomeScreen.kt
var showOnDeviceSettings by remember { mutableStateOf(false) }

// Trigger overlay
if (showOnDeviceSettings) {
    Dialog(
        onDismissRequest = { showOnDeviceSettings = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LauncherSettingsScreen(
            config = cfg,
            onClose = { showOnDeviceSettings = false }
        )
    }
}
```

---

## 6. Verification & Testing Strategy

1. **Remote Navigation Test**: Verify that D-pad navigation (`Up`, `Down`, `Left`, `Right`, `Select`, `Back`) moves focus smoothly between categories and controls without losing focus.
2. **Instant Recomposition Test**: Change accent color or layout style on the TV settings overlay and verify that the background home screen updates instantly behind the overlay.
3. **Persist Test**: Restart the TV or force-stop the launcher app; ensure modified settings persist via `SharedPreferences`.
4. **Desktop App Interoperability**: Open the desktop Electron app after making TV on-device changes, connect via ADB, and verify that the desktop app reads the updated config without conflicts.

---

## 7. Next Steps for Development

1. Add `"customize"` to `QuickId` in `@shared/api.ts`, `QUICK` in `src/main/home.ts`, `ALL_QUICK` in `HomeLauncher.tsx`, `QUICK_GLYPH`/`QUICK_LABEL` in `HomePreview.tsx`, and `QuickActions.kt` in `/home`. *(Completed)*
2. Implement `LauncherSettingsScreen.kt` and sub-panels in `/home`.
3. Connect the **`Customize`** (`"customize"`) shortcut icon in `HomeScreen.kt` `onQuick` handler to trigger `showOnDeviceSettings = true`.
4. Test remote D-pad focus handling across all settings tabs on real Android TV devices / emulators.
