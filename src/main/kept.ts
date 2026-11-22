// The launcher an owner keeps on beside CleanMyTV Home, per TV: Google TV's, when apps need it (#99, #88).
// Clean up and a reinstall read it, so neither switches that launcher off again.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import * as T from './tv'
import { paths } from './paths'
import { isSymptom, type SymptomId } from '@shared/symptoms'

const file = (): string => join(paths.data, 'kept-launchers.json')

function tvKey(): string {
  const st = T.getStatus()
  return st.probe?.device || st.host || ''
}

function all(): Record<string, string> {
  try {
    return existsSync(file()) ? (JSON.parse(readFileSync(file(), 'utf8')) as Record<string, string>) : {}
  } catch {
    return {}
  }
}

// TVs that must keep Google TV's launcher on: switching it off brings the system down, and the Home key opens
// it whatever the Home setting says. CleanMyTV Home comes back in front of it instead, as on Fire TV. The
// Strong LEAP-NOVA stick (device CXR) restarts with system_server crashed (#132).
const MUST_KEEP: { device: string; manufacturer: string; pkg: string }[] = [
  { device: 'CXR', manufacturer: 'SkyworthDigital', pkg: 'com.google.android.apps.tv.launcherx' }
]

/** The launcher the connected TV can't do without, if it's one of those. */
export function mustKeep(): string | undefined {
  const p = T.getStatus().probe
  return p ? MUST_KEEP.find((m) => m.device === p.device && m.manufacturer === p.manufacturer)?.pkg : undefined
}

/** The launcher kept on for the connected TV, if any: by the owner's choice, or because the TV needs it. */
export function keptLauncher(): string | undefined {
  const k = tvKey()
  const pkg = k ? all()[k] : undefined
  return mustKeep() ?? (pkg && T.PACKAGE_RE.test(pkg) ? pkg : undefined)
}

/** Remembers `pkg` as kept on for the connected TV, or forgets it with null. */
export function keepLauncher(pkg: string | null): void {
  const k = tvKey()
  if (!k) return
  const { [k]: _old, ...rest } = all()
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(file(), JSON.stringify(pkg ? { ...rest, [k]: pkg } : rest, null, 2))
}

// Packages "Something broke" turned back on, per TV, with the symptom: Clean up leaves them unticked from then
// on, so the next run doesn't switch them off again without the owner seeing it. Ticking one by hand and
// applying forgets it.
const turnedFile = (): string => join(paths.data, 'turned-back-on.json')

function allTurned(): Record<string, Record<string, SymptomId>> {
  try {
    return existsSync(turnedFile()) ? (JSON.parse(readFileSync(turnedFile(), 'utf8')) as Record<string, Record<string, SymptomId>>) : {}
  } catch {
    return {}
  }
}

/** The packages turned back on for the connected TV, and what for. */
export function turnedOn(): Record<string, SymptomId> {
  const k = tvKey()
  const mine = k ? allTurned()[k] ?? {} : {}
  return Object.fromEntries(Object.entries(mine).filter(([p, s]) => T.PACKAGE_RE.test(p) && isSymptom(s)))
}

/** Remembers `pkgs` as turned back on for `symptom` on the connected TV, or forgets them with null. */
export function rememberTurnedOn(pkgs: string[], symptom: SymptomId | null): void {
  const k = tvKey()
  if (!k || !pkgs.length) return
  const all = allTurned()
  const mine = { ...all[k] }
  if (!symptom && !pkgs.some((p) => p in mine)) return
  for (const p of pkgs) {
    if (symptom) mine[p] = symptom
    else delete mine[p]
  }
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(turnedFile(), JSON.stringify({ ...all, [k]: mine }, null, 2))
}
