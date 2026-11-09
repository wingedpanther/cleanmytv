# Android TV Launcher Customization Navigation

## Objective

Improve D-pad navigation and focus management in the existing Launcher Customization screen.

> [!IMPORTANT]
> Keep the existing two-pane UI layout. Do not hide the sidebar, expand the settings panel, introduce full-screen category navigation, or redesign the existing interface.

The goal is to make navigation between the left sidebar and right settings panel intuitive and predictable, following native Android TV settings navigation conventions.

---

## 1. Existing Layout

The screen contains two areas:
- **Left Sidebar:** Customization categories such as Layout & Rows, Look & Wallpaper, Widgets & Header, Apps & Organization, Remote Buttons, etc.
- **Right Panel:** Settings and controls belonging to the selected category.

> [!NOTE]
> Both areas must remain visible at all times.

---

## 2. Initial Screen Behaviour

- Restore the previously selected category, if available.
- Initially focus the selected category in the sidebar.
- The right panel should display its settings without automatically taking focus.

---

## 3. Sidebar Navigation

When focus is in the sidebar:
- **D-pad Up/Down:** Navigate between categories.
- **OK/Enter:** Select the focused category and move focus to the first interactive setting in the right panel.
- **D-pad Right:** Enter the settings panel, following the same selection and focus behaviour.
- **D-pad Left:** No action unless the existing screen has a parent navigation destination.

> [!IMPORTANT]
> Moving focus between sidebar categories should **not** automatically change the active category. Only `OK` or `Right` should activate the category.

---

## 4. Settings Panel Navigation

When focus enters the right panel:
- Move focus to the first interactive setting when entering a category for the first time.
- Restore the previously focused setting when returning to a category.
- **D-pad Up/Down:** Navigate vertically between interactive settings.
- **D-pad Left/Right:** Handle navigation or value changes according to the currently focused control.
- **OK/Enter:** Activate or edit the focused setting.
- **Back:** Return focus to the currently selected sidebar category.

---

## 5. Context-Aware Horizontal Navigation

> [!WARNING]
> Do not globally intercept D-pad Left/Right events. Different controls must handle horizontal navigation independently.

### Control Behaviors
- **Color selectors:**
  - `Left/Right`: Navigate between color options.
  - `Up/Down`: Navigate to other settings.
- **Segmented controls:**
  - `Left/Right`: Navigate between options.
  - `Up/Down`: Navigate to other settings.
- **Sliders:**
  - `Left/Right`: Decrease/increase values.
  - `Up/Down`: Leave the slider and navigate to other settings.
- **Buttons:**
  - `Left`: Return focus to the selected sidebar category.
  - `Right/OK`: Activate the button as appropriate.
- **Other controls:**
  - Follow their natural horizontal interaction behaviour.
  - Return to the sidebar using `Left` only when the focused control does not have a meaningful `Left` action.

> [!IMPORTANT]
> Never allow horizontal control interactions to accidentally move focus into the sidebar.

---

## 6. Focus Management

Maintain separate states for:
- Currently selected sidebar category.
- Currently focused sidebar category.
- Current focus zone (`SIDEBAR` or `SETTINGS_PANEL`).
- Last focused setting ID for each category.

### Requirements
- Use stable IDs to identify settings.
- Preserve focus when navigating between sidebar and settings panel.
- When returning to the sidebar, focus the currently selected category.
- When entering the panel again, restore the last-focused setting.
- If no previous focus exists, focus the first interactive setting.
- Ensure focus automatically scrolls into view when necessary.
- Avoid unnecessary focus resets during recomposition.

---

## 7. Compose Implementation

Use appropriate Jetpack Compose APIs:
- `FocusRequester`
- `FocusProperties`
- `onPreviewKeyEvent` / `onKeyEvent`
- `FocusState`
- `BackHandler` where appropriate.

### Implementation Guidelines
- Keep focus management reusable and avoid implementing separate navigation logic for every category.
- Allow individual controls to consume their own D-pad Left/Right events before applying sidebar navigation behaviour.
- Avoid duplicate event handling and focus loops.

---

## 8. Edge Cases

Test the following scenarios:
- Switching between all customization categories.
- Returning to the sidebar from different settings controls.
- Re-entering a category and restoring the previous focus.
- Horizontal color selector navigation.
- Segmented control navigation.
- Slider adjustments.
- Buttons and dialogs.
- Scrolling long settings panels.
- Categories with different numbers and types of controls.
- Rapid D-pad navigation.
- Focus restoration after dialogs close.

---

## 9. Implementation Constraints

- **Tech Stack:** Kotlin and Jetpack Compose.
- **Layout:** Preserve the existing two-pane layout and visual design.
- **Restrictions:** 
  - Do not introduce full-screen category navigation.
  - Do not hide or collapse the sidebar.
  - Avoid unnecessary dependencies.
  - Reuse existing components and architecture.
  - Do not break existing setting interactions.

---

## Completion Checklist

1. Inspect existing navigation and focus architecture.
2. Implement required focus management rules.
3. Verify D-pad remote navigation on real TV hardware / emulator.
4. Provide a summary of modified files, focus-management decisions, and test results.
