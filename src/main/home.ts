// CleanMyTV Home, the preview launcher in home/. Its settings and wallpaper live in its own private
// storage and come in over ADB through its ConfigProvider (`content call`), so there's no shared folder
// to fail (#20) and nothing to reread after a reboot (#21). Changes show on the TV straight away.
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { HOME_PKG, detectStock, handOver } from './launcher'
import type { HomeConfig, HomeGroup, HomeStatus, InstallResult, TvApp } from '@shared/api'

const TASK = 'install'
const MAIN = `${HOME_PKG}/.MainActivity`
const URI = `content://${HOME_PKG}.config`
const HEX = /^#[0-9A-Fa-f]{6}$/

export const DEFAULTS: HomeConfig = {
  layout: 'shelf', sections: ['dock', 'rows', 'folders', 'grid'], top_space: 25, quick: ['inputs', 'wifi', 'settings'],
  rows: [], folders: [], dock_scale: 100, tile_scale: 100,
  accent: '#4CAF50', focus: 'glow', background: '#1C2A1F', wallpaper_dim: 35,
  order: [], hidden: [], dock_size: 5, columns: 6, show_names: true, clock: 'time_date', greeting: '',
  visualiser: true, viz_style: 'bars', viz_bars: 32, viz_height: 12, viz_opacity: 85, viz_colour: 'accent', viz_peaks: true, viz_sensitivity: 100
}

const CHOICES = {
  layout: ['shelf', 'split', 'rail'],
  focus: ['glow', 'outline', 'none'],
  clock: ['time_date', 'time', 'off'],
  viz_style: ['bars', 'mirror', 'wave'],
  viz_colour: ['accent', 'rainbow', 'white']
} as const

const LISTS = {
  sections: ['dock', 'rows', 'folders', 'grid'],
  quick: ['settings', 'wifi', 'inputs', 'bluetooth', 'display', 'apps', 'sleep', 'search']
} as const

const GROUPS = { rows: 8, folders: 12 } as const

// eslint-disable-next-line no-control-regex
const plain = (s: string, max: number): string => s.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max)

const RANGES = {
  top_space: [0, 40], dock_scale: [60, 150], tile_scale: [60, 150],
  wallpaper_dim: [0, 80], dock_size: [0, 8], columns: [3, 8],
  viz_bars: [8, 64], viz_height: [4, 40], viz_opacity: [20, 100], viz_sensitivity: [25, 300]
} as const

const BOOLS = ['show_names', 'visualiser', 'viz_peaks'] as const

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
  return {
    installed: !!versionName,
    versionName,
    isHome: !!home && home.startsWith(HOME_PKG + '/'),
    bundled: existsSync(paths.bundledHomeApk),
    bundledVersion: bundledVersion()
  }
}

/** Installs or updates CleanMyTV Home, starts it, and only then makes it Home. */
export async function install(): Promise<InstallResult> {
  if (!existsSync(paths.bundledHomeApk)) {
    return { ok: false, summary: 'This copy of the app has no CleanMyTV Home bundled.', warnings: [] }
  }
  const stock = await detectStock()
  log(TASK, 'info', `stock launcher: ${stock}`)
  const tmp = '/data/local/tmp/cmtv-home.apk'
  await T.pushB64(TASK, readFileSync(paths.bundledHomeApk), tmp)
  const out = await T.sh(TASK, `pm install -r ${tmp}`, { timeoutMs: 300000 })
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
  return failed ?? { ok: true, summary: 'CleanMyTV Home is Home now. Press Home on the remote to see it.', warnings: [] }
}

/** What the launcher answered, with anything missing or odd replaced by the default. */
export function parse(out: string): HomeConfig {
  const m = /config=(\{.*\})\}\]/.exec(out)
  if (!m) throw new Error(`CleanMyTV Home didn't answer: ${out.trim().slice(0, 300) || 'no output'}`)
  const o = JSON.parse(m[1]) as Record<string, unknown>
  const cfg: HomeConfig = { ...DEFAULTS, ...clean(o, true) }
  cfg.has_wallpaper = o.has_wallpaper === true
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
  if (o.greeting !== undefined) {
    if (typeof o.greeting === 'string') out.greeting = plain(o.greeting, 40)
    else bad('greeting must be text')
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

export async function readConfig(): Promise<HomeConfig> {
  return parse(await T.tv().shell(`content call --uri ${URI} --method get`))
}

export async function setConfig(change: Partial<HomeConfig>): Promise<HomeConfig> {
  const json = JSON.stringify(clean(change as Record<string, unknown>))
  log('theme', 'cmd', `set ${json}`)
  return parse(await call('set64', Buffer.from(json).toString('base64')))
}

/** Uploads a JPEG in base64 pieces, then has the launcher check its MD5 and swap it in. */
export async function setWallpaper(jpegBase64: string | null): Promise<HomeConfig> {
  if (jpegBase64 === null) return parse(await call('wallpaper_remove'))
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
  log('theme', 'ok', 'wallpaper set')
  return cfg
}
