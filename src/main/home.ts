// CleanMyTV Home, the launcher in home/. Its settings and wallpaper live in its own private
// storage and come in over ADB through its ConfigProvider (`content call`), so there's no shared folder
// to fail (#20) and nothing to reread after a reboot (#21). Changes show on the TV straight away.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { HOME_PKG, OLD_PKG, addService, detectStock, dropServices, handOver } from './launcher'
import type { HomeButton, HomeConfig, HomeGroup, HomeStatus, InstallResult, KeyPress, TvApp } from '@shared/api'

const TASK = 'install'
const MAIN = `${HOME_PKG}/.MainActivity`
const URI = `content://${HOME_PKG}.config`
const HEX = /^#[0-9A-Fa-f]{6}$/

export const DEFAULTS: HomeConfig = {
  layout: 'shelf', sections: ['nowplaying', 'dock', 'rows', 'folders', 'grid'], top_space: 25, quick: ['inputs', 'wifi', 'settings'],
  rows: [], folders: [], dock_scale: 100, tile_scale: 100, buttons_on: false, buttons: [],
  accent: '#4CAF50', focus: 'glow', background: '#1C2A1F', wallpaper_dim: 35,
  order: [], hidden: [], dock_size: 5, columns: 6, show_names: true, clock: 'time_date', greeting: '',
  weather_place: '', weather_lat: 0, weather_lon: 0, weather_unit: 'c',
  visualiser: true, viz_style: 'bars', viz_bars: 32, viz_height: 12, viz_opacity: 85, viz_colour: 'accent', viz_peaks: true, viz_sensitivity: 100
}

const CHOICES = {
  layout: ['shelf', 'split', 'rail'],
  focus: ['glow', 'outline', 'none'],
  clock: ['time_date', 'time', 'off'],
  viz_style: ['bars', 'mirror', 'wave'],
  viz_colour: ['accent', 'rainbow', 'white'],
  weather_unit: ['c', 'f']
} as const

const LISTS = {
  sections: ['nowplaying', 'dock', 'rows', 'folders', 'grid'],
  quick: ['settings', 'wifi', 'inputs', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music']
} as const

const GROUPS = { rows: 8, folders: 12 } as const

// eslint-disable-next-line no-control-regex
const plain = (s: string, max: number): string => s.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max)

const RANGES = {
  top_space: [0, 40], dock_scale: [60, 150], tile_scale: [60, 150],
  wallpaper_dim: [0, 80], dock_size: [0, 8], columns: [3, 8],
  viz_bars: [8, 64], viz_height: [4, 40], viz_opacity: [20, 100], viz_sensitivity: [25, 300]
} as const

