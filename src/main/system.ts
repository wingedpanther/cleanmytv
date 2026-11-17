// TV settings outside the launcher: ad-blocking Private DNS, matching the video's frame rate, animation speed,
// storage, the remote, and a screenshot of the TV.
import { Socket } from 'node:net'
import * as T from './tv'
import type { ApplyResult, SystemState } from '@shared/api'
import { t, UiError } from './i18n'

const TASK = 'system'
/** Android 12 brought match_content_frame_rate. */
const FRAME_RATE_SDK = 31

export async function state(): Promise<SystemState> {
  const get = async (ns: string, key: string): Promise<string> => (await T.tv().shell(`settings get ${ns} ${key}`)).trim()
  const sdk = T.getStatus().probe?.sdk ?? 0
  return {
    sdk,
    privateDnsMode: await get('global', 'private_dns_mode'),
    privateDnsHost: await get('global', 'private_dns_specifier'),
    animationScale: await get('global', 'window_animation_scale'),
    matchFrameRate: sdk >= FRAME_RATE_SDK ? await get('secure', 'match_content_frame_rate') : '',
    storage: await T.storage(TASK)
  }
}

/** Clears every app's cache and says how much space that gave back. */
export async function clearCaches(): Promise<ApplyResult> {
  const before = await T.storage(TASK)
  await T.trimCaches(TASK)
  const after = await T.storage(TASK)
  if (!before || !after) return { ok: true, summary: t("Cleared every app's cache."), warnings: [] }
  const mb = (kb: number): number => Math.round(kb / 1024)
  const freed = mb(Math.max(0, after.freeKb - before.freeKb))
  return {
    ok: true,
    summary: freed
      ? t("Cleared every app's cache: {freed} MB back, {free} MB free now. Apps rebuild some of it as you use them.", { freed, free: mb(after.freeKb) })
      : t("Cleared every app's cache: {freed} MB back, {free} MB free now.", { freed, free: mb(after.freeKb) }),
    warnings: []
  }
}

export async function privateDns(host: string | null): Promise<ApplyResult> {
  if (!host) {
    await T.sh(TASK, 'settings put global private_dns_mode opportunistic')
    return { ok: true, summary: t('Private DNS is back to automatic.'), warnings: [] }
  }
  T.q(host)
  if (!/^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(host)) throw new UiError('not a DNS host name: {host}', { host })
  if ((T.getStatus().probe?.sdk ?? 0) < 28) throw new UiError('Private DNS needs Android 9 or newer.')
  await T.sh(TASK, `settings put global private_dns_specifier ${host}`)
  await T.sh(TASK, 'settings put global private_dns_mode hostname')
  return {
    ok: true,
    summary: t("Every app's DNS now goes to {host}.", { host }),
    warnings: [t('If an app stops playing or the TV says "No internet", switch Private DNS off here.')]
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
  if (!have.length) return { ok: false, summary: t("This TV doesn't have Google's screensaver apps, so there's nothing to bring back."), warnings: [] }
  const warnings: string[] = []
  let on = 0
  let failed = 0
  for (const p of have.filter((p) => disabled.has(p))) {
    const out = await T.sh(TASK, `pm enable ${p}`)
    if (/enabled/.test(out)) on++
    else { failed++; warnings.push(`${p}: ${out.trim() || t('no answer')}`) }
  }
  // The real component from the TV, not a guess: dreamx's first, then the older backdrop app's.
  const dreams = (await T.sh(TASK, 'cmd package query-services --brief -a android.service.dreams.DreamService'))
    .split('\n').map((l) => l.trim()).filter((l) => /^[\w.]+\/[\w.$]+$/.test(l))
  const pick = have.map((p) => dreams.find((d) => d.startsWith(p + '/'))).find(Boolean)
  if (!pick) {
    warnings.push(t("Couldn't find Google's screensaver among the TV's screensavers. Pick it in the TV's Settings → System → Ambient mode."))
  } else {
    T.q(pick)
    await T.sh(TASK, `settings put secure screensaver_components ${pick}`)
    await T.sh(TASK, 'settings put secure screensaver_enabled 1')
  }
  warnings.push(t("Choose your albums in the TV's Settings → System → Ambient mode, or in the Google Home app → your TV → Ambient mode. On a Chromecast with Google TV the Photos choice can reset after a restart while CleanMyTV Home is Home; pick the albums again if it does."))
  return {
    ok: !!pick && failed === 0,
    summary: on
      ? pick
        ? t("Google's screensaver is back: {n} {n|app|apps} switched on again, and it is the screensaver now.", { n: on })
        : t("Google's screensaver is back: {n} {n|app|apps} switched on again.", { n: on })
      : pick
        ? t("Google's screensaver is back, and it is the screensaver now.")
        : t("Google's screensaver is back."),
    warnings
  }
}

export async function animationScale(scale: number): Promise<ApplyResult> {
  if (![0, 0.5, 1].includes(scale)) throw new Error('scale must be 0, 0.5 or 1')
  for (const k of ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']) {
    await T.sh(TASK, `settings put global ${k} ${scale}`)
  }
  return { ok: true, summary: scale === 0 ? t('System animations are off.') : t('System animations run at {scale}x.', { scale }), warnings: [] }
}

/**
 * Match content frame rate (Android 12+): the TV changes its refresh rate to the video's. 0 never, 1 only when
 * the display can switch without going blank (Android's default), 2 always, even if the screen blanks for a moment.
 */
export async function matchFrameRate(mode: number): Promise<ApplyResult> {
  if (![0, 1, 2].includes(mode)) throw new Error('mode must be 0, 1 or 2')
  if ((T.getStatus().probe?.sdk ?? 0) < FRAME_RATE_SDK) throw new UiError('Matching the frame rate needs Android 12 or newer.')
  await T.sh(TASK, `settings put secure match_content_frame_rate ${mode}`)
  return {
    ok: true,
    summary: mode === 0 ? t('The TV keeps its refresh rate for every video.') : mode === 1 ? t("The TV matches a video's frame rate when it can switch without the screen going blank.") : t("The TV always matches a video's frame rate."),
    warnings: []
  }
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
  if (!png || png.length < 100) throw new UiError('The TV returned an empty screenshot. Live TV, HDMI and some streaming apps block capture: press Home and try again.')
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
    s.once('connect', () => done(false, t('{host}:5555 is still open: network debugging is on. On the TV, switch USB debugging and Network/ADB debugging off, reboot, and check again.', { host })))
    s.once('timeout', () => done(true, t('{host}:5555 did not answer in 3 s: closed, unless the TV is off or at another address.', { host })))
    s.once('error', (e: NodeJS.ErrnoException) => {
      if (e.code === 'ECONNREFUSED') done(true, t('{host}:5555 is closed: the TV refused the connection.', { host }))
      else done(false, t('Could not check {host}:5555 ({error}). Is the TV on and on this network?', { host, error: e.code ?? e.message }))
    })
    s.connect(5555, host)
  })
}
