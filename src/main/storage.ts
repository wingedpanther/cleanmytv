// What takes the space on the TV. Android measures every app's code, data and cache about once a day and
// `dumpsys diskstats` prints the last measurement; a TV that hasn't made one yet gets the code size of the
// apps outside its system image instead, which shell can read.
import * as T from './tv'
import { log } from './log'
import { t, UiError } from './i18n'
import type { AppSize, AppSizes, ApplyResult, PackageState } from '@shared/api'

const TASK = 'storage'

/** One app's sizes from diskstats, in bytes. */
export interface Measured {
  pkg: string
  app: number
  data: number
  cache: number
}

/** A "Name: [ ... ]" line of the dump as a JSON array, or null when it's missing or garbled. */
function arrayAfter(dump: string, name: string): unknown[] | null {
  const m = new RegExp(`^${name}: (\\[.*\\])\\s*$`, 'm').exec(dump)
  if (!m) return null
  try {
    const v = JSON.parse(m[1]) as unknown
    return Array.isArray(v) ? v : null
  } catch {
    return null
  }
}

/** The per-app sizes in `dumpsys diskstats`; null when the TV hasn't measured its apps yet. */
export function parseDiskstats(dump: string): Measured[] | null {
  const names = arrayAfter(dump, 'Package Names')
  const apps = arrayAfter(dump, 'App Sizes')
  if (!names || !apps || names.length !== apps.length) return null
  // Older Android has no data sizes; a list of the wrong length is no use either.
  const fit = (a: unknown[] | null): unknown[] => (a && a.length === names.length ? a : [])
  const data = fit(arrayAfter(dump, 'App Data Sizes'))
  const cache = fit(arrayAfter(dump, 'Cache Sizes'))
  const n = (v: unknown): number => (typeof v === 'number' && Number.isFinite(v) && v > 0 ? v : 0)
  const out = new Map<string, Measured>()
  names.forEach((name, i) => {
    if (typeof name !== 'string' || !T.PACKAGE_RE.test(name)) return
    // A package can be listed once per user: they add up.
    const cur = out.get(name) ?? { pkg: name, app: 0, data: 0, cache: 0 }
    out.set(name, { pkg: name, app: Math.max(cur.app, n(apps[i])), data: cur.data + n(data[i]), cache: cur.cache + n(cache[i]) })
  })
  return out.size ? [...out.values()] : null
}

/** System apps whose code lives in /data/app: they carry an update, from `pm list packages -f -s`. */
export function updatedSystemApps(list: string): Set<string> {
  const out = new Set<string>()
  for (const line of list.split('\n')) {
    const m = /^package:\/data\/app\/\S+\.apk=(\S+)\s*$/.exec(line.trim())
    if (m && T.PACKAGE_RE.test(m[1])) out.add(m[1])
  }
  return out
}

/** "<KB> <package>" lines from the du loop in codeSizes. */
export function parseCodeSizes(out: string): Map<string, number> {
  const sizes = new Map<string, number>()
  for (const line of out.split('\n')) {
    const m = /^(\d+)\s+(\S+)$/.exec(line.trim())
    if (m && T.PACKAGE_RE.test(m[2])) sizes.set(m[2], (sizes.get(m[2]) ?? 0) + Number(m[1]) * 1024)
  }
  return sizes
}

// Each app's folder under /data/app, measured on the TV in one go. du can't read the compiled code folder,
// and says so on stderr; the rest still adds up.
const CODE_SIZES = "pm list packages -f | sed -n 's|^package:\\(/data/app/.*\\)/[^/]*\\.apk=\\(.*\\)$|\\1 \\2|p' | while read d p; do echo \"$(du -sk \"$d\" 2>/dev/null | cut -f1) $p\"; done"

/** Free space and what each app takes, biggest first. */
export async function sizes(): Promise<AppSizes> {
  const { installed, disabled } = await T.packageSets()
  const updated = updatedSystemApps(await T.sh(TASK, 'pm list packages -f -s', { quiet: true }))
  const state = (p: string): PackageState => (!installed.has(p) ? 'absent' : disabled.has(p) ? 'disabled' : 'enabled')
  const measured = parseDiskstats(await T.sh(TASK, 'dumpsys diskstats', { quiet: true, timeoutMs: 30_000 }).catch(() => ''))
  let apps: AppSize[]
  let source: AppSizes['source']
  if (measured) {
    source = 'diskstats'
    apps = measured.filter((m) => installed.has(m.pkg)).map((m) => ({ ...m, state: state(m.pkg), updated: updated.has(m.pkg) }))
  } else {
    source = 'code'
    const code = parseCodeSizes(await T.sh(TASK, CODE_SIZES, { quiet: true, timeoutMs: 60_000 }).catch(() => ''))
    apps = [...code].filter(([p]) => installed.has(p)).map(([pkg, app]) => ({ pkg, app, state: state(pkg), updated: updated.has(pkg) }))
  }
  const total = (a: AppSize): number => a.app + (a.data ?? 0) + (a.cache ?? 0)
  apps.sort((a, b) => total(b) - total(a) || a.pkg.localeCompare(b.pkg))
  return { storage: await T.storage(TASK), apps, source }
}

/**
 * Takes the Play Store update off a switched-off system app. The version the TV came with stays, still
 * switched off; Android deletes the app's data with the update, which is why the window asks first.
 * Switching it on again lets the Play Store update it again.
 */
export async function removeUpdate(pkg: string): Promise<ApplyResult> {
  if (!T.PACKAGE_RE.test(pkg)) throw new UiError('not a package name: {pkg}', { pkg })
  const { disabled } = await T.packageSets()
  if (!disabled.has(pkg)) throw new UiError('{pkg} is on. Only apps that are switched off can lose their update here.', { pkg })
  // Only a system app with its code in /data/app: on anything else, pm uninstall would remove the whole app.
  if (!updatedSystemApps(await T.sh(TASK, 'pm list packages -f -s', { quiet: true })).has(pkg)) {
    throw new UiError('{pkg} has no update to remove.', { pkg })
  }
  const before = await T.storage(TASK)
  // Android 10 and later have a command for it; Android 9 takes the update off a system app with uninstall.
  let out = await T.sh(TASK, `pm uninstall-system-updates ${pkg}`)
  if (/Unknown command/i.test(out)) out = await T.sh(TASK, `pm uninstall ${pkg}`)
  if (!/Success/.test(out)) return { ok: false, summary: t("The TV didn't remove the update to {pkg}.", { pkg }), warnings: out.trim() ? [out.trim()] : [] }
  // The version left behind should still be off; if the TV switched it on, it goes off again.
  const now = await T.packageSets()
  if (now.installed.has(pkg) && !now.disabled.has(pkg)) await T.sh(TASK, `pm disable-user --user 0 ${pkg}`)
  const after = await T.storage(TASK)
  const freed = before && after ? Math.max(0, Math.round((after.freeKb - before.freeKb) / 1024)) : 0
  log(TASK, 'ok', `removed the update to ${pkg}, ${freed} MB back`)
  return {
    ok: true,
    summary: freed
      ? t('Removed the update to {pkg}: {freed} MB back. It stays switched off.', { pkg, freed })
      : t('Removed the update to {pkg}. It stays switched off.', { pkg }),
    warnings: []
  }
}
