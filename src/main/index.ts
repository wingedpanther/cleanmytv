import { app, BrowserWindow, dialog, ipcMain, session, shell, type SaveDialogOptions } from 'electron'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'
import { configurePaths, paths } from './paths'
import { log, setSinks } from './log'
import * as T from './tv'
import * as debloat from './debloat'
import * as launcher from './launcher'
import * as system from './system'
import * as sideload from './sideload'
import * as tools from './toolchain'
import { discover, stopDiscovery } from './discover'
import { localSubnets, scan } from './scan'
import { reconnect } from './reconnect'
import { bootCheck } from './bootcheck'
import { check as checkUpdate, isNewer, type UpdateCache } from './updates'
import * as updater from './updater'
import { toJpeg } from './picture'
import { autoConnect, forgetTv, listTvs, rememberTv, setThumb } from './tvlist'
import * as web from './web'
import * as home from './home'
import * as themes from './themes'
import { THEME_EXT, themeFileName } from '@shared/themes'
import { bugReport, fullReport } from './report'
import * as storage from './storage'
import { tellHome } from './switchedoff'
import * as record from './record'
import * as checkup from './checkup'
import { englishOf, languageFromLocale, setLanguage, t, UiError } from './i18n'
import type { CheckItem, ConnStatus, CytvApi, KnownTv } from '@shared/api'

let win: BrowserWindow | null = null
/** The new version's installer once it is downloaded and checked. */
let updateFile: string | null = null
/** The first update check of each launch asks GitHub, whatever the cache says. */
let askedGitHub = false

interface Settings {
  lastHost?: string
  /** The TV that answered there, to notice a different device at the same address. */
  lastDevice?: string
  lastModel?: string
  /** Every TV connected before. With more than one, the app waits for the user to pick. */
  tvs?: KnownTv[]
  update?: UpdateCache
}

function readSettings(): Settings {
  try { return JSON.parse(readFileSync(paths.settings, 'utf8')) } catch { return {} }
}

function writeSettings(s: Settings): void {
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(paths.settings, JSON.stringify(s, null, 2))
}

const knownTvs = (): KnownTv[] => listTvs(readSettings())
/** A TV that connected: remembered, and CleanMyTV Home told what stays switched off, in the background. */
const remember = (host: string, st: ConnStatus): void => {
  writeSettings(rememberTv(readSettings(), host, st))
  void tellHome()
}

/** The save dialog over the window, or on its own when the window is gone. */
async function saveDialog(opts: SaveDialogOptions): Promise<string | null> {
  const r = win && !win.isDestroyed() ? await dialog.showSaveDialog(win, opts) : await dialog.showSaveDialog(opts)
  return r.canceled || !r.filePath ? null : r.filePath
}

/** The connected TV's model and today's date, for file names. */
function fileTag(): string {
  const model = (T.getStatus().probe?.model ?? 'no-tv').replace(/[^\w-]+/g, '-').toLowerCase()
  return `${model}-${new Date().toISOString().slice(0, 10)}`
}

async function checks(): Promise<CheckItem[]> {
  const st = T.getStatus()
  const connected = st.state === 'connected'
  return [
    {
      id: 'network', label: t('Local network'),
      ...(localSubnets().length
        ? { status: 'ok', detail: t('This computer is on {nets}.', { nets: localSubnets().map((n) => n + '.x').join(', ') }) }
        : { status: 'missing', detail: t('No home network found. Join the same Wi-Fi or Ethernet as the TV.') })
    },
    {
      id: 'tv', label: t('TV with network debugging on'),
      ...(connected
        ? { status: 'ok', detail: t('{tv} at {host}', { tv: `${st.probe?.manufacturer} ${st.probe?.model}`, host: st.host ?? '' }) }
        : { status: 'missing', detail: t('Turn on Developer options and USB debugging on the TV, then find it below.') })
    },
    {
      id: 'auth', label: t('This computer allowed on the TV'),
      ...(connected
        ? { status: 'ok', detail: t('The TV accepted this computer.') }
        : st.state === 'waiting-auth'
          ? { status: 'checking', detail: t('Choose Allow on the TV.') }
          : { status: 'missing', detail: t('The TV asks once, the first time you connect.') })
    },
    {
      id: 'platform-tools', label: t("Google's adb (only for Wireless debugging pairing)"),
      ...(tools.platformToolsReady() ? { status: 'ok', detail: tools.adbPath() } : { status: 'optional', detail: t('About 15 MB. Some Google TVs need a one-time pairing code.'), download: 'platform-tools' })
    }
  ] as CheckItem[]
}

