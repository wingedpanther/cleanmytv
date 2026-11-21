// Back up the TV: what Clean up switched off and CleanMyTV Home's settings, in one JSON file, to put back
// after a reset or on another TV. Restoring goes through Clean up's own apply and Home's own settings call,
// so every guard they have still holds. The PIN never leaves the TV, and wallpapers and themes stay out.
import { readFileSync, statSync } from 'node:fs'
import { basename } from 'node:path'
import * as T from './tv'
import * as debloat from './debloat'
import * as home from './home'
import { HOME_PKG } from './launcher'
import { log } from './log'
import { t, UiError } from './i18n'
import type { ApplyResult, BackupPreview, HomeConfig } from '@shared/api'

const KIND = 'cleanmytv-tv-backup'
const VERSION = 1
// The settings and a list or two are a few KB; anything this big is something else.
const MAX_BYTES = 1024 * 1024
const LIST_ID = /^[a-z0-9][a-z0-9-]{0,80}$/

export interface TvBackup {
  kind: typeof KIND
  version: number
  saved: string
  app: string
  tv: { manufacturer: string; model: string; device: string; android: string }
  /** Per list, the packages Clean up keeps switched off with it. */
  cleanup: { list: string; switched_off: string[] }[]
  home?: Partial<HomeConfig>
}

/** The connected TV's backup. */
export async function make(appVersion: string): Promise<TvBackup> {
  const p = T.getStatus().probe
  if (!p) throw new UiError('Connect to a TV first.')
  const { installed } = await T.packageSets()
  const cfg = installed.has(HOME_PKG) ? await home.readConfig().catch(() => null) : null
  return {
    kind: KIND,
    version: VERSION,
    saved: new Date().toISOString(),
    app: appVersion,
    tv: { manufacturer: p.manufacturer, model: p.model, device: p.device, android: p.release },
    cleanup: debloat.appliedLists().map((l) => ({ list: l.listId, switched_off: l.active })),
    ...(cfg ? { home: home.portable(cfg as unknown as Record<string, unknown>) } : {})
  }
}

/** A backup file read back, checked: package names only, known list ids, Home settings through home.portable. */
export function parseBackup(text: string): TvBackup {
  let o: Record<string, unknown>
  try {
    o = JSON.parse(text.replace(/^﻿/, '')) as Record<string, unknown>
  } catch {
    throw new UiError('This is not a CleanMyTV backup.')
  }
  if (!o || typeof o !== 'object' || o.kind !== KIND) throw new UiError('This is not a CleanMyTV backup.')
  if (o.version !== VERSION) throw new UiError('This backup is from a newer CleanMyTV. Update the app, then try again.')
  const tv = (o.tv && typeof o.tv === 'object' ? o.tv : {}) as Record<string, unknown>
  const str = (v: unknown): string => (typeof v === 'string' ? v.slice(0, 100) : '')
  const cleanup = (Array.isArray(o.cleanup) ? o.cleanup : []).flatMap((c) => {
    const x = (c && typeof c === 'object' ? c : {}) as Record<string, unknown>
    if (typeof x.list !== 'string' || !LIST_ID.test(x.list) || !Array.isArray(x.switched_off)) return []
    const pkgs = x.switched_off.filter((p): p is string => typeof p === 'string' && T.PACKAGE_RE.test(p))
    return [{ list: x.list, switched_off: [...new Set(pkgs)].slice(0, 2000) }]
  })
  const cfg = o.home && typeof o.home === 'object' && !Array.isArray(o.home) ? home.portable(o.home as Record<string, unknown>) : null
  return {
    kind: KIND,
    version: VERSION,
    saved: str(o.saved),
    app: str(o.app),
    tv: { manufacturer: str(tv.manufacturer), model: str(tv.model), device: str(tv.device), android: str(tv.android) },
    cleanup,
    ...(cfg && Object.keys(cfg).length ? { home: cfg } : {})
  }
}

/** The backup opened last, which Restore puts on the TV: the window only ever sees the preview. */
let pending: { file: string; backup: TvBackup } | null = null

export async function open(file: string): Promise<BackupPreview> {
  if (statSync(file).size > MAX_BYTES) throw new UiError('This is not a CleanMyTV backup.')
  const backup = parseBackup(readFileSync(file, 'utf8'))
  const p = T.getStatus().probe
  if (!p) throw new UiError('Connect to a TV first.')
  pending = { file, backup }
  const { installed, disabled } = await T.packageSets()
  const all = [...new Set(backup.cleanup.flatMap((c) => c.switched_off))]
  return {
    file: basename(file),
    savedAt: backup.saved,
    tv: `${backup.tv.manufacturer} ${backup.tv.model}`.trim(),
    otherModel: backup.tv.model !== p.model || backup.tv.device !== p.device,
    switchOff: all.filter((pkg) => installed.has(pkg) && !disabled.has(pkg)),
    missing: all.filter((pkg) => !installed.has(pkg)),
    home: !backup.home ? 'none' : installed.has(HOME_PKG) ? 'apply' : 'not-installed'
  }
}

/** Puts the opened backup on the TV: each list through Clean up's apply, then Home's settings. */
export async function restore(): Promise<ApplyResult> {
  if (!pending) throw new UiError('Open a backup first.')
  const { file, backup } = pending
  const { installed } = await T.packageSets()
  // A list this copy of the app doesn't have (renamed, or from a newer app) goes in the custom list.
  const known = new Set(debloat.lists(undefined).map((l) => l.id))
  const byList = new Map<string, string[]>()
  for (const c of backup.cleanup) {
    const id = known.has(c.list) ? c.list : 'custom'
    byList.set(id, [...new Set([...(byList.get(id) ?? []), ...c.switched_off.filter((p) => installed.has(p))])])
  }
  const said: string[] = []
  const warnings: string[] = []
  let ok = true
  for (const [listId, disable] of byList) {
    if (!disable.length) continue
    const r = await debloat.apply({ listId, disable, enable: [] })
    ok &&= r.ok
    said.push(r.summary)
    warnings.push(...r.warnings)
  }
  if (backup.home) {
    if (installed.has(HOME_PKG)) {
      const noPin = await home.putSettings(backup.home as Record<string, unknown>)
      said.push(t("CleanMyTV Home's settings are back."))
      if (noPin) warnings.push(t('The app lock stays off until you set a PIN in the Home screen step.'))
    } else {
      warnings.push(t('CleanMyTV Home is not on this TV, so its settings were left out. Install it, then restore again.'))
    }
  }
  log('backup', ok ? 'ok' : 'warn', `restored ${basename(file)}: ${byList.size} lists${backup.home ? ' and Home settings' : ''}`)
  return { ok, summary: [t('Restored the backup.'), ...said].join(' '), warnings }
}
