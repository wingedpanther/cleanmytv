// Debloat lists, the recovery record and disable/restore: the TypeScript port of upstream
// tools/disable.sh, tools/recovery.py and tools/restore.sh, with the same guards and the same record
// format in ~/.cleanyourtv/, so either tool can undo what the other did.
import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readdirSync, readFileSync, realpathSync, renameSync, rmSync, writeFileSync, openSync, fsyncSync, closeSync } from 'node:fs'
import { homedir } from 'node:os'
import { join, resolve } from 'node:path'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { keptLauncher } from './kept'
import { keyboardPackages, noteKeyboard } from './keyboard'
import { t, UiError } from './i18n'
import { inEnglish } from './english'
import type { ApplyResult, DebloatApplyRequest, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState, Probe } from '@shared/api'

const TASK = 'debloat'
export const SETUPWRAITH = 'com.google.android.tungsten.setupwraith'
// They answer HOME too, but they are not launchers: setupwraith taking Home is the crash loop the
// home guard exists to prevent.
export const NOT_LAUNCHERS = [SETUPWRAITH, 'com.android.tv.settings', 'com.android.settings', 'com.android.provision']
/** CleanMyTV Home (launcher.ts HOME_PKG; launcher.ts imports this file, so it is repeated here). */
const OUR_HOME = 'io.github.wingedpanther.cleanmytv.home'
/** Google's launchers take Home back while they are on (#27), so the handover switches them off. */
export const GOOGLE_LAUNCHERS = ['com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher']
export const KNOWN_STOCK_LAUNCHERS = [...GOOGLE_LAUNCHERS, 'com.mitv.tvhome']
const CUSTOM_LAUNCHER = 'com.gathin.tvhome'
// Core Android and TV middleware: with one of these off, the TV may not boot again (upstream #884, #1578).
// Same patterns as upstream's tools/recovery.py, and no option overrides them.
export const CORE_RE = /^(android|android\.ext\.(services|shared)|com\.android\.sdksandbox|com\.android\.location\.fused|com\.android\.systemui|com\.android\.shell|com\.android\.phone|com\.android\.settings|com\.android\.tv\.settings|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)|com\.google\.android\.overlay\.modules.*|com\.android\.providers\..*|com\.cltv\.mal|com\.mediatek\.(tis|tv\.service|tv\.agent))$/
export const CORE_OK = /^com\.android\.providers\.(calendar|contacts|userdictionary|blockednumber)$/
export const isCore = (pkg: string): boolean => CORE_RE.test(pkg) && !CORE_OK.test(pkg)
const SETTINGS = ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale'] as const
const HOME_RE = /^[A-Za-z_][\w.]*\/[\w.$]+$/
const SCALE_RE = /^-?\d+(\.\d+)?$/
const VERSION = 1

// ---- lists ---------------------------------------------------------------------------------------

export interface ListEntry {
  pkg: string
  note: string
  suggested: boolean
  /** The comment heading the block the package sits in, e.g. "Personal choice: voice assistant". */
  section?: string
}

export interface ParsedList {
  header: string[]
  entries: ListEntry[]
}

/**
 * A debloat list: active lines are package names with an optional "# note"; a commented-out line
 * holding only a package (and a note) is one the author chose to keep. A BOM and CRLF are dropped.
 * Anything else on an active line refuses the whole list, as recovery.py does.
 */