type Handlers = { [K in keyof CytvApi as CytvApi[K] extends (...a: never[]) => Promise<unknown> ? K : never]: CytvApi[K] }

const handlers: Omit<Handlers, 'onLog' | 'onProgress' | 'onStatus'> = {
  setLanguage: async (lang) => setLanguage(lang),
  scan: () => scan(),
  discover: () => discover(),
  stopDiscovery: async () => stopDiscovery(),
  connect: async (host) => {
    const s = await T.connect(host)
    if (s.state === 'connected') remember(host.trim(), s)
    return s
  },
  // Only the saved address; T.connect, not handlers.connect, so a wrong device is never saved. With more
  // than one TV known, nothing connects by itself: the user picks.
  reconnect: async () => {
    if (!autoConnect(readSettings())) return null
    const s = await reconnect(readSettings(), { connect: T.connect, disconnect: T.disconnect, report: T.report })
    if (s?.state === 'connected') remember(s.host ?? readSettings().lastHost!, s)
    return s
  },
  knownTvs: async () => knownTvs(),
  connectKnown: async (host) => {
    const tv = knownTvs().find((t) => t.host === host)
    if (!tv) throw new UiError('That TV is not in the list.')
    const s = await reconnect({ lastHost: tv.host, lastDevice: tv.device, lastModel: tv.model }, { connect: T.connect, disconnect: T.disconnect, report: T.report })
    if (s?.state === 'connected') remember(tv.host, s)
    return s ?? T.getStatus()
  },
  forgetTv: async (host) => {
    writeSettings(forgetTv(readSettings(), host))
    return knownTvs()
  },
  saveTvThumb: async (host, dataUrl) => {
    writeSettings(setThumb(readSettings(), host, dataUrl))
    return knownTvs()
  },

  disconnect: async () => T.disconnect(),
  status: async () => T.getStatus(),
  lastHost: async () => readSettings().lastHost ?? null,
  checkAdbClosed: (host) => system.adbClosed(host),
  bootCheck: () => bootCheck(),
  checkUpdate: async (force) => {
    const s = readSettings()
    const { info, cache } = await checkUpdate(app.getVersion(), s.update ?? {}, Date.now(), fetch, force === true || !askedGitHub)
    askedGitHub = true
    if (cache !== s.update) writeSettings({ ...readSettings(), update: cache })
    return { ...info, canInstall: app.isPackaged && !!updater.assetName(info.latest ?? '', process.platform, process.arch) }
  },
  downloadUpdate: async () => {
    const latest = readSettings().update?.latest
    if (!latest || !isNewer(latest, app.getVersion())) return { ok: false, summary: t('There is no newer version to download.'), warnings: [] }
    updateFile = await updater.download(latest, { platform: process.platform, arch: process.arch, dir: join(paths.data, 'updates') })
    return { ok: true, summary: t('Version {v} is downloaded and checked.', { v: latest }), warnings: [] }
  },
  installUpdate: async () => {
    if (!updateFile) return { ok: false, summary: t('Download the update first.'), warnings: [] }
    return updater.install(updateFile, { platform: process.platform, exe: app.getPath('exe'), dir: join(paths.data, 'updates'), quit: () => app.quit(), openPath: (p) => shell.openPath(p) })
  },
  // The pairing form connects once this returns. Connecting here too would do it twice.
  pair: (host, pairPort, code, connectPort) => tools.pair(host, pairPort, code, connectPort),
  bugReport: () => bugReport(app.getVersion()),
  saveReport: async () => {
    const text = await fullReport(app.getVersion())
    const path = await saveDialog({ title: t('Save the report'), defaultPath: join(app.getPath('downloads'), `cleanmytv-report-${fileTag()}.txt`), filters: [{ name: t('Text'), extensions: ['txt'] }] })
    if (!path) return null
    writeFileSync(path, text)
    shell.showItemInFolder(path)
    return path
  },
  recordStart: () => record.start(),
  recordStop: async () => {
    const text = await record.stop(await bugReport(app.getVersion()))
    const model = (T.getStatus().probe?.model ?? 'tv').replace(/[^\w-]+/g, '-').toLowerCase()
    const stamp = new Date().toISOString().slice(0, 16).replace(/[:T]/g, '-')
    const opts = { title: t('Save the TV log'), defaultPath: join(app.getPath('documents'), `cleanmytv-log-${model}-${stamp}.txt`), filters: [{ name: t('Text'), extensions: ['txt'] }] }
    const r = win && !win.isDestroyed() ? await dialog.showSaveDialog(win, opts) : await dialog.showSaveDialog(opts)
    if (r.canceled || !r.filePath) return null
    writeFileSync(r.filePath, text)
    shell.showItemInFolder(r.filePath)
    return r.filePath
  },
  recordCancel: async () => record.cancel(),
  checkup: () => checkup.run(),
  checkupFix: async (id) => { await checkup.fix(id); return checkup.run() },
  checks,
  download: (tool) => tools.get(tool),
  lists: async () => debloat.lists(T.getStatus().probe),
  plan: (id) => debloat.plan(id),
  tvPackages: () => debloat.tvPackages(),
  applyDebloat: async (req) => {
    const r = await debloat.apply(req)
    await tellHome()
    return r
  },
  restore: async (id) => {
    const r = await debloat.restore(id)
    await tellHome()
    return r
  },
  cameBack: async () => (await debloat.switchedOff()).back,
  useStockLauncher: async () => home.leftHome(await launcher.useStock()),
  otherLaunchers: () => launcher.otherLaunchers(),
  useOtherLauncher: async (pkg) => home.leftHome(await launcher.useOther(String(pkg ?? ''))),
  keepGoogleLauncher: (on) => home.keepGoogleLauncher(on === true),
  removeOldLauncher: () => launcher.removeOld(),
  homeStatus: () => home.status(),
  installHome: async (stock) => {
    const r = await home.install(typeof stock === 'string' ? stock : undefined)
    if (r.ok) await tellHome()
    return r
  },
  useHome: async (stock) => {
    const st = await home.status()
    if (!st.installed) return { ok: false, summary: t('CleanMyTV Home is not installed yet.'), warnings: [] }
    return launcher.switchTo(`${launcher.HOME_PKG}/.MainActivity`, typeof stock === 'string' ? stock : undefined)
  },
  homeApps: () => home.apps(),
  readHomeConfig: () => home.readConfig(),
  setRemoteButtons: (on) => home.setRemoteButtons(on === true),
  setLockPin: (pin) => home.setLockPin(String(pin ?? '')),
  setAppLock: (on) => home.setAppLock(on === true),
  clearAppLock: () => home.clearAppLock(),
  themePackBegin: () => home.themePackBegin(),
  themePackPut: (item) => home.themePackPut(item),
  themePackEnd: (order) => home.themePackEnd(order),
  setSlides: (jpegs) => home.setSlides(jpegs),
  fireplace: (action) => home.fireplace(action === 'put' || action === 'saver' || action === 'remove' ? action : 'show'),
  homeKeys: () => home.keys(),
  saveHomeLook: (slot) => home.saveLook(slot),
  saveThemeOnTv: (name) => home.saveThemeOnTv(String(name ?? '')),
  setHomeConfig: (change) => home.setConfig(change ?? {}),
  setHomeWallpaper: (jpeg) => home.setWallpaper(jpeg),
  pictureToJpeg: (bytes) => toJpeg(bytes),
  applyHomeTheme: (theme) => themes.apply(theme),
  themeBackup: () => themes.backup(),
  restoreThemeBackup: () => themes.restore(),
  forgetThemeBackup: async () => themes.forget(),
  saveHomeTheme: (name) =>
    themes.save(String(name ?? ''), async (n) => {
      const opts = { title: t('Save the theme'), defaultPath: join(app.getPath('documents'), themeFileName(n)), filters: [{ name: t('CleanMyTV theme'), extensions: [THEME_EXT] }] }
      const r = win && !win.isDestroyed() ? await dialog.showSaveDialog(win, opts) : await dialog.showSaveDialog(opts)
      return r.canceled || !r.filePath ? null : r.filePath
    }),
  screenshot: () => system.screenshot(),
  saveScreenshot: async () => {
    const png = system.lastScreenshot()
    if (!png) return null
    const opts = { title: t('Save the snapshot'), defaultPath: join(app.getPath('pictures'), system.shotName()), filters: [{ name: t('PNG image'), extensions: ['png'] }] }
    const r = win && !win.isDestroyed() ? await dialog.showSaveDialog(win, opts) : await dialog.showSaveDialog(opts)
    if (r.canceled || !r.filePath) return null
    writeFileSync(r.filePath, png)
    shell.showItemInFolder(r.filePath)
    return r.filePath
  },
  // Nobody asked for this one, so a TV that can't take it (a Mi Box S, on first connect) gets a note, not an error.
  thumbShot: () => system.screenshot().catch((e: Error) => {
    log('screenshot', 'info', `no picture of the TV screen for the list this time: ${e.message}`)
    return null
  }),
  webGet: (url) => web.get(String(url)),
  systemState: () => system.state(),
  setPrivateDns: (host) => system.privateDns(host),
  setAnimationScale: (s) => system.animationScale(s),
  setMatchFrameRate: (m) => system.matchFrameRate(m),
  fixScreensaver: () => system.fixScreensaver(),
  sideloadInfo: (path) => sideload.inspect(String(path)),
  sideload: (path) => sideload.install(String(path)),
  clearCaches: () => system.clearCaches(),
  appSizes: () => storage.sizes(),
  removeUpdate: (pkg) => storage.removeUpdate(String(pkg ?? '')),
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
  const icon = process.platform === 'linux'
    ? [join(paths.resources, 'icon.png'), join(app.getAppPath(), 'build', 'icon.png')].find(existsSync)
    : undefined
  win = new BrowserWindow({
    width: 1240,
    height: 820,
    minWidth: 960,
    minHeight: 640,
    title: 'CleanMyTV',
    backgroundColor: '#FFFFFF',
    show: false,
    ...(icon ? { icon } : {}),
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
  languageFromLocale(app.getLocale())
  // The app needs no camera, microphone, notifications or other web permissions.
  session.defaultSession.setPermissionRequestHandler((_wc, _perm, cb) => cb(false))
  // Packaged: lists and the APK sit in Contents/Resources (mac) or resources/ (Windows and Linux).
  configurePaths(app.isPackaged ? process.resourcesPath : join(app.getAppPath(), 'resources'), app.getPath('userData'))
  setSinks((line) => send('log', line), (p) => send('progress', p))
  T.init(paths.adbKey, (s: ConnStatus) => send('status', s))
  for (const [name, fn] of Object.entries(handlers)) {
    ipcMain.handle(name, async (e, ...args: unknown[]) => {
      if (!isAppUrl(e.senderFrame?.url)) throw new Error('blocked: this request did not come from the app window')
      try {
        return await (fn as (...a: unknown[]) => Promise<unknown>)(...args)
      } catch (e) {
        log(name, 'error', englishOf(e))
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
