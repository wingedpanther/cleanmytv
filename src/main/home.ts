// CleanMyTV Home, the launcher in home/. Its settings and wallpaper live in its own private
// storage and come in over ADB through its ConfigProvider (`content call`), so there's no shared folder
// to fail (#20) and nothing to reread after a reboot (#21). Changes show on the TV straight away.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { FIRE_LAUNCHER, HOME_PKG, OLD_PKG, StockUnknown, addService, detectStock, dropServices, handOver, rememberStock, toSwitchOff, wraithBack } from './launcher'
import { GOOGLE_LAUNCHERS } from './debloat'
import { keepLauncher, keptLauncher, mustKeep } from './kept'
import { themeName } from '@shared/themes'
import { noteKeyboard } from './keyboard'
import { englishOf, t, UiError } from './i18n'
import type { ApplyResult, HomeButton, HomeConfig, HomeGroup, HomeStatus, InstallResult, KeyPress, LookSlot, QuickId, ThemePackItem, TvApp } from '@shared/api'

const TASK = 'install'
const MAIN = `${HOME_PKG}/.MainActivity`
const URI = `content://${HOME_PKG}.config`
const HEX = /^#[0-9A-Fa-f]{6}$/

export const DEFAULTS: HomeConfig = {
  layout: 'shelf', sections: ['nowplaying', 'dock', 'watchnext', 'rows', 'folders', 'grid'], top_space: 25, quick: ['inputs', 'wifi', 'settings'],
  rows: [], folders: [], new_apps_to: '', dock_scale: 100, tile_scale: 100, buttons_on: false, buttons: [], stock_redirect: '', power_on_input: false, power_on_target: '', power_on_wait: 0,
  accent: '#4CAF50', focus: 'lift', background: '#1C2A1F', wallpaper_dim: 35, snow: false,
  edit_on_tv: false, idle_style: 'corner', font: 'default', bing_daily: false, bing_caption: true, theme_memory: true, lock_editor: false, update_check: true, keyboard_guard: true, limits: {}, shade_top: 60, shade_top_reach: 40, shade_bottom: 80, shade_bottom_reach: 65, wash: 50, wash_reach: 100,
  order: [], hidden: [], dock_size: 5, columns: 6, show_names: true, name_focus: false, follow_colour: false, pin_focus: false, tilt: false,
  icon_style: 'banner', tile_shape: 'wide', tile_glass: 10, intro: true, wallpaper_from: '',
  lock_on: false, lock_apps: [], lock_system: false, lock_for: 'sleep', lock_show: 'badge',
  fire_wall: false, fire_sound: true, fire_wall_sound: false, fire_volume: 40, fire_timer: 0,
  slide_minutes: 0, slide_from: 'mine', other_apps: false, live_tv: true,
  clock: 'time_date', greeting: '', names: {}, idle_minutes: 0,
  weather_place: '', weather_lat: 0, weather_lon: 0, weather_unit: 'c', weather_hours: false, memory: false,
  day_night: false, day_at: 7 * 60, night_at: 19 * 60,
  bedtime_on: false, bedtime_at: 23 * 60 + 30, bedtime_days: [1, 2, 3, 4, 5, 6, 7], input_tiles: false,
  visualiser: true, viz_style: 'bars', viz_bars: 32, viz_height: 12, viz_opacity: 85, viz_colour: 'accent', viz_peaks: true, viz_sensitivity: 100
}

const CHOICES = {
  layout: ['shelf', 'split', 'rail'],
  focus: ['lift', 'glow', 'outline', 'none'],
  clock: ['time_date', 'time', 'off'],
  viz_style: ['bars', 'mirror', 'wave'],
  viz_colour: ['accent', 'rainbow', 'white'],
  weather_unit: ['c', 'f'],
  icon_style: ['banner', 'colour', 'mono', 'duo'],
  tile_shape: ['wide', 'square', 'squircle', 'round'],
  lock_for: ['sleep', '15', 'always'],
  lock_show: ['badge', 'hide'],
  slide_from: ['mine', 'theme'],
  idle_style: ['corner', 'center', 'photos'],
  font: ['default', 'condensed', 'serif', 'casual']
} as const

const LISTS = {
  sections: ['nowplaying', 'recent', 'dock', 'watchnext', 'rows', 'folders', 'grid']
} as const

const QUICK = ['settings', 'wifi', 'inputs', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music', 'panel', 'themes', 'fireplace', 'memory', 'customize', 'timer'] as const
/** App shortcuts the top bar takes, as the launcher's HomeConfig.MAX_QUICK_APPS. */
export const MAX_QUICK_APPS = 6
const quickApp = (q: unknown): boolean => typeof q === 'string' && q.startsWith('app:') && T.PACKAGE_RE.test(q.slice(4))

const GROUPS = { rows: 8, folders: 12 } as const

// eslint-disable-next-line no-control-regex
const plain = (s: string, max: number): string => s.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max)

const RANGES = {
  top_space: [0, 80], dock_scale: [60, 150], tile_scale: [60, 150],
  wallpaper_dim: [0, 80], dock_size: [0, 8], columns: [3, 8], idle_minutes: [0, 60],
  viz_bars: [8, 64], viz_height: [4, 40], viz_opacity: [20, 100], viz_sensitivity: [25, 300],
  fire_volume: [0, 100], fire_timer: [0, 240], slide_minutes: [0, 240], tile_glass: [0, 60],
  day_at: [0, 24 * 60 - 1], night_at: [0, 24 * 60 - 1], bedtime_at: [0, 24 * 60 - 1],
  shade_top: [0, 100], shade_top_reach: [0, 100], shade_bottom: [0, 100], shade_bottom_reach: [0, 100], wash: [0, 100], wash_reach: [10, 100]
} as const