export function parseList(text: string, name = 'list'): ParsedList {
  const header: string[] = []
  const entries: ListEntry[] = []
  const seen = new Set<string>()
  // A blank line ends a block; the last comment line that is not a package heads the block.
  let section: string | undefined
  const lines = text.replace(/^﻿/, '').split(/\r?\n/)
  lines.forEach((raw, i) => {
    const line = raw.trim()
    if (!line) { section = undefined; return }
    if (line.startsWith('#')) {
      const body = line.replace(/^#+\s*/, '')
      const m = /^(\S+)\s*(?:#\s*(.*))?$/.exec(body)
      if (m && T.PACKAGE_RE.test(m[1])) {
        if (!seen.has(m[1])) { seen.add(m[1]); entries.push({ pkg: m[1], note: (m[2] ?? '').trim(), suggested: false, section }) }
      } else {
        if (!entries.length) header.push(body)
        if (body) section = body
      }
      return
    }
    const hash = line.indexOf('#')
    const code = (hash >= 0 ? line.slice(0, hash) : line).trim()
    const note = hash >= 0 ? line.slice(hash + 1).trim() : ''
    const pkg = code.split(/\s+/)[0]
    if (!T.PACKAGE_RE.test(pkg)) throw new Error(`${name}:${i + 1}: not a package name: ${pkg}`)
    const existing = entries.find((e) => e.pkg === pkg)
    if (existing) { existing.suggested = true; return }
    seen.add(pkg)
    entries.push({ pkg, note, suggested: true, section })
  })
  return { header, entries }
}

interface Upstream {
  id: string
  title: string
  /** Another model the list fits, named after the title. */
  also?: string
  text: string
  parsed: ParsedList
  /** From the list's tv.json: every model the list was checked on, and the brand. */
  tv: string
  brand: string
}

let upstreamCache: Upstream[] | null = null

/** Models owners have confirmed on a bundled list that doesn't name them (same board). */
const ALSO_FITS: Record<string, string> = {
  'tcl-55q6cx1': 'TCL 75Q6C' // G08 board, #98
}

function readTvJson(file: string): { tv: string; brand: string } {
  try {
    const j = JSON.parse(readFileSync(file, 'utf8')) as { tv?: unknown; brand?: unknown }
    return { tv: typeof j.tv === 'string' ? j.tv : '', brand: typeof j.brand === 'string' ? j.brand : '' }
  } catch {
    return { tv: '', brand: '' }
  }
}

function upstreamLists(): Upstream[] {
  if (upstreamCache) return upstreamCache
  const out: Upstream[] = []
  if (existsSync(paths.examples)) {
    for (const id of readdirSync(paths.examples).sort()) {
      const file = join(paths.examples, id, 'debloat.txt')
      if (!existsSync(file)) continue
      try {
        const text = readFileSync(file, 'utf8')
        const parsed = parseList(text, `${id}/debloat.txt`)
        const meta = readTvJson(join(paths.examples, id, 'tv.json'))
        const also = ALSO_FITS[id]
        out.push({ id, title: parsed.header[0] ?? id, also, text, parsed, tv: also ? `${meta.tv} ${also}` : meta.tv, brand: meta.brand })
      } catch (e) {
        log(TASK, 'warn', `skipping upstream list ${id}: ${(e as Error).message}`)
      }
    }
  }
  upstreamCache = out
  return out
}

let stockCache: string[] | null = null

/**
 * Every stock launcher this app knows: Google's and Xiaomi's, plus the vendor launchers upstream lists
 * name in their "Stock launcher:" notes (TCL cyberui, Hisense JuUI, CVTE, Zeasn, Sber and more).
 */
export function stockLaunchers(): string[] {
  if (stockCache) return stockCache
  const found = new Set(KNOWN_STOCK_LAUNCHERS)
  for (const l of upstreamLists()) {
    for (const m of l.text.matchAll(/^#.*\bstock launcher:\s*([A-Za-z][\w]*(?:\.\w+)+)/gim)) {
      const pkg = m[1]
      if (T.PACKAGE_RE.test(pkg) && !NOT_LAUNCHERS.includes(pkg) && pkg !== OUR_HOME && pkg !== CUSTOM_LAUNCHER) found.add(pkg)
    }
  }
  stockCache = [...found]
  return stockCache
}

/** How many upstream lists disable each package, and the first note found for it. */
function commonality(): Map<string, { count: number; note: string }> {
  const m = new Map<string, { count: number; note: string }>()
  for (const l of upstreamLists()) {
    for (const e of l.parsed.entries) {
      if (!e.suggested) continue
      const cur = m.get(e.pkg) ?? { count: 0, note: '' }
      cur.count++
      if (!cur.note && e.note) cur.note = e.note
      m.set(e.pkg, cur)
    }
  }
  return m
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** How well a list's header, folder name and tv.json models fit the TV's properties. */
export function matchScore(id: string, header: string[], probe: Pick<Probe, 'model' | 'device' | 'product' | 'manufacturer'>, tv = ''): number {
  const text = (header.slice(0, 4).join(' ') + ' ' + id + ' ' + tv).toLowerCase()
  const has = (token: string): boolean => {
    const t = token.trim().toLowerCase()
    if (t.length < 3) return false
    return new RegExp(`(^|[^a-z0-9])${escapeRe(t)}([^a-z0-9]|$)`).test(text)
  }
  let score = 0
  if (has(probe.device)) score += 5
  if (has(probe.model)) score += 4
  if (probe.product && probe.product !== probe.device && has(probe.product)) score += 3
  const brand = probe.manufacturer.trim().toLowerCase().split(/\s+/)[0]
  if (score && brand && id.startsWith(brand)) score += 1
  return score
}

export function lists(probe: Probe | undefined): DebloatListSummary[] {
  const all = upstreamLists().map((l) => ({
    id: l.id,
    title: titleOf(l),
    score: probe ? matchScore(l.id, l.parsed.header, probe, l.tv) : 0,
    sameBrand: !!probe && sameBrand(l.brand, probe.manufacturer)
  }))
  // Lists from the TV's own brand come next after real matches. They keep score 0: a brand alone
  // is no reason for the guided run to pick a list.
  all.sort((a, b) => b.score - a.score || Number(b.sameBrand) - Number(a.sameBrand) || a.id.localeCompare(b.id))
  const out: DebloatListSummary[] = all.map(({ id, title, score }) => ({ id, title, score }))
  // Device-family fallback (#2): when no list matches the TV on model/device (best exact score
  // < 5), infer one from the manufacturer so unlisted models of a known family still start from
  // that family's list instead of the generic one. The entry keeps the base list's id (so
  // selecting it loads the real list) and is clearly marked as inferred.
  const bestExact = all[0]?.score ?? 0
  if (probe && bestExact < 5) {
    const family = familyFor(probe)
    const base = family ? upstreamLists().find((l) => l.id === family.baseListId) : undefined
    // A base list that already matches a little keeps its own row and score.
    if (family && base && !all.some((l) => l.id === base.id && l.score > 0)) {
      // It replaces the base list's own row, so each id is listed once, and goes right after any matches.
      const own = out.findIndex((l) => l.id === base.id)
      if (own >= 0) out.splice(own, 1)
      const unmatched = out.findIndex((l) => l.score === 0)
      out.splice(unmatched < 0 ? out.length : unmatched, 0, {
        id: base.id,
        title: t('Inferred for your {family} device — review each package', { family: family.label }),
        score: 0
      })
    }
  }
  return [{ id: 'custom', title: t('My TV is not listed: pick from packages other TVs disable'), score: 0 }, ...out]
}

/** The list's own title, and the other model it fits. */
const titleOf = (l: Upstream): string => (l.also ? `${l.title} ${t('Also fits the {tv}.', { tv: l.also })}` : l.title)

/**
 * Known device families: maps a TV's manufacturer (and model-code prefix) to the upstream list
 * that best represents the family. Real owner reports can promote an inferred match into a
 * dedicated upstream list over time; keep this list small and evidence-based.
 */
const DEVICE_FAMILIES: Array<{ label: string; manufacturers: string[]; modelPrefixes: string[]; baseListId: string }> = [
  { label: 'Xiaomi / Mi TV', manufacturers: ['xiaomi', 'redmi'], modelPrefixes: ['mitv', 'mssp', 'mftr'], baseListId: 'xiaomi-mitv-mssp3' },
  { label: 'TCL', manufacturers: ['tcl', 'thomson'], modelPrefixes: [], baseListId: 'tcl-c755-google-tv' }
]

/** tv.json brands read "Amazon Fire TV" or "SHARP"; ro.product.manufacturer reads "Amazon" or "Sharp". */
function sameBrand(brand: string, manufacturer: string): boolean {
  const a = brand.trim().toLowerCase().split(/\s+/)[0]
  const b = manufacturer.trim().toLowerCase().split(/\s+/)[0]
  return !!a && !!b && (a === b || (a.length >= 3 && b.startsWith(a)) || (b.length >= 3 && a.startsWith(b)))
}

function familyFor(probe: Pick<Probe, 'model' | 'device' | 'product' | 'manufacturer'>): { label: string; baseListId: string } | null {
  const brand = probe.manufacturer.trim().toLowerCase().split(/\s+/)[0]
  const model = probe.model.trim().toLowerCase()
  for (const f of DEVICE_FAMILIES) {
    const mfrHit = brand && f.manufacturers.some((m) => brand === m || brand.includes(m))
    const modelHit = model && f.modelPrefixes.some((p) => model.startsWith(p))
    if (mfrHit || modelHit) return { label: f.label, baseListId: f.baseListId }
  }
  return null
}

// ---- the saved list and its recovery record -------------------------------------------------------

const cytvDir = (): string => join(homedir(), '.cleanyourtv')

function slug(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '') || 'tv'
}

/** The list this app writes for the TV and hands to the record, like a user's own debloat.txt. */
export function savedListPath(listId: string, probe: Probe): string {
  const base = listId === 'custom' ? `custom-${slug(probe.device || probe.model || probe.host)}` : slug(listId)
  return join(cytvDir(), 'lists', `${base}.txt`)
}

/** Python's Path.resolve(): absolute with symlinks followed (macOS /var is /private/var). */
function pyResolve(p: string): string {
  try { return realpathSync.native(p) } catch { return resolve(p) }
}

/** Same name as recovery.py: one record per TV and list, the list identified by its absolute path. */
export function recordPath(listPath: string, host: string): string {
  const tag = (host || 'no-host').replace(/[^A-Za-z0-9.-]/g, '_')
  const digest = createHash('sha1').update(pyResolve(listPath)).digest('hex').slice(0, 8)
  return join(cytvDir(), `recovery-${tag}-${digest}.json`)
}

export interface RecoveryRecord {
  version: number
  created: string
  host: string
  list: string
  home: string | null
  settings: Record<string, string>
  packages: Record<string, PackageState>
  /**
   * ro.product.device of the TV the record was taken from. Not in upstream's format (recovery.py
   * ignores it); it lets the app follow the TV to a new address and refuse another device's record.
   */
  device?: string
}

export function loadRecord(path: string): RecoveryRecord {
  let record: RecoveryRecord
  try {
    record = JSON.parse(readFileSync(path, 'utf8'))
  } catch (e) {
    throw new UiError('{path}: not a usable recovery record ({error}). It is left where it is: move it aside deliberately.', { path, error: (e as Error).message })
  }
  if (record.version !== VERSION || typeof record.packages !== 'object' || !record.packages) {
    throw new UiError('{path}: unknown recovery record format; move it aside deliberately.', { path })
  }
  // The record is replayed on the TV's shell: a hand-edited or damaged file stops here.
  for (const [name, state] of Object.entries(record.packages)) {
    if (!T.PACKAGE_RE.test(name)) throw new Error(`${path}: not a package name: ${name}`)
    if (!['absent', 'disabled', 'enabled'].includes(state)) throw new Error(`${path}: unknown state for ${name}: ${state}`)
  }
  if (record.home != null && !(typeof record.home === 'string' && HOME_RE.test(record.home))) {
    throw new Error(`${path}: not a Home component: ${record.home}`)
  }
  if (record.settings != null && typeof record.settings !== 'object') throw new Error(`${path}: settings is not a mapping`)
  if (record.device != null && typeof record.device !== 'string') throw new Error(`${path}: device is not a string`)
  return record
}

/**
 * The record for this TV and list. Records are named after the TV's address, as upstream names them;
 * when the TV got a new address, its record is found by device and list and moved to the new name, so
 * undo keeps the state from before the first cleanup. A record taken from another device is refused.
 */
export function findRecord(listPath: string, host: string, device: string | undefined): { path: string; record: RecoveryRecord | null } {
  const path = recordPath(listPath, host)
  if (existsSync(path)) {
    const record = loadRecord(path)
    if (record.device && device && record.device !== device) {
      throw new UiError('The restore record for {host} was taken from another device ({other}); this TV reports {device}. Nothing was changed. If the TV at this address really is new, move {path} aside first.',
        { host, other: record.device, device, path })
    }
    return { path, record }
  }
  if (device && existsSync(cytvDir())) {
    const list = pyResolve(listPath)
    for (const name of readdirSync(cytvDir())) {
      if (!/^recovery-.*\.json$/.test(name)) continue
      const old = join(cytvDir(), name)
      let record: RecoveryRecord
      try { record = loadRecord(old) } catch { continue }
      if (record.list !== list || record.device !== device) continue
      log(TASK, 'info', `this TV moved from ${record.host} to ${host}; keeping its restore record from ${record.created}`)
      writeRecord(path, { ...record, host })
      renameSync(old, old + '.moved')
      return { path, record: { ...record, host } }
    }
  }
  return { path, record: null }
}

function sortKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(sortKeys)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value as object).sort().map((k) => [k, sortKeys((value as Record<string, unknown>)[k])]))
  }
  return value
}

