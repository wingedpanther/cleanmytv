// The contract between the main process (talks to the TV) and the renderer (the UI).
// Every call goes through window.cytv, which the preload script builds from this interface.

export type PackageState = 'enabled' | 'disabled' | 'absent'

export interface Probe {
  host: string
  model: string
  device: string
  product: string
  manufacturer: string
  /** Android version, e.g. "11". */
  release: string
  sdk: number
  build: string
  /** "0" means port 5555 accepts any device on the network without the Allow prompt. */
  adbSecure: string
}

export type ConnState = 'idle' | 'connecting' | 'waiting-auth' | 'connected' | 'error'

export interface ConnStatus {
  state: ConnState
  host?: string
  /** Plain-language reason when state is error. */
  error?: string
  /** The port answered but asked for TLS: Wireless debugging needs a one-time pairing. */
  needsPairing?: boolean
  /**
   * Set by reconnect: the saved TV is not (or no longer) at the saved address. The user picks it from a
   * scan; the app never handshakes with scanned devices on its own.
   */
  moved?: boolean
  probe?: Probe
}

export interface ScanHit {
  host: string
}

export type CheckStatus = 'ok' | 'missing' | 'warn' | 'optional' | 'checking'

export interface CheckItem {
  id: 'network' | 'tv' | 'auth' | 'apk' | 'jdk' | 'sdk' | 'source' | 'platform-tools'
  label: string
  status: CheckStatus
  detail: string
  /** A download the app can do for the user, by ToolId. */
  download?: ToolId
}

export type ToolId = 'jdk' | 'sdk' | 'source' | 'platform-tools'

export interface DebloatListSummary {
  /** Folder name in upstream examples/, or "custom". */
  id: string
  /** First header line of the list, without the leading "# ". */
  title: string
  /** Higher is a better match for the connected TV; 0 = no match. */
  score: number
}

export interface DebloatEntry {
  pkg: string
  /** The list's inline comment for this package. */
  note: string
  /** In the list as an active line (true) or commented out by the author (false). */
  suggested: boolean
  /** Live state on the TV; absent when the package is not installed. */
  state: PackageState
  /** State in the recovery record, when one exists for this TV and list. */
  recorded?: PackageState
  /** How many of the upstream lists disable this package. */
  common: number
  /**
   * Set when switching this package off loses something people use (the phone remote, casting, voice)
   * or the list puts it under "Personal choice"/"Optional": not ticked unless the user ticks it.
   */
  choice?: string
}

export interface DebloatPlan {
  listId: string
  title: string
  header: string[]
  entries: DebloatEntry[]
  /** Recovery record for this TV and list, if one was written earlier. */
  record?: { path: string; created: string }
  /** Package that owns Home right now. */
  homePackage: string | null
}

export interface DebloatApplyRequest {
  listId: string
  /** Packages to disable, from the plan's entries. */
  disable: string[]
  /** Packages to switch back on now (unticked after an earlier run). */
  enable: string[]
  forceHome?: boolean
  forceSetupwraith?: boolean
}

export interface ApplyResult {
  ok: boolean
  /** One line for the user. */
  summary: string
  /** Things that were skipped or need the user's attention. */
  warnings: string[]
}

export interface LauncherStatus {
  installed: boolean
  versionName?: string
  isHome: boolean
  homeComponent: string | null
  /** The stock launcher package found on the TV, if any. */
  stockLauncher: string | null
  bundledApk: boolean
  bundledVersion?: string
  localApk: boolean
}

export interface InstallRequest {
  apk: 'bundled' | 'local'
  /** 'auto' asks the TV; 'none' leaves the stock launcher enabled. */
  stockLauncher: 'auto' | 'none' | string
  inputKeyService: boolean
  homeKeyService: boolean
  /** Uninstall a launcher signed with a different key before installing (loses on-TV order). */
  replaceIncompatible?: boolean
}

/** CleanMyTV Home, the preview launcher. */
export interface HomeStatus {
  installed: boolean
  versionName?: string
  isHome: boolean
  /** This copy of the desktop app carries it. */
  bundled: boolean
  bundledVersion?: string
}

export type HomeLayout = 'shelf' | 'split' | 'rail'
export type HomeSection = 'dock' | 'rows' | 'folders' | 'grid'
export type QuickId = 'settings' | 'wifi' | 'inputs' | 'bluetooth' | 'display' | 'apps' | 'sleep' | 'search'

/** A named row of apps, or a folder. */
export interface HomeGroup {
  name: string
  apps: string[]
}

