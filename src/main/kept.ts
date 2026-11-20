// The launcher an owner keeps on beside CleanMyTV Home, per TV: Google TV's, when apps need it (#99, #88).
// Clean up and a reinstall read it, so neither switches that launcher off again.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import * as T from './tv'
import { paths } from './paths'

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