/** Written next to its final name first, as recovery.py does: a crash must not leave half a record. */
export function writeRecord(path: string, record: RecoveryRecord): void {
  mkdirSync(join(path, '..'), { recursive: true, mode: 0o700 })
  const tmp = path + '.tmp'
  // A leftover tmp from a crash is removed, and the new one is created fresh ('wx'): never written
  // through a file or link that was already there.
  rmSync(tmp, { force: true })
  writeFileSync(tmp, JSON.stringify(sortKeys(record), null, 2) + '\n', { flag: 'wx', mode: 0o600 })
  const fd = openSync(tmp, 'r+')
  try { fsyncSync(fd) } finally { closeSync(fd) }
  renameSync(tmp, path)
}

function strftimeNow(): string {
  const d = new Date()
  const p = (n: number): string => String(n).padStart(2, '0')
  const off = -d.getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}` +
    `${sign}${p(Math.floor(Math.abs(off) / 60))}${p(Math.abs(off) % 60)}`
}

function stateOf(name: string, installed: Set<string>, disabled: Set<string>): PackageState {
  return !installed.has(name) ? 'absent' : disabled.has(name) ? 'disabled' : 'enabled'
}

/** recovery.py --save: the first record wins; later runs only add names the list gained. */
export async function saveRecord(listPath: string, host: string, packages: string[], device?: string): Promise<RecoveryRecord> {
  const found = findRecord(listPath, host, device)
  const path = found.path
  if (found.record) {
    const record = found.record
    const added = packages.filter((p) => !(p in record.packages))
    log(TASK, 'info', `keeping the recovery record from ${record.created}: ${path}`)
    if (added.length) {
      const { installed, disabled } = await T.packageSets()
      for (const name of added) record.packages[name] = stateOf(name, installed, disabled)
      writeRecord(path, record)
      log(TASK, 'info', `added ${added.length} names the list gained since then: ${added.join(', ')}`)
    }
    return record
  }
  const { installed, disabled } = await T.packageSets()
  let home = await T.homeComponent()
  const settings: Record<string, string> = {}
  for (const key of SETTINGS) settings[key] = (await T.tv().shell(`settings get global ${key}`)).trim()
  // The launcher that owns Home may not be in the list, but it is what undo has to put back. Android's
  // chooser answers as "android/...ResolverActivity": no launcher owns Home then, and "android" is not a
  // name loadRecord would accept, so it is recorded as no Home at all (restore then picks the stock one).
  if (home && !T.PACKAGE_RE.test(home.split('/')[0])) home = null
  const homePkg = home ? home.split('/')[0] : null
  const targets = homePkg && !packages.includes(homePkg) ? [...packages, homePkg] : packages
  const record: RecoveryRecord = {
    version: VERSION,
    created: strftimeNow(),
    host,
    list: pyResolve(listPath),
    home,
    settings,
    packages: Object.fromEntries(targets.map((n) => [n, stateOf(n, installed, disabled)])),
    ...(device ? { device } : {})
  }
  writeRecord(path, record)
  const already = Object.values(record.packages).filter((s) => s === 'disabled').length
  log(TASK, 'ok', `recorded ${targets.length} packages (${already} already disabled, left alone by undo) -> ${path}`)
  log(TASK, 'info', `Home before: ${home ?? 'no component answered'}`)
  return record
}

/** recovery.py --commands: the batch that puts the recorded states back. */
export function restoreCommands(listPackages: string[], record: RecoveryRecord | null, stock: string, notes: string[]): string[] {
  if (!T.PACKAGE_RE.test(stock)) throw new Error(`not a package name: ${stock}`)
  const cmds: string[] = []
  const resolveStock = `cmd package set-home-activity --user 0 $(${T.HOME_QUERY} ${stock} | tail -n 1)`
  if (!record) {
    notes.push(t('No recovery record for this TV and list: enabling every listed package and resetting Home and the animation scales to their defaults.'))
    for (const n of listPackages) cmds.push(`pm enable ${n}`)
    cmds.push(`pm enable ${stock}`)
    cmds.push(resolveStock)
    for (const k of SETTINGS) cmds.push(`settings put global ${k} 1.0`)
    cmds.push('pm list packages -d')
    return cmds
  }
  const states = record.packages
  const reEnable = Object.keys(states).filter((n) => states[n] === 'enabled')
  for (const n of reEnable) cmds.push(`pm enable ${n}`)
  const untouched = Object.keys(states).filter((n) => states[n] !== 'enabled')
  if (untouched.length) notes.push(t('Left as they were before cleanup: {pkgs}', { pkgs: untouched.join(', ') }))
  // The stock launcher comes back unless the owner had disabled it before cleanup.
  if (states[stock] !== 'disabled' && !reEnable.includes(stock)) cmds.push(`pm enable ${stock}`)
  const home = record.home
  if (home && home.split('/')[0] !== CUSTOM_LAUNCHER) cmds.push(`cmd package set-home-activity --user 0 ${home}`)
  else cmds.push(resolveStock)
  for (const k of SETTINGS) {
    const v = String(record.settings?.[k] ?? '').trim()
    if (v === 'null') cmds.push(`settings delete global ${k}`)
    else if (SCALE_RE.test(v)) cmds.push(`settings put global ${k} ${v}`)
  }
  const edited = listPackages.filter((n) => !(n in states))
  if (edited.length) notes.push(t('Not in the record, left alone: {pkgs}', { pkgs: edited.join(', ') }))
  cmds.push('pm list packages -d')
  return cmds
}

// ---- plan, apply, restore -------------------------------------------------------------------------

// Features people notice when they are gone. Whatever a list says, these are the owner's call: they are
// never ticked by default (upstream's prompt asks about them before anything is disabled).
// Functions, so the words come out in the window's language when a plan is read.
const FEATURES: Record<string, () => string> = {
  'com.google.android.tv.remote.service': () => t('the phone remote (Google TV / Android TV Remote app)'),
  'com.google.android.apps.mediashell': () => t('casting from phones (Chromecast built-in)'),
  'com.google.android.katniss': () => t('voice search and the remote\'s mic button'),
  'com.google.android.speech.pumpkin': () => t('voice search and the remote\'s mic button'),
  // Both are needed: with backdrop off, dreamx still runs but shows Google's gallery, not the owner's albums (#57).
  'com.google.android.apps.tv.dreamx': () => t('the Ambient screensaver and its Google Photos albums'),
  'com.google.android.backdrop': () => t('the Ambient screensaver and its Google Photos albums'),
  // USB drives (#71): the media browser and the apps it opens photos and videos with, and Android's own
  // storage providers, which every file browser reads through.
  'com.xiaomi.mitv.mediaexplorer': () => t('USB drives in Media Player'),
  'com.mitv.gallery': () => t('photos from USB drives in Media Player'),
  'com.mitv.videoplayer': () => t('videos from USB drives in Media Player'),
  'com.android.providers.media': () => t('USB drives and photos in every app'),
  'com.android.providers.media.module': () => t('USB drives and photos in every app'),
  'com.android.externalstorage': () => t('USB drives in file browsers'),
  'com.android.documentsui': () => t('picking files from USB drives'),
  // Web viewers (#55): YouTube's ads and many sign-in pages load in them; without them the picture can go black.
  'com.google.android.webview': () => t('web pages inside apps, including YouTube ads'),
  'com.android.webview': () => t('web pages inside apps, including YouTube ads'),
  'com.tcl.android.webview': () => t('web pages inside apps, including YouTube ads'),
  'com.mediatek.support.webview': () => t('web pages inside apps, including YouTube ads'),
  // TCL's phone app (TCL Home, once T-Cast) talks to these (#81): with them off it can't find the TV. ttvs also
  // carries TCL's viewing analytics, so it's still there to tick for anyone who doesn't use the app.
  'com.tcl.ttvs': () => t("TCL's phone app (TCL Home), which also sends TCL viewing data"),
  'com.tcl.magiconnectfree': () => t("TCL's phone app (T-Cast) and phone mirroring"),
  // Streaming apps: people pick their TV's list for the bloat, not to lose what they watch. Many lists
  // switch these off, so they were ticked and had to be turned back on by hand.
  'com.netflix.ninja': () => 'Netflix',
  'com.netflix.mediaclient': () => 'Netflix',
  'com.netflix.partner.ncm': () => t('the remote\'s Netflix button'),
  'com.google.android.youtube.tv': () => 'YouTube',
  'com.google.android.youtube.tvmusic': () => 'YouTube Music',
  'com.google.android.youtube.tvkids': () => 'YouTube Kids',
  'com.amazon.amazonvideo.livingroom': () => 'Prime Video',
  'com.disney.disneyplus': () => 'Disney+',
  'com.spotify.tv.android': () => 'Spotify',
  'com.apple.atve.androidtv.appletv': () => 'Apple TV',
  'com.hbo.hbonow': () => 'Max',
  'tv.twitch.android.app': () => 'Twitch'
}

/** Why a package is the owner's decision rather than a default, or undefined when it is plain bloat. */
export function choiceFor(e: ListEntry): string | undefined {
  if (FEATURES[e.pkg]) return t('Turns off {feature}.', { feature: FEATURES[e.pkg]() })
  if (e.section && /^(personal choice|optional)\b/i.test(e.section)) return e.section
  return undefined
}

function sourceList(listId: string): { title: string; parsed: ParsedList } {
  if (listId === 'custom') {
    const entries = [...commonality().entries()]
      .sort((a, b) => b[1].count - a[1].count || a[0].localeCompare(b[0]))
      .map(([pkg, c]) => ({ pkg, note: c.note, suggested: false }))
    return { title: t('Custom list'), parsed: { header: [t('Custom list made with CleanMyTV from packages other TVs disable.')], entries } }
  }
  const l = upstreamLists().find((u) => u.id === listId)
  if (!l) throw new UiError('no such list: {id}', { id: listId })
  return { title: titleOf(l), parsed: l.parsed }
}

/** Active package lines of the list this app saved earlier for this TV, if any. */
function savedActive(file: string): string[] {
  if (!existsSync(file)) return []
  try {
    return parseList(readFileSync(file, 'utf8'), file).entries.filter((e) => e.suggested).map((e) => e.pkg)
  } catch {
    return []
  }
}

export async function plan(listId: string): Promise<DebloatPlan> {
  const probe = T.getStatus().probe
  if (!probe) throw new UiError('Connect to a TV first.')
  const { title, parsed } = sourceList(listId)
  const { installed, disabled } = await T.packageSets()
  const file = savedListPath(listId, probe)
  const { path: rpath, record } = findRecord(file, probe.host, probe.device || undefined)
  const common = commonality()
  let entries: DebloatEntry[] = parsed.entries.map((e) => ({
    pkg: e.pkg,
    note: e.note || common.get(e.pkg)?.note || '',
    suggested: e.suggested,
    state: stateOf(e.pkg, installed, disabled),
    recorded: record?.packages[e.pkg],
    common: common.get(e.pkg)?.count ?? 0,
    choice: choiceFor(e)
  }))
  // The custom list is every package other TVs disable: only the ones on this TV are worth showing.
  if (listId === 'custom') entries = entries.filter((e) => e.state !== 'absent')
  // Packages the owner added by hand (#39) live only in the saved list.
  const inList = new Set(entries.map((e) => e.pkg))
  for (const pkg of savedActive(file)) {
    if (inList.has(pkg)) continue
    entries.push(addedEntry(pkg, stateOf(pkg, installed, disabled), common, record?.packages[pkg]))
  }
  // Lists name core packages as lines their authors kept, so don't offer them. One already off stays visible.
  entries = entries.filter((e) => !isCore(e.pkg) || e.state === 'disabled')
  const home = await T.homeComponent()
  const homePackage = home ? home.split('/')[0] : null
  return {
    listId,
    title,
    header: parsed.header,
    entries,
    record: record ? { path: rpath, created: record.created } : undefined,
    homePackage,
    cameBack: record ? cameBack(savedActive(file), installed, disabled, homePackage, await screensaverApps()) : []
  }
}

/**
 * Packages this app switched off on this TV that are on again, usually after a firmware update. The
 * stock launcher and setupwraith only count once CleanMyTV Home owns Home: until then apply keeps them on.
 */
export function cameBack(active: string[], installed: Set<string>, disabled: Set<string>, homePackage: string | null, keep: string[] = []): string[] {
  const held = homePackage === OUR_HOME ? [] : [...stockLaunchers(), SETUPWRAITH]
  // Google's launcher kept on for apps that need it is on by choice, and setupwraith with it (#99, #88).
  const kept = keptLauncher()
  if (kept) held.push(kept, SETUPWRAITH)
  return active.filter((p) => installed.has(p) && !disabled.has(p) && !isCore(p) && !held.includes(p) && !keep.includes(p))
}

/** The screensaver in use, and the backdrop app Google's needs with it: on by choice, not by an update (#57). */
async function screensaverApps(): Promise<string[]> {
  const comp = (await T.sh(TASK, 'settings get secure screensaver_components', { quiet: true })).trim().split(',')[0] ?? ''
  const pkg = comp.split('/')[0]
  if (!pkg || pkg === 'null') return []
  return pkg === 'com.google.android.apps.tv.dreamx' ? [pkg, 'com.google.android.backdrop'] : [pkg]
}

function addedEntry(pkg: string, state: PackageState, common: Map<string, { count: number; note: string }>, recorded?: PackageState): DebloatEntry {
  return {
    pkg,
    note: common.get(pkg)?.note ?? '',
    suggested: true,
    state,
    recorded,
    common: common.get(pkg)?.count ?? 0,
    choice: choiceFor({ pkg, note: '', suggested: true }),
    added: true
  }
}

/** Every package on the TV, for adding one by hand (#39). Launchers and our own apps are left out. */
export async function tvPackages(): Promise<DebloatEntry[]> {
  const { installed, disabled } = await T.packageSets()
  const common = commonality()
  const skip = new Set([...stockLaunchers(), ...NOT_LAUNCHERS, OUR_HOME, CUSTOM_LAUNCHER, 'android'])
  return [...installed].filter((p) => !skip.has(p) && !isCore(p)).sort().map((p) => addedEntry(p, stateOf(p, installed, disabled), common))
}

function writeSavedList(file: string, title: string, header: string[], active: string[], known: ListEntry[]): void {
  mkdirSync(join(file, '..'), { recursive: true, mode: 0o700 })
  const notes = new Map(known.map((e) => [e.pkg, e.note]))
  const width = Math.max(40, ...known.map((e) => e.pkg.length + 2))
  const line = (pkg: string): string => {
    const note = notes.get(pkg)
    return note ? `${pkg.padEnd(width)}# ${note}` : pkg
  }
  const kept = known.map((e) => e.pkg).filter((p) => !active.includes(p))
  const out = [
    `# ${title}`,
    ...header.slice(1).map((h) => `# ${h}`.trimEnd()),
    '#',
    `# Written by CleanMyTV on ${strftimeNow()}. Undo from the app, or with upstream:`,
    `#   TV_HOST=<tv ip> tools/restore.sh ${file}`,
    '',
    ...active.map(line),
    '',
    '# Kept (not disabled):',
    ...kept.map((p) => `# ${line(p)}`)
  ]
  writeFileSync(file, out.join('\n') + '\n')
}