const BOOLS = ['show_names', 'visualiser', 'viz_peaks', 'buttons_on'] as const
const KEY_SERVICE = `${HOME_PKG}/${HOME_PKG}.KeyService`
/** Lets the launcher see other apps' media sessions; on only while the Now playing section is. */
const NOW_LISTENER = `${HOME_PKG}/${HOME_PKG}.NowPlayingListener`
/** Android 9: CleanMyTV Home's minSdk (home/app/build.gradle.kts). */
export const MIN_SDK = 28
const BUTTON_ACTIONS = ['settings', 'wifi', 'inputs', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music', 'home', 'back', 'none', 'app']

function bundledVersion(): string | undefined {
  try {
    return JSON.parse(readFileSync(paths.bundledHomeInfo, 'utf8')).versionName
  } catch {
    return undefined
  }
}

export async function status(): Promise<HomeStatus> {
  const dump = await T.tv().shell(`dumpsys package ${HOME_PKG} | grep -m1 versionName`)
  const versionName = /versionName=(\S+)/.exec(dump)?.[1]
  const home = await T.homeComponent()
  const { installed } = await T.packageSets()
  return {
    oldLauncher: installed.has(OLD_PKG),
    installed: !!versionName,
    versionName,
    isHome: !!home && home.startsWith(HOME_PKG + '/'),
    bundled: existsSync(paths.bundledHomeApk),
    bundledVersion: bundledVersion(),
    unsupported: unsupported() ?? undefined
  }
}

/** Why CleanMyTV Home can't go on the connected TV, or null when it can. */
export function unsupported(): string | null {
  const probe = T.getStatus().probe
  // An unknown SDK (0) is let through: pm install gives its own answer.
  if (!probe?.sdk || probe.sdk >= MIN_SDK) return null
  return `CleanMyTV Home needs Android 9 or newer, and this TV runs Android ${probe.release || '?'} (SDK ${probe.sdk}). Clean up still works on it.`
}

/** Installs or updates CleanMyTV Home, starts it, and only then makes it Home. */
export async function install(): Promise<InstallResult> {
  if (!existsSync(paths.bundledHomeApk)) {
    return { ok: false, summary: 'This copy of the app has no CleanMyTV Home bundled.', warnings: [] }
  }
  const tooOld = unsupported()
  if (tooOld) return { ok: false, summary: tooOld, warnings: [] }
  const stock = await detectStock()
  log(TASK, 'info', `stock launcher: ${stock}`)
  const tmp = '/data/local/tmp/cmtv-home.apk'
  await T.pushB64(TASK, readFileSync(paths.bundledHomeApk), tmp)
  // -d: an older copy of this app may put its older launcher back over a newer one. An uninstall would
  // be the only other way, and it wipes the settings.
  const out = await T.sh(TASK, `pm install -r -d ${tmp}`, { timeoutMs: 300000 })
  await T.sh(TASK, `rm -f ${tmp}`)
  if (!out.includes('Success')) {
    return { ok: false, summary: 'Install failed; Home was left as it was.', warnings: [out.trim()] }
  }
  // The music bars read the TV's sound output. Granted here so the TV never asks.
  await T.sh(TASK, `pm grant ${HOME_PKG} android.permission.RECORD_AUDIO 2>/dev/null; echo audio-granted`)
  const start = await T.sh(TASK, `am start -W -n ${MAIN}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: 'CleanMyTV Home did not start, so Home was left as it was.', warnings: [start.trim()] }
  }
  const failed = await handOver(stock, MAIN)
  if (failed) return failed
  // Android switches accessibility services off when their app is reinstalled: turn the button service back on.
  const cfg = await readConfig().catch(() => null)
  if (cfg?.buttons_on) await addService(KEY_SERVICE)
  if (cfg) await nowPlayingAccess(cfg.sections.includes('nowplaying'))
  const restored = cfg?.configured === false ? await restoreBackup() : false
  return {
    ok: true,
    summary: 'CleanMyTV Home is Home now. Press Home on the remote to see it.' + (restored ? " It started empty, so your settings and wallpaper came back from this computer's copy." : ''),
    warnings: []
  }
}

/** What the launcher answered, with anything missing or odd replaced by the default. */
export function parse(out: string): HomeConfig {
  const m = /config=(\{.*\})\}\]/.exec(out)
  if (!m) throw new Error(`CleanMyTV Home didn't answer: ${out.trim().slice(0, 300) || 'no output'}`)
  const o = JSON.parse(m[1]) as Record<string, unknown>
  const cfg: HomeConfig = { ...DEFAULTS, ...clean(o, true) }
  cfg.has_wallpaper = o.has_wallpaper === true
  if (typeof o.configured === 'boolean') cfg.configured = o.configured
  // Older launchers don't report this; leave it unset so the editor doesn't mark everything unavailable.
  if (Array.isArray(o.quick_available)) cfg.quick_available = o.quick_available.filter((q): q is HomeConfig['quick'][number] => (LISTS.quick as readonly unknown[]).includes(q))
  return cfg
}

/** Keeps only valid keys and values. `lenient` drops bad values; otherwise they throw. */
export function clean(o: Record<string, unknown>, lenient = false): Partial<HomeConfig> {
  const out: Record<string, unknown> = {}
  const bad = (msg: string): void => { if (!lenient) throw new Error(msg) }
  for (const k of ['accent', 'background'] as const) {
    if (o[k] === undefined) continue
    if (typeof o[k] === 'string' && HEX.test(o[k] as string)) out[k] = (o[k] as string).toUpperCase()
    else bad(`${k} must be a colour like #FF3000`)
  }
  for (const [k, allowed] of Object.entries(CHOICES)) {
    if (o[k] === undefined) continue
    if ((allowed as readonly unknown[]).includes(o[k])) out[k] = o[k]
    else bad(`${k} must be one of ${allowed.join(', ')}`)
  }
  for (const [k, [lo, hi]] of Object.entries(RANGES)) {
    if (o[k] === undefined) continue
    if (typeof o[k] === 'number' && Number.isFinite(o[k])) out[k] = Math.min(hi, Math.max(lo, Math.round(o[k] as number)))
    else bad(`${k} must be a number`)
  }
  for (const k of BOOLS) {
    if (o[k] === undefined) continue
    if (typeof o[k] === 'boolean') out[k] = o[k]
    else bad(`${k} must be true or false`)
  }
  for (const k of ['order', 'hidden'] as const) {
    if (o[k] === undefined) continue
    if (Array.isArray(o[k]) && (o[k] as unknown[]).length <= 500 && (o[k] as unknown[]).every((p) => typeof p === 'string' && T.PACKAGE_RE.test(p))) out[k] = [...new Set(o[k] as string[])]
    else bad(`${k} must be a list of package names`)
  }
  for (const [k, allowed] of Object.entries(LISTS)) {
    if (o[k] === undefined) continue
    if (Array.isArray(o[k]) && (o[k] as unknown[]).every((v) => (allowed as readonly unknown[]).includes(v))) out[k] = [...new Set(o[k] as string[])]
    else bad(`${k} must be a list of ${allowed.join(', ')}`)
  }
  for (const [k, max] of Object.entries(GROUPS)) {
    if (o[k] === undefined) continue
    const v = o[k]
    const ok = Array.isArray(v) && v.length <= max && v.every((g) =>
      g && typeof g === 'object' && typeof g.name === 'string' && plain(g.name, 30) !== '' &&
      Array.isArray(g.apps) && g.apps.length <= 60 && g.apps.every((p: unknown) => typeof p === 'string' && T.PACKAGE_RE.test(p)))
    if (ok) out[k] = (v as HomeGroup[]).map((g) => ({ name: plain(g.name, 30), apps: [...new Set(g.apps)] }))
    else bad(`${k} must be up to ${max} named groups of package names`)
  }
  if (o.buttons !== undefined) {
    const v = o.buttons
    const ok = Array.isArray(v) && v.length <= 30 && v.every((b) =>
      b && typeof b === 'object' && Number.isInteger(b.code) && b.code >= 1 && b.code <= 400 && BUTTON_ACTIONS.includes(b.action) &&
      (b.action !== 'app' || (typeof b.app === 'string' && T.PACKAGE_RE.test(b.app))))
    if (ok) {
      const seen = new Set<number>()
      out.buttons = (v as HomeButton[]).filter((b) => !seen.has(b.code) && seen.add(b.code)).map((b) => ({ code: b.code, action: b.action, ...(b.action === 'app' ? { app: b.app } : {}) }))
    } else bad('buttons must be up to 30 key codes (1 to 400), each with a known action')
  }
  if (o.greeting !== undefined) {
    if (typeof o.greeting === 'string') out.greeting = plain(o.greeting, 40)
    else bad('greeting must be text')
  }
  if (o.weather_place !== undefined) {
    if (typeof o.weather_place === 'string') out.weather_place = plain(o.weather_place, 40)
    else bad('weather_place must be text')
  }
  for (const [k, lim] of [['weather_lat', 90], ['weather_lon', 180]] as const) {
    if (o[k] === undefined) continue
    if (typeof o[k] === 'number' && Number.isFinite(o[k]) && Math.abs(o[k] as number) <= lim) out[k] = Math.round((o[k] as number) * 10000) / 10000
    else bad(`${k} must be a number from -${lim} to ${lim}`)
  }
  return out as Partial<HomeConfig>
}

/** The JSON goes over as base64, so a greeting with quotes or emoji never needs shell quoting. */
function call(method: string, arg?: string, task = 'theme'): Promise<string> {
  if (arg !== undefined && !/^[A-Za-z0-9+/=]*$/.test(arg)) throw new Error('internal: argument must be base64 or hex')
  const cmd = `content call --uri ${URI} --method ${method}${arg !== undefined ? ` --arg ${arg}` : ''}`
  return arg && arg.length > 200 ? T.tv().shell(cmd) : T.sh(task, cmd)
}

/** The apps CleanMyTV Home shows, with the names the TV gives them. */
export async function apps(): Promise<TvApp[]> {
  const out = await T.tv().shell(`content call --uri ${URI} --method apps`)
  const m = /apps=(\[.*\])\}\]/.exec(out)
  if (!m) throw new Error(`CleanMyTV Home didn't list its apps: ${out.trim().slice(0, 200) || 'no output'}`)
  const list = JSON.parse(m[1]) as unknown[]
  return list.flatMap((a) => {
    const o = a as Record<string, unknown>
    if (typeof o.pkg !== 'string' || !T.PACKAGE_RE.test(o.pkg)) return []
    const label = typeof o.label === 'string' && o.label.trim() ? o.label.slice(0, 60) : o.pkg
    return [{ pkg: o.pkg, label, tv: o.tv === true }]
  })
}

