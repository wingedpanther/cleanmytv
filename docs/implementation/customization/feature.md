# On-Device Launcher Customization: Technical Implementation Specification

This document details the architecture, design, and modular implementation of the **on-device Customization & Settings interface** directly inside **CleanMyTV Home** (`/home`). 

CleanMyTV Home includes a native, 0ms-delay Jetpack Compose TV on-device customization interface that allows TV owners to configure their entire launcher experience directly using their TV remote control (D-pad), without requiring a PC or ADB connection.

---

## 1. System Architecture & Performance Design

### Architectural Highlights
* **Dedicated Translucent Activity Window (`CustomizationActivity`)**: Implemented as a dedicated, lightweight `ComponentActivity` registered with `@android:style/Theme.Translucent.NoTitleBar.Fullscreen` and zero activity transition delay (`overridePendingTransition(0, 0)`). 
  - **100% Focus Isolation:** Operating in its own Android `Window` ensures that D-pad remote focus CANNOT physically leak or escape to background home screen elements under any circumstances.
  - **Background Pause (`ON_PAUSE`):** When `CustomizationActivity` is on screen, `MainActivity` enters `ON_PAUSE`, freezing background home screen recomposition passes and reducing GPU overdraw to 0%.
* **0% Idle Resource Usage**: When unmounted, the customization UI uses **0 bytes of RAM and 0% CPU**, keeping the launcher running at full 60fps.
* **Single Source of Truth (`ConfigStore.kt`)**: Any change in the customization panel triggers `ConfigStore.update(ctx, jsonObject)`, which immediately persists to disk (`config.json`) and emits updated `HomeConfig` via `StateFlow` for **instant real-time live sync**.
* **7% Translucent Backdrop (`PANEL_BG.copy(alpha = 0.93f)`)**: 7% transparent background overlay allowing the live home screen wallpaper and tiles to subtly show through the panel.

---

## 2. Settings Modules & Options Overview