const BOOLS = ['show_names', 'name_focus', 'follow_colour', 'pin_focus', 'tilt', 'intro', 'lock_on', 'lock_system', 'fire_wall', 'fire_sound', 'fire_wall_sound', 'other_apps', 'live_tv', 'visualiser', 'viz_peaks', 'buttons_on', 'memory', 'day_night', 'power_on_input', 'edit_on_tv', 'bing_daily', 'bing_caption', 'theme_memory', 'lock_editor', 'update_check', 'keyboard_guard', 'snow', 'weather_hours', 'bedtime_on', 'input_tiles'] as const
const KEY_SERVICE = `${HOME_PKG}/${HOME_PKG}.KeyService`
/** Lets the launcher see other apps' media sessions; on only while the Now playing section is. */
const NOW_LISTENER = `${HOME_PKG}/${HOME_PKG}.NowPlayingListener`
/** Android 8: CleanMyTV Home's minSdk (home/app/build.gradle.kts). */
export const MIN_SDK = 26
// Above Android's own codes: TCL remotes send 4001 and up (#47).
const MAX_KEY = 65535
const BUTTON_ACTIONS = ['settings', 'wifi', 'inputs', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music', 'panel', 'themes', 'fireplace', 'memory', 'timer', 'next_wallpaper', 'brightness', 'home', 'launcher', 'back', 'none', 'app']
/** A built-in theme's picture, as "<theme id>:<picture>". */
const THEME_PICTURE = /^[a-z0-9-]{1,40}:[0-9]$/
/** A TvInputManager input id, like com.mediatek.tvinput/.hdmi.HDMIInputService/HW5 (Config.kt INPUT_ID). */
const INPUT_ID = /^[A-Za-z0-9_.$/:-]{1,200}$/
export const POWER_ON_WAITS = [0, 3, 5, 10] as const

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
  const { installed, disabled } = await T.packageSets()
  const fireTv = installed.has(FIRE_LAUNCHER)
  const google = GOOGLE_LAUNCHERS.find((p) => installed.has(p))
  const kept = keptLauncher()
  const keptOn = !!kept && kept === google && !disabled.has(kept)
  // Fire TV, and a TV whose Google launcher is kept on, open that launcher first; CleanMyTV Home comes back in front of it.
  // That only works while the remote-button service runs; giving Home back switches it off.
  const redirected = !!versionName && (fireTv || keptOn) && !!home && (home.startsWith(HOME_PKG + '/') || home.startsWith((fireTv ? FIRE_LAUNCHER : kept) + '/')) &&
    (await T.tv().shell('settings get secure enabled_accessibility_services')).includes(HOME_PKG + '/')
  return {
    oldLauncher: installed.has(OLD_PKG),
    installed: !!versionName,
    versionName,
    isHome: (!!home && home.startsWith(HOME_PKG + '/')) || redirected,
    fireTv,
    googleLauncher: fireTv ? undefined : google,
    googleKept: keptOn,
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
  return t('CleanMyTV Home needs Android 8 or newer, and this TV runs Android {release} (SDK {sdk}). Clean up still works on it.', { release: probe.release || '?', sdk: probe.sdk })
}

// "Requested internal only, but not enough space" on older Android, INSTALL_FAILED_INSUFFICIENT_STORAGE on newer (#48).
const SPACE_RE = /not enough space|INSUFFICIENT_STORAGE/i

function noSpace(free: number, need: number): string {
  const mb = (kb: number): string => `${Math.max(0, kb / 1024).toFixed(kb < 10240 ? 1 : 0)} MB`
  return Number.isFinite(free)
    ? t("The TV is out of storage: it has {free} free and CleanMyTV Home needs about {need}. The app already cleared every app's cache. Uninstall an app you don't use, then try again.", { free: mb(free), need: mb(need) })
    : t("The TV is out of storage: it CleanMyTV Home needs about {need}. The app already cleared every app's cache. Uninstall an app you don't use, then try again.", { need: mb(need) })
}

/** Installs or updates CleanMyTV Home, starts it, and only then makes it Home. */
export async function install(chosen?: string): Promise<InstallResult> {
  if (!existsSync(paths.bundledHomeApk)) {
    return { ok: false, summary: t('This copy of the app has no CleanMyTV Home bundled.'), warnings: [] }
  }
  const tooOld = unsupported()
  if (tooOld) return { ok: false, summary: tooOld, warnings: [] }
  const { installed: onTv, disabled: offOnTv } = await T.packageSets()
  // Fire OS never hands Home over, and a kept Google launcher stays on: both get the redirect instead.
  const fire = onTv.has(FIRE_LAUNCHER)
  const kept = keptLauncher()
  const redirect = fire ? FIRE_LAUNCHER : kept && onTv.has(kept) && !offOnTv.has(kept) ? kept : null
  let stock: string
  try {
    stock = redirect ?? (await detectStock(chosen))
  } catch (e) {
    if (e instanceof StockUnknown) return { ok: false, summary: e.message, warnings: [], stockChoices: e.candidates }
    throw e
  }
  log(TASK, 'info', redirect ? `${redirect} stays on; CleanMyTV Home comes back in front of it` : `stock launcher: ${stock}`)
  const apk = readFileSync(paths.bundledHomeApk)
  // The upload's base64 copy, the APK, Android's own copy of it and the compiled code all sit on /data.
  const need = Math.ceil((apk.length * 5) / 1024)
  let trimmed = false
  if ((await T.freeKb(TASK)) < need) {
    await T.trimCaches(TASK)
    trimmed = true
    const free = await T.freeKb(TASK)
    if (free < need) return { ok: false, summary: noSpace(free, need), warnings: [] }
  }
  const tmp = '/data/local/tmp/cmtv-home.apk'
  await T.pushB64(TASK, apk, tmp)
  // -d: an older copy of this app may put its older launcher back over a newer one. An uninstall would
  // be the only other way, and it wipes the settings.
  const installCmd = `pm install -r -d ${tmp}`
  let out = await T.sh(TASK, installCmd, { timeoutMs: 300000 })
  if (SPACE_RE.test(out) && !trimmed) {
    await T.trimCaches(TASK)
    trimmed = true
    out = await T.sh(TASK, installCmd, { timeoutMs: 300000 })
  }
  await T.sh(TASK, `rm -f ${tmp}`)
  if (SPACE_RE.test(out)) return { ok: false, summary: noSpace(await T.freeKb(TASK), need), warnings: [out.trim()] }
  if (!out.includes('Success')) {
    return { ok: false, summary: t('Install failed; Home was left as it was.'), warnings: [out.trim()] }
  }
  // The music bars read the TV's sound output. Granted here so the TV never asks.
  await T.sh(TASK, `pm grant ${HOME_PKG} android.permission.RECORD_AUDIO 2>/dev/null; echo audio-granted`)
  // Continue watching reads the TV's Watch Next list, which shows Home only its own entries without this.
  await watchNextAccess(TASK)
  // The Quick settings panel changes screensaver, sleep, DNS, animations and clock settings. Some TVs refuse
  // one or both; the panel then opens the TV's own screens for those rows.
  await T.sh(TASK, `pm grant ${HOME_PKG} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null; appops set ${HOME_PKG} WRITE_SETTINGS allow 2>/dev/null; echo settings-granted`)
  // Going back to the input at power on (#94) opens the TV's input app from the background, which Android 10
  // and later only allow an app that may draw over other apps. TCL's boot manager also stops apps starting at
  // boot without its auto-start op (AUTO_START on older boards, APP_AUTO_START on newer); other TVs don't have
  // that op, as upstream's wake-app found. A TV that refuses any of it still gets Home.
  await T.sh(TASK, `appops set ${HOME_PKG} SYSTEM_ALERT_WINDOW allow 2>/dev/null; appops set ${HOME_PKG} AUTO_START allow 2>/dev/null; appops set ${HOME_PKG} APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; echo appops-granted`).catch(() => '')
  const start = await T.sh(TASK, `am start -W -n ${MAIN}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: t('CleanMyTV Home did not start, so Home was left as it was.'), warnings: [start.trim()] }
  }
  let notes: string[] = []
  if (redirect) {
    // Fire OS answers Success and ignores it; with Google's launcher on it holds until that launcher claims Home.
    await T.sh(TASK, `cmd package set-home-activity --user 0 ${MAIN}`)
  } else {
    const handed = await handOver(toSwitchOff(stock), MAIN)
    if (handed.failed) return handed.failed
    notes = handed.notes
    rememberStock(stock)
  }
  let cfg = await readConfig().catch(() => null)
  const restored = cfg?.configured === false ? await restoreBackup() : false
  if (restored) cfg = await readConfig().catch(() => cfg)
  if (cfg && cfg.stock_redirect !== (redirect ?? '')) cfg = await setConfig({ stock_redirect: redirect ?? '' }).catch(() => cfg)
  // Android switches accessibility services off when their app is reinstalled: turn the service back on.
  if (cfg && needsService(cfg)) await addService(KEY_SERVICE)
  if (cfg) await nowPlayingAccess(cfg.sections.includes('nowplaying'))
  if (cfg) await usageAccess(cfg.sections.includes('recent'))
  await noteKeyboard((await T.sh(TASK, 'settings get secure default_input_method', { quiet: true }).catch(() => '')).trim())
  const again = restored ? ` ${t("It started empty, so your settings and wallpaper came back from this computer's copy.")}` : ''
  return {
    ok: true,
    summary: (fire
      ? t("CleanMyTV Home is installed. Fire TV always opens Amazon's home screen first, so CleanMyTV Home comes back in front a moment after you press Home, up to 5 seconds on some models.")
      : redirect && redirect === mustKeep()
        ? t("CleanMyTV Home is Home now. This TV restarts when Google TV's launcher is switched off, so it stays on, and after a Home press CleanMyTV Home comes back in front a moment later.")
        : redirect
        ? t("CleanMyTV Home is Home now. Google TV's launcher stays on, as you chose, so after a Home press CleanMyTV Home comes back in front a moment later.")
        : t('CleanMyTV Home is Home now. Press Home on the remote to see it.')) + again,
    warnings: notes
  }
}

/** The remote-button service has work: buttons, the app lock, the Home redirect or the power-on input. */
export const needsService = (c: Pick<HomeConfig, 'buttons_on' | 'lock_on' | 'stock_redirect' | 'power_on_input' | 'bedtime_on'>): boolean =>
  c.buttons_on || c.lock_on || !!c.stock_redirect || c.power_on_input || !!c.bedtime_on

/**
 * Keeps Google TV's own launcher on beside CleanMyTV Home (#99, #88). Some apps need it: Apple TV and Netflix
 * on some TVs stop playing, and Google Photos albums for the screensaver won't load, while it is off. It
 * stays on, and the remote-button service sends its home screen back to CleanMyTV Home. Off puts the usual
 * handover back.
 */
export async function keepGoogleLauncher(on: boolean): Promise<ApplyResult> {
  const { installed } = await T.packageSets()
  const google = GOOGLE_LAUNCHERS.find((p) => installed.has(p))
  if (!google) return { ok: false, summary: t('This TV has no Google TV launcher to keep.'), warnings: [] }
  if (on) {
    await T.sh(TASK, `pm enable --user 0 ${google}`)
    await wraithBack()
    keepLauncher(google)
    await T.sh(TASK, `cmd package set-home-activity --user 0 ${MAIN}`)
    await setConfig({ stock_redirect: google })
    await addService(KEY_SERVICE)
    return { ok: true, summary: t('{pkg} is on again, and Clean up leaves it on. After a Home press, CleanMyTV Home comes back in front a moment later.', { pkg: google }), warnings: [] }
  }
  if (google === mustKeep()) return { ok: false, summary: t("This TV restarts when Google TV's launcher is switched off, so it stays on."), warnings: [] }
  const { failed, notes } = await handOver(google, MAIN)
  if (failed) return failed
  keepLauncher(null)
  const cfg = await setConfig({ stock_redirect: '' })
  if (!needsService(cfg)) await dropServices(HOME_PKG)
  return { ok: true, summary: t('{pkg} is off again, and CleanMyTV Home is Home.', { pkg: google }), warnings: notes }
}

/**
 * Home went to the stock or another launcher: CleanMyTV Home stops sending a launcher's home screen back to
 * itself, and Clean up stops keeping Google's launcher on for it.
 */
export async function leftHome(result: ApplyResult): Promise<ApplyResult> {
  if (!result.ok) return result
  keepLauncher(null)
  await setConfig({ stock_redirect: '' }).catch(() => undefined)
  return result
}



/** What the launcher answered, with anything missing or odd replaced by the default. */
export function parse(out: string): HomeConfig {
  const m = /config=(\{.*\})\}\]/.exec(out)
  if (!m) throw new UiError("CleanMyTV Home didn't answer: {output}", { output: out.trim().slice(0, 300) || 'no output' })
  const o = JSON.parse(m[1]) as Record<string, unknown>
  const cfg: HomeConfig = { ...DEFAULTS, ...clean(o, true) }
  cfg.has_wallpaper = o.has_wallpaper === true
  if (typeof o.configured === 'boolean') cfg.configured = o.configured
  if (typeof o.lock_pin_set === 'boolean') cfg.lock_pin_set = o.lock_pin_set
  if (typeof o.themes_on_tv === 'number') cfg.themes_on_tv = o.themes_on_tv
  if (typeof o.can_change_settings === 'boolean') cfg.can_change_settings = o.can_change_settings
  if (Array.isArray(o.looks_saved)) cfg.looks_saved = o.looks_saved.filter((l): l is LookSlot => l === 'day' || l === 'night')
  // Older launchers don't report this; leave it unset so the editor doesn't mark everything unavailable.
  if (Array.isArray(o.tv_inputs)) {
    cfg.tv_inputs = o.tv_inputs.flatMap((i) => {
      const x = i as Record<string, unknown>
      return typeof x.id === 'string' && INPUT_ID.test(x.id) ? [{ id: x.id, name: typeof x.name === 'string' && x.name.trim() ? x.name.slice(0, 60) : x.id }] : []
    })
  }
  if (Array.isArray(o.quick_available)) cfg.quick_available = o.quick_available.filter((q): q is QuickId => (QUICK as readonly unknown[]).includes(q))
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
  if (o.stock_redirect !== undefined) {
    if (typeof o.stock_redirect === 'string' && (o.stock_redirect === '' || T.PACKAGE_RE.test(o.stock_redirect))) out.stock_redirect = o.stock_redirect
    else bad('stock_redirect must be a package name or empty')
  }
  if (o.power_on_target !== undefined) {
    if (typeof o.power_on_target === 'string' && (o.power_on_target === '' || INPUT_ID.test(o.power_on_target))) out.power_on_target = o.power_on_target
    else bad('power_on_target must be an input id or empty')
  }
  if (o.power_on_wait !== undefined) {
    if ((POWER_ON_WAITS as readonly unknown[]).includes(o.power_on_wait)) out.power_on_wait = o.power_on_wait
    else bad(`power_on_wait must be one of ${POWER_ON_WAITS.join(', ')}`)
  }
  if (o.wallpaper_from !== undefined) {
    if (typeof o.wallpaper_from === 'string' && (o.wallpaper_from === '' || THEME_PICTURE.test(o.wallpaper_from))) out.wallpaper_from = o.wallpaper_from
    else bad('wallpaper_from must be "<theme id>:<picture>" or empty')
  }
  for (const k of ['order', 'hidden', 'lock_apps', 'switched_off'] as const) {
    if (o[k] === undefined) continue
    if (Array.isArray(o[k]) && (o[k] as unknown[]).length <= 500 && (o[k] as unknown[]).every((p) => typeof p === 'string' && T.PACKAGE_RE.test(p))) out[k] = [...new Set(o[k] as string[])]
    else bad(`${k} must be a list of package names`)
  }
  for (const [k, allowed] of Object.entries(LISTS)) {
    if (o[k] === undefined) continue
    if (Array.isArray(o[k]) && (o[k] as unknown[]).every((v) => (allowed as readonly unknown[]).includes(v))) out[k] = [...new Set(o[k] as string[])]
    else bad(`${k} must be a list of ${allowed.join(', ')}`)
  }
  if (o.quick !== undefined) {
    const v = o.quick
    const ok = Array.isArray(v) && v.every((q) => (QUICK as readonly unknown[]).includes(q) || quickApp(q)) && v.filter(quickApp).length <= MAX_QUICK_APPS
    if (ok) out.quick = [...new Set(v as string[])]
    else bad(`quick must be a list of ${QUICK.join(', ')} or app:<package>, with up to ${MAX_QUICK_APPS} apps`)
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
  if (o.new_apps_to !== undefined) {
    if (typeof o.new_apps_to === 'string') out.new_apps_to = plain(o.new_apps_to, 30)
    else bad('new_apps_to must be "", "dock" or a row name')
  }
  if (o.bedtime_days !== undefined) {
    const v = o.bedtime_days
    if (Array.isArray(v) && v.length <= 7 && v.every((d) => Number.isInteger(d) && d >= 1 && d <= 7)) out.bedtime_days = [...new Set(v as number[])].sort()
    else bad('bedtime_days must be days 1 (Monday) to 7 (Sunday)')
  }
  if (o.buttons !== undefined) {
    const v = o.buttons
    const ok = Array.isArray(v) && v.length <= 30 && v.every((b) =>
      b && typeof b === 'object' && Number.isInteger(b.code) && b.code >= 1 && b.code <= MAX_KEY && BUTTON_ACTIONS.includes(b.action) &&
      (b.action !== 'app' || (typeof b.app === 'string' && T.PACKAGE_RE.test(b.app))) &&
      (b.hold === undefined || (BUTTON_ACTIONS.includes(b.hold) && (b.hold !== 'app' || (typeof b.hold_app === 'string' && T.PACKAGE_RE.test(b.hold_app))))))
    if (ok) {
      const seen = new Set<number>()
      out.buttons = (v as HomeButton[]).filter((b) => !seen.has(b.code) && seen.add(b.code)).map((b) => ({
        code: b.code, action: b.action, ...(b.action === 'app' ? { app: b.app } : {}),
        ...(b.hold && b.hold !== 'none' ? { hold: b.hold, ...(b.hold === 'app' ? { hold_app: b.hold_app } : {}) } : {})
      }))
    } else bad('buttons must be up to 30 key codes (1 to 65535), each with a known action')
  }
  if (o.greeting !== undefined) {
    if (typeof o.greeting === 'string') out.greeting = plain(o.greeting, 40)
    else bad('greeting must be text')
  }
  if (o.names !== undefined) {
    const v = o.names
    const ok = v && typeof v === 'object' && !Array.isArray(v) &&
      Object.entries(v).every(([k, s]) => ((QUICK as readonly string[]).includes(k) || k === 'all_apps') && typeof s === 'string')
    if (ok) out.names = Object.fromEntries(Object.entries(v as Record<string, string>).map(([k, s]) => [k, plain(s, 24)]).filter(([, s]) => s !== ''))
    else bad(`names must map ${QUICK.join(', ')} or all_apps to text`)
  }
  if (o.limits !== undefined) {
    const v = o.limits
    const ok = v && typeof v === 'object' && !Array.isArray(v) && Object.keys(v).length <= 60 &&
      Object.entries(v).every(([k, m]) => T.PACKAGE_RE.test(k) && Number.isInteger(m) && (m as number) >= 5 && (m as number) <= 720)
    if (ok) out.limits = { ...(v as Record<string, number>) }
    else bad('limits must map package names to 5 to 720 minutes a day')
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

const PRIVATE_SETTINGS = ['greeting', 'weather_place', 'weather_lat', 'weather_lon']

/** The JSON goes over as base64, so a greeting with quotes or emoji never needs shell quoting. */
function call(method: string, arg?: string, task = 'theme'): Promise<string> {
  if (arg !== undefined && !/^[A-Za-z0-9+/=]*$/.test(arg)) throw new Error('internal: argument must be base64 or hex')
  const cmd = `content call --uri ${URI} --method ${method}${arg !== undefined ? ` --arg ${arg}` : ''}`
  // Every answer carries all the settings, which include the home location and the greeting, so answers
  // stay out of the log. So do set64's arguments, which are those settings in base64.
  if ((!arg || arg.length <= 200) && method !== 'set64' && method !== 'lock_pin') log(task, 'cmd', cmd)
  return T.tv().shell(cmd)
}

/** The apps CleanMyTV Home shows, with the names the TV gives them. */
export async function apps(): Promise<TvApp[]> {
  const out = await T.tv().shell(`content call --uri ${URI} --method apps`)
  // Storage asks for the names whether or not Home is on the TV. Without it there are none to give (#141).
  if (out.includes('Could not find provider')) return []
  const m = /apps=(\[.*\])\}\]/.exec(out)
  if (!m) throw new UiError("CleanMyTV Home didn't list its apps: {output}", { output: out.trim().slice(0, 200) || 'no output' })
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
  if (!m) throw new UiError("CleanMyTV Home didn't list the buttons: {output}", { output: out.trim().slice(0, 200) || 'no output' })
  return (JSON.parse(m[1]) as unknown[]).flatMap((k) => {
    const o = k as Record<string, unknown>
    return Number.isInteger(o.seq) && Number.isInteger(o.code) ? [{ seq: o.seq as number, code: o.code as number, name: typeof o.name === 'string' ? o.name.slice(0, 40) : `KEYCODE_${o.code}` }] : []
  })
}

/** Switches the remote-button service on or off: the TV's accessibility setting plus the launcher's flag. */
export async function setRemoteButtons(on: boolean): Promise<HomeConfig> {
  if (on) await addService(KEY_SERVICE)
  // The same service guards locked apps and more; it stays on while any of them is.
  else if (!needsService({ ...(await readConfig()), buttons_on: false })) await dropServices(HOME_PKG)
  return setConfig({ buttons_on: on === true })
}

// ---- app lock -----------------------------------------------------------------------------------
// The PIN goes to the launcher once, in base64 so it needs no quoting, and is never logged or read back.

export async function setLockPin(pin: string): Promise<HomeConfig> {
  if (typeof pin !== 'string' || !/^\d{4,6}$/.test(pin)) throw new UiError('The PIN must be 4 to 6 digits.')
  log('theme', 'cmd', 'set the app lock PIN')
  return keep(parse(await call('lock_pin', Buffer.from(JSON.stringify({ pin })).toString('base64'))))
}

/** The key service watches which app comes to the front; it's on while the lock or the remote buttons are. */
export async function setAppLock(on: boolean): Promise<HomeConfig> {
  const cfg = await readConfig()
  if (on && !cfg.lock_pin_set) throw new UiError('Set a PIN first.')
  if (on) await addService(KEY_SERVICE)
  else if (!needsService({ ...cfg, lock_on: false })) await dropServices(HOME_PKG)
  return setConfig({ lock_on: on === true })
}

export async function clearAppLock(): Promise<HomeConfig> {
  const cfg = keep(parse(await call('lock_clear')))
  if (!needsService(cfg)) await dropServices(HOME_PKG)
  log('theme', 'ok', 'app lock off and its PIN forgotten')
  return cfg
}

// ---- fireplace ----------------------------------------------------------------------------------
// A looping video and its crackle, downloaded once from the project's own release and kept on this
// computer. They go into Home's folder on the TV's shared storage, where ADB can write and Home can read.
// Neither file is in the repository or the installer.

const FIRE_RELEASE = 'https://github.com/wingedpanther/cleanmytv/releases/download/fireplace-1/'
const FIRE_FILES = [
  { name: 'fireplace.mp4', size: 9683738, sha256: '35a99bfd61111e05ef5941a8dfcc0cdde08f4359ee0d2ac1b0a2fe254d28727c' },
  { name: 'crackle.m4a', size: 1120535, sha256: 'e941736970cf0074c2bcf22d323c01ab9fcbb2bcfcad5b164b25724ba8a630a8' }
] as const
/** GitHub sends release downloads on to one of these. */
const FIRE_HOSTS = new Set(['github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com'])
const FIRE_DREAM = `${HOME_PKG}/.FireplaceDream`

async function fireFile(f: (typeof FIRE_FILES)[number], fetchImpl: typeof fetch = fetch): Promise<Buffer> {
  const dir = join(paths.data, 'media')
  const local = join(dir, f.name)
  const sum = (b: Buffer): string => createHash('sha256').update(b).digest('hex')
  if (existsSync(local)) {
    const b = readFileSync(local)
    if (sum(b) === f.sha256) return b
  }
  log('fireplace', 'cmd', `download ${f.name} (${Math.round(f.size / 1024)} KB)`)
  let u = new URL(FIRE_RELEASE + f.name)
  for (let hop = 0; hop < 5; hop++) {
    if (u.protocol !== 'https:' || !FIRE_HOSTS.has(u.hostname)) throw new UiError('the fireplace download went somewhere unexpected ({host})', { host: u.hostname })
    const res = await fetchImpl(u, { redirect: 'manual', signal: AbortSignal.timeout(120000) })
    const next = res.headers.get('location')
    if (res.status >= 300 && res.status < 400 && next) { u = new URL(next, u); continue }
    if (!res.ok) throw new UiError("couldn't download the fireplace ({file}: HTTP {status})", { file: f.name, status: res.status })
    const b = Buffer.from(await res.arrayBuffer())
    if (b.length !== f.size || sum(b) !== f.sha256) throw new UiError('the fireplace download is damaged ({file}); try again', { file: f.name })
    mkdirSync(dir, { recursive: true })
    writeFileSync(local, b)
    return b
  }
  throw new UiError('too many redirects')
}

/** Home 1.6.0 to 1.7.2: the files go into its folder on shared storage, which ADB can write on most TVs. */
async function fireToSharedFolder(files: { f: (typeof FIRE_FILES)[number]; data: Buffer }[]): Promise<void> {
  const out = await T.tv().shell(`content call --uri ${URI} --method fire_dir`)
  const dir = /dir=([^,}\s]+)/.exec(out)?.[1]
  if (!dir) throw new UiError('this TV has no shared storage for Home to read the fireplace from. Update Home on the Overview tab and try again.')
  T.q(dir)
  await T.sh('fireplace', `mkdir -p ${dir}`)
  for (const { f, data } of files) {
    await T.pushB64('fireplace', data, `${dir}/${f.name}.new`)
    await T.sh('fireplace', `mv -f ${dir}/${f.name}.new ${dir}/${f.name}`)
  }
}

export async function fireplace(action: 'put' | 'show' | 'saver' | 'remove'): Promise<{ ok: boolean; summary: string; warnings: string[] }> {
  if (action === 'remove') {
    await call('fire_clear')
    return { ok: true, summary: t('The fireplace is off the TV. Home is back on its picture.'), warnings: [] }
  }
  if (action === 'saver') {
    await T.sh('theme', `settings put secure screensaver_components ${FIRE_DREAM}; settings put secure screensaver_enabled 1`)
    return { ok: true, summary: t("The fireplace is the TV's screensaver now. It starts after the TV's screensaver time."), warnings: [] }
  }
  if (action === 'show') {
    // Through Home, which opens it; the fireplace screen itself isn't open to other apps.
    await T.sh('theme', `am start -n ${MAIN} --es open fireplace`)
    return { ok: true, summary: t('The fireplace is on the TV. Back on the remote closes it.'), warnings: [] }
  }
  const files = []
  for (const f of FIRE_FILES) files.push({ f, data: await fireFile(f) })
  const need = Math.ceil(files.reduce((n, x) => n + x.data.length, 0) * 2.4 / 1024)
  const free = await T.freeKb('fireplace')
  if (free < need) throw new UiError('the TV needs about {need} MB free for the fireplace and has {free} MB', { need: Math.round(need / 1024), free: Math.round(free / 1024) })
  for (const { f, data } of files) {
    // Through Home's settings channel into its own storage, as the wallpaper goes: Fire OS won't let ADB
    // write to Android/data (#70). Home before 1.7.3 doesn't know these calls; it gets the old folder.
    let shown = 0
    try {
      await sendFile('fire_file', Buffer.from(f.name).toString('base64'), data, 'fireplace', (done, of) => {
        const pct = Math.floor((done / of) * 10) * 10
        if (pct > shown) { shown = pct; log('fireplace', 'info', `${f.name}: ${pct}%`) }
      })
    } catch (e) {
      if (!/unknown method/.test(englishOf(e))) throw e
      await fireToSharedFolder(files)
      break
    }
  }
  await call('fire_ready')
  return { ok: true, summary: t('The fireplace is on the TV. Show it, make it the screensaver, or put it behind Home.'), warnings: [] }
}

// ---- themes on the TV ---------------------------------------------------------------------------
// The renderer draws each built-in theme's pictures; they go up like a wallpaper, into a staging folder
// the launcher only swaps in when the last one has arrived.

const THEME_ID = /^[a-z0-9-]{1,40}$/

export async function themePackBegin(): Promise<void> {
  log('theme', 'cmd', 'put the themes on the TV')
  await call('pack_begin')
}

export async function themePackPut(item: ThemePackItem): Promise<void> {
  if (!item || !THEME_ID.test(item.id) || !Number.isInteger(item.picture) || item.picture < 0 || item.picture > 3) throw new Error('bad theme picture')
  if (item.picture === 0) {
    const meta = { id: item.id, name: plain(String(item.name ?? ''), 40), look: clean((item.look ?? {}) as Record<string, unknown>, true) }
    await call('pack_meta', Buffer.from(JSON.stringify(meta)).toString('base64'))
  }
  const data = jpeg(item.jpeg)
  await sendFile('pack_file', Buffer.from(`${item.id}:${item.picture}`).toString('base64'), data, 'theme')
}

/**
 * Home's look and wallpaper now become a theme on the TV called [name] (launcher 1.11.0), next to the
 * built-in ones. A theme of that name already there is updated; the others stay as they are.
 */
export async function saveThemeOnTv(name: string): Promise<HomeConfig> {
  const n = themeName(name)
  log('theme', 'cmd', `save this look on the TV as the theme ${n}`)
  const out = await call('theme_save', Buffer.from(n).toString('base64'))
  if (!/\btheme=/.test(out)) {
    if (/unknown method/.test(out)) throw new UiError('This CleanMyTV Home is too old to save themes. Press Update and use on the Overview tab first.')
    const why = /IllegalArgumentException: ([^\n]+)/.exec(out)?.[1]
    throw why ? new UiError("The TV didn't save it: {why}.", { why }) : new UiError("The TV didn't save it: {why}", { why: out.trim().slice(0, 200) || 'no answer' })
  }
  return parse(out)
}

/**
 * Keeps Home's look and wallpaper as they are now as the day or night look (launcher 1.7.5). Home keeps
 * them itself and switches at the set times, so nothing needs uploading and any theme works.
 */
export async function saveLook(slot: LookSlot): Promise<HomeConfig> {
  if (slot !== 'day' && slot !== 'night') throw new Error('bad look')
  log('theme', 'cmd', `save this look for the ${slot}`)
  return parse(await call('look_save', slot))
}

/** Replaces the slideshow's pictures on the TV (#56). Each is a prepared JPEG in base64; an empty list clears them. */
export async function setSlides(jpegs: string[]): Promise<HomeConfig> {
  const list = Array.isArray(jpegs) ? jpegs : []
  if (list.length > MAX_SLIDES) throw new Error(`the slideshow takes up to ${MAX_SLIDES} pictures`)
  if (list.length === 0) return parse(await call('slides_clear'))
  const datas = list.map(jpeg)
  log('theme', 'cmd', `put ${datas.length} slideshow pictures on the TV`)
  await call('slides_begin')
  for (const [n, data] of datas.entries()) {
    await sendFile('slide_file', String(n), data, 'theme')
  }
  return parse(await call('slides_end'))
}

/** As the launcher's Slides.MAX. */
export const MAX_SLIDES = 12

export async function themePackEnd(order: string[]): Promise<HomeConfig> {
  const ids = (Array.isArray(order) ? order : []).filter((id) => typeof id === 'string' && THEME_ID.test(id))
  const cfg = parse(await call('pack_end', Buffer.from(JSON.stringify(ids)).toString('base64')))
  log('theme', 'ok', `${cfg.themes_on_tv ?? ids.length} themes are on the TV`)
  return cfg
}

function jpeg(b64: unknown): Buffer {
  if (typeof b64 !== 'string' || !/^[A-Za-z0-9+/]+=*$/.test(b64)) throw new Error('the picture must be a JPEG in base64')
  const data = Buffer.from(b64, 'base64')
  if (data.length > 4 * 1024 * 1024) throw new Error('the picture is over 4 MB')
  if (data[0] !== 0xff || data[1] !== 0xd8) throw new Error('the picture must be a JPEG')
  return data
}

/**
 * One file up to Home in pieces: begin, a chunk per piece, then end with its MD5. The TV may kill Home
 * between two calls to free memory (Sony, 2 GB boxes: #121). Home now picks the file up again from disk,
 * but older ones forget it, and a piece in flight dies with the process. So a file that fails anywhere
 * starts over from begin, once.
 */
export async function sendFile(kind: 'fire_file' | 'pack_file' | 'slide_file', name: string, data: Buffer, task: string,
  onPiece?: (done: number, of: number) => void): Promise<HomeConfig> {
  const parts = pieces(data)
  const md5 = createHash('md5').update(data).digest('hex')
  for (let attempt = 1; ; attempt++) {
    try {
      parse(await call(`${kind}_begin`, name, task))
      for (const [i, piece] of parts.entries()) {
        parse(await call(`${kind}_chunk`, piece, task))
        onPiece?.(i + 1, parts.length)
      }
      return parse(await call(`${kind}_end`, md5, task))
    } catch (e) {
      // A Home that doesn't know the calls won't learn them on a second go.
      if (attempt > 1 || /unknown method/.test(englishOf(e))) throw e
      log(task, 'warn', `${kind} failed, sending it again: ${englishOf(e).slice(0, 300)}`)
    }
  }
}

/** Base64 pieces that fit one ADB payload with the command; a multiple of 3 keeps '=' padding at the end. */
function pieces(data: Buffer): string[] {
  let chunk = Math.min(48 * 1024, Math.floor(((T.tv().maxData - 200) * 3) / 4))
  chunk -= chunk % 3
  if (chunk < 3) throw new UiError('this TV accepts commands too short to upload a picture')
  const out: string[] = []
  for (let i = 0; i < data.length; i += chunk) out.push(data.subarray(i, i + chunk).toString('base64'))
  return out
}

export async function readConfig(): Promise<HomeConfig> {
  return keep(parse(await T.tv().shell(`content call --uri ${URI} --method get`)))
}

// Settings that only make sense on the TV they came from, or that the app sets itself.
const TV_ONLY = ['stock_redirect', 'wallpaper_from', 'power_on_target', 'switched_off'] as const

/** The settings worth carrying to another TV or a reset one: valid ones only, no PIN and no wallpaper. */
export function portable(o: Record<string, unknown>): Partial<HomeConfig> {
  const out: Record<string, unknown> = { ...clean(o, true) }
  for (const k of TV_ONLY) delete out[k]
  return out as Partial<HomeConfig>
}

/**
 * Puts settings from a backup on the TV. The lock stays off on a TV without a PIN, as the PIN never leaves
 * a TV. True when that happened.
 */
export async function putSettings(saved: Record<string, unknown>): Promise<boolean> {
  const change = portable(saved)
  const noPin = change.lock_on === true && !(await readConfig()).lock_pin_set
  if (noPin) delete change.lock_on
  const cfg = await setConfig(change)
  if (needsService(cfg)) await addService(KEY_SERVICE)
  return noPin
}

export async function setConfig(change: Partial<HomeConfig>): Promise<HomeConfig> {
  const checked = clean(change as Record<string, unknown>)
  const json = JSON.stringify(checked)
  // People paste the activity log into public bug reports.
  log('theme', 'cmd', `set ${JSON.stringify(checked, (k, v) => (PRIVATE_SETTINGS.includes(k) ? '(hidden)' : v))}`)
  const cfg = keep(parse(await call('set64', Buffer.from(json).toString('base64'))))
  if (change.sections) await nowPlayingAccess(cfg.sections.includes('nowplaying'))
  if (change.sections) await usageAccess(cfg.sections.includes('recent'))
  // A Home that updated itself never had the install's grant. Never taken back: Android stops Home to do that.
  if (change.sections && cfg.sections.includes('watchnext')) await watchNextAccess('theme')
  // Going back to the input at power on (#94) is the remote-button service's job too.
  // So is bedtime: the service shows the note and puts the TV to sleep.
  if (checked.power_on_input === true || checked.bedtime_on === true) await addService(KEY_SERVICE)
  else if ((checked.power_on_input === false || checked.bedtime_on === false) && !needsService(cfg)) await dropServices(HOME_PKG)
  return cfg
}

// ---- the copy on this computer -------------------------------------------------------------------
// Updates keep the launcher's settings, but an uninstall, a factory reset or a move from another
// launcher starts it empty. The app keeps the last settings it saw for each TV, and the last wallpaper
// it sent, and puts them back when a fresh install reports `configured: false`.
// The launcher can't hand its wallpaper back, so the app also leaves a copy on the TV, where only ADB
// reads it: any computer can then save the look with its wallpaper, whichever one set it.
const TV_WALLPAPER = '/data/local/tmp/cleanmytv-wallpaper.jpg'

export function backupBase(): string | null {
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
      const { has_wallpaper: _w, quick_available: _q, configured: _c, tv_inputs: _i, ...settings } = cfg
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

async function keepOnTv(jpeg: Buffer | null): Promise<void> {
  try {
    if (jpeg) await T.pushB64('theme', jpeg, TV_WALLPAPER)
    else await T.tv().shell(`rm -f ${TV_WALLPAPER}`)
  } catch (e) {
    log('theme', 'info', `could not keep the wallpaper copy on the TV: ${(e as Error).message}`)
  }
}

const isJpeg = (b: Buffer | null): b is Buffer => !!b && b.length > 2 && b[0] === 0xff && b[1] === 0xd8

/**
 * The wallpaper on the connected TV, in base64: the copy on the TV first, as another computer may have
 * set a newer one, then this computer's. A copy only this computer has goes onto the TV too.
 */
/** The wallpaper the launcher has now, from launcher 1.5.0 on; null from older ones or with no wallpaper. */
async function wallpaperFromLauncher(): Promise<Buffer | null> {
  const parts: Buffer[] = []
  let size = -1
  let at = 0
  while (size < 0 || at < size) {
    const out = await T.tv().shell(`content call --uri ${URI} --method wallpaper_read --arg ${at}`)
    const m = /size=(\d+)/.exec(out)
    if (!m) return null
    size = Number(m[1])
    if (size === 0) return null
    const d = /data=([A-Za-z0-9+/=]+)/.exec(out)
    if (!d) return null
    const piece = Buffer.from(d[1], 'base64')
    if (!piece.length) return null
    parts.push(piece)
    at += piece.length
  }
  return Buffer.concat(parts)
}

export async function wallpaperCopy(): Promise<string | null> {
  // Launcher 1.5.0 hands its wallpaper back, including one picked on the TV's theme carousel.
  const live = await wallpaperFromLauncher().catch(() => null)
  if (isJpeg(live)) { keepWallpaper(live); return live.toString('base64') }
  const onTv = await T.pullB64(TV_WALLPAPER).catch(() => null)
  if (isJpeg(onTv)) {
    keepWallpaper(onTv)
    return onTv.toString('base64')
  }
  const base = backupBase()
  if (!base || !existsSync(base + '.jpg')) return null
  const here = readFileSync(base + '.jpg')
  await keepOnTv(here)
  return here.toString('base64')
}

/** Puts the saved settings and wallpaper back on a launcher that has none. True if there was a copy. */
async function restoreBackup(): Promise<boolean> {
  const base = backupBase()
  if (!base || !existsSync(base + '.json')) return false
  try {
    const saved = JSON.parse(readFileSync(base + '.json', 'utf8')) as Record<string, unknown>
    log(TASK, 'info', "CleanMyTV Home started empty; putting this computer's copy of its settings back")
    await setConfig(clean(saved, true))
    const wallpaper = await wallpaperCopy()
    if (wallpaper) await setWallpaper(wallpaper)
    return true
  } catch (e) {
    log(TASK, 'error', `could not put the settings back: ${(e as Error).message}`)
    return false
  }
}

/** Notification-listener access for the Now playing section: given with the section, taken away without it. */
async function nowPlayingAccess(on: boolean): Promise<void> {
  const out = await T.sh('theme', `cmd notification ${on ? 'allow' : 'disallow'}_listener ${NOW_LISTENER}`).catch(() => '')
  // Android 8.0 has no such command: change the setting it writes, the list of listeners, by hand (#139).
  if (!/No shell command implementation|Unknown command/i.test(out)) return
  const now = (await T.tv().shell('settings get secure enabled_notification_listeners')).trim()
  const list = now && now !== 'null' ? now.split(':').filter((c) => c !== NOW_LISTENER) : []
  const next = on ? [...list, NOW_LISTENER] : list
  await T.sh('theme', next.length ? `settings put secure enabled_notification_listeners ${next.join(':')}` : 'settings delete secure enabled_notification_listeners').catch(() => '')
}

/** Lets the launcher see which apps the TV opened, for Recently used; on only while that row is. */
async function usageAccess(on: boolean): Promise<void> {
  await T.sh('theme', `appops set ${HOME_PKG} GET_USAGE_STATS ${on ? 'allow' : 'default'}`).catch(() => '')
}

/** Lets the launcher read other apps' entries in the TV's Watch Next list, for Continue watching. */
async function watchNextAccess(task: string): Promise<void> {
  await T.sh(task, `pm grant ${HOME_PKG} android.permission.READ_TV_LISTINGS 2>/dev/null; echo listings-granted`).catch(() => '')
}

/** Uploads a JPEG in base64 pieces, then has the launcher check its MD5 and swap it in. */
export async function setWallpaper(jpegBase64: string | null, from = ''): Promise<HomeConfig> {
  if (jpegBase64 === null) {
    keepWallpaper(null)
    await keepOnTv(null)
    return parse(await call('wallpaper_remove'))
  }
  if (typeof jpegBase64 !== 'string' || !/^[A-Za-z0-9+/]+=*$/.test(jpegBase64)) throw new Error('the wallpaper must be a JPEG in base64')
  const data = Buffer.from(jpegBase64, 'base64')
  if (data.length > 4 * 1024 * 1024) throw new Error('the wallpaper is over 4 MB')
  if (data[0] !== 0xff || data[1] !== 0xd8) throw new Error('the wallpaper must be a JPEG')
  log('theme', 'cmd', `upload wallpaper (${Math.round(data.length / 1024)} KB)`)
  await call('wallpaper_begin')
  for (const piece of pieces(data)) await call('wallpaper_chunk', piece)
  let cfg = parse(await call('wallpaper_end', createHash('md5').update(data).digest('hex')))
  // Which theme picture this is, or none: the TV's "next wallpaper" button only steps through a theme's own.
  if (cfg.wallpaper_from !== from) cfg = await setConfig({ wallpaper_from: THEME_PICTURE.test(from) ? from : '' })
  keepWallpaper(data)
  await keepOnTv(data)
  log('theme', 'ok', 'wallpaper set')
  return cfg
}
