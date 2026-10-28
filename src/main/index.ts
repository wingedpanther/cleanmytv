import { app, BrowserWindow, ipcMain, session, shell } from 'electron'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { configurePaths, paths } from './paths'
import { setSinks } from './log'
import * as T from './tv'
import * as debloat from './debloat'
import * as launcher from './launcher'
import * as theme from './theme'
import * as system from './system'
import * as tools from './toolchain'
import { localSubnets, scan } from './scan'
import { reconnect } from './reconnect'
import * as web from './web'
import * as home from './home'
import type { CheckItem, ConnStatus, CytvApi } from '@shared/api'

let win: BrowserWindow | null = null

interface Settings {
  lastHost?: string
  /** The TV that answered there, to notice a different device at the same address. */
  lastDevice?: string
  lastModel?: string
  /** Show CleanMyTV Home, the preview launcher. */
  preview?: boolean
}

function readSettings(): Settings {
  try { return JSON.parse(readFileSync(paths.settings, 'utf8')) } catch { return {} }
}

function writeSettings(s: Settings): void {
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(paths.settings, JSON.stringify(s, null, 2))
}

async function checks(): Promise<CheckItem[]> {
  const st = T.getStatus()
  const connected = st.state === 'connected'
  const hasApk = existsSync(paths.bundledApk) || existsSync(paths.localApk)
  const jdk = tools.javaHome()
  return [
    {
      id: 'network', label: 'Local network',
      ...(localSubnets().length
        ? { status: 'ok', detail: `This computer is on ${localSubnets().map((n) => n + '.x').join(', ')}.` }
        : { status: 'missing', detail: 'No home network found. Join the same Wi-Fi or Ethernet as the TV.' })
    },
    {
      id: 'tv', label: 'TV with network debugging on',
      ...(connected
        ? { status: 'ok', detail: `${st.probe?.manufacturer} ${st.probe?.model} at ${st.host}` }
        : { status: 'missing', detail: 'Turn on Developer options and USB debugging on the TV, then find it below.' })
    },
    {
      id: 'auth', label: 'This computer allowed on the TV',
      ...(connected
        ? { status: 'ok', detail: 'The TV accepted this computer.' }
        : st.state === 'waiting-auth'
          ? { status: 'checking', detail: 'Choose Allow on the TV.' }
          : { status: 'missing', detail: 'The TV asks once, the first time you connect.' })
    },
    {
      id: 'apk', label: 'Clean launcher',
      ...(hasApk
        ? { status: 'ok', detail: existsSync(paths.bundledApk) ? 'Bundled with this app, nothing to download.' : 'Your local build is ready.' }
        : { status: 'missing', detail: 'Not bundled in this copy. Build it from source (Launcher step).' })
    },
    {
      id: 'jdk', label: 'Java 17 (only to build from source)',
      ...(jdk ? { status: 'ok', detail: jdk } : { status: 'optional', detail: 'About 190 MB. Not needed with the bundled launcher.', download: 'jdk' })
    },
    {
      id: 'sdk', label: 'Android SDK 34 (only to build from source)',
      ...(tools.sdkReady() ? { status: 'ok', detail: tools.dirs.sdk } : { status: 'optional', detail: 'About 250 MB, needs Java 17 first.', download: 'sdk' })
    },
    {
      id: 'source', label: 'Launcher source (only to build from source)',
      ...(tools.sourceReady() ? { status: 'ok', detail: tools.dirs.source } : { status: 'optional', detail: 'The latest cleanyourtv code from GitHub, about 7 MB.', download: 'source' })
    },
    {
      id: 'platform-tools', label: "Google's adb (only for Wireless debugging pairing)",
      ...(tools.platformToolsReady() ? { status: 'ok', detail: tools.adbPath() } : { status: 'optional', detail: 'About 15 MB. Some Google TVs need a one-time pairing code.', download: 'platform-tools' })
    }
  ] as CheckItem[]
}

type Handlers = { [K in keyof CytvApi as CytvApi[K] extends (...a: never[]) => Promise<unknown> ? K : never]: CytvApi[K] }