| Module | Features & Controls | UI Control Component |
| :--- | :--- | :--- |
| **1. Layout & Rows** | • Launcher layout style (`shelf`, `split`, `rail`)<br>• Top space slider (0–100 dp)<br>• Active sections & ordering (`clock`, `quick`, `widgets`, `dock`, `grid`, `visualizer`) *(Collapsible)*<br>• Quick Settings shortcuts list & ordering *(Collapsible)*<br>• Unified `Alert` hint bar | `TvRadioGroup`, `TvSlider`, Side-by-Side `CollapsibleCard`, `Alert` |
| **2. Look & Wallpaper** | • Custom wallpaper image trigger (MediaStore & recursive TV storage scanner with 2-pane preview)<br>• Accent color palette picker (White, Green, Blue, Orange, Pink, Purple, Cyan) with focus scaling<br>• Tile focus effect (`lift`, `glow`, `border`, `none`)<br>• Wallpaper dimming overlay slider (0–80%)<br>• Tile tilt effect toggle<br>• Stationary focus (Pin Focus) toggle<br>• Icon style (`banner`, `colour`, `mono`, `duo`) & Tile shape (`wide`, `square`, `squircle`, `round`)<br>• Dock & Grid tile scale sliders (80–150%)<br>• Show app names toggle & Name on focus only toggle | `Surface` Swatch Buttons, `TvRadioGroup`, `TvSlider`, `TvSwitch`, `WallpaperPickerModal` |
| **3. Widgets & Header** | • Clock format (`time_date`, `time`, `off`)<br>• Weather temperature unit (°C / °F)<br>• Idle screen timer slider (0–60 mins) | `TvRadioGroup`, `TvSlider` |
| **4. Apps & Organization** | • Dock size slider (0–20 apps)<br>• 5-column app grid cards with edge-to-edge banners (no white border)<br>• D-pad app reordering (`←` and `→` buttons)<br>• Hide / Show app toggle<br>• Reset Order and Show All action buttons | `TvSlider`, `AppTileControlCard`, `TvSecondaryButton` |
| **5. Remote Buttons** | • Interactive remote button capture modal (`KeyCaptureModal`) listening for live D-pad / remote keypresses<br>• Map key code to Launcher Actions or TV App launch<br>• Enable Remote Key Remapping master toggle (`buttons_on`)<br>• Remove key mapping action buttons | `TvSwitch`, `KeyCaptureModal`, `TvDestructiveButton` |
| **6. Music Visualizer** | • Toggle visualizer section on/off (`visualiser`)<br>• Visualizer style (`bars`, `mirror`, `wave`)<br>• Visualizer color (`accent`, `rainbow`, `white`)<br>• Bar height scale (4–32%)<br>• Opacity slider (10–100%)<br>• Audio sensitivity slider (10–300%) | `TvSwitch`, `TvRadioGroup`, `TvSlider` |
| **7. Fireplace Ambient** | • Fireplace storage status card & 1-click on-device direct downloader (11 MB)<br>• Fireplace home background wallpaper toggle<br>• Crackling audio toggle<br>• Volume slider (0–100%)<br>• Auto-off timer slider (0–240 mins) | `TvSwitch`, `TvSlider`, `TvPrimaryButton`, `TvSecondaryButton`, `TvDestructiveButton` |
| **8. App Lock & Security** | • On-screen PIN setup & confirmation modal (4–6 digits)<br>• PIN status badge & Change PIN / Clear PIN actions<br>• Enable App Lock master toggle<br>• Lock System Settings toggle (Android Settings, Play Store, Quick Settings)<br>• 5-column grid for individual app lock selection (`lock_apps`) with "Lock All" and "Unlock All" buttons<br>• Unlock duration (`sleep`, `15 mins`, `always`)<br>• Lock badge visibility (`badge`, `hide`) | `PinSetupModal`, `TvSwitch`, `TvRadioGroup`, 5-Column Lock Grid |
| **9. Default Launcher** | • Default Home app status & shortcut to System Home Settings<br>• Accessibility KeyService status indicator & shortcut to System Accessibility Settings<br>• Home Button Interception toggle | `TvSwitch`, `TvSecondaryButton` |
| **10. Backup & Restore** | • Export Config: Writes current JSON config to app backup storage (`cleanmytv-config.json`) with inline `Alert(type = SUCCESS)` feedback<br>• Import Config: Reads JSON from app backup storage and applies it via `ConfigStore.update`<br>• Reset to Default: `TvDestructiveButton` calling `ConfigStore.reset(ctx)` to reset all customization back to factory defaults | `TvSecondaryButton`, `TvDestructiveButton`, `Alert` |

---

## 3. Directory & Modular Package Structure

All customization files reside under package `io.github.wingedpanther.cleanmytv.home.customization` inside `/home`:

```
home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/customization/
├── CustomizationActivity.kt        # Translucent Activity window host (Focus-isolated, ON_PAUSE for MainActivity)
├── Customization.kt                # Root customization layout, 2-pane category drawer & header bar
├── CustomizationCategory.kt        # Enum for 10 customization categories
├── LayoutCustomization.kt          # "Layout & Rows" tab (Layout mode, Top space slider, Side-by-side collapsible section/quick cards)
├── LookCustomization.kt            # "Look & Wallpaper" tab (Wallpaper picker, Accent palette, Focus effect, Icons/Shapes, Scales)
├── WidgetCustomization.kt          # "Widgets & Header" tab (Clock format, Weather unit, Idle screen timer)
├── AppCustomization.kt             # "Apps & Organization" tab & AppTileControlCard (Dock size, 5-col grid, D-pad reordering, Hide/Show)
├── RemoteCustomization.kt          # "Remote Buttons" tab & KeyCaptureModal (Live key code listener, Action/App launch mapping)
├── SetupCustomization.kt           # "Default Launcher" tab (Default Launcher settings, Accessibility KeyService status, Home trap toggle)
├── SecurityCustomization.kt        # "App Lock" tab & PinSetupModal (PBKDF2 PIN creation & confirmation on TV, 5-col lock grid)
├── AmbientCustomization.kt         # "Music Visualizer" & "Fireplace" tabs (Direct on-device 11MB fireplace downloader)
├── BackupCustomization.kt          # "Backup & Restore" tab (JSON export/import & Reset All Settings to Default)
├── WallpaperPickerModal.kt         # TV Storage MediaStore & recursive file scanner 2-pane preview dialog
└── components/
    ├── Alert.kt                    # Reusable Alert component (INFO, SUCCESS, WARNING, ERROR types with matching icons & backgrounds)
    ├── CustomizationColors.kt      # Color constants (PRIMARY_BUTTON_COLOR, PANEL_BG, CUSTOMIZATION_BG, MUTED_COLOR)
    ├── ReorderableCheckList.kt     # Interactive D-pad list reordering component
    ├── TvButton.kt                 # TvPrimaryButton, TvSecondaryButton, TvDestructiveButton
    ├── TvRadioGroup.kt             # Segmented radio selection group
    ├── TvSlider.kt                 # Slider component with 60fps local key handling
    └── TvSwitch.kt                 # Toggle switch component
```

