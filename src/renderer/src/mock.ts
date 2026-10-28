// A fake TV for viewing the UI in a plain browser (npm run dev without Electron, or vite preview).
// Installed only when the preload script did not provide window.cytv.
import type {
  ApplyResult,
  CheckItem,
  ConnStatus,
  CytvApi,
  HomeConfig,
  KnownTv,
  DebloatEntry,
  DebloatPlan,
  DownloadProgress,
  LauncherConfig,
  LogLine,
  Probe,
  SystemState,
  ThemePreset,
  TvApp,
  TvTheme
} from '@shared/api'

const wait = (ms: number): Promise<void> => new Promise((r) => setTimeout(r, ms))

export function installMock(): void {
  if (window.cytv) return

  const logs = new Set<(l: LogLine) => void>()
  const progress = new Set<(p: DownloadProgress) => void>()
  const statuses = new Set<(s: ConnStatus) => void>()
  const log = (task: string, level: LogLine['level'], text: string): void =>
    logs.forEach((cb) => cb({ task, level, text, time: Date.now() }))

  const probe: Probe = {
    host: '192.168.1.50',
    model: 'K-65XR90M2',
    device: 'BRAVIA_AE3',
    product: 'BRAVIA_AE3_PA',
    manufacturer: 'Sony',
    release: '14',
    sdk: 34,
    build: 'UKN4.250422.001',
    adbSecure: '1'
  }
  // ?connected opens the mock already connected, for looking at the later steps.
  let status: ConnStatus = new URLSearchParams(location.search).has('connected')
    ? { state: 'connected', host: probe.host, probe }
    : { state: 'idle' }
  const setStatus = (s: ConnStatus): void => {
    status = s
    statuses.forEach((cb) => cb(s))
  }

  const tools = { jdk: false, sdk: false, source: false, 'platform-tools': false }
  let launcherInstalled = false
  let launcherHome = false
  let homeInstalled = false
  let homeIsHome = false
  const prefs = { preview: false }
  const homeConfig: HomeConfig = {
    accent: '#FF3000', focus: 'glow', background: '#0B1E2A', wallpaper_dim: 35,
    order: [], hidden: [], dock_size: 5, columns: 6, show_names: true, clock: 'time_date', greeting: '',
    visualiser: true, viz_style: 'bars', viz_bars: 32, viz_height: 12, viz_opacity: 85, viz_colour: 'accent', viz_peaks: true, viz_sensitivity: 100,
    has_wallpaper: false
  }
  let tvs: KnownTv[] = [
    { host: '192.168.1.50', device: 'BRAVIA_AE3', model: 'K-65XR90M2', manufacturer: 'Sony', lastUsed: Date.now() - 3600_000 },
    { host: '192.168.1.62', device: 'oneday', model: 'MIBOX4', manufacturer: 'Xiaomi', lastUsed: Date.now() - 86400_000 }
  ]

  const apps: TvApp[] = [
    ['com.netflix.ninja', 'Netflix'],
    ['com.google.android.youtube.tv', 'YouTube'],
    ['com.amazon.amazonvideo.livingroom', 'Prime Video'],
    ['com.disney.disneyplus', 'Disney+'],
    ['com.spotify.tv.android', 'Spotify'],
    ['com.plexapp.android', 'Plex'],
    ['com.apple.atve.androidtv.appletv', 'Apple TV'],
    ['tv.twitch.android.app', 'Twitch'],
    ['com.google.android.youtube.tvmusic', 'YouTube Music'],
    ['org.videolan.vlc', 'VLC'],
    ['com.hbo.hbonow', 'Max'],
    ['com.sony.dtv.tvlin', 'Live TV'],
    ['com.android.vending', 'Google Play Store'],
    ['com.google.android.apps.photos', 'Google Photos']
  ].map(([pkg, label], i) => ({ pkg, label, tv: i !== 13 }))

  let theme: TvTheme = {
    present: false,
    config: {
      dock_apps: ['com.netflix.ninja', 'com.amazon.amazonvideo.livingroom', 'com.google.android.youtube.tv', 'com.disney.disneyplus'],
      columns: 4,
      rows_below_dock: 0
    },
    wallpaper: null,
    wallpapers: []
  }

  const entries: DebloatEntry[] = [
    ['com.sony.dtv.promos', 'sponsored rows on Home', true, 'enabled', 41],
    ['com.google.android.apps.tv.launcherx', 'stock launcher; disable after the new one is Home', true, 'enabled', 38],
    ['com.google.android.tungsten.setupwraith', 'its RecoveryActivity otherwise takes over Home', true, 'enabled', 33],
    ['com.sony.dtv.discovery', 'Discover row', true, 'enabled', 12],
    ['com.google.android.apps.tv.dreamx', 'Google screensaver; the new launcher has a Clock screensaver', true, 'enabled', 29],
    ['com.google.android.feedback', 'feedback reporter', true, 'disabled', 52],
    ['com.sony.dtv.smarthelp', 'Help app', true, 'enabled', 9],
    ['com.sony.dtv.analytics', 'usage analytics', true, 'enabled', 11],
    ['com.google.android.tts', 're-enable if you use accessibility speech', false, 'enabled', 18],
    ['com.sony.dtv.airplayapp', 'AirPlay; kept by owner', false, 'enabled', 3],
    ['com.sony.dtv.bravia.cam', 'BRAVIA CAM', false, 'absent', 2],
    ['com.sony.dtv.osat.album', 'Album app', true, 'enabled', 6]
  ].map(([pkg, note, suggested, state, common]) => ({
    pkg: pkg as string,
    note: note as string,
    suggested: suggested as boolean,
    state: state as DebloatEntry['state'],
    common: common as number
  }))
  let record: DebloatPlan['record']

  const ok = (summary: string, warnings: string[] = []): ApplyResult => ({ ok: true, summary, warnings })

  const presets: ThemePreset[] = [
    { id: 'default', name: 'Default', description: 'The built-in look written out', config: { columns: 4, background_color: '#000000', wallpaper: false }, wallpaper: null },
    { id: 'aurora', name: 'Aurora', description: 'Teal and violet wallpaper on navy', config: { background_color: '#070A14', wallpaper: true }, wallpaper: gradientUrl('#0b6e6e', '#3b1d6e', '#070a14') },
    { id: 'dussehra', name: 'Dussehra', description: 'Festive skin, marigold and diya', config: { festival: 'dussehra', festival_until: '2026-10-21', accent_color: '#FFB23C', background_color: '#120809', wallpaper: true }, wallpaper: gradientUrl('#5a2a05', '#2a0c08', '#120809') },
    { id: 'six-columns', name: 'Six columns', description: 'Six smaller tiles, one row under the dock', config: { columns: 6, rows_below_dock: 1, background_color: '#000000', wallpaper: false }, wallpaper: null }
  ]

  const sys: SystemState = {
    sdk: 34,
    screensaverIsClock: false,
    privateDnsMode: 'opportunistic',
    privateDnsHost: '',
    animationScale: '1.0'
  }

  async function fakeDownload(tool: keyof typeof tools): Promise<ApplyResult> {
    const total = tool === 'jdk' ? 190e6 : tool === 'sdk' ? 160e6 : tool === 'source' ? 6e6 : 12e6
    for (let r = 0; r <= total; r += total / 20) {
      progress.forEach((cb) => cb({ tool, received: r, total, phase: 'download' }))
      await wait(80)
    }
    progress.forEach((cb) => cb({ tool, received: total, total, phase: 'extract' }))
    await wait(400)
    progress.forEach((cb) => cb({ tool, received: total, total, phase: 'done' }))
    tools[tool] = true
    log('download', 'ok', `${tool} ready`)
    return ok(`${tool} downloaded`)
  }

  const api: CytvApi = {
    platform: 'darwin',
    async scan() {
      await wait(1200)
      return [{ host: '192.168.1.50' }, { host: '192.168.1.73' }]
    },
    async connect(host) {
      setStatus({ state: 'connecting', host })
      await wait(700)
      if (host === '192.168.1.73') {
        const s: ConnStatus = { state: 'error', host, needsPairing: true, error: 'The TV answered but wants Wireless debugging pairing first.' }
        setStatus(s)
        return s
      }
      setStatus({ state: 'waiting-auth', host })
      await wait(2500)
      const s: ConnStatus = { state: 'connected', host, probe: { ...probe, host } }
      setStatus(s)
      log('connect', 'ok', `Connected to ${probe.manufacturer} ${probe.model}`)
      return s
    },
    async disconnect() {
      setStatus({ state: 'idle' })
    },
    async status() {
      return status
    },
    async lastHost() {
      return '192.168.1.50'
    },
    async reconnect() {
      return null
    },
    async checkAdbClosed(host) {
      await wait(800)
      return { closed: true, message: `${host}:5555 is closed: the TV refused the connection.` }
    },
    async pair(host) {
      log('pair', 'cmd', `adb pair ${host}`)
      await wait(1500)
      return ok('Paired. Port 5555 is open until the next reboot.')
    },
    async checks() {
      await wait(300)
      const connected = status.state === 'connected'
      const items: CheckItem[] = [
        { id: 'network', label: 'This computer is on a network', status: 'ok', detail: 'Wi-Fi, 192.168.1.20' },
        { id: 'tv', label: 'TV answers on port 5555', status: connected ? 'ok' : 'missing', detail: connected ? `${status.host}` : 'Turn on USB debugging on the TV, then find it below.' },
        { id: 'auth', label: 'TV allows this computer', status: connected ? 'ok' : 'missing', detail: connected ? 'Allowed' : 'The TV asks once. Choose Allow.' },
        { id: 'apk', label: 'Launcher app', status: 'ok', detail: 'Bundled with this app, version 2.4' },
        { id: 'jdk', label: 'Java 17 (only to build from source)', status: tools.jdk ? 'ok' : 'optional', detail: tools.jdk ? 'Temurin 17.0.12' : 'About 190 MB', download: tools.jdk ? undefined : 'jdk' },
        { id: 'sdk', label: 'Android SDK 34 (only to build from source)', status: tools.sdk ? 'ok' : 'optional', detail: tools.sdk ? 'platforms;android-34, build-tools;34.0.0' : 'About 160 MB', download: tools.sdk ? undefined : 'sdk' },
        { id: 'source', label: 'Launcher source code', status: tools.source ? 'ok' : 'optional', detail: tools.source ? 'cleanyourtv main' : 'From GitHub, about 6 MB', download: tools.source ? undefined : 'source' },
        { id: 'platform-tools', label: "Google's adb (only for pairing)", status: tools['platform-tools'] ? 'ok' : 'optional', detail: 'Some Google TVs need a one-time pairing code.', download: tools['platform-tools'] ? undefined : 'platform-tools' }
      ]
      return items
    },
    download: (tool) => fakeDownload(tool),
    async lists() {
      await wait(200)
      return [
        { id: 'sony-bravia-9-ii', title: 'Sony BRAVIA 9 II, K-65XR90M2 (2026, device BRAVIA_AE3, MediaTek MT5838)', score: 9 },
        { id: 'sony-bravia-4k-ae2', title: 'Sony BRAVIA 4K AE2 (BRAVIA_AE2, MediaTek MT5897), North America', score: 3 },
        { id: 'custom', title: 'Start from the packages on this TV', score: 0 },
        { id: 'tcl-g10-google-tv', title: 'TCL 55" 4K Google TV, 2023 (G10, Realtek RTD2875P)', score: 0 },
        { id: 'xiaomi-mi-box-s', title: 'Xiaomi Mi Box S (MIBOX4, oneday), streaming box', score: 0 },
        { id: 'philips-r8g-google-tv', title: 'Philips R8G Google TV (MOKA, Realtek RTD2851F), US', score: 0 }
      ]
    },
    async plan(listId) {
      await wait(500)
      return {
        listId,
        title: 'Sony BRAVIA 9 II, K-65XR90M2 (2026, device BRAVIA_AE3, MediaTek MT5838)',
        header: ['Google TV 14 (API 34), build UKN4.250422.001.', 'Everything is DISABLED, not uninstalled.'],
        entries: entries.map((e) => ({ ...e })),
        record,
        homePackage: launcherHome ? 'com.gathin.tvhome' : 'com.google.android.apps.tv.launcherx'
      }
    },
    async applyDebloat(req) {
      log('debloat', 'info', 'Recording the current state first')
      await wait(500)
      if (!record) record = { path: '~/.cleanyourtv/recovery-192.168.1.50-3fa1b2c4.json', created: new Date().toISOString() }
      const warnings: string[] = []
      for (const pkg of req.disable) {
        const e = entries.find((x) => x.pkg === pkg)
        if (pkg === 'com.google.android.apps.tv.launcherx' && !launcherHome && !req.forceHome) {
          warnings.push('Skipped com.google.android.apps.tv.launcherx: it owns Home. Install the new launcher first, then apply again.')
          continue
        }
        log('debloat', 'cmd', `pm disable-user --user 0 ${pkg}`)
        await wait(120)
        if (e) {
          e.recorded ??= e.state
          e.state = 'disabled'
        }
      }
      for (const pkg of req.enable) {
        log('debloat', 'cmd', `pm enable ${pkg}`)
        const e = entries.find((x) => x.pkg === pkg)
        if (e) e.state = 'enabled'
      }
      return ok(`Disabled ${req.disable.length - warnings.length} packages, switched ${req.enable.length} back on.`, warnings)
    },
    async restore() {
      await wait(900)
      entries.forEach((e) => {
        if (e.recorded) e.state = e.recorded
      })
      log('restore', 'ok', 'Restored the recorded state')
      return ok('Put back the state recorded before the first change.')
    },
    async launcherStatus() {
      await wait(300)
      return {
        installed: launcherInstalled,
        versionName: launcherInstalled ? '2.4' : undefined,
        isHome: launcherHome,
        homeComponent: launcherHome ? 'com.gathin.tvhome/.MainActivity' : 'com.google.android.apps.tv.launcherx/.home.HomeActivity',
        stockLauncher: 'com.google.android.apps.tv.launcherx',
        bundledApk: true,
        bundledVersion: '2.4',
        localApk: false
      }
    },
    async install(req) {
      const steps: [LogLine['level'], string][] = [
        ['info', 'Uploading the launcher (390 KB)'],
        ['cmd', 'pm install -r /data/local/tmp/tvhome.apk'],
        ['cmd', 'am start -W -n com.gathin.tvhome/.MainActivity'],
        ['cmd', 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity']
      ]
      for (const [level, text] of steps) {
        log('install', level, text)
        await wait(500)
      }
      if (req.inputKeyService) log('install', 'cmd', 'settings put secure enabled_accessibility_services com.gathin.tvhome/.InputKeyService')
      launcherInstalled = true
      launcherHome = true
      return { ok: true, summary: 'Installed. Press Home on the remote to see it.', warnings: [] }
    },
    async buildLocal() {
      log('build', 'cmd', './gradlew assembleRelease')
      await wait(2000)
      return ok('Built app-release.apk from source.')
    },
    async useStockLauncher() {
      launcherHome = false
      homeIsHome = false
      return ok('Home belongs to com.google.android.apps.tv.launcherx again.')
    },
    async useCleanYourTv() {
      await wait(600)
      launcherHome = true
      homeIsHome = false
      return ok('Switched. Press Home on the remote to see it.')
    },
    async prefs() {
      return { ...prefs }
    },
    async setPrefs(change) {
      Object.assign(prefs, change)
      return { ...prefs }
    },
    async homeStatus() {
      await wait(200)
      return { installed: homeInstalled, versionName: homeInstalled ? '0.2.0-preview' : undefined, isHome: homeIsHome, bundled: true, bundledVersion: '0.2.0-preview' }
    },
    async installHome() {
      for (const text of ['pm install -r /data/local/tmp/cmtv-home.apk', 'am start -W -n io.github.wingedpanther.cleanmytv.home/.MainActivity']) {
        log('install', 'cmd', text)
        await wait(500)
      }
      homeInstalled = true
      homeIsHome = true
      launcherHome = false
      return ok('CleanMyTV Home is Home now. Press Home on the remote to see it.')
    },
    async useHome() {
      await wait(600)
      homeIsHome = true
      launcherHome = false
      return ok('Switched. Press Home on the remote to see it.')
    },
    async readHomeConfig() {
      await wait(200)
      return { ...homeConfig }
    },
    async setHomeConfig(change) {
      await wait(150)
      Object.assign(homeConfig, change)
      return { ...homeConfig }
    },
    async setHomeWallpaper(jpeg) {
      await wait(800)
      homeConfig.has_wallpaper = jpeg !== null
      return { ...homeConfig }
    },
    async knownTvs() {
      return tvs.map((t) => ({ ...t }))
    },
    async connectKnown(host) {
      return api.connect(host)
    },
    async forgetTv(host) {
      tvs = tvs.filter((t) => t.host !== host)
      return tvs.map((t) => ({ ...t }))
    },
    async saveTvThumb(host, dataUrl) {
      tvs = tvs.map((t) => (t.host === host ? { ...t, thumb: dataUrl, thumbAt: Date.now() } : t))
      return tvs.map((t) => ({ ...t }))
    },
    async apps() {
      await wait(300)
      return apps
    },
    async readTheme() {
      await wait(300)
      return structuredClone(theme)
    },
    async applyTheme(req) {
      log('theme', 'info', 'Pushing config.json')
      await wait(700)
      theme = {
        present: true,
        config: req.config as LauncherConfig,
        wallpaper:
          req.wallpaper.kind === 'set'
            ? `data:image/jpeg;base64,${req.wallpaper.image.base64}`
            : req.wallpaper.kind === 'remove'
              ? null
              : theme.wallpaper,
        wallpapers: req.wallpapers.kind === 'set' ? req.wallpapers.images.map((i) => i.name) : theme.wallpapers
      }
      log('theme', 'ok', 'Home restarted with the new theme')
      return ok('Applied. Home restarted on the TV.')
    },
    async resetTheme() {
      theme = { present: false, config: {}, wallpaper: null, wallpapers: [] }
      return ok('Removed the theme. The launcher uses its built-in look.')
    },
    async presets() {
      return presets
    },
    async screenshot() {
      await wait(900)
      return gradientUrl('#101820', '#0b2a3a', '#000000')
    },
    async webGet() {
      throw new Error('Background sources need the desktop app.')
    },
    async systemState() {
      return { ...sys }
    },
    async setClockScreensaver(on) {
      sys.screensaverIsClock = on
      return ok(on ? 'Clock screensaver is on.' : 'Screensaver set back to the TV default.')
    },
    async setPrivateDns(host) {
      sys.privateDnsMode = host ? 'hostname' : 'opportunistic'
      sys.privateDnsHost = host ?? ''
      return ok(host ? `Private DNS set to ${host}.` : 'Private DNS back to automatic.')
    },
    async setAnimationScale(scale) {
      sys.animationScale = String(scale)
      return ok(`Animation speed set to ${scale}x.`)
    },
    async setLauncherLocale(tag) {
      return ok(`Launcher language set to ${tag}.`)
    },
    async key(code) {
      log('system', 'cmd', `input keyevent ${code}`)
    },
    async reboot() {
      log('system', 'cmd', 'reboot')
      setStatus({ state: 'idle' })
    },
    onLog(cb) {
      logs.add(cb)
      return () => logs.delete(cb)
    },
    onProgress(cb) {
      progress.add(cb)
      return () => progress.delete(cb)
    },
    onStatus(cb) {
      statuses.add(cb)
      return () => statuses.delete(cb)
    }
  }
  window.cytv = api
}

function gradientUrl(a: string, b: string, c: string): string {
  const canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 180
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  const g = ctx.createLinearGradient(0, 0, 320, 180)
  g.addColorStop(0, a)
  g.addColorStop(0.55, b)
  g.addColorStop(1, c)
  ctx.fillStyle = g
  ctx.fillRect(0, 0, 320, 180)
  return canvas.toDataURL('image/jpeg', 0.8)
}