/** CleanMyTV Home's settings, as its ConfigProvider holds them. Snake case, as on the TV. */
export interface HomeConfig {
  // Arrangement
  layout: HomeLayout
  /** Top to bottom; a section left out is hidden. */
  sections: HomeSection[]
  /** Percent of the screen left open above the apps (Shelf), 0 to 40. */
  top_space: number
  /** Quick-settings icons, in order. */
  quick: QuickId[]
  rows: HomeGroup[]
  folders: HomeGroup[]
  /** Percent, 60 to 150. */
  dock_scale: number
  /** Row and folder tiles, percent, 60 to 150. */
  tile_scale: number
  // Look
  /** #RRGGBB, for the focus glow or outline and the music bars. */
  accent: string
  focus: 'glow' | 'outline' | 'none'
  /** #RRGGBB behind everything when there's no wallpaper. */
  background: string
  /** Percent of black over the wallpaper, 0 to 80. */
  wallpaper_dim: number
  // Layout
  /** Apps first in this order; the rest follow alphabetically. */
  order: string[]
  hidden: string[]
  /** The first visible apps in `order` that sit in the dock, 0 to 8. */
  dock_size: number
  /** Apps per row in the grid, 3 to 8. */
  columns: number
  show_names: boolean
  clock: 'time_date' | 'time' | 'off'
  /** A name for "Good evening, …"; empty for none. */
  greeting: string
  // Music bars
  visualiser: boolean
  viz_style: 'bars' | 'mirror' | 'wave'
  viz_bars: number
  /** Percent of the screen height, 4 to 40. */
  viz_height: number
  viz_opacity: number
  viz_colour: 'accent' | 'rainbow' | 'white'
  viz_peaks: boolean
  /** Percent, 25 to 300. */
  viz_sensitivity: number
  /** Read only: a wallpaper is set. */
  has_wallpaper?: boolean
}

/** A TV this app has connected to before. */
export interface KnownTv {
  host: string
  device?: string
  model?: string
  manufacturer?: string
  /** A small JPEG snapshot of the TV screen, as a data URL. */
  thumb?: string
  thumbAt?: number
  lastUsed: number
}

/** The desktop app's own settings. */
export interface Prefs {
  /** Shows CleanMyTV Home, the preview launcher. */
  preview: boolean
}

export interface InstallResult extends ApplyResult {
  /** The installed launcher was signed with another key; ask before replacing it. */
  incompatible?: boolean
}

export interface TvApp {
  pkg: string
  label: string
  /** True for apps with a TV (leanback) entry; false for phone apps the launcher only shows from the library. */
  tv: boolean
}

/** The keys from upstream themes/README.md. Every one is optional. */
export interface LauncherConfig {
  dock_apps?: string[]
  fill_dock?: boolean
  hidden_apps?: string[]
  live_tv_package?: string
  live_tv_action?: string
  source_panel_service?: string
  source_panel_action?: string
  input_action?: string
  columns?: number
  rows_below_dock?: number
  dock_title?: boolean
  dock_row?: boolean
  reorder_button?: boolean
  app_library?: boolean
  shamsi_date?: boolean
  greeting_name?: string
  module_cards?: boolean
  reduced_motion?: boolean
  vpn_app?: string
  animations?: boolean
  advanced_effects?: boolean
  show_home_settings_shortcut?: boolean
  show_stats?: boolean
  background_color?: string
  sky_intensity?: number
  wallpaper?: boolean
  wallpaper_rotation_minutes?: number
  wallpaper_dim?: number
  festival?: string
  festival_until?: string
  greeting?: string
  accent_color?: string
  settings_key_keycodes?: number[]
  settings_key_panel?: boolean
  input_key_keycodes?: number[]
  app_keys?: string[]
}

export interface TvTheme {
  config: LauncherConfig
  /** config.json was on the TV. */
  present: boolean
  /** wallpaper.jpg on the TV, as a data URL, or null. */
  wallpaper: string | null
  /** File names in the TV's wallpapers/ folder. */
  wallpapers: string[]
}

export interface ImageFile {
  name: string
  /** JPEG bytes, base64, already sized for the TV by the renderer. */
  base64: string
}

export interface ThemeApplyRequest {
  config: LauncherConfig
  wallpaper: { kind: 'keep' } | { kind: 'remove' } | { kind: 'set'; image: ImageFile }
  wallpapers: { kind: 'keep' } | { kind: 'set'; images: ImageFile[] }
}

export interface ThemePreset {
  id: string
  name: string
  description: string
  config: LauncherConfig
  /** Data URL of the preset's wallpaper.jpg, if it has one. */
  wallpaper: string | null
}

export interface SystemState {
  sdk: number
  screensaverIsClock: boolean
  privateDnsMode: string
  privateDnsHost: string
  /** window_animation_scale; "null" when never set. */
  animationScale: string
}

export interface DownloadProgress {
  tool: ToolId
  received: number
  total: number
  phase: 'download' | 'extract' | 'install' | 'done' | 'error'
  message?: string
}

