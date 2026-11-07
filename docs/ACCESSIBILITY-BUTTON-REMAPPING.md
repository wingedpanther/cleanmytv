# Accessibility Service Remote Button Interception Specification

This document details the architecture, configuration, and customization guidelines for using an **Android Accessibility Service** to intercept and remap remote control hardware buttons (such as the `Home` button, Netflix, YouTube, or vendor hotkeys) in **CleanMyTV Home**.

---

## Overview & Architecture

Android Accessibility Services run at the system level and receive low-level hardware input events before foreground applications process them. By intercepting key events in `onKeyEvent()` and returning `true`, the launcher can consume specific keypresses—preventing the default stock TV action (such as launching the factory Home launcher or default sponsored app) and executing custom launcher behavior instead.

---

## 1. Core Logic (`KeyService.kt`)

**Location in project:**  
`home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/KeyService.kt`

This class extends Android's `AccessibilityService`. Button presses are captured in the `onKeyEvent` function.

### Method Signature & Overriding:

```kotlin
// File: KeyService.kt
// Location: home/app/src/main/java/io/github/wingedpanther/cleanmytv/home/KeyService.kt

class KeyService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        val keyCode = event.keyCode

        // Intercept the HOME button when pressed down
        if (keyCode == KeyEvent.KEYCODE_HOME && action == KeyEvent.ACTION_DOWN) {
            
            // Launch your custom launcher activity (MainActivity)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            
            // Return TRUE to consume the event so stock Google launcher doesn't open
            return true 
        }

        // Intercept branded remote buttons (e.g., Netflix, YouTube, or vendor keys)
        if (keyCode == KeyEvent.KEYCODE_PROG_RED && action == KeyEvent.ACTION_DOWN) {
            // Custom action or app launch logic here
            return true
        }

        // Let all other button presses pass through normally to foreground apps
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent) {
        // Accessibility event processing if needed
    }

    override fun onInterrupt() {}
}
```

### Key Rules for `onKeyEvent`:
1. **Consuming Events (`return true`)**: Return `true` when a key press is handled. This prevents the operating system or stock launcher from receiving the key event.
2. **Passing Through Events (`return false` / `super.onKeyEvent(event)`)**: Return `false` or `super.onKeyEvent(event)` for unmapped keys to allow standard apps to receive D-pad navigation and back button input.
3. **Filtering Key Action**: Ensure button actions are checked for `KeyEvent.ACTION_DOWN` or `KeyEvent.ACTION_UP` to prevent duplicate execution when keys are pressed and released.

---

## 2. Accessibility Service Configuration XML

For Android to route hardware button events to the `AccessibilityService`, key event filtering must be explicitly enabled in XML.

**Location in project:**  
`home/app/src/main/res/xml/key_service.xml`

### Required Manifest XML Declaration:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagRequestFilterKeyEvents"
    android:canRequestFilterKeyEvents="true"
    android:canRetrieveWindowContent="false"
    android:description="@string/key_service_description"
    android:notificationTimeout="100" />
```

### Critical Attribute:
* **`android:accessibilityFlags="flagRequestFilterKeyEvents"`**: Without this flag (and `android:canRequestFilterKeyEvents="true"`), Android will silently drop hardware key events and `onKeyEvent()` will not be called.
* **Programmatic Fallback (`KeyService.kt`)**: Some TV firmware builds ignore XML flags. Re-applying the flag during `onServiceConnected()` guarantees enforcement:
  ```kotlin
  override fun onServiceConnected() {
      serviceInfo = serviceInfo.apply { 
          flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS 
      }
  }
  ```

---

## 3. Android TV Setup & Manual Service Activation

Because Accessibility Services possess elevated system privileges, Android OS security requires users to manually grant permission on the TV.

### Enabling the Service on Android TV:
1. Open the TV **Settings**.
2. Navigate to **Device Preferences** > **Accessibility** (or **System** > **Accessibility** on Android TV 12+).
3. Select **CleanMyTV Home** under Installed Services.
4. Toggle the switch to **Enable / On** and confirm the permissions dialog.

### ADB Automated Grant Option:
During automated installation via ADB, the service can also be toggled into the Android secure settings table:

```bash
adb shell settings put secure enabled_accessibility_services io.github.wingedpanther.cleanmytv.home/.KeyService
adb shell settings put secure accessibility_enabled 1
```

Once activated, pressing the mapped hardware keys (such as `HOME` or custom hotkeys) routes directly to `KeyService`, invoking the custom Jetpack Compose interface and bypassing stock system UI launcher defaults.

---

## 4. Troubleshooting: Why External Tools (e.g., TV Box Tools / Launch Manager) Were Needed

On many stock Android TV / Google TV / Fire TV builds (Chromecast with Google TV, Xiaomi Mi Box, Shield TV, TCL, etc.):

1. **SystemUI Home Priority**: The system UI intercepts `KEYCODE_HOME` (`3`) at a higher priority before standard activity declarations can process it.
2. **Missing Explicit HOME Key Trap**: `KeyService.kt` previously only trapped keycodes explicitly listed in `cfg.buttons`. If `KEYCODE_HOME` (`code = 3`) was not explicitly present in the button map, `KeyService` returned `false`, allowing the operating system to pass `HOME` to the stock Google/Amazon launcher.
3. **The Fix (Built-in Home Key Trap)**: By explicitly catching `KeyEvent.KEYCODE_HOME` inside `KeyService.kt`:
   ```kotlin
   if (event.keyCode == KeyEvent.KEYCODE_HOME && event.action == KeyEvent.ACTION_DOWN) {
       val intent = Intent(this, MainActivity::class.java).apply {
           addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
       }
       startActivity(intent)
       return true // Swallows HOME so stock Google/Amazon launcher never opens
   }
   ```
   This eliminates the dependency on third-party utilities like TV Box Tools or Launch Manager on locked-down devices.