export interface WifiCheck {
  owner: 'setupwraith' | 'system' | 'unknown'
  /** Other saved networks that belong to setupwraith and go with it; the one in use is fine. */
  lost: number
  /** The TV reaches this computer over Ethernet, so deleting saved Wi-Fi can't cut ADB off. */
  ethernet?: boolean
}

/**
 * Who owns the saved Wi-Fi network the TV uses. A network saved in the first-run wizard belongs to
 * setupwraith, and Android deletes it the moment setupwraith is disabled. Only the saved-networks list
 * counts: the rest of the dump repeats networks, old ones included, and a TV whose owner joined the
 * network again still matched there (discussion #13, #55). With no network in use (Ethernet), any
 * network of setupwraith's counts. 'unknown' when the TV lists none or didn't answer.
 */
export async function wifiOwner(task: string): Promise<WifiCheck> {
  let wifi = ''
  try { wifi = await T.sh(task, 'dumpsys wifi', { quiet: true }) } catch { wifi = '' }
  const check = parseWifi(wifi)
  if (check.owner === 'system') return check
  // A box on Ethernet has no Wi-Fi to forget and join again. If the address this app talks to sits
  // on an Ethernet port, setupwraith can go: only saved Wi-Fi from the first setup goes with it.
  let ip = ''
  try { ip = await T.sh(task, 'ip -o -4 addr show', { quiet: true }) } catch { ip = '' }
  if (onEthernet(ip, T.host())) return { owner: 'system', lost: wraithNetworks(wifi), ethernet: true }
  return check
}

