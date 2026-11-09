# On-Device Launcher Customization: Technical Implementation Specification

This document details the architecture, design, and modular implementation of the **on-device Customization & Settings interface** directly inside **CleanMyTV Home** (`/home`). 

CleanMyTV Home includes a native, 0ms-delay Jetpack Compose TV on-device customization screen that allows TV owners to configure their entire launcher experience directly using their TV remote control (D-pad), without requiring a PC or ADB connection.

---

## 1. System Architecture & Performance Design

### Architectural Highlights
* **0ms Composable Overlay Window**: Implemented as an on-demand Jetpack Compose TV `Dialog` overlay (`LauncherCustomizationDialog`) rendered inside `MainActivity`. This avoids the 300ms–1500ms window loading lag and RAM duplication of opening a separate Android Activity.
* **0% Idle Resource Usage**: When unmounted (`showCustomize = false`), the customization UI uses **0 bytes of RAM and 0% CPU**, keeping the launcher running at full 60fps.
* **Single Source of Truth (`ConfigStore.kt`)**: Any change in the customization panel triggers `ConfigStore.update(ctx, jsonObject)`, which immediately persists to `SharedPreferences` (`cleanmytv_home_config`) and emits updated `HomeConfig` via `StateFlow` for **instant real-time background recomposition**.
* **45% Translucent Backdrop (`Color(0x73000000)`)**: Changes to wallpaper dimming, accent colors, tile focus styles, or row ordering update the live home screen behind the panel in real time.

---

## 2. Settings Modules & Options Overview

| Module | Features & Controls | UI Control Component |
| :--- | :--- | :--- |
| **1. Layout & Rows** | • Launcher layout style (`shelf`, `split`, `rail`)<br>• Top space slider (0–100 dp)<br>• Active sections & ordering (`clock`, `quick`, `widgets`, `dock`, `grid`, `visualizer`) *(Collapsible)*<br>• Quick Settings shortcuts list & ordering *(Collapsible)* | `TvRadioGroup`, `TvSlider`, Side-by-Side `CollapsibleCard` |
| **2. Look & Wallpaper** | • Custom wallpaper image trigger (MediaStore & recursive TV storage scanner with 2-pane preview)<br>• Accent color palette picker (White, Green, Blue, Orange, Pink, Purple, Cyan) with 1.25x scale & ring border focus<br>• Tile focus effect (`lift`, `glow`, `border`, `none`)<br>• Wallpaper dimming overlay slider (0–80%)<br>• Tile tilt effect toggle<br>• Stationary focus (Pin Focus) toggle<br>• Dynamic app logo color tint toggle | `Surface` Swatch Buttons, `TvRadioGroup`, `TvSlider`, `TvSwitch`, `WallpaperPickerModal` |
| **3. Icons & Tiles** | • Icon style (`banner`, `colour`, `mono`, `duo`)<br>• Tile shape (`wide`, `square`, `squircle`, `round`)<br>• Dock tile size slider (80–150%)<br>• Grid tile size slider (80–150%)<br>• Show app names toggle<br>• Name on focus only toggle | `TvRadioGroup`, `TvSlider`, `TvSwitch` |
| **4. Widgets & Header** | • Clock format (`time_date`, `time`, `off`)<br>• Weather temperature unit (°C / °F)<br>• Idle screen timer slider (0–60 mins) | `TvRadioGroup`, `TvSlider` |
| **5. Apps & Organization** | • Dock size slider (0–20 apps)<br>• 5-column app grid cards with edge-to-edge banners<br>• D-pad app reordering (`←` and `→` buttons with fixed 24dp height & disabled focus skip)<br>• Hide / Show app toggle<br>• Reset Order and Show All action buttons | `TvSlider`, `AppTileControlCard`, `TvSecondaryButton` |
| **6. Remote Buttons** | • Interactive remote button capture modal (`KeyCaptureModal`) listening for live D-pad / remote keypresses<br>• Map key code to Launcher Actions or TV App launch<br>• Enable Remote Key Remapping master toggle (`buttons_on`)<br>• Remove key mapping action buttons | `TvSwitch`, `KeyCaptureModal`, `TvDestructiveButton` |
| **7. Music Visualizer** | • Toggle visualizer section on/off (`visualiser`)<br>• Visualizer style (`bars`, `mirror`, `wave`)<br>• Visualizer color (`accent`, `rainbow`, `white`)<br>• Bar height scale (4–32%)<br>• Opacity slider (10–100%)<br>• Audio sensitivity slider (10–300%) | `TvSwitch`, `TvRadioGroup`, `TvSlider` |
| **8. Fireplace Ambient** | • Fireplace storage status card & 1-click on-device direct downloader (11 MB)<br>• Fireplace home background wallpaper toggle<br>• Crackling audio toggle<br>• Volume slider (0–100%)<br>• Auto-off timer slider (0–240 mins) | `TvSwitch`, `TvSlider`, `TvPrimaryButton`, `TvSecondaryButton` |
| **9. App Lock & Security** | • On-screen PIN setup & confirmation modal (4–6 digits)<br>• PIN status badge & Change PIN / Clear PIN actions<br>• Enable App Lock master toggle<br>• Lock System Settings toggle (Android Settings, Play Store, Quick Settings)<br>• 5-column grid for individual app lock selection (`lock_apps`) with "Lock All" and "Unlock All" buttons<br>• Unlock duration (`sleep`, `15 mins`, `always`)<br>• Lock badge visibility (`badge`, `hide`) | `PinSetupModal`, `TvSwitch`, `TvRadioGroup`, 5-Column Lock Grid |
| **10. Default Launcher** | • Default Home app status & shortcut to System Home Settings<br>• Accessibility KeyService status indicator & shortcut to System Accessibility Settings<br>• Home Button Interception toggle | `TvSwitch`, `TvSecondaryButton` |
| **11. Backup & Restore** | • Export Config: Writes current JSON config to `/sdcard/Download/cleanmytv-config.json`<br>• Import Config: Reads JSON from `/sdcard/Download/cleanmytv-config.json` and updates `ConfigStore` | `TvSecondaryButton` |

