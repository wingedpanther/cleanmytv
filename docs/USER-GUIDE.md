# CleanMyTV user guide

A step-by-step guide for TV owners. Written against **v0.2** — screens change, the ideas don't. Every screenshot in the README is labelled with the version it was taken from; expect this guide's pictures to be added the same way as the app settles.

If you are comfortable with ADB, package names and rollback semantics, the [README](../README.md) is the short version. This guide is for everyone else: it walks each step at TV-owner pace, says what you will see, and has a "if it didn't work" box for the common failures.

---

## 1. Before you start: what this app does (and does not do)

**In one sentence:** it turns off the apps on your Android TV that you don't want, puts its own clean home screen (CleanMyTV Home) in their place, and can put everything back the way it was.

Three promises worth knowing before anything else:

- **Nothing is uninstalled.** Apps are switched off *for your user* (`pm disable-user`), not removed from the TV. They take no space on screen, and they come back with one click of **Restore**.
- **It only talks to your TV.** The app connects over your home Wi-Fi to the TV's debugging port (5555). No account, no cloud, no telemetry. The two exceptions happen only when you ask: Google's pairing tools for Wireless debugging, and Bing's picture of the day as a wallpaper.
- **There is always a way back.** Before the first cleanup the app writes a restore record — which packages were on, which launcher Home pointed at — and Restore replays it exactly. [Chapter 7](#7-safety-and-rollback) covers this in full.

---

## 2. Prepare the TV (5 minutes, one-time)

You need to switch on the TV's developer options, the same ones phone apps use. Nothing you do here can break the TV; the switch can be turned off again at any time.

1. On the TV remote: **Settings → Device Preferences → About** (on some Google TVs: **Settings → System → About**).
2. Move down to **Build** (or **Android TV OS build**) and press OK on it **seven times**. After the third or so a small counter appears; at seven the TV says *You are now a developer* (or similar).
3. Go back one screen → **Developer options** → turn on **USB debugging**. If your TV also lists **Network debugging** or **ADB debugging**, turn that on too — that is the one this app uses.
4. Leave the TV on and awake. It must stay on the same Wi-Fi as your computer.

> **If it didn't work**
>
> - *No Developer options in the menu.* Some TVs hide it under a different Settings layout — look for **System → Advanced**. A few budget models need the TV signed into a Google account first.
> - *The counter never appears.* You are pressing a different row — it must be the **Build number** itself, not the Android version above it.

---

## 3. Connect (the Allow prompt)

1. Open CleanMyTV on your computer. The **Setup** step checks the network and lists the TVs on your Wi-Fi by name. Older TVs that don't announce themselves turn up when you press **Scan the network**.
2. Pick your TV. The app connects and the TV shows an **Allow USB debugging?** prompt with your computer's identity on it.
3. On the TV, choose **Allow**, and tick **Always allow from this computer** to skip the question next time.

Google TVs that only offer *Wireless debugging* get a pairing form instead — the app walks you through the TV's Wireless-debugging screen. When the pairing screen opens, the ports fill in by themselves, and you type the six-digit code it shows.

The app remembers each TV it has connected to. With one TV, it reconnects by itself next time. With more than one, it waits: Setup shows your TVs with a snapshot of each screen, and you pick one. The sidebar shows a thumbnail of the TV you're connected to.

**The quick way.** **Clean & launch** in the sidebar runs chapters 4 and 5 in one go: it applies the list that best matches your TV, then installs CleanMyTV Home. The separate steps are still there if you want to choose package by package.

> **If it didn't work**
>
> - *The TV isn't listed.* The TV and the computer must be on the same Wi-Fi — and on the *same band* if your router splits 2.4 GHz and 5 GHz into separate network names. Guest Wi-Fi often blocks TV-to-computer traffic ("client isolation"); use the main network.
> - *The Allow prompt never appears.* The TV usually shows it within a few seconds. If not, a previous connection attempt may be stuck: on the TV, Developer options → **Revoke USB debugging authorisations**, then connect again.
> - *It connected yesterday but not today.* The TV's Wi-Fi address changed. Go back to Setup and let it scan again — this is normal after a router restart.

---

## 4. Clean up (choose what goes away)

You will see a list of apps that other TVs like yours disable, each showing its **live state on your TV right now**. Ticked means "switch this off"; unticked stays untouched.

- The app picks the list that matches your TV's model, from 371 TVs. If none matches, lists from your TV's brand come first, and **My TV is not listed** shows the packages other TVs disable — start conservative.
- **Know a package that isn't in the list?** Press **Add a package that isn't in the list**, type its name or pick it from the apps on your TV. It joins the list ticked, and Restore switches it back on like the rest.
- **Start with the obvious ad and recommendation apps.** Anything whose name contains *recommend*, *ads*, *live* or a store you never use is a safe first pass. The system apps the TV needs to function are not in the list.
- Press the cleanup button. Each row updates as it goes. Anything that fails is left on and clearly marked — nothing is half-done behind your back.

> **If it didn't work**
>
> - *A row says "failed".* The app leaves that one on and continues with the rest. Some apps refuse to be disabled on certain firmwares; skip them — the rest of the cleanup still counts.
> - *The TV feels slower, or something you use vanished.* Open the same screen later and untick nothing — the screen also lists how to re-enable one app at a time. Or use full **Restore** ([chapter 7](#7-safety-and-rollback)).

---

## 5. Home screen (the new launcher)

This step installs CleanMyTV Home and points the TV's **Home** button at it. It needs Android 9 or newer.

1. Open **Home screen** → **Overview** and press install. The app puts CleanMyTV Home on the TV and hands Home over.
2. Press Home on the TV remote once. You should land on the new home screen.

If you used CleanMyTV 0.1, your TV may still have the old CleanYourTV launcher. The Overview tab offers to remove it in one click.

> **If it didn't work**
>
> - *Home still opens the old screen.* Press Home a second time — some TVs show a "use which app?" chooser the first time; pick CleanMyTV Home and **Always**. If it still lands wrong, the step's rollback runs automatically and tells you; nothing is left half-switched.
> - *I want the old home back.* Overview → **Give Home back to the stock launcher**. See also [chapter 7](#7-safety-and-rollback).
> - *"Which one is the TV's own launcher?"* The app couldn't tell which app is your TV's home screen. Pick the one the TV came with (usually the name with your brand in it). It stays on, and Undo gives Home back to it.
> - *Xiaomi: the Mi button does nothing.* It opened PatchWall, which the cleanup switched off. Open **Remote buttons** and press **Make the Mi button go Home**.

---

## 6. Customise and finish

- **Home screen** — every change shows on the TV straight away. The Layout and Apps tabs show a live preview of the TV screen at the top.
  - *Overview:* install or update, give Home back to the stock launcher, take a snapshot of the TV screen.
  - *Layout:* pick Shelf (the default), Split or Rail. Set the space above the apps. Put the dock, rows, folders and all apps in any order, or hide any of them. Choose quick-settings icons: Inputs, Wi-Fi, Bluetooth, Picture & sound, Apps, Screensaver, Search, Music bars, Settings. An icon your TV can't open is marked "not on this TV". Also tile sizes, apps per row, app names, clock and greeting.
  - *Apps, rows & folders:* dock size, drag to reorder, hide apps, make named rows and folders.
  - *Look & wallpaper:* accent colour, focus glow, outline or none. Use your own picture or Bing's picture of the day, darken it, or pick a background colour.
  - *Widgets:* Now playing, a card with what any music or video app plays, with play, pause and next. Weather next to the clock: search for your town and pick °C or °F.
  - *Music bars:* bars that move with the TV's sound while music or video plays. Choose the style, colour, peak dots, number, height, opacity and sensitivity. The Music bars icon on the TV turns them on or off.
- **TV extras** — ad-blocking Private DNS, animation speed, an on-screen remote, reboot.
- **Finish** — first the restart check: the app restarts the TV, reconnects and checks the home screen comes up. If it doesn't, the app puts the stock launcher back while it still can. Only then does the guide walk you through switching debugging back **off**, and check that port 5555 is closed. Do this: an open debugging port is a plain-text shell anyone on the Wi-Fi could use, and some TVs don't ask permission again once paired.

The app remembers where you were — close it any time and come back; a TV that already runs CleanMyTV Home opens straight at the Home screen step. Your settings and wallpaper are kept on the TV and survive a reboot.

> **If it didn't work**
>
> - *The port check says still open.* Some TVs restart their debugging service when you toggle it — wait a few seconds and press check again. If it stays open, toggle Developer options itself off on the TV; the launcher and your cleanup are unaffected.
> - *The TV got a new IP address.* Nothing is lost. Connect again from Setup; the app recognises the TV by its device identity, not its address, and keeps its restore record.

---

## 7. Safety and rollback

**Disabled vs uninstalled — the difference that matters.**

| | Disabled (what this app does) | Uninstalled (what this app never does) |
|---|---|---|
| Where the app goes | Still on the TV, switched off for your user | Removed from storage |
| Comes back? | Instantly — one toggle or Restore | Only by finding and reinstalling it |
| TV system apps | Untouched | Would break things |
| Storage freed | None | Some |

**Restore — the one-click undo.** Before the first cleanup the app writes a record on your computer: every package's on/off state, and which launcher Home pointed at. Restore replays that record exactly:

- every package you switched off comes back **on**, exactly as it was;
- Home goes back to the launcher you had before — the stock one if you never changed it;
- the record is tied to the TV's device identity, not its IP address, so a TV that moves or gets a new address keeps its history;
- a record from a *different* TV is refused — Restore never applies one TV's list to another.

**Getting the stock launcher back**, step by step: open CleanMyTV → connect → Restore. To keep the cleanup and only change Home: Home screen → Overview → **Give Home back to the stock launcher**. If you no longer have the app: on the TV, Settings → Apps → the stock launcher → **Enable** (it was disabled, not deleted). Re-enabling debugging works the same way: repeat [chapter 2](#2-prepare-the-tv-5-minutes-one-time) — the seven presses work whether the options are on or off.

---

## 8. Troubleshooting quick table

| Symptom | First thing to try | Section |
|---|---|---|
| TV not found | Same Wi-Fi, same band, not guest network | 3 |
| Allow prompt not appearing | Revoke authorisations on the TV, reconnect | 3 |
| Home didn't switch | Press Home again; pick "Always" in the chooser | 5 |
| A package refused to disable | Leave it on; everything else still worked | 4 |
| Something I use got disabled | Re-enable it one-by-one, or Restore | 7 |
| TV got a new IP | Just reconnect; the app knows the TV by identity | 6 |
| Port check says still open | Wait a few seconds, or toggle Developer options off | 6 |
| Want everything back | Restore — one click | 7 |

---

*Guide version: v0.3 (2026-10-30). Written to match app v0.3.0; when the app's steps change, this guide is the place to update, alongside the README screenshots.*
