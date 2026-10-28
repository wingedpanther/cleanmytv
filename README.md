# CleanMyTV

A Mac and Windows app for [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by
[@gathint](https://x.com/gathint). It debloats an Android TV, installs the clean launcher and lets you
customise it, without Python, Java, Android Studio or `adb` on your computer.

New to this? The **[user guide](docs/USER-GUIDE.md)** walks every step at TV-owner pace, including
rollback and troubleshooting, and the **[known-devices table](docs/KNOWN-DEVICES.md)** shows what the
bundled lists cover per model. This README is the technical overview.

Questions, help and home-screen setups: join the **[CleanMyTV Discord](https://discord.gg/6hZeQkDYf9)**.
Bugs and feature requests go to [GitHub issues](https://github.com/wingedpanther/cleanmytv/issues).

This is an independent project, not part of cleanyourtv. The debloat lists, themes and launcher come
from upstream unchanged; this app is the desktop front end for them. Tested on a Xiaomi Mi Box S from
Windows.

![Customise: drag apps into order, the first row is the dock, with a live sketch of the TV home](docs/customise.png)

## Download and install

Get the latest version from [Releases](https://github.com/wingedpanther/cleanmytv/releases):

| Computer | File |
|---|---|
| Mac with Apple silicon (M1 and later) | `CleanMyTV-<version>-arm64.dmg` |
| Mac with Intel | `CleanMyTV-<version>.dmg` |
| Windows 10 or 11 | `CleanMyTV-Setup-<version>.exe` |

The installers are not signed with a paid developer certificate, so your computer warns you the first
time:

- **Mac:** open the dmg and drag CleanMyTV to Applications, then open it. When macOS says it can't
  verify the developer, choose **Done**, go to System Settings → Privacy & Security and press
  **Open Anyway** next to CleanMyTV (on macOS 14 and earlier, right-click the app and choose **Open**
  instead). macOS asks whether CleanMyTV may find devices on your local network: choose **Allow**, or
  it cannot reach the TV.

  If macOS says CleanMyTV **is damaged and can't be opened**, you have v0.1.0, whose Mac build was
  not signed correctly. Run this in Terminal, then open the app again:

  ```bash
  xattr -cr /Applications/CleanMyTV.app
  codesign --force --deep --sign - /Applications/CleanMyTV.app
  ```
- **Windows:** when SmartScreen says it protected your PC, choose **More info**, then **Run anyway**.

## Before you start: on the TV

1. Settings → Device Preferences → About (on some Google TVs: Settings → System → About).
2. Press OK on **Build** (or **Android TV OS build**) seven times, until the TV says you are a developer.
3. Back one screen → **Developer options** → turn on **USB debugging**. If you also see
   **Network debugging** or **ADB debugging**, turn that on too.
4. Keep the TV on and on the same Wi-Fi as your computer.

Open CleanMyTV, find the TV and choose **Allow** when the TV asks. Tick **Always allow from this
computer** to skip the question next time.

## What it does

| Step | What happens |
|---|---|
| **Setup** | Checks the network, finds TVs with port 5555 open, connects, and waits while you press Allow on the TV. Google TVs that only offer Wireless debugging get a pairing form. |
| **Clean up** | Picks the upstream list that matches your TV's model. Every package shows its live state on the TV, and you choose what to disable. If your TV isn't listed, it offers the packages other TVs disable. |
| **Launcher** | Installs the bundled launcher and hands Home over. If Home doesn't land on it, it rolls back. Optionally enables the Input-key and Home-key helpers. |
| **Customise** | Drag apps into order (the first row is the dock), hide apps, set columns and rows, upload a wallpaper or a rotating set, adjust dim, background colour, time-of-day sky, greeting, festival skin, motion and remote button mappings. Reads the TV's current `config.json` first, and can take a screenshot of the TV. |
| **TV extras** | Clock screensaver, ad-blocking Private DNS, animation speed, launcher language, an on-screen remote, reboot. |
| **Finish** | Walks through switching debugging off, then checks that port 5555 is closed. |

| | |
|---|---|
| ![Setup, connected to a TV](docs/setup.png) | ![Clean up: the list that matches the TV, with each package's state](docs/clean-up.png) |

Nothing is uninstalled. Packages are switched off for your user, and Restore puts every package back
the way it was before the first cleanup.

## Coming back to customise

Turn USB debugging on again, open the app, and it reconnects to the TV you used last time. When the
clean launcher is already Home it opens Customise, loaded with what is on the TV now. Switch debugging
off again when you're done. If you didn't revoke the authorisations, there's no Allow prompt.

If the router gave the TV a new address, or another device now answers at the old one, the app says so
and scans the network. You pick the TV from the list; the app does not log in to devices it found by
itself.

## Security and privacy

- **What it talks to.** Only the TV address you choose, over ADB on port 5555 of your local network.
  The optional downloads (Java, the Android SDK, Google's `adb`, the launcher source) come from
  Adoptium, dl.google.com and GitHub over HTTPS, and only when you click them. The background
  sources in Customise fetch from www.bing.com, or from api.themoviedb.org and image.tmdb.org with
  your own TMDB key, only when you use them. No analytics, no accounts, no server of its own.
- **Your keys.** The app makes its own ADB key in its data folder and signs only with that. It never
  uses `~/.android/adbkey`, which your phones may also trust, and it only logs in to an address you
  picked. Network ADB is plaintext by design, so use it on a network you trust.
- **Pinned upstream.** The lists, themes and launcher source come from one upstream commit pinned in
  [`upstream.json`](upstream.json) with a content hash. A build refuses anything that doesn't match, so
  a change upstream reaches this app only after someone reviews it and moves the pin.
- **The launcher signing key ships inside the app, on purpose.** `resources/signing/cleanmytv.jks`
  (password `cleanyourtv`) identifies the sideloaded launcher for upgrades, so a "Build from source"
  copy installs over the bundled one. It gives no access to a TV: installing anything still needs ADB
  access the owner allowed.
- **Close debugging when you're done.** Some TVs (`ro.adb.secure=0`) accept any device on the network
  without the Allow prompt; the app warns you if yours is one. The Finish step checks that port 5555 is
  closed. The launcher keeps working without debugging.
- **Window lockdown.** The app window is sandboxed, cannot navigate away from its own page, and only
  that page may call into the part that talks to the TV.

Found a security problem? Please report it privately through the repository's
**Security → Report a vulnerability** page rather than in a public issue.

## How it relates to upstream

The app is a TypeScript port of upstream's `tools/` and `launcher/build-and-install.sh`:

- `src/main/adb/` is an ADB-over-TCP client, the twin of `tools/tv.py`.
- `src/main/debloat.ts` has the same guards as `disable.sh`: it won't disable the app that owns Home while nothing else can take it, and it won't disable setupwraith while it owns the Wi-Fi network. It writes the same recovery record to `~/.cleanyourtv/`, plus the TV's device name so the record follows the TV to a new address. `tests/engine.test.ts` checks that the record names and the restore batch match upstream `recovery.py` exactly, so `tools/restore.sh ~/.cleanyourtv/lists/<tv>.txt` can also undo what the app did.
- `src/main/launcher.ts` follows the handover order of `build-and-install.sh`: install, start, disable the stock launcher, set Home, check, roll back.
- `src/main/theme.ts` pushes what `tools/theme.sh` pushes, with the same restart.

### The launcher APK

The app bundles a launcher built by `npm run apk` from the pinned upstream commit. That script
downloads JDK 17 and the Android SDK into `.cytv-build/`, the same way the app's "Build from source"
option does, and both builds use the same signing key. `resources/launcher.json` records the upstream
commit each APK came from.

A launcher built with upstream's own script is signed with that computer's debug key. The app detects
the mismatch and asks before replacing it, because a reinstall loses the tile order set on the TV.

## Develop

```bash
npm install
npm run sync                 # lists and themes from the pinned upstream commit
npm run apk                  # resources/launcher.apk (first run downloads ~800 MB of build tools)
npm run dev                  # the app, with hot reload
npx tsx scripts/fake-tv.ts   # a pretend TV on 127.0.0.1:5556 to try the app without one
UPSTREAM_DIR=<clone at the pinned commit> npm test
npm run dist:mac             # dist/*.dmg   (on a Mac)
npm run dist:win             # dist/*.exe   (on Windows, a Mac, or in CI)
```

To move to a newer upstream: review its changes, then `node scripts/pin-upstream.mjs <commit sha>`,
`npm run sync`, `npm run apk`, and test on a TV. `UPSTREAM_UNPINNED=1` lets `sync` and `apk` use other
content for a local trial.

`.github/workflows/build.yml` builds the APK on Linux and runs the tests on every push and pull request.
Pushes to `main` and version tags also build the macOS and Windows installers; download them from the
run's **Artifacts**. It runs with read-only repository permissions and actions pinned to commits.

### Releasing

1. Set the new `version` in `package.json` and commit it.
2. Tag and push: `git tag v0.1.1 && git push origin main v0.1.1`.
3. The workflow builds both installers and opens a **draft** release with them attached. Check it,
   then press **Publish release**.

The tag has to match the version in `package.json`, or the release step stops.

## Help and community

- **Chat and how-to questions:** the [CleanMyTV Discord](https://discord.gg/6hZeQkDYf9). Share your
  home screen in `#show-your-setup`.
- **Bugs and feature requests:** [open a GitHub issue](https://github.com/wingedpanther/cleanmytv/issues/new)
  with your TV model, Android version and what happened. Issues are where fixes get tracked, so please
  don't report bugs only in Discord.

## Known limits

- Tile order and app-library choices made on the TV live in the launcher's private storage, which
  ADB can't read. The app sets the order through `dock_apps`, which takes precedence over the
  on-TV order.
- App names come from a built-in table of common TV apps. Others get a name made from the package,
  and the TV's own labels and icons aren't read.
- Wireless debugging pairing uses Google's `adb`, downloaded on demand. On macOS, Local Network
  privacy can block that binary. If pairing fails there, pair from a Windows PC or run `adb pair`
  in Terminal once.

## Licence

MIT, see [LICENSE](LICENSE). The bundled lists, themes and launcher are from
[cleanyourtv](https://github.com/Gathin23/cleanyourtv), also MIT; its licence ships in `resources/upstream/LICENSE`.