---

## 3. Directory & Modular Package Structure

All customization files reside under package `io.github.wingedpanther.cleanmytv.home.customization` inside `/home`:

```
home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/customization/
├── LauncherCustomizationDialog.kt  # Root Compose Dialog overlay & 2-pane category drawer layout
├── CustomizationCategory.kt        # Enum for 11 customization categories
├── CustomizationComponents.kt      # Reusable D-pad controls (TvSwitch, TvRadioGroup, TvSlider, ReorderableCheckList, TvPrimaryButton, TvSecondaryButton, TvDestructiveButton)
├── LayoutCustomization.kt          # "Layout & Rows" tab (Layout mode, Top space slider, Side-by-side collapsible section/quick cards)
├── LookCustomization.kt            # "Look & Wallpaper" tab (Wallpaper picker trigger, Accent palette with 1.25x scale & ring border, Focus effect, Dimming, Tilt)
├── IconCustomization.kt            # "Icons & Tiles" tab (Icon style, Tile shape, Dock tile scale, Grid tile scale, Show app names, Name on focus)
├── WidgetCustomization.kt          # "Widgets & Header" tab (Clock format, Weather unit, Idle screen timer)
├── AppCustomization.kt             # "Apps & Organization" tab & AppTileControlCard (Dock size, 5-col grid, D-pad reordering, Hide/Show)
├── RemoteCustomization.kt          # "Remote Buttons" tab & KeyCaptureModal (Live key code listener, Action/App launch mapping, Removal)
├── SetupCustomization.kt           # "Default Launcher" tab (Default Launcher settings, Accessibility KeyService status, Home trap toggle)
├── SecurityCustomization.kt        # "App Lock" tab & PinSetupModal (PBKDF2 PIN creation & confirmation on TV, Lock system settings, 5-col lock grid)
├── AmbientCustomization.kt         # "Music Visualizer" & "Fireplace" tabs (Direct on-device 11MB fireplace downloader)
├── BackupCustomization.kt          # "Backup & Restore" tab (JSON export/import to /sdcard/Download/cleanmytv-config.json)
└── WallpaperPickerModal.kt         # TV Storage MediaStore & recursive file scanner 2-pane preview dialog
```

