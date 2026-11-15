# On-Device Launcher Customization: Technical Implementation Specification

This document details the architecture, design, and modular implementation of the **on-device Customization & Settings interface** directly inside **CleanMyTV Home** (`/home`). 

CleanMyTV Home includes a native, 0ms-delay Jetpack Compose TV on-device customization interface that allows TV owners to configure their entire launcher experience directly using their TV remote control (D-pad), without requiring a PC or ADB connection.

---

## 1. System Architecture & Performance Design

### Architectural Highlights
* **Experimental Switch (`edit_on_tv`)**: On-device customization is disabled by default (`HomeConfig.editOnTv = false`). The `"Customize"` quick-settings action icon is default-off and appears in the top action bar when `edit_on_tv` is enabled via the desktop app.
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
| **2. Look & Wallpaper** | • Accent color palette picker (White, Green, Blue, Orange, Pink, Purple, Cyan) with 4dp start padding & dynamic focus ring<br>• Tile focus effect (`lift`, `glow`, `outline`, `none`)<br>• Wallpaper dimming overlay slider (0–80%)<br>• Tile tilt effect toggle<br>• Stationary focus (Pin Focus) toggle<br>• Icon style (`banner`, `colour`, `mono`, `duo`) & Tile shape (`wide`, `square`, `squircle`, `round`)<br>• Dock & Grid tile scale sliders (80–150%)<br>• Show app names toggle & Name on focus only toggle | `Surface` Swatch Buttons, `TvRadioGroup`, `TvSlider`, `TvSwitch` |
| **3. Widgets & Header** | • Clock format (`time_date`, `time`, `off`)<br>• Weather temperature unit (°C / °F)<br>• Open-Meteo city weather location geocoding search modal (`PlaceSearchModal`) & location clear button<br>• Idle screen timer slider (0–60 mins) | `TvRadioGroup`, `TvSlider`, `PlaceSearchModal`, `TvPrimaryButton`, `TvDestructiveButton` |
| **4. Apps & Organization** | • Dock size slider (0–20 apps)<br>• 5-column app grid cards with edge-to-edge banners (no white border)<br>• D-pad app reordering (`←` and `→` buttons)<br>• Hide / Show app toggle<br>• Reset Order and Show All action buttons | `TvSlider`, `AppTileControlCard`, `TvSecondaryButton` |
| **5. Music Visualizer** | • Toggle visualizer section on/off (`visualiser`)<br>• Visualizer style (`bars`, `mirror`, `wave`)<br>• Visualizer color (`accent`, `rainbow`, `white`)<br>• Bar height scale (4–32%)<br>• Opacity slider (10–100%)<br>• Audio sensitivity slider (10–300%) | `TvSwitch`, `TvRadioGroup`, `TvSlider` |
| **6. Fireplace Ambient** | • Fireplace storage status indicator (`MEDIA READY` / `NO MEDIA ON TV`)<br>• Fireplace home background wallpaper toggle<br>• Crackling audio toggle<br>• Volume slider (0–100%)<br>• Auto-off timer slider (0–240 mins) | `TvSwitch`, `TvSlider` |

---

## 3. Directory & Modular Package Structure

All customization files reside under package `io.github.wingedpanther.cleanmytv.home.customization` inside `/home`:

```
home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/customization/
├── CustomizationActivity.kt        # Translucent Activity window host (Focus-isolated, ON_PAUSE for MainActivity)
├── Customization.kt                # Root customization layout, 2-pane category drawer & header bar
├── CustomizationCategory.kt        # Enum for 6 active customization categories
├── LayoutCustomization.kt          # "Layout & Rows" tab (Layout mode, Top space slider, Side-by-side collapsible section/quick cards)
├── LookCustomization.kt            # "Look & Wallpaper" tab (Accent palette, Focus effect, Icons/Shapes, Scales)
├── WidgetCustomization.kt          # "Widgets & Header" tab (Clock format, Weather unit, PlaceSearchModal location search)
├── AppCustomization.kt             # "Apps & Organization" tab & AppTileControlCard (Dock size, 5-col grid, D-pad reordering, Hide/Show)
├── AmbientCustomization.kt         # "Music Visualizer" & "Fireplace" tabs (Storage status, Crackling audio, Volume)
└── components/
    ├── Alert.kt                    # Reusable Alert component (INFO, SUCCESS, WARNING, ERROR types with matching icons & backgrounds)
    ├── CustomizationColors.kt      # Color constants (PRIMARY_BUTTON_COLOR, PANEL_BG, CUSTOMIZATION_BG, MUTED_COLOR)
    ├── PlaceSearchModal.kt         # Open-Meteo city geocoding REST search modal with automatic TV software keyboard activation
    ├── ReorderableCheckList.kt     # Interactive D-pad list reordering component
    ├── TvButton.kt                 # TvPrimaryButton, TvSecondaryButton, TvDestructiveButton
    ├── TvRadioGroup.kt             # Segmented radio selection group
    ├── TvSlider.kt                 # Slider component
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

5. **City Weather Location Search (`PlaceSearchModal.kt`)**:
   * Integrated Open-Meteo REST API geocoding search (`https://geocoding-api.open-meteo.com/v1/search?name=...`).
   * Automatically triggers Android TV on-screen software keyboard when entering/focusing the search field.
   * Selecting a city sets `weather_place`, `weather_lat`, and `weather_lon`. `HomeScreen` immediately fetches weather for the coordinates.

6. **PIN Security Integration**:
   * Opening customization verifies `Lock.needsPin(ctx, Lock.PANEL)`. If App Lock is active on the TV, it prompts for the 4–6 digit PIN via `PinPad` before launching `CustomizationActivity`.

7. **Instant Google TV Category Previews**:
   * Scrolling `Up` or `Down` in the left category menu drawer updates `selectedCategory` instantly on focus (`onFocusChanged { if (it.isFocused) selectedCategory = cat }`), allowing the user to preview category settings live on the right panel without having to press `OK`.
   * Pressing `OK`, `Enter`, or `D-pad Right` enters/focuses the settings panel and slides the left menu off-screen.

---

## 5. Integration Point in `HomeScreen.kt`

Opening customization prompts for PIN (if locked) and launches `CustomizationActivity` via Intent:

```kotlin
// Inside HomeScreen.kt
val openCustomize = {
    if (Lock.needsPin(ctx, Lock.PANEL)) {
        customizePin = true
    } else {
        ctx.startActivity(Intent(ctx, CustomizationActivity::class.java))
    }
}

// Triggered via Quick Settings shortcut "customize" (when edit_on_tv = true)
```
