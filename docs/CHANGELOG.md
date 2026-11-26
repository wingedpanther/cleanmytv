# Changelog

What changed in each CleanMyTV release, newest first. The latest one is at the top of the
[README](../README.md), and every release with its downloads is on the
[Releases page](https://github.com/wingedpanther/cleanmytv/releases).

## 0.13.4

- **Hold a button for a second job:** each remote button can do one thing when pressed and another
  when held down, set in Remote buttons on the TV or here.
- **Inputs as tiles:** each HDMI port can become a tile named as the Inputs menu names it, like PS5.
  Put it in the dock or a row, and one press switches to it.
- **Bedtime:** the TV turns off at a set time on the days you pick. A minute before, a note says so,
  and any button keeps it on for the night.
- **Apps that came back:** system updates often switch the bloat back on. Clean up now says when that
  happened and switches them off again in one click. CleanMyTV Home says so on the TV too.
- **Storage:** TV extras lists the apps taking the most space, and can remove updates from switched-off
  apps.
- **Back up the TV:** save the clean-up choices and Home's settings to a file, and restore them after a
  factory reset or on another TV.
- **Send a report:** one button in Activity saves a text file with what a bug report needs, ready to
  attach to a GitHub issue.
- **Check for updates (#133):** a button in the sidebar, and the app now checks every time it starts.
- **XGIMI projectors (#127):** picking an HDMI port switches straight to it.

## 0.13.3

- **Remote buttons and the Customize menu (#133):** with a button job set, the focus no longer jumps past
  Themes and wallpaper.
- **More space above the apps (#134):** it goes up to 80% now, and the first row always stays on screen.
  At the most, the dock sits at the bottom and the rest waits below until you press Down.
- **XGIMI projectors (#127):** Inputs opens XGIMI's own HDMI switcher.
- **Strong LEAP-NOVA stick (#132):** Google TV's launcher stays on there, since the stick restarts without
  it. CleanMyTV Home comes back in front after a Home press, as on Fire TV.
- **App order on the TV (#135):** the header above the grid is back to its normal height, so the apps
  show without scrolling. Thanks to harikrishna-salver.

## 0.13.2

- **Fire TV Settings stay open (#126):** on the Fire TV Stick 4K Max, Settings opened from Home no longer
  jumps straight back to Home. Thanks to humun.
- **Top space holds (#129):** after scrolling down the page and back up, the first row is back where you put it.
- **Start on the TV's channels (#128):** the TV's own tuner is in the list of inputs to open at power on.
- **Pairing** takes the TV address with the port after it, the way the TV's Wireless debugging screen shows it.

## 0.13.1

- **Uploads that pick up where they stopped (#121):** if the TV closes Home while the fireplace, a theme
  or a slideshow picture is going over, Home carries on with the part it already has. The app also tries
  a failed file once more before it gives up.
- **Rows run to the screen edge (#124):** apps slide off the side of the screen instead of being cut off
  short of it.
- **Mono icons with the Glow focus (#119):** the focused app is white with a black logo, like the top bar.
- **Where the Bing picture was taken (#123):** Bing's caption shows small in the bottom corner. Turn it
  off with **Show where it was taken**.

## 0.13.0

- **German:** the app and CleanMyTV Home now come in German, translated by WiesoBistnSuus from our Discord.
  Want CleanMyTV in your language? Ask on Discord: it's a spreadsheet, no coding.
- **Continue watching:** a row on Home with what you were part-way through, from Android's Watch Next list.
  It's a section, so you can move it or turn it off. Hidden apps stay out, and locked apps ask for the PIN.
- **Christmas theme:** a winter night with fairy lights and falling snow. **Snow** has its own switch under
  Wallpaper, so it works with any theme.
- **Power on (#94):** choose the input the TV opens when it comes on, and how long to wait first. The
  check-up also finds TVs that HDMI devices can't turn on, and fixes them.
- **Match frame rate** in TV extras, so films at 24 fps don't judder (Android 12 and later).
- **Hourly forecast** under the weather, off unless you turn it on in Widgets.
- **Grids for App lock and the app order (#117):** pick and move apps on a grid with the remote, plus Lock
  All and Unlock All. Thanks to harikrishna-salver.
- **Fixes:** the fireplace crackle plays on TCL TVs where it was silent (#118). Mono icons with the Glow
  focus show black on white (#119). The TV's wallpaper picker shows a preview. Home stays the Home app on
  Google TV 14 and Haier TVs that carry a hidden launcher entry.

## 0.12.0

- **Russian:** the app follows your computer's language and CleanMyTV Home follows the TV's. You can
  pick the app's language in the sidebar. Built on linaiven91-cyber's translation (#83).
- **Check-up after Clean up:** after Apply, Clean up checks the keyboard, the Home button, the remote's
  microphone and the screensaver, with a button to fix each. The Finish step has the same check.
- **Record a problem:** in the Activity drawer, record the TV's log while you make the problem happen,
  then save it as a file for your GitHub issue. Addresses and email addresses are taken out.
- **The keyboard stays after standby (#98):** on TVs that switch to the phone keyboard every time they
  wake, Home puts yours back straight away. **Keep my keyboard** turns it off.
- **Brightness (#74)** in Quick settings and as a remote button action, plus a Picture & sound row.
- **Fireplace (#116):** listed in the top bar and the TV's screensavers only while it's on the TV.
- **Search on the TV** goes straight to the setting. The PIN pad takes the keyboard's Delete key.

## 0.11.0

- **Each theme keeps its own look** (LimeNinja's idea): change the look on the TV while a theme is on,
  and that theme keeps it. That covers colours, tile sizes, glass, dimming, shading, spacing and music
  bars. Switch themes and each one comes back the way you left it, at the day and night switch too.
  Rows, folders, App lock and buttons stay the same for every theme. **Forget changes** puts a theme back.
- **Save to the TV:** the Look tab saves the TV's look and wallpaper as a theme on the TV, next to the
  built-in ones, or updates the theme with that name. On the TV, **Save this look as a theme** does the
  same. Putting the built-in themes on the TV again keeps the ones you saved.
- **The keyboard stays (#98):** on TVs that switch to the phone-remote keyboard at every restart, Home
  now puts yours back. The 0.8.0 fix couldn't see the TV's keyboards. Clean up also leaves on any app
  that carries a keyboard, like Google's speech services.
- **Copy for a bug report** lists the TV's apps, on and switched off, and its keyboard. New TV lists
  are made from that.
- **App lock:** with App lock on, showing hidden apps or resetting the app order in the editor asks for
  the PIN (thanks harikrishna-salver).
- **Smoother Home:** the layout is worked out once per change instead of on every redraw, and the app
  colour wash and the sunrise no longer redraw the whole screen while they fade.
- **Editor colours:** with a white or near-white accent, the editor marks picked items in its own blue,
  so they don't look focused.
- **TCL 75Q6C** shows on the TCL 55Q6CX1 list. They share the G08 board.

## 0.10.0

The big one: **CleanMyTV Home can now be set up from the TV itself.** Turn on **Edit on the TV** in the
desktop app once, and a Customize icon in Home's top bar opens an editor you drive with the remote.

- **Customize from the remote** (thanks harikrishna-salver for the editor, #95): layout and sections,
  rows and folders, look and shading, themes and wallpaper, widgets, apps, remote buttons, App lock,
  music bars, the fireplace, the greeting and names. It wears your theme's colours, and it has its own
  PIN switch in App lock.
- **Search settings:** type "font", "PIN", "photo" or "sleep" and jump straight to the setting.
- **Photos from your phone:** scan a QR code on the TV and send a photo as the wallpaper or into the
  slideshow. No computer, and no storage permission.
- **Home updates itself:** when a new version is out, an update icon shows in the top bar, with the
  release notes, and Android's own installer does the rest. Your settings stay.
- **Sleep timer:** 15 minutes to 2 hours, from a top bar icon or a remote button, with "Sleeps at"
  under the clock.
- **Daily limits:** give an app so many minutes a day; after that it needs the PIN for the rest of the day.
- **Idle screen styles:** the clock in the corner, a big clock, or your photos with the clock.
- **Fonts:** usual, condensed, serif or casual. Themes carry the font.
- **Bing every day:** Bing's new picture becomes the wallpaper each day.
- **Fixes:** Rail lines the weather and date up under the clock; square tiles in All apps line up with
  the heading; Mono cuts the marks out of more logos, like RTL+ and Nova Video Player (#113); Home's
  menus use your accent instead of green (#112); setting a PIN on the TV uses Home's own number pad.

## 0.9.0

- **Linux:** an AppImage for 64-bit Linux (#109, thanks Morditux).
- **Recently used:** a Home section with the last 8 apps the TV opened, newest first, however they were
  opened. Turn it on under Layout → Sections (#85).
- **Shading:** sliders for how strong the top and bottom shades over the wallpaper are and how far they
  reach, and the same for the app colour wash. Themes and day and night looks carry them.
- **Long press:** letting go of OK no longer picks Move from the menu.
- **HEIC pictures:** iPhone and Mac photos work as wallpapers and in the slideshow (on a Mac).
- **Pairing:** when it fails, the app says why: a wrong code, a closed pairing window, a TV that's off, or
  another device on the TV's address. The example port numbers that looked real are gone from the form.
- **Tile shape** shows under Banners too, with a note on how to change it.
- **Windows:** the installer stops on Windows 8.1 and older, and on 32-bit Windows, and says why (#107).
- **Mac:** the app names the network services it looks for, so macOS asks for local network access (#65).
- **TCL:** Clean up leaves the TCL Home phone app's packages on unless you tick them (#81).
- **CleanMyTV Home on its own:** each release has the launcher's APK, for updating it without the desktop app.

## 0.8.0

- **Remote buttons stay on.** Home turns its button service back on after a reboot, or when the TV
  switched it off, so mapped buttons keep working (#78).
- **Weather from MET Norway** when the network blocks Open-Meteo (#76, #80).
- **Inputs:** Live TV shows once, even on TVs with a tuner per broadcast type, and a new switch hides
  it (#50).
- **Bluetooth button:** on TVs that block the pairing screen, it opens Settings instead (#97).
- **Rename the Quick settings buttons** and the All apps title (#67).
- **Mono icons** cut the shape out of single-colour icons (#91).
- **Save a snapshot** of the TV screen to your computer (#92).
- **Xiaomi:** Clean up and Finish say when USB drives stop working while debugging is on (#71).
- **Memory:** free memory under the clock, and a Clean memory button that closes apps left running in the
  background, on Android 9 to 13. Android 14 doesn't let one app close another.
- **Day and night looks:** save one look for the day and one for the night, and Home swaps them at the times
  you set. Any theme works, shared ones too.
- **Mono glass:** a slider for how much the tile behind each logo shows. Glow focus no longer fills Mono tiles.
- **Mice and air mice:** point at a tile to move to it, click to open it.
- **No more green haze:** the shade over the wallpaper uses your background colour (#102).
- **Dock:** tiles grow past 100% (#89), and the dock stays where Space above the apps puts it (#90).
- **Volume:** holding a volume button repeats again on TVs where remote buttons stopped it. Home only listens
  to buttons while one is mapped (#87).
- **Keyboard:** on TVs that switch back to their own keyboard at every restart, Home puts yours back (#98).
- **Clean up:** Netflix, YouTube, YouTube Music, Prime Video and other streaming apps stay on unless you tick them.
- **Another launcher:** one you installed with Install an app can be Home instead (#82).
- **Keep Google TV's launcher on:** for TVs where Apple TV or Netflix stop playing, or Google Photos albums
  won't load, without it (#99, #88). CleanMyTV Home comes back in front after a Home press.
- **Update from the app (#93):** when a new version is out, Update now in the sidebar downloads it, checks it
  against the release's checksums, installs it and opens CleanMyTV again. This starts with the version after
  this one.
- **Long press an app on Home** for Move, Hide, App info and Uninstall. Move lets the arrow keys put the app
  anywhere in the dock, All apps or its row, with no computer needed.
- **Back to the input at power on:** if the TV went to sleep on HDMI or TV channels, it wakes up there (#94).
- **Fire TV:** installing no longer reports an error, CleanMyTV Home only comes back in front of Amazon's home
  screen, and Clean up says which Amazon apps Fire OS won't let go.
- **Volume with mapped buttons:** a volume press pauses the button service for 4 seconds, so holding it repeats.
- **Clean up** keeps the keyboard you type with on, whatever the list says.
- **472 TV lists from 129 brands**, up from 371.

## 0.7.2

- **Fireplace on every TV.** The fireplace now goes onto the TV through CleanMyTV Home itself, instead
  of the folder that Fire TV, Xiaomi boxes and some TCL TVs refused (#70).
- **Fire TV:** CleanMyTV Home comes back in front when Fire OS opens its own launcher, as long as remote
  buttons are on. Thanks Sean Warren.
- **USB drives and YouTube ads:** Clean up no longer ticks Media Player, its gallery and video player,
  Android's storage apps, or the TV's web viewers by default (#71, #55).
- **Mac:** when macOS never shows the Local Network switch, the Connect screen gives a Terminal command
  that starts the app with Terminal's network access (#65).
- **Phone apps in an Other apps folder:** apps with no TV picture can move out of the dock and grid into
  one folder (#3).

## 0.7.1

- **Install an app.** A new step in the sidebar. Drop an .apk on it, see the app's name, version and
  whether the TV has it already, then install it. A warning about apps from outside the Play Store
  stays on top, and if Android refuses the app, the app says why in plain words.
- **Back on the home screen** stays on Home instead of closing it (#69).
- **The theme picker on the TV** shows the picture of the theme you're on (#68).
- **Now playing** goes away when the music or video stops, or after 10 minutes paused (#66).
- **Clean & launch** leaves the Google screensaver, phone remote, casting and voice on, like Clean up
  does. Thanks nasd223.
- **Copy for a bug report** shows your home folder as ~, so it doesn't paste your computer's user
  name. Thanks nasd223.
- The restart check no longer mistakes Settings' fallback screen for a working home screen. Thanks
  nasd223.

## 0.7.0

- **Fireplace.** A real log fire with its crackle, looping without a seam. Show it full screen from an
  icon or a remote button, make it the screensaver, or put it behind Home. The app downloads it once
  (about 11 MB) and sends it to the TV. Set it to turn off after 30 minutes, 1 or 2 hours, and the fire
  fades before the TV sleeps.
- **Live TV in Inputs.** The TV's own tuner is first in the Inputs menu and opens your channels (#63).
- **Slideshow.** Up to 12 of your pictures, or the current theme's three, change every 5 to 60 minutes
  (#56).
- **Make your own theme.** Start from a built-in theme, pick the accent, a second colour, the background
  and the picture style, and see it before it goes on the TV.
- **Google screensaver.** TV extras switches Google's Ambient screensaver apps back on and makes it the
  screensaver again (#57).
- **Clean up after a firmware update.** It lists packages you switched off that came back on, with one
  button to switch them off again.
- **Help when Connect fails.** The app names the likely cause: macOS blocking local network access,
  another network, a VPN, or the pairing port typed into Connect.
- Folders and your name in the greeting take the theme's accent.




https://github.com/user-attachments/assets/4e3eb18a-d196-4a51-8d0e-93ce98e11913