/** Whether [host] is the address of an Ethernet port, from `ip -o -4 addr show`. */
export function onEthernet(ipAddr: string, host: string): boolean {
  const line = ipAddr.split('\n').find((l) => l.includes(` inet ${host}/`))
  const iface = line?.trim().split(/\s+/)[1] ?? ''
  return /^eth\d/.test(iface)
}

/** Saved networks that belong to setupwraith, in the saved-networks section or, failing that, the whole dump. */
export function wraithNetworks(wifi: string): number {
  const section = /Configured networks Begin -+\n([\s\S]*?)^[^\n]*Configured networks End/m.exec(wifi)?.[1] ?? ''
  return section.split(/\n(?=(?:\* |- DSBLE |- )?ID: \d+ SSID: )/).filter((b) => /cname=\S*setupwraith/.test(b)).length
}

const isWraith = (cname: string | undefined): boolean => !!cname?.includes('setupwraith')

export function parseWifi(wifi: string): WifiCheck {
  const section = /Configured networks Begin -+\n([\s\S]*?)^[^\n]*Configured networks End/m.exec(wifi)?.[1]
  if (section === undefined) {
    // A dump without the usual section: the old, cautious reading.
    const owners = wifi.match(/cname=[^ ]*/g) ?? []
    if (!owners.length) return { owner: 'unknown', lost: 0 }
    return { owner: owners.some((o) => o.includes('setupwraith')) ? 'setupwraith' : 'system', lost: 0 }
  }
  // "* ID:" is the network in use on older Android; newer ones say so in mWifiInfo.
  const nets = section.split(/\n(?=(?:\* |- DSBLE |- )?ID: \d+ SSID: )/).flatMap((b) => {
    const m = /^(\* )?(?:- DSBLE |- )?ID: (\d+) SSID: (.*?) PROVIDER-NAME:/.exec(b.trim())
    return m ? [{ id: Number(m[2]), ssid: m[3].replace(/^"|"$/g, ''), current: !!m[1], cname: /cname=(\S+)/.exec(b)?.[1] }] : []
  })
  if (!nets.length) return { owner: 'unknown', lost: 0 }
  const info = /mWifiInfo SSID: (.*?), BSSID: [^\n]*?Supplicant state: (\w+)/.exec(wifi)
  const inUse =
    nets.find((n) => n.current) ??
    (info && info[2] === 'COMPLETED' ? nets.find((n) => n.ssid === info[1].replace(/^"|"$/g, '')) : undefined)
  if (inUse) {
    if (!inUse.cname) return { owner: 'unknown', lost: 0 }
    return { owner: isWraith(inUse.cname) ? 'setupwraith' : 'system', lost: nets.filter((n) => n !== inUse && isWraith(n.cname)).length }
  }
  if (nets.some((n) => !n.cname)) return { owner: 'unknown', lost: 0 }
  return { owner: nets.some((n) => isWraith(n.cname)) ? 'setupwraith' : 'system', lost: 0 }
}