export interface LogLine {
  /** Which job wrote it: 'debloat', 'restore', 'install', 'theme', 'build', 'pair', 'system'. */
  task: string
  level: 'info' | 'cmd' | 'warn' | 'error' | 'ok'
  text: string
  time: number
}

/** A download for the background sources, made by the main process. */
export interface WebResponse {
  status: number
  contentType: string
  base64: string
}

export interface CytvApi {
  platform: 'darwin' | 'win32' | 'linux'

  // Connection
  scan(): Promise<ScanHit[]>
  connect(host: string): Promise<ConnStatus>
  disconnect(): Promise<void>
  status(): Promise<ConnStatus>
  lastHost(): Promise<string | null>
  /**
   * Connects to the TV used last time, at the address saved then, and checks the same model answers.
   * If it doesn't, the status has `moved` set and the user picks the TV from a scan. Null when no TV was saved.
   */
  reconnect(): Promise<ConnStatus | null>
  /** Port 5555 refuses or ignores connections (after debugging was switched off). */
  checkAdbClosed(host: string): Promise<{ closed: boolean; message: string }>
  /** Wireless-debugging pairing with Google's adb (downloaded on demand), then opens port 5555. */
  pair(host: string, pairPort: number, code: string, connectPort: number): Promise<ApplyResult>

  // Readiness
  checks(): Promise<CheckItem[]>
  download(tool: ToolId): Promise<ApplyResult>

  // Debloat
  lists(): Promise<DebloatListSummary[]>
  plan(listId: string): Promise<DebloatPlan>
  applyDebloat(req: DebloatApplyRequest): Promise<ApplyResult>
  restore(listId: string): Promise<ApplyResult>

  // Launcher
  launcherStatus(): Promise<LauncherStatus>
  install(req: InstallRequest): Promise<InstallResult>
  buildLocal(): Promise<ApplyResult>
  /** Give Home back to the stock launcher, leaving the new one installed. */
  useStockLauncher(): Promise<ApplyResult>
  /** Make the installed CleanYourTV launcher Home again (from CleanMyTV Home). */
  useCleanYourTv(): Promise<ApplyResult>

  // CleanMyTV Home (preview)
  prefs(): Promise<Prefs>
  setPrefs(change: Partial<Prefs>): Promise<Prefs>
  homeStatus(): Promise<HomeStatus>
  /** Installs or updates CleanMyTV Home and makes it Home, putting the old Home back if that fails. */
  installHome(): Promise<InstallResult>
  /** Make the installed CleanMyTV Home the Home screen. */
  useHome(): Promise<ApplyResult>
  /** The apps CleanMyTV Home shows, named as on the TV. */
  homeApps(): Promise<TvApp[]>
  readHomeConfig(): Promise<HomeConfig>
  /** Applies on the TV straight away; returns what the launcher now has. */
  setHomeConfig(change: Partial<HomeConfig>): Promise<HomeConfig>
  /** A prepared JPEG (base64) for the home screen, or null to remove it. */
  setHomeWallpaper(jpegBase64: string | null): Promise<HomeConfig>

  // TVs
  /** TVs connected before, most recent first. */
  knownTvs(): Promise<KnownTv[]>
  /** Connect to a TV from that list, checking the same model answers there. */
  connectKnown(host: string): Promise<ConnStatus>
  forgetTv(host: string): Promise<KnownTv[]>
  /** Saves a small snapshot (JPEG data URL, under 200 KB) for the connected TV. */
  saveTvThumb(host: string, dataUrl: string): Promise<KnownTv[]>

  // Customise
  apps(): Promise<TvApp[]>
  readTheme(): Promise<TvTheme>
  applyTheme(req: ThemeApplyRequest): Promise<ApplyResult>
  resetTheme(): Promise<ApplyResult>
  presets(): Promise<ThemePreset[]>
  /** PNG of the TV screen as a data URL. */
  screenshot(): Promise<string>
  /** HTTPS GET from Bing or TMDB only (the page itself has no network access). */
  webGet(url: string): Promise<WebResponse>

  // TV extras
  systemState(): Promise<SystemState>
  setClockScreensaver(on: boolean): Promise<ApplyResult>
  setPrivateDns(host: string | null): Promise<ApplyResult>
  setAnimationScale(scale: number): Promise<ApplyResult>
  setLauncherLocale(tag: string): Promise<ApplyResult>
  key(keycode: number): Promise<void>
  reboot(): Promise<void>

  // Events
  onLog(cb: (line: LogLine) => void): () => void
  onProgress(cb: (p: DownloadProgress) => void): () => void
  onStatus(cb: (s: ConnStatus) => void): () => void
}

declare global {
  interface Window {
    cytv: CytvApi
  }
}
