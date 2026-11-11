// TV settings outside the launcher: ad-blocking Private DNS, animation speed, storage, the remote, and a
// screenshot of the TV.
import { Socket } from 'node:net'
import * as T from './tv'
import type { ApplyResult, SystemState } from '@shared/api'

const TASK = 'system'

export async function state(): Promise<SystemState> {
  const get = async (ns: string, key: string): Promise<string> => (await T.tv().shell(`settings get ${ns} ${key}`)).trim()
  return {
    sdk: T.getStatus().probe?.sdk ?? 0,
    privateDnsMode: await get('global', 'private_dns_mode'),
    privateDnsHost: await get('global', 'private_dns_specifier'),
    animationScale: await get('global', 'window_animation_scale'),
    storage: await T.storage(TASK)
  }
}

/** Clears every app's cache and says how much space that gave back. */
export async function clearCaches(): Promise<ApplyResult> {
  const before = await T.storage(TASK)
  await T.trimCaches(TASK)
  const after = await T.storage(TASK)
  if (!before || !after) return { ok: true, summary: "Cleared every app's cache.", warnings: [] }
  const mb = (kb: number): number => Math.round(kb / 1024)
  const freed = mb(Math.max(0, after.freeKb - before.freeKb))
  return {
    ok: true,
    summary: `Cleared every app's cache: ${freed} MB back, ${mb(after.freeKb)} MB free now.` + (freed ? ' Apps rebuild some of it as you use them.' : ''),
    warnings: []
  }
}

export async function privateDns(host: string | null): Promise<ApplyResult> {
  if (!host) {
    await T.sh(TASK, 'settings put global private_dns_mode opportunistic')
    return { ok: true, summary: 'Private DNS is back to automatic.', warnings: [] }
  }
  T.q(host)
  if (!/^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(host)) throw new Error(`not a DNS host name: ${host}`)
  if ((T.getStatus().probe?.sdk ?? 0) < 28) throw new Error('Private DNS needs Android 9 or newer.')
  await T.sh(TASK, `settings put global private_dns_specifier ${host}`)
  await T.sh(TASK, 'settings put global private_dns_mode hostname')
  return {
    ok: true,
    summary: `Every app's DNS now goes to ${host}.`,
    warnings: ['If an app stops playing or the TV says "No internet", switch Private DNS off here.']
  }
}

/** Google TV's Ambient screensaver (Google Photos, art, the weather), and the backdrop app it draws with. */
export const AMBIENT = ['com.google.android.apps.tv.dreamx', 'com.google.android.backdrop'] as const

/**
 * Brings back Google's screensaver (#57): switches its two apps back on, and makes it the screensaver again.
 * Clean up's check then leaves them alone, because the screensaver in use is never "came back on".
 */
export async function fixScreensaver(): Promise<ApplyResult> {
  const { installed, disabled } = await T.packageSets()
  const have = AMBIENT.filter((p) => installed.has(p))
  if (!have.length) return { ok: false, summary: "This TV doesn't have Google's screensaver apps, so there's nothing to bring back.", warnings: [] }
  const warnings: string[] = []
  let on = 0
  let failed = 0
  for (const p of have.filter((p) => disabled.has(p))) {
    const out = await T.sh(TASK, `pm enable ${p}`)
    if (/enabled/.test(out)) on++
    else { failed++; warnings.push(`${p}: ${out.trim() || 'no answer'}`) }
  }
  // The real component from the TV, not a guess: dreamx's first, then the older backdrop app's.
  const dreams = (await T.sh(TASK, 'cmd package query-services --brief -a android.service.dreams.DreamService'))
    .split('\n').map((l) => l.trim()).filter((l) => /^[\w.]+\/[\w.$]+$/.test(l))
  const pick = have.map((p) => dreams.find((d) => d.startsWith(p + '/'))).find(Boolean)
  if (!pick) {
    warnings.push("Couldn't find Google's screensaver among the TV's screensavers. Pick it in the TV's Settings → System → Ambient mode.")
  } else {
    T.q(pick)
    await T.sh(TASK, `settings put secure screensaver_components ${pick}`)
    await T.sh(TASK, 'settings put secure screensaver_enabled 1')
  }
  warnings.push('Choose your albums in the TV\'s Settings → System → Ambient mode, or in the Google Home app → your TV → Ambient mode. On a Chromecast with Google TV the Photos choice can reset after a restart while CleanMyTV Home is Home; pick the albums again if it does.')
  return {
    ok: !!pick && failed === 0,
    summary: `Google's screensaver is back${on ? `: ${on === 1 ? '1 app' : `${on} apps`} switched on again` : ''}${pick ? ', and it is the screensaver now.' : '.'}`,
    warnings
  }
}

export async function animationScale(scale: number): Promise<ApplyResult> {
  if (![0, 0.5, 1].includes(scale)) throw new Error('scale must be 0, 0.5 or 1')
  for (const k of ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']) {
    await T.sh(TASK, `settings put global ${k} ${scale}`)
  }
  return { ok: true, summary: scale === 0 ? 'System animations are off.' : `System animations run at ${scale}x.`, warnings: [] }
}

export async function key(code: number): Promise<void> {
  if (!Number.isInteger(code) || code < 0 || code > 400) throw new Error('bad keycode')
  await T.tv().shell(`input keyevent ${code}`)
}

/** The last snapshot taken, so Save writes what the app showed and nothing the page hands back. */
let lastShot: Buffer | null = null

export async function screenshot(): Promise<string> {
  const f = '/data/local/tmp/cytv-shot.png'
  await T.tv().shell(`screencap -p ${f}`, 30000)
  const png = await T.pullB64(f)
  await T.tv().shell(`rm -f ${f}`)
  if (!png || png.length < 100) throw new Error('The TV returned an empty screenshot. Live TV, HDMI and some streaming apps block capture: press Home and try again.')
  lastShot = png
  return `data:image/png;base64,${png.toString('base64')}`
}

/** The last snapshot as PNG bytes, or null before the first one (#92). */
export function lastScreenshot(): Buffer | null {
  return lastShot
}

/** A file name like "TV 2026-11-11 10.42.png", in local time. */
export function shotName(d = new Date()): string {
  const p = (n: number): string => String(n).padStart(2, '0')
  return `TV ${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}.${p(d.getMinutes())}.png`
}

export async function reboot(): Promise<void> {
  await T.tv().shell('reboot').catch(() => undefined)
}

/** Upstream tools/adb-closed.py: refused or silent means closed. */
export function adbClosed(host: string): Promise<{ closed: boolean; message: string }> {
  return new Promise((resolve) => {
    const s = new Socket()
    const done = (closed: boolean, message: string): void => { s.destroy(); resolve({ closed, message }) }
    s.setTimeout(3000)
    s.once('connect', () => done(false, `${host}:5555 is still open: network debugging is on. On the TV, switch USB debugging and Network/ADB debugging off, reboot, and check again.`))
    s.once('timeout', () => done(true, `${host}:5555 did not answer in 3 s: closed, unless the TV is off or at another address.`))
    s.once('error', (e: NodeJS.ErrnoException) => {
      if (e.code === 'ECONNREFUSED') done(true, `${host}:5555 is closed: the TV refused the connection.`)
      else done(false, `Could not check ${host}:5555 (${e.code ?? e.message}). Is the TV on and on this network?`)
    })
    s.connect(5555, host)
  })
}
