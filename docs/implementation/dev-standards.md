# Android TV Development Standards and Review Checklist

A working standard for Android TV and launcher development, also usable as a code review checklist. Platform details change between releases, so check the current docs at developer.android.com/tv (especially the TV app quality checklist and Compose for TV guidance) before treating any specific number or API as final.

## 1. Core principles

- **10-foot, D-pad first.** Assume no touch, no mouse and a remote with Up/Down/Left/Right, OK, Back and Home. Anything a user can't reach with those keys is a bug.
- **Predictability over cleverness.** Focus moves where the user expects, Back always goes back, and nothing traps the user.
- **Low-end hardware is the baseline.** Plan for 1–2 GB RAM, slow storage and weak GPUs (Mi Box S class), not a flagship Shield.
- **Fail safe.** Anything that replaces or disables system components, like a launcher, needs a documented recovery path.

## 2. Manifest and app identity

- Declare `android.software.leanback` and set `android.hardware.touchscreen` to `required="false"`.
- Use `CATEGORY_LEANBACK_LAUNCHER` for the main activity and provide a 320×180 dp banner.
- A launcher also declares `HOME` and `DEFAULT` categories. Use `singleTask` and handle `onNewIntent`, since the Home key re-delivers intents.
- Export only what must be exported, and request the minimum permissions. Treat `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES` and accessibility services as privileged, with a written justification for each.

## 3. Focus and navigation

This is the most common source of TV bugs.

- Every interactive element is focusable, with a clearly visible focus state (scale, border or glow). Don't rely on color alone.
- Focus order follows visual order. When you leave and return to a list, restore focus to the previously focused item (`focusRestorer`, saved indices).
- Set an explicit initial focus with `FocusRequester`, after the node is composed.
- Back must always work. Modal modes such as reorder mode consume D-pad keys but must exit on Back, and the exit must be visible on screen.
- Handle both key down and key up consistently. Use `onPreviewKeyEvent` only when you truly need to intercept before children, and return `true` only for keys you handled.
- Support long-press explicitly, not by accident, and never make it the only way to reach a feature.
- Debounce key repeat on sliders and lists so holding a key doesn't flood state writes.

## 4. Layout and visual design

- Design at 960×540 dp (1080p). Keep content inside the overscan safe area, about 5% margins (roughly 48 dp horizontal, 27 dp vertical).
- Use dark, low-glare themes and test contrast against arbitrary user wallpapers. Dim or scrim as needed.
- Use readable type, at least about 14 sp for secondary text and larger for primary, because users sit far away.
- Focus effects should be subtle (about 1.05–1.15x scale). Avoid layout shifts when focus changes.
- Prefer the Compose for TV libraries (`androidx.tv:tv-material`) over reimplementing cards, carousels and lists. Check the current status of `tv-foundation` lazy lists, since parts of it have been folded into standard lazy layouts.

## 5. Performance

- Provide Baseline Profiles and measure with Macrobenchmark. Aim for no dropped frames while scrolling.
- Use stable `key`s in lazy lists, hoist state and avoid recomposition storms. Check with the Layout Inspector's recomposition counts.
- Load images at target size only, with a bounded cache (Coil/Glide). Never decode full-size wallpapers on the main thread.
- Do disk, network and package scanning on `Dispatchers.IO`. Add timeouts, depth limits and cancellation to anything recursive, such as storage scans.
- Enable R8 and resource shrinking in release builds, and watch APK size and cold-start time.
- Use `StrictMode` in debug builds. Idle cost should be near zero: no polling timers or leaked collectors, and every `Flow` collected lifecycle-aware.

## 6. Architecture

- Use unidirectional data flow: `ViewModel` exposes `StateFlow<UiState>`, the UI emits events, and a repository owns persistence.
- Keep one source of truth for config. Prefer DataStore to `SharedPreferences`, and batch or debounce writes.
- Version your persisted schema and write migrations. Validate and clamp every value you read from outside the process (backups, ContentProviders, intents).
- Keep UI components free of business logic so they are testable. A reorderable list, for example, should be a reusable component driven by state, with the state machine tested separately from the composable.
- Use DI (Hilt or manual constructor injection), coroutines for async work, and no global singletons holding `Context`.

## 7. Security and privacy

- Hash secrets like PINs with a salt and a slow KDF, and keep keys in the Android Keystore. Never log them. Note that `EncryptedSharedPreferences` is deprecated, so look at Keystore plus Tink or DataStore.
- Disallow cleartext traffic via `networkSecurityConfig`. Download only over HTTPS, verify checksums, write to a temp file and rename atomically.
- Don't read or write config from world-readable paths like `/sdcard/Download` without validation, and never put secrets in exports. Use scoped storage, MediaStore or SAF on API 29+.
- Treat all intents and ContentProvider input as untrusted. Use permission-protected providers and `FileProvider` for shared files.
- Accessibility services see every key event. Request the minimum capabilities (`canRetrieveWindowContent=false` where possible), never log key codes and document why the service exists. If you ever distribute through Play, check its accessibility policy.
- Be honest in the UI and docs about what a lock does. A launcher-level app lock is a convenience, not a security boundary, since ADB and other launchers bypass it.

## 8. Launcher-specific rules

- Handle HOME presses deterministically, without relying on key interception that varies by firmware. Prefer the standard HOME intent-filter and the system's default-home mechanisms, and treat accessibility interception as an optional enhancement.
- Load app banners and icons with `PackageManager` (`getApplicationBanner`, `loadBanner`), and handle apps that supply neither.
- Keep package-visibility `<queries>` declarations narrow, and handle apps being installed or removed while the launcher is running.
- Always ship a documented rollback, such as re-enabling the stock launcher via ADB, and test it. Any feature that can lock the user out (settings lock, PIN) needs a recovery path.

## 9. Accessibility

- Provide `contentDescription` or semantics for all non-text elements, and test with TalkBack on TV.
- Respect system font scale and display size, and don't encode meaning in color alone.

## 10. Testing and compatibility

- **Unit tests:** ViewModels, reducers and state machines, config migration and validation.
- **Compose UI tests:** inject D-pad key events (`performKeyInput`) and assert focus. Add screenshot tests for focus states.
- **Device matrix:** at least one low-RAM box, one Google TV, one vendor ROM (Xiaomi, TCL, Sony) and the emulator at multiple API levels. Fire TV is its own platform, so test it separately if you support it.
- **Manual script with a real remote:** cold start, Home from inside other apps, long-press, key repeat, Back from every screen, and rotating through all the settings.
- Go through Google's TV app quality checklist before release.

## 11. PR and release standards

- Small, single-purpose PRs (a few hundred lines is a healthy target). Split features from refactors from docs.
- No IDE, build or cache files in commits (`../../.idea`, `*.iml`, `build/`, local properties). Keep the `.gitignore` honest.
- CI runs lint, ktlint or detekt, unit tests and a release build. PRs include test evidence (device, API level, remote model) and screenshots or short recordings of focus behavior.
- Keep docs accurate and avoid unmeasurable claims such as "0ms" or "0% CPU". State what you tested instead.
- Sign releases consistently so updates install over earlier builds. Publish checksums, and bump the version and any version manifest in the same PR that changes the artifact.

## Quick review checklist

1. Can I do everything with only a remote, and can I always get out with Back?
2. Is focus visible, ordered and restored?
3. Does it hold up on 1–2 GB RAM with no main-thread I/O?
4. Are secrets hashed and external input validated?
5. Is there a recovery path if it breaks the Home experience?
6. Is it tested on real hardware, and is the PR small enough to review?