---

## 4. UI/UX Rules & Refined Remote Interactivity

1. **No Focus Scale Overflow (`focusedScale = 1f`)**:
   * All surfaces inside the customization drawer set `scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)`.
   * Disables 1.05x zoom scale animation so control items stay strictly within panel boundaries without clipping.

2. **Crisp Contrast Focus Highlighting**:
   * D-pad focus is indicated by a solid **White background container** (`focusedContainerColor = Color.White`) with dark `#0B1D33` (`CUSTOMIZATION_BG`) text/icons.
   * Active/Selected category items use `PRIMARY_BUTTON_COLOR` (`Color(0xFFA8C7FA)`) container with `Color.Black` bold text.

3. **Header Bar & Back Navigation**:
   * Back button uses Material AutoMirrored `ArrowBack` icon (`Icons.AutoMirrored.Rounded.ArrowBack`) + "Categories" label inside a rounded `Surface`.
   * Header height is fixed at `40.dp` to eliminate vertical height fluctuations during navigation transitions.
   * `AnimatedVisibility` uses `expandHorizontally` + `fadeIn` on enter and `shrinkHorizontally` + `fadeOut` on exit for smooth horizontal title sliding.

4. **App Tile Cards (`AppTileControlCard`)**:
   * Render actual TV app banners (`app.art`) at 5 columns per row with `height(48.dp)` and `ContentScale.Crop` / `ContentScale.Fit`.
   * Clean edge-to-edge banner display without white border outlines.
   * Hidden apps display a dark black overlay (`Color(0xEE000000)`) with a bold white `"HIDDEN"` label.

5. **Reusable `Alert` Component (`components/Alert.kt`)**:
   * Standardized notification box used in headers, layout hint bars, and inline inside `BackupCustomization`.
   * Green `AlertType.SUCCESS` uses `Color(0xFF4CAF50).copy(alpha = 0.1f)` background with green `CheckCircle` icon.

6. **Reset All Settings to Default (`BackupCustomization.kt`)**:
   * Red `TvDestructiveButton` calling `ConfigStore.reset(ctx)` to wipe custom settings and restore factory default configuration.

7. **Instant Google TV Category Previews**:
   * Scrolling `Up` or `Down` in the left category menu drawer updates `selectedCategory` instantly on focus (`onFocusChanged { if (it.isFocused) selectedCategory = cat }`), allowing the user to preview category settings live on the right panel without having to press `OK`.
   * Pressing `OK`, `Enter`, or `D-pad Right` enters/focuses the settings panel and slides the left menu off-screen.

---

## 5. Integration Point in `HomeScreen.kt`

Opening customization launches `CustomizationActivity` via Intent:

```kotlin
// Inside HomeScreen.kt
val openCustomize = { ctx.startActivity(Intent(ctx, CustomizationActivity::class.java)) }

// Triggered via Quick Settings shortcut "customize" or menu action
```