---

## 4. UI/UX Rules & Refined Remote Interactivity

1. **No Focus Scale Overflow (`focusedScale = 1f`)**:
   * All surfaces inside the customization drawer set `scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)`.
   * Disables 1.05x zoom scale animation so control items stay strictly within panel boundaries without clipping.

2. **Crisp Contrast Focus Highlighting**:
   * D-pad focus is indicated by a solid **White background container** (`focusedContainerColor = Color.White`) with dark `#0B1D33` (`CUSTOMIZATION_BG`) text/icons.

3. **Pixel-Perfect Vector Icons & Focus Tinting**:
   * App tile reordering (`AppTileControlCard`) and section reordering (`ReorderableCheckList`) use native Material vector icons (`ArrowBack`, `ArrowForward`, `ArrowUpward`, `ArrowDownward`) inside centered `Box` containers.
   * Focus states (`onFocusChanged`) dynamically tint vector arrows to `#0B1D33` when focused, white when unfocused, or 30% muted grey when disabled at list bounds.

4. **App Tile Cards (`AppTileControlCard`)**:
   * Render actual TV app banners (`app.art`) at 5 columns per row with `height(48.dp)` and `ContentScale.Crop` / `ContentScale.Fit`.
   * Edge-to-edge banner display without colored tint box or extra inner padding.
   * Hidden apps display a dark black overlay (`Color(0xEE000000)`) with a bold white `"HIDDEN"` label.

5. **On-Device Remote Key Capture & Remapping (`RemoteCustomization` + `KeyCaptureModal`)**:
   * Interactive listener modal: Press any remote button (e.g. Red, Green, Blue, Guide, Netflix, Prime) to capture its native key code.
   * Assign launcher action (`Settings`, `Wi-Fi`, `Inputs`, `Sleep`, `Quick Settings`, `Next Wallpaper`, `Home`, `Back`) or launch any installed TV app.
   * Automatically persists mapped key shortcuts into `HomeConfig.buttons`.

6. **On-Device App Lock & Security (`SecurityCustomization` + `PinSetupModal`)**:
   * Interactive TV PIN setup modal: enter 4 to 6-digit PIN and re-enter to confirm using remote number keys or D-pad number pad.
   * PBKDF2 salted/hashed PIN stored in private app storage (`Lock.setPin`).
   * "Clear PIN" action to wipe PIN and disable App Lock.
   * 5-column grid for individual app lock selection (`lock_apps`) with lock badge overlay and "Lock All" / "Unlock All" buttons.
   * Master toggle for system settings lock (`lock_system`).

7. **Backup & Restore (`BackupCustomization`)**:
   * Export config: Writes JSON to `/sdcard/Download/cleanmytv-config.json`.
   * Import config: Reads JSON from `/sdcard/Download/cleanmytv-config.json` and applies it instantly via `ConfigStore.update`.

8. **Quick Action Icon**:
   * Uses `Icons.Outlined.DisplaySettings` for the `"Customize"` Quick Settings shortcut.

---

## 5. Integration Point in `HomeScreen.kt`

In `HomeScreen.kt`, opening customization mounts `LauncherCustomizationDialog`:

```kotlin
// Inside HomeScreen.kt
var showCustomize by remember { mutableStateOf(false) }

// Trigger overlay via Quick Settings shortcut "customize"
if (showCustomize) {
    LauncherCustomizationDialog(cfg, apps) { showCustomize = false }
}
```
