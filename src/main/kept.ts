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

/** The launcher kept on for the connected TV, if any. */
export function keptLauncher(): string | undefined {
  const k = tvKey()
  const pkg = k ? all()[k] : undefined
  return pkg && T.PACKAGE_RE.test(pkg) ? pkg : undefined
}

/** Remembers `pkg` as kept on for the connected TV, or forgets it with null. */
export function keepLauncher(pkg: string | null): void {
  const k = tvKey()
  if (!k) return
  const { [k]: _old, ...rest } = all()
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(file(), JSON.stringify(pkg ? { ...rest, [k]: pkg } : rest, null, 2))
}