/** The last remote buttons the TV received, from the launcher's key log. */
export async function keys(): Promise<KeyPress[]> {
  const out = await T.tv().shell(`content call --uri ${URI} --method keys`)
  const m = /keys=(\[.*\])\}\]/.exec(out)
  if (!m) throw new Error(`CleanMyTV Home didn't list the buttons: ${out.trim().slice(0, 200) || 'no output'}`)
  return (JSON.parse(m[1]) as unknown[]).flatMap((k) => {
    const o = k as Record<string, unknown>
    return Number.isInteger(o.seq) && Number.isInteger(o.code) ? [{ seq: o.seq as number, code: o.code as number, name: typeof o.name === 'string' ? o.name.slice(0, 40) : `KEYCODE_${o.code}` }] : []
  })
}

/** Switches the remote-button service on or off: the TV's accessibility setting plus the launcher's flag. */
export async function setRemoteButtons(on: boolean): Promise<HomeConfig> {
  if (on) await addService(KEY_SERVICE)
  else await dropServices(HOME_PKG)
  return setConfig({ buttons_on: on === true })
}

export async function readConfig(): Promise<HomeConfig> {
  return keep(parse(await T.tv().shell(`content call --uri ${URI} --method get`)))
}

export async function setConfig(change: Partial<HomeConfig>): Promise<HomeConfig> {
  const json = JSON.stringify(clean(change as Record<string, unknown>))
  log('theme', 'cmd', `set ${json}`)
  const cfg = keep(parse(await call('set64', Buffer.from(json).toString('base64'))))
  if (change.sections) await nowPlayingAccess(cfg.sections.includes('nowplaying'))
  return cfg
}