/** For the warnings: saved networks from the first setup that go with setupwraith. No names: logs get pasted. */
export function lostNetworks(n: number): string[] {
  if (!n) return []
  return [n === 1
    ? t("One other saved Wi-Fi network from the TV's first setup went with setupwraith. If you use it, join again in Settings > Network & Internet.")
    : t("{n} other saved Wi-Fi networks from the TV's first setup went with setupwraith. If you use them, join again in Settings > Network & Internet.", { n })]
}

export async function apply(req: DebloatApplyRequest): Promise<ApplyResult> {
  const probe = T.getStatus().probe
  if (!probe) throw new UiError('Connect to a TV first.')
  for (const p of [...req.disable, ...req.enable]) if (!T.PACKAGE_RE.test(p)) throw new UiError('not a package name: {pkg}', { pkg: p })
  const { title, parsed } = sourceList(req.listId)
  const host = T.host()
  const warnings: string[] = []
  const core = [...new Set(req.disable)].filter(isCore)
  if (core.length) {
    warnings.push(core.length === 1
      ? t('Skipped {pkgs}: without it the TV may not boot again.', { pkgs: core[0] })
      : t('Skipped {pkgs}: without them the TV may not boot again.', { pkgs: core.join(', ') }))
  }
  const wanted = req.disable.filter((p) => !isCore(p))
  let pkgs = [...new Set(wanted)]

  // A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it the
  // moment setupwraith is disabled: the TV drops off Wi-Fi, and ADB with it.
  if (pkgs.includes(SETUPWRAITH) && !req.forceSetupwraith) {
    const { owner, lost } = await wifiOwner(TASK)
    if (owner === 'system') warnings.push(...lostNetworks(lost))
    if (owner === 'unknown') {
      warnings.push(t('Skipped {pkg}: could not check which app owns the saved Wi-Fi networks. Try again, or force it if the TV uses Ethernet.', { pkg: SETUPWRAITH }))
      pkgs = pkgs.filter((p) => p !== SETUPWRAITH)
    } else if (owner === 'setupwraith') {
      warnings.push(t('Skipped {pkg}: the saved Wi-Fi network belongs to it, and disabling it would delete that network and take the TV off Wi-Fi. On the TV, forget the network and join it again in Settings > Network & Internet, then apply again.', { pkg: SETUPWRAITH }))
      pkgs = pkgs.filter((p) => p !== SETUPWRAITH)
    }
  }

  // Google TV's launcher, kept on by the owner for apps that need it, stays on with setupwraith (#99, #88).
  const kept = keptLauncher()
  const keptHeld = kept ? pkgs.filter((p) => p === kept || p === SETUPWRAITH) : []
  if (keptHeld.length) {
    warnings.push(keptHeld.length === 1
      ? t("Kept on: {pkgs}. You chose to keep Google TV's launcher on (Home screen → Overview), and it needs to stay on for that.", { pkgs: keptHeld[0] })
      : t("Kept on: {pkgs}. You chose to keep Google TV's launcher on (Home screen → Overview), and they need to stay on for that.", { pkgs: keptHeld.join(', ') }))
    pkgs = pkgs.filter((p) => !keptHeld.includes(p))
  }

  // The keyboard in use: without it there is no typing a Wi-Fi password or a search.
  const imeId = (await T.sh(TASK, 'settings get secure default_input_method', { quiet: true }).catch(() => '')).trim()
  const ime = imeId.split('/')[0]
  if (T.PACKAGE_RE.test(ime) && pkgs.includes(ime)) {
    warnings.push(t("Kept on: {pkg}. It's the TV's keyboard. Pick another keyboard in the TV's settings first if you want it gone.", { pkg: ime }))
    pkgs = pkgs.filter((p) => p !== ime)
  }
  // The other keyboards too: with one of them off, some TVs (TCL, #98) drop to the phone-remote keyboard at
  // every restart, and the on-screen keyboard is gone.
  const keyboards = keyboardPackages(await T.sh(TASK, 'ime list -s', { quiet: true }).catch(() => ''))
  const keptKeyboards = pkgs.filter((p) => keyboards.includes(p))
  if (keptKeyboards.length) {
    warnings.push(keptKeyboards.length === 1
      ? t("Kept on: {pkgs}. It carries a keyboard, and switching it off can lose the TV's keyboard after a restart.", { pkgs: keptKeyboards[0] })
      : t("Kept on: {pkgs}. They carry keyboards, and switching them off can lose the TV's keyboard after a restart.", { pkgs: keptKeyboards.join(', ') }))
    pkgs = pkgs.filter((p) => !keptKeyboards.includes(p))
  }
  await noteKeyboard(imeId)

  // Home guard: never leave the TV with nothing that can answer Home.
  const homeAns = await T.homeAnswer()
  const homePkg = homeAns.split('/')[0]
  // The stock launcher and setupwraith are Google TV's fallback Homes. They stay on until CleanMyTV Home
  // owns Home: with both off and the launcher that is left failing at boot, the TV shows a black screen
  // and can't be reached (#33). setupwraith that already owns Home is the broken state, so it may go.
  if (homePkg !== OUR_HOME && !req.forceHome) {
    const held = pkgs.filter((p) => stockLaunchers().includes(p) || (p === SETUPWRAITH && homePkg !== SETUPWRAITH))
    if (held.length) {
      warnings.push(held.length === 1
        ? t("Kept on for now: {pkgs}. It is the TV's fallback Home. Install CleanMyTV Home (Home screen step), then apply again.", { pkgs: held[0] })
        : t("Kept on for now: {pkgs}. They are the TV's fallback Home. Install CleanMyTV Home (Home screen step), then apply again.", { pkgs: held.join(', ') }))
      pkgs = pkgs.filter((p) => !held.includes(p))
    }
  }
  if (homePkg && pkgs.includes(homePkg) && !req.forceHome) {
    const claims = await T.homeClaimants()
    const others = claims.filter((c) => c !== homePkg && !pkgs.includes(c) && !NOT_LAUNCHERS.includes(c))
    if (!others.length) {
      warnings.push(t('Skipped {pkg}: it owns Home and nothing else that can answer Home stays enabled. Install CleanMyTV Home first (Home screen step), then apply again.', { pkg: homePkg }))
      pkgs = pkgs.filter((p) => p !== homePkg)
    }
  }

  const adbSecure = probe.adbSecure

  // The saved list holds everything this app has chosen to disable on this TV so far, minus what
  // the user switched back on; the record is written from it before the first command.
  const file = savedListPath(req.listId, probe)
  // A core package an older version saved stays in the list, so Restore without a record still covers it.
  const active = [...new Set([...savedActive(file), ...wanted])].filter((p) => !req.enable.includes(p))
  writeSavedList(file, title, parsed.header, active, parsed.entries)
  log(TASK, 'info', `list saved to ${file}`)
  await saveRecord(file, host, active, probe.device || undefined)

  let done = 0
  // Fire OS and some other firmware refuse their own apps outright; that is the TV's rule, not a failure.
  const guarded: string[] = []
  for (const p of pkgs) {
    const out = await T.sh(TASK, `pm disable-user --user 0 ${p}`)
    if (/disabled/.test(out)) done++
    else if (/Cannot disable a protected package/i.test(out)) guarded.push(p)
    else warnings.push(`${p}: ${out.trim() || t('no answer')}`)
  }
  if (guarded.length) {
    const all = guarded.length === pkgs.length
    warnings.unshift(guarded.length === 1
      ? all
        ? t("The TV doesn't let this one be switched off, so it stays on: {pkgs}. Newer Fire TV software protects every Amazon app this way.", { pkgs: guarded[0] })
        : t("The TV doesn't let this one be switched off, so it stays on: {pkgs}.", { pkgs: guarded[0] })
      : all
        ? t("The TV doesn't let these {n} be switched off, so they stay on: {pkgs}. Newer Fire TV software protects every Amazon app this way.", { n: guarded.length, pkgs: guarded.join(', ') })
        : t("The TV doesn't let these {n} be switched off, so they stay on: {pkgs}.", { n: guarded.length, pkgs: guarded.join(', ') }))
  }
  let enabled = 0
  for (const p of req.enable) {
    const out = await T.sh(TASK, `pm enable ${p}`)
    if (/enabled/.test(out)) enabled++
    else warnings.push(`${p}: ${out.trim() || t('no answer')}`)
  }
  if (adbSecure === '0') {
    warnings.push(t('This TV reports ro.adb.secure=0: network debugging accepts any device on your network without the Allow prompt. Switch debugging off when you are done (Finish step).'))
  }
  // Everything picked was held back by a guard: "Disabled 0 of 0" read like a bug (#40).
  // Core packages never go, so they aren't "for now".
  const stayedOn = [...new Set(wanted)].filter((p) => !pkgs.includes(p))
  const tried = pkgs.length - guarded.length
  const back = req.enable.length > 0
  const say = (): string => {
    const v = { pkgs: stayedOn.join(', '), done, n: tried, more: guarded.length, back: enabled }
    if (pkgs.length === 0 && req.disable.length) {
      if (stayedOn.length) {
        return back
          ? t('Nothing switched off yet: {pkgs} stayed on for now. The notes below say why, switched {back} back on.', v)
          : t('Nothing switched off yet: {pkgs} stayed on for now. The notes below say why.', v)
      }
      return back ? t('Nothing switched off. The notes below say why, switched {back} back on.', v) : t('Nothing switched off. The notes below say why.')
    }
    if (tried === 0) return back ? t('Nothing switched off: the TV protects every package picked, switched {back} back on.', v) : t('Nothing switched off: the TV protects every package picked.')
    if (guarded.length) {
      return back
        ? t('Disabled {done} of {n} {n|package|packages}; the TV protects {more} more, switched {back} back on.', v)
        : t('Disabled {done} of {n} {n|package|packages}; the TV protects {more} more.', v)
    }
    return back ? t('Disabled {done} of {n} {n|package|packages}, switched {back} back on.', v) : t('Disabled {done} of {n} {n|package|packages}.', v)
  }
  const summary = say()
  log(TASK, 'ok', inEnglish(say))
  return { ok: done === tried && enabled === req.enable.length, summary, warnings }
}

