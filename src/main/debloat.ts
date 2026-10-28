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
import type { ApplyResult, DebloatApplyRequest, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState, Probe } from '@shared/api'

const TASK = 'debloat'
export const SETUPWRAITH = 'com.google.android.tungsten.setupwraith'
// They answer HOME too, but they are not launchers: setupwraith taking Home is the crash loop the
// home guard exists to prevent.
const NOT_LAUNCHERS = [SETUPWRAITH, 'com.android.tv.settings', 'com.android.settings', 'com.android.provision']
export const KNOWN_STOCK_LAUNCHERS = ['com.google.android.tvlauncher', 'com.google.android.apps.tv.launcherx', 'com.google.android.leanbacklauncher', 'com.mitv.tvhome']
const CUSTOM_LAUNCHER = 'com.gathin.tvhome'
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
  text: string
  parsed: ParsedList
}

let upstreamCache: Upstream[] | null = null

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
        out.push({ id, title: parsed.header[0] ?? id, text, parsed })
      } catch (e) {
        log(TASK, 'warn', `skipping upstream list ${id}: ${(e as Error).message}`)
      }
    }
  }
  upstreamCache = out
  return out
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

/** How well a list's header and folder name fit the TV's properties. */
export function matchScore(id: string, header: string[], probe: Pick<Probe, 'model' | 'device' | 'product' | 'manufacturer'>): number {
  const text = (header.slice(0, 4).join(' ') + ' ' + id).toLowerCase()
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
    title: l.title,
    score: probe ? matchScore(l.id, l.parsed.header, probe) : 0
  }))
  all.sort((a, b) => b.score - a.score || a.id.localeCompare(b.id))
  const out: DebloatListSummary[] = [...all]
  // Device-family fallback (#2): when no list matches the TV on model/device (best exact score
  // < 5), infer one from the manufacturer so unlisted models of a known family still start from
  // that family's list instead of the generic one. The entry keeps the base list's id (so
  // selecting it loads the real list) and is clearly marked as inferred.
  const bestExact = all[0]?.score ?? 0
  if (probe && bestExact < 5) {
    const family = familyFor(probe)
    const base = family ? upstreamLists().find((l) => l.id === family.baseListId) : undefined
    if (family && base && !all.some((l) => l.id === base.id && l.score >= 5)) {
      out.push({
        id: base.id,
        title: `Inferred for your ${family.label} device — review each package`,
        score: 0
      })
    }
  }
  return [{ id: 'custom', title: 'My TV is not listed: pick from packages other TVs disable', score: 0 }, ...out]
}

/**
 * Known device families: maps a TV's manufacturer (and model-code prefix) to the upstream list
 * that best represents the family. Real owner reports can promote an inferred match into a
 * dedicated upstream list over time; keep this list small and evidence-based.
 */
const DEVICE_FAMILIES: Array<{ label: string; manufacturers: string[]; modelPrefixes: string[]; baseListId: string }> = [
  { label: 'Xiaomi / Mi TV', manufacturers: ['xiaomi', 'redmi'], modelPrefixes: ['mitv', 'mssp', 'mftr'], baseListId: 'xiaomi-mitv-mssp3' },
  { label: 'TCL', manufacturers: ['tcl', 'thomson'], modelPrefixes: [], baseListId: 'tcl-c755-google-tv' }
]

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
    throw new Error(`${path}: not a usable recovery record (${(e as Error).message}). It is left where it is: move it aside deliberately.`)
  }
  if (record.version !== VERSION || typeof record.packages !== 'object' || !record.packages) {
    throw new Error(`${path}: unknown recovery record format; move it aside deliberately.`)
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
      throw new Error(`The restore record for ${host} was taken from another device (${record.device}); this TV reports ${device}. ` +
        `Nothing was changed. If the TV at this address really is new, move ${path} aside first.`)
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
    notes.push('No recovery record for this TV and list: enabling every listed package and resetting Home and the animation scales to their defaults.')
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
  if (untouched.length) notes.push(`Left as they were before cleanup: ${untouched.join(', ')}`)
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
  if (edited.length) notes.push(`Not in the record, left alone: ${edited.join(', ')}`)
  cmds.push('pm list packages -d')
  return cmds
}

// ---- plan, apply, restore -------------------------------------------------------------------------

// Features people notice when they are gone. Whatever a list says, these are the owner's call: they are
// never ticked by default (upstream's prompt asks about them before anything is disabled).
const FEATURES: Record<string, string> = {
  'com.google.android.tv.remote.service': 'the phone remote (Google TV / Android TV Remote app)',
  'com.google.android.apps.mediashell': 'casting from phones (Chromecast built-in)',
  'com.google.android.katniss': 'voice search and the remote\'s mic button',
  'com.google.android.speech.pumpkin': 'voice search and the remote\'s mic button'
}

/** Why a package is the owner's decision rather than a default, or undefined when it is plain bloat. */
export function choiceFor(e: ListEntry): string | undefined {
  if (FEATURES[e.pkg]) return `Turns off ${FEATURES[e.pkg]}.`
  if (e.section && /^(personal choice|optional)\b/i.test(e.section)) return e.section
  return undefined
}