// ---- the copy on this computer -------------------------------------------------------------------
// Updates keep the launcher's settings, but an uninstall, a factory reset or a move from another
// launcher starts it empty. The app keeps the last settings it saw for each TV, and the last wallpaper
// it sent, and puts them back when a fresh install reports `configured: false`.

function backupBase(): string | null {
  const p = T.getStatus().probe
  if (!p?.device) return null
  return join(paths.homeBackups, `${p.device}-${p.host}`.replace(/[^A-Za-z0-9._-]/g, '_'))
}

/** Saves the settings the TV reported, unless it is a fresh install that would overwrite the good copy. */
function keep(cfg: HomeConfig): HomeConfig {
  const base = backupBase()
  if (base && cfg.configured !== false) {
    try {
      mkdirSync(paths.homeBackups, { recursive: true, mode: 0o700 })
      const { has_wallpaper: _w, quick_available: _q, configured: _c, ...settings } = cfg
      writeFileSync(base + '.json', JSON.stringify(settings, null, 2), { mode: 0o600 })
    } catch (e) {
      log('theme', 'info', `could not save the settings copy: ${(e as Error).message}`)
    }
  }
  return cfg
}

function keepWallpaper(jpeg: Buffer | null): void {
  const base = backupBase()
  if (!base) return
  try {
    mkdirSync(paths.homeBackups, { recursive: true, mode: 0o700 })
    if (jpeg) writeFileSync(base + '.jpg', jpeg, { mode: 0o600 })
    else rmSync(base + '.jpg', { force: true })
  } catch (e) {
    log('theme', 'info', `could not save the wallpaper copy: ${(e as Error).message}`)
  }
}

