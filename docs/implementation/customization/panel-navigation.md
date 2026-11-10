# Android TV Customization Navigation & UI Architecture

## Overview & Architecture

This document describes the D-pad focus management, slide navigation pattern, and UI component architecture implemented for the **CleanMyTV** Launcher Customization screen using Jetpack Compose TV.

The design implements a **Google TV-inspired Settings Navigation Pattern**: a dual-pane layout where entering a category smoothly translates the entire layout leftward, allowing the settings panel to occupy the primary viewing space while preserving fixed panel dimensions.

---

## 1. UI & Visual Architecture

### Full-Screen Page Surface
- **Solid Page Background:** The root container fills 100% of the screen (`fillMaxSize()`) with a solid `PANEL_BG` (`Color(0xFF1E2836)`), eliminating all system dialog window margins, translucent backdrops, and top/side padding bleed-through.
- **Direct Full-Screen Overlay:** The customization overlay is rendered directly on top of the home screen as a full-screen Compose composable without wrapping inside an Android `DialogWindow` (which would introduce platform insets and dimming).

### Left Category Drawer Styling
- **Grey Background Container:** The menu list sits inside a `Color.White.copy(alpha = 0.05f)` card with `RoundedCornerShape(12.dp)`.
- **Zero Inner Margin & 2dp Spacing:** Items touch the horizontal container edges with `Arrangement.spacedBy(2.dp)` vertical spacing between category items.
- **Active & Focused State Colors:**
  - **Selected / Active Item:** `PRIMARY_BUTTON_COLOR` (`Color(0xFFA8C7FA)`) background with `Color.Black` bold text.
  - **Focused Item:** `Color.White` background with `CUSTOMIZATION_BG` (`Color(0xFF0B1D33)`) dark text.
  - **Unselected Item:** Transparent background with `Color.White` text.

### Header Bar & Helper Elements
- **Back Button:** Uses Material AutoMirrored `ArrowBack` icon (`Icons.AutoMirrored.Rounded.ArrowBack`) + "Categories" label inside a rounded `Surface`.
- **Height Stability:** Header row uses a fixed height `Modifier.height(40.dp)` to prevent any vertical layout shifts during navigation transitions.
- **Smooth Button Animation:** `AnimatedVisibility` wraps the back button using `expandHorizontally` + `fadeIn` on enter, and `shrinkHorizontally` + `fadeOut` on exit so the main title gracefully slides horizontally without jumping.
- **Reusable `Alert` Component:**
  - `Alert(text, type)` component located at `components/Alert.kt`.
  - Supports `INFO` (Primary Blue `0xFFA8C7FA`), `SUCCESS` (Green `0xFF4CAF50` with 10% opacity background), `WARNING` (Amber `0xFFFFA000`), and `ERROR` (Red `0xFFE53935`).
  - Used in top-right header ("Press BACK to go back"), in `LayoutCustomization` hint bars, and inline inside `BackupCustomization` next to Export/Import buttons.

---

## 2. Interaction & Slide Navigation Modes

### Mode 1: Category Overview (`inContentPane = false`)
* **Layout:** 2-pane split view (Left category drawer 280dp, Right settings panel `totalWidth - 280.dp`).
* **Sidebar Remote Controls:**
  - `D-pad Up / Down`: Moves highlight between category items.
  - `OK / Enter / D-pad Right`: Activates the selected category (`selectedCategory = cat`) and slides into Active Settings Mode (`inContentPane = true`).
  - `Back`: Dismisses the customization screen (`onDismiss()`).

### Mode 2: Active Settings Mode (`inContentPane = true`)
* **Layout:** The entire `Row` translates left by `-280.dp` (`slideOffset`). The Right Settings Panel maintains a **fixed width** (`totalWidth - 280.dp`) and slides flush to the left screen padding ($x = 0$), while the Left Drawer slides off-screen into clipped bounds (`clipToBounds()`).
* **Settings Navigation:**
  - `D-pad Up / Down / Left / Right`: Controls belong 100% to internal settings (sliders, color swatches, segmented controls, app reordering) with zero global `Left` key interception.
  - `Back` Key or clicking `← Categories`: Translates the layout back right, restores the category drawer, and returns focus directly to the active category item in the left menu.

---

## 3. Focus Management & Target Resolution

### Asynchronous Composition Handoff
To prevent Compose TV focus eviction during layout shifts, focus handoffs are executed asynchronously via `LaunchedEffect`:

```kotlin
LaunchedEffect(inContentPane, selectedCategory) {
    if (inContentPane) {
        delay(150) // Wait for slide animation and layout pass
        runCatching {
            headerBackFocusRequester.requestFocus()
            focusManager.moveFocus(FocusDirection.Down)
        }
    } else {
        delay(50) // Wait for Left Drawer to mount
        runCatching { categoryFocusRequesters[selectedCategory]?.requestFocus() }
    }
}
```

### Deterministic State Re-creation (`key(selectedCategory)`)
Wrapping the settings panel inside `key(selectedCategory)` forcefully destroys the previous tab's scroll position and composable state when switching categories. This prevents stale focus targets or random focus jumps across category switches.

### Direct Navigation Routing (`focusProperties`)
To ensure pressing `UP` from the top-most setting item reliably moves focus to the `← Categories` Back button (regardless of horizontal alignment or button position), the settings `LazyColumn` uses programmatic focus routing:

```kotlin
LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .focusProperties {
            up = headerBackFocusRequester
        }
)
```

---

## 4. Key UX Benefits

1. **Zero Accidental Menu Jumps:** `Left` and `Right` D-pad keys remain 100% available for complex setting controls (like color pickers or sliders) without accidentally jumping back to the sidebar.
2. **Fixed-Width Slide Stability:** The right settings panel translates smoothly without width expansion, eliminating layout re-measurement stutter or text re-wrapping.
3. **Seamless 100% Opaque Surface:** Unified dark background card ensures zero translucent borders or top padding gaps.