function sourceList(listId: string): { title: string; parsed: ParsedList } {
  if (listId === 'custom') {
    const entries = [...commonality().entries()]
      .sort((a, b) => b[1].count - a[1].count || a[0].localeCompare(b[0]))
      .map(([pkg, c]) => ({ pkg, note: c.note, suggested: false }))
    return { title: 'Custom list', parsed: { header: ['Custom list made with CleanMyTV from packages other TVs disable.'], entries } }
  }
  const l = upstreamLists().find((u) => u.id === listId)
  if (!l) throw new Error(`no such list: ${listId}`)
  return { title: l.title, parsed: l.parsed }
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
  if (!probe) throw new Error('Connect to a TV first.')
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
  const home = await T.homeComponent()
  return {
    listId,
    title,
    header: parsed.header,
    entries,
    record: record ? { path: rpath, created: record.created } : undefined,
    homePackage: home ? home.split('/')[0] : null
  }
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

export async function apply(req: DebloatApplyRequest): Promise<ApplyResult> {
  const probe = T.getStatus().probe
  if (!probe) throw new Error('Connect to a TV first.')
  for (const p of [...req.disable, ...req.enable]) if (!T.PACKAGE_RE.test(p)) throw new Error(`not a package name: ${p}`)
  const { title, parsed } = sourceList(req.listId)
  const host = T.host()
  const warnings: string[] = []
  let pkgs = [...new Set(req.disable)]

  // A Wi-Fi network saved during the first-run wizard belongs to setupwraith, and Android deletes it the
  // moment setupwraith is disabled: the TV drops off Wi-Fi, and ADB with it.
  if (pkgs.includes(SETUPWRAITH) && !req.forceSetupwraith) {
    let wifi = ''
    try { wifi = await T.sh(TASK, 'dumpsys wifi', { quiet: true }) } catch { wifi = '' }
    const owners = wifi.match(/cname=[^ ]*/g) ?? []
    if (!owners.length) {
      warnings.push(`Skipped ${SETUPWRAITH}: could not check which app owns the saved Wi-Fi networks. Try again, or force it if the TV uses Ethernet.`)
      pkgs = pkgs.filter((p) => p !== SETUPWRAITH)
    } else if (owners.some((o) => o.includes('setupwraith'))) {
      warnings.push(`Skipped ${SETUPWRAITH}: the saved Wi-Fi network belongs to it, and disabling it would delete that network and take the TV off Wi-Fi. On the TV, forget the network and join it again in Settings > Network & Internet, then apply again.`)
      pkgs = pkgs.filter((p) => p !== SETUPWRAITH)
    }
  }

  // Home guard: never leave the TV with nothing that can answer Home.
  const homeAns = await T.homeAnswer()
  const homePkg = homeAns.split('/')[0]
  if (homePkg && pkgs.includes(homePkg) && !req.forceHome) {
    const claims = await T.homeClaimants()
    const others = claims.filter((c) => c !== homePkg && !pkgs.includes(c) && !NOT_LAUNCHERS.includes(c))
    if (!others.length) {
      warnings.push(`Skipped ${homePkg}: it owns Home and nothing else that can answer Home stays enabled. Install CleanMyTV Home first (Home screen step), then apply again.`)
      pkgs = pkgs.filter((p) => p !== homePkg)
    }
  }

  const adbSecure = probe.adbSecure

  // The saved list holds everything this app has chosen to disable on this TV so far, minus what
  // the user switched back on; the record is written from it before the first command.
  const file = savedListPath(req.listId, probe)
  const active = [...new Set([...savedActive(file), ...req.disable])].filter((p) => !req.enable.includes(p))
  writeSavedList(file, title, parsed.header, active, parsed.entries)
  log(TASK, 'info', `list saved to ${file}`)
  await saveRecord(file, host, active, probe.device || undefined)

  let done = 0
  for (const p of pkgs) {
    const out = await T.sh(TASK, `pm disable-user --user 0 ${p}`)
    if (/disabled/.test(out)) done++
    else warnings.push(`${p}: ${out.trim() || 'no answer'}`)
  }
  let enabled = 0
  for (const p of req.enable) {
    const out = await T.sh(TASK, `pm enable ${p}`)
    if (/enabled/.test(out)) enabled++
    else warnings.push(`${p}: ${out.trim() || 'no answer'}`)
  }
  if (adbSecure === '0') {
    warnings.push('This TV reports ro.adb.secure=0: network debugging accepts any device on your network without the Allow prompt. Switch debugging off when you are done (Finish step).')
  }
  const summary = `Disabled ${done} of ${pkgs.length} package${pkgs.length === 1 ? '' : 's'}` + (req.enable.length ? `, switched ${enabled} back on.` : '.')
  log(TASK, 'ok', summary)
  return { ok: done === pkgs.length && enabled === req.enable.length, summary, warnings }
}

async function stockFallback(): Promise<string> {
  const { installed } = await T.packageSets()
  return KNOWN_STOCK_LAUNCHERS.find((p) => installed.has(p)) ?? 'com.google.android.tvlauncher'
}

export async function restore(listId: string): Promise<ApplyResult> {
  const probe = T.getStatus().probe
  if (!probe) throw new Error('Connect to a TV first.')
  const file = savedListPath(listId, probe)
  const { record } = findRecord(file, probe.host, probe.device || undefined)
  const listPkgs = existsSync(file) ? savedActive(file) : sourceList(listId).parsed.entries.filter((e) => e.suggested).map((e) => e.pkg)
  const notes: string[] = []
  const cmds = restoreCommands(listPkgs, record, await stockFallback(), notes)
  for (const n of notes) log('restore', 'info', n)
  await T.batch('restore', cmds)
  const home = await T.homeComponent()
  const summary = record
    ? `Restored the state recorded on ${record.created}. Home is ${home ?? 'not set'}.`
    : `Enabled every package in the list. Home is ${home ?? 'not set'}.`
  log('restore', 'ok', summary)
  return { ok: true, summary, warnings: notes }
}