const handlers: Omit<Handlers, 'onLog' | 'onProgress' | 'onStatus'> = {
  scan: () => scan(),
  connect: async (host) => {
    const s = await T.connect(host)
    if (s.state === 'connected') writeSettings({ ...readSettings(), lastHost: host.trim(), lastDevice: s.probe?.device, lastModel: s.probe?.model })
    return s
  },
  // Only the saved address; T.connect, not handlers.connect, so a wrong device is never saved.
  reconnect: () => reconnect(readSettings(), { connect: T.connect, disconnect: T.disconnect, report: T.report }),
  disconnect: async () => T.disconnect(),
  status: async () => T.getStatus(),
  lastHost: async () => readSettings().lastHost ?? null,
  checkAdbClosed: (host) => system.adbClosed(host),
  pair: async (host, pairPort, code, connectPort) => {
    const r = await tools.pair(host, pairPort, code, connectPort)
    if (r.ok) await handlers.connect(host)
    return r
  },
  checks,
  download: (tool) => tools.get(tool),
  lists: async () => debloat.lists(T.getStatus().probe),
  plan: (id) => debloat.plan(id),
  applyDebloat: (req) => debloat.apply(req),
  restore: (id) => debloat.restore(id),
  launcherStatus: () => launcher.status(),
  install: (req) => launcher.install(req),
  buildLocal: () => tools.buildLocal(),
  useStockLauncher: () => launcher.useStock(),
  useCleanYourTv: () => launcher.useCleanYourTv(),
  prefs: async () => ({ preview: readSettings().preview === true }),
  setPrefs: async (change) => {
    const s = readSettings()
    if (change && typeof change.preview === 'boolean') writeSettings({ ...s, preview: change.preview })
    return { preview: readSettings().preview === true }
  },
  homeStatus: () => home.status(),
  installHome: () => home.install(),
  useHome: async () => {
    const st = await home.status()
    if (!st.installed) return { ok: false, summary: 'CleanMyTV Home is not installed yet.', warnings: [] }
    return launcher.switchTo(`${launcher.HOME_PKG}/.MainActivity`)
  },
  readHomeConfig: () => home.readConfig(),
  setHomeConfig: (change) => home.setConfig(change ?? {}),
  apps: () => theme.apps(),
  readTheme: () => theme.read(),
  applyTheme: (req) => theme.apply(req),
  resetTheme: () => theme.reset(),
  presets: async () => theme.presets(),
  screenshot: () => theme.screenshot(),
  webGet: (url) => web.get(String(url)),
  systemState: () => system.state(),
  setClockScreensaver: (on) => system.clockScreensaver(on),
  setPrivateDns: (host) => system.privateDns(host),
  setAnimationScale: (s) => system.animationScale(s),
  setLauncherLocale: (tag) => system.launcherLocale(tag),
  key: (code) => system.key(code),
  reboot: () => system.reboot()
}

function send(channel: string, value: unknown): void {
  if (win && !win.isDestroyed()) win.webContents.send(channel, value)
}

// The dev server URL is honoured only in a dev run; an installed app always loads its own files.
const devUrl = !app.isPackaged ? process.env.ELECTRON_RENDERER_URL : undefined
const appPage = (): string => pathToFileURL(join(__dirname, '../renderer/index.html')).href

/** The bridge controls the TV, so only the app's own page may use it: never a navigated-to or framed page. */
function isAppUrl(url: string | undefined): boolean {
  if (!url) return false
  const bare = url.split('#')[0].split('?')[0]
  return devUrl ? new URL(url).origin === new URL(devUrl).origin : bare === appPage()
}

function createWindow(): void {
  win = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    title: 'CleanMyTV',
    backgroundColor: '#FFFFFF',
    show: false,
    webPreferences: { preload: join(__dirname, '../preload/index.js'), sandbox: true, contextIsolation: true, nodeIntegration: false, webviewTag: false }
  })
  // Nothing in the app navigates; links open in the system browser instead (below).
  win.webContents.on('will-navigate', (e, url) => { if (!isAppUrl(url)) e.preventDefault() })
  win.webContents.on('will-redirect', (e, url) => { if (!isAppUrl(url)) e.preventDefault() })
  win.webContents.on('will-attach-webview', (e) => e.preventDefault())
  win.once('ready-to-show', () => win?.show())
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  if (devUrl) win.loadURL(devUrl)
  else win.loadFile(join(__dirname, '../renderer/index.html'))
}

// One copy at a time: two would race on the key file and on the same TV connection.
const primary = app.requestSingleInstanceLock()
if (!primary) app.quit()
app.on('second-instance', () => {
  if (win) { if (win.isMinimized()) win.restore(); win.focus() }
})

app.whenReady().then(() => {
  if (!primary) return
  // The app needs no camera, microphone, notifications or other web permissions.
  session.defaultSession.setPermissionRequestHandler((_wc, _perm, cb) => cb(false))
  // Packaged: lists, themes and the APK sit in Contents/Resources (mac) or resources\ (Windows).
  configurePaths(app.isPackaged ? process.resourcesPath : join(app.getAppPath(), 'resources'), app.getPath('userData'))
  setSinks((line) => send('log', line), (p) => send('progress', p))
  T.init(paths.adbKey, (s: ConnStatus) => send('status', s))
  for (const [name, fn] of Object.entries(handlers)) {
    ipcMain.handle(name, async (e, ...args: unknown[]) => {
      if (!isAppUrl(e.senderFrame?.url)) throw new Error('blocked: this request did not come from the app window')
      try {
        return await (fn as (...a: unknown[]) => Promise<unknown>)(...args)
      } catch (e) {
        send('log', { task: name, level: 'error', text: (e as Error).message, time: Date.now() })
        throw e
      }
    })
  }
  createWindow()
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() })
})

app.on('window-all-closed', () => {
  T.disconnect()
  if (process.platform !== 'darwin') app.quit()
})