async function stockFallback(): Promise<string> {
  const { installed } = await T.packageSets()
  return stockLaunchers().find((p) => installed.has(p)) ?? 'com.google.android.tvlauncher'
}

export async function restore(listId: string): Promise<ApplyResult> {
  const probe = T.getStatus().probe
  if (!probe) throw new UiError('Connect to a TV first.')
  const file = savedListPath(listId, probe)
  const { record } = findRecord(file, probe.host, probe.device || undefined)
  const listPkgs = existsSync(file) ? savedActive(file) : sourceList(listId).parsed.entries.filter((e) => e.suggested).map((e) => e.pkg)
  const notes: string[] = []
  const stock = await stockFallback()
  const cmds = restoreCommands(listPkgs, record, stock, notes)
  for (const n of inEnglish(() => { const en: string[] = []; restoreCommands(listPkgs, record, stock, en); return en })) log('restore', 'info', n)
  await T.batch('restore', cmds)
  const home = await T.homeComponent()
  const say = (): string => record
    ? home ? t('Restored the state recorded on {date}. Home is {home}.', { date: record.created, home }) : t('Restored the state recorded on {date}. Home is not set.', { date: record.created })
    : home ? t('Enabled every package in the list. Home is {home}.', { home }) : t('Enabled every package in the list. Home is not set.')
  const summary = say()
  log('restore', 'ok', inEnglish(say))
  return { ok: true, summary, warnings: notes }
}