/** Puts the saved settings and wallpaper back on a launcher that has none. True if there was a copy. */
async function restoreBackup(): Promise<boolean> {
  const base = backupBase()
  if (!base || !existsSync(base + '.json')) return false
  try {
    const saved = JSON.parse(readFileSync(base + '.json', 'utf8')) as Record<string, unknown>
    log(TASK, 'info', "CleanMyTV Home started empty; putting this computer's copy of its settings back")
    await setConfig(clean(saved, true))
    if (existsSync(base + '.jpg')) await setWallpaper(readFileSync(base + '.jpg').toString('base64'))
    return true
  } catch (e) {
    log(TASK, 'error', `could not put the settings back: ${(e as Error).message}`)
    return false
  }
}

/** Notification-listener access for the Now playing section: given with the section, taken away without it. */
async function nowPlayingAccess(on: boolean): Promise<void> {
  await T.sh('theme', `cmd notification ${on ? 'allow' : 'disallow'}_listener ${NOW_LISTENER}`).catch(() => '')
}

/** Uploads a JPEG in base64 pieces, then has the launcher check its MD5 and swap it in. */
export async function setWallpaper(jpegBase64: string | null): Promise<HomeConfig> {
  if (jpegBase64 === null) {
    keepWallpaper(null)
    return parse(await call('wallpaper_remove'))
  }
  if (typeof jpegBase64 !== 'string' || !/^[A-Za-z0-9+/]+=*$/.test(jpegBase64)) throw new Error('the wallpaper must be a JPEG in base64')
  const data = Buffer.from(jpegBase64, 'base64')
  if (data.length > 4 * 1024 * 1024) throw new Error('the wallpaper is over 4 MB')
  if (data[0] !== 0xff || data[1] !== 0xd8) throw new Error('the wallpaper must be a JPEG')
  // Each piece plus the command must fit one ADB payload; a multiple of 3 keeps '=' padding at the end.
  let chunk = Math.min(48 * 1024, Math.floor(((T.tv().maxData - 200) * 3) / 4))
  chunk -= chunk % 3
  if (chunk < 3) throw new Error('this TV accepts commands too short to upload a wallpaper')
  log('theme', 'cmd', `upload wallpaper (${Math.round(data.length / 1024)} KB)`)
  await call('wallpaper_begin')
  for (let i = 0; i < data.length; i += chunk) await call('wallpaper_chunk', data.subarray(i, i + chunk).toString('base64'))
  const cfg = parse(await call('wallpaper_end', createHash('md5').update(data).digest('hex')))
  keepWallpaper(data)
  log('theme', 'ok', 'wallpaper set')
  return cfg
}
