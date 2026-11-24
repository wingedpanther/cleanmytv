// The details a bug report needs, for the activity drawer's copy button. No address, serial or settings.
// The package lists are what a new TV list is made from. Send a report adds the rest an issue needs, as a file.
import { release } from 'node:os'
import * as T from './tv'
import * as debloat from './debloat'
import * as home from './home'
import { HOME_PKG } from './launcher'
import { sessionLog } from './log'
import { lastLines, scrub } from './record'

export async function bugReport(appVersion: string): Promise<string> {
  const out = [`CleanMyTV ${appVersion}, ${process.platform} ${release()} ${process.arch}`]
  const p = T.getStatus().probe
  if (!p) return [...out, 'TV: not connected'].join('\n')
  out.push(`TV: ${p.manufacturer} ${p.model} (device ${p.device}, product ${p.product}), Android ${p.release} (SDK ${p.sdk}), build ${p.build}`)
  const best = debloat.lists(p).find((l) => l.score > 0)
  out.push(`Matching list: ${best ? `${best.id} (score ${best.score})` : 'none'}`)
  out.push(`Home: ${(await T.homeComponent().catch(() => null)) ?? 'unknown'}`)
  const claims = await T.homeClaimants().catch(() => [] as string[])
  out.push(`Apps that answer Home: ${claims.join(', ') || 'none'}`)
  const h = await home.status().catch(() => null)
  out.push(`CleanMyTV Home: ${!h ? 'unknown' : h.installed ? `${h.versionName ?? '?'} installed${h.isHome ? ', is Home' : ''}` : 'not installed'}` +
    (h?.bundledVersion ? `, this app carries ${h.bundledVersion}` : ''))
  out.push(`Keyboard: ${(await T.sh('report', 'settings get secure default_input_method', { quiet: true }).catch(() => '')).trim() || 'unknown'}`)
  const sets = await T.packageSets().catch(() => null)
  if (sets) {
    const off = [...sets.disabled].sort()
    const on = [...sets.installed].filter((p) => !sets.disabled.has(p)).sort()
    out.push('', `Switched off (${off.length}):`, ...(off.length ? off : ['none']), '', `On (${on.length}):`, ...on)
  }
  return out.join('\n')
}

/** The TV's log lines a report carries: enough for the minutes before the problem. */
export const LOGCAT_LINES = 3000

// The PIN never leaves the TV, but a launcher that one day reports a secret must not put it in a report.
const SECRET = /(^|_)(pin|password|passcode|secret|token)(_|$)/i
// Where the owner lives and what they call themselves.
const PRIVATE = ['greeting', 'weather_place', 'weather_lat', 'weather_lon', 'wifi_name']

/** CleanMyTV Home's settings for a report: secrets left out, the greeting and home location hidden. */
export function reportConfig(cfg: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(cfg)
    .filter(([k, v]) => !SECRET.test(k) || typeof v === 'boolean')
    .map(([k, v]) => [k, PRIVATE.includes(k) && v !== '' && v !== 0 ? '(hidden)' : v]))
}

/** The packages `pm list packages -u` adds to the plain list: uninstalled for user 0, still on the system. */
export function uninstalledForUser(all: string, installed: string): string[] {
  const names = (out: string): string[] => out.split('\n').filter((l) => l.startsWith('package:')).map((l) => l.slice(8).trim())
  const have = new Set(names(installed))
  return names(all).filter((p) => !have.has(p)).sort()
}

/**
 * Send a report: everything an issue needs in one file, with addresses, MAC and email addresses taken out.
 * Without a TV it holds the computer's part and says so.
 */
export async function fullReport(appVersion: string): Promise<string> {
  const out = [`CleanMyTV report, ${new Date().toISOString()}`, '', await bugReport(appVersion)]
  const p = T.getStatus().probe
  const sh = (cmd: string, timeoutMs?: number): Promise<string> => T.sh('report', cmd, { quiet: true, timeoutMs }).catch((e: Error) => `(${e.message})`)
  if (!p) out.push('', 'No TV was connected, so this report has the computer\'s part only. Connect the TV and save the report again for the rest.')
  else {
    out.push(`Build fingerprint: ${(await sh('getprop ro.build.fingerprint')).trim() || 'unknown'}`)
    const gone = uninstalledForUser(await sh('pm list packages -u'), await sh('pm list packages'))
    out.push('', `Uninstalled for user 0 (${gone.length}):`, ...(gone.length ? gone : ['none']))
    out.push('', 'What CleanMyTV keeps switched off, by list:')
    const lists = debloat.appliedLists()
    if (!lists.length) out.push('nothing: no clean-up on this TV from this computer')
    for (const l of lists) out.push(`${l.listId} (${l.active.length}): ${l.active.join(', ') || 'none'}`)
    const back = await debloat.switchedOff().then((s) => s.back.flatMap((b) => b.packages), () => [])
    if (back.length) out.push(`On again since: ${back.join(', ')}`)
    const version = /versionName=(\S+)/.exec(await sh(`dumpsys package ${HOME_PKG} | grep -m1 versionName`))?.[1]
    out.push('', `CleanMyTV Home: ${version ?? 'not installed'}`)
    if (version) {
      const got = /config=(\{.*\})\}\]/.exec(await sh(`content call --uri content://${HOME_PKG}.config --method get`))
      let cfg = '(no answer)'
      try { if (got) cfg = JSON.stringify(reportConfig(JSON.parse(got[1]) as Record<string, unknown>), null, 1) } catch { cfg = '(not readable)' }
      out.push(`Settings: ${cfg}`)
    }
    out.push('', 'Focused window:', (await sh('dumpsys window | grep -E "mCurrentFocus|mFocusedApp"')).trimEnd() || 'unknown')
  }
  out.push('', 'This session\'s activity:', sessionLog() || '(empty)')
  if (p) {
    const logcat = await sh(`logcat -d -v threadtime -t ${LOGCAT_LINES}`, 60_000)
    out.push('', `TV log, the last ${LOGCAT_LINES} lines:`, lastLines(logcat.trimEnd(), LOGCAT_LINES + 10))
  }
  return scrub(out.join('\n') + '\n')
}
