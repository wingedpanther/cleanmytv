// TV settings outside the launcher: ad-blocking Private DNS, animation speed, the remote, and a
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
    animationScale: await get('global', 'window_animation_scale')
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

export async function screenshot(): Promise<string> {
  const f = '/data/local/tmp/cytv-shot.png'
  await T.tv().shell(`screencap -p ${f}`, 30000)
  const png = await T.pullB64(f)
  await T.tv().shell(`rm -f ${f}`)
  if (!png || png.length < 100) throw new Error('The TV returned an empty screenshot. Live TV, HDMI and some streaming apps block capture: press Home and try again.')
  return `data:image/png;base64,${png.toString('base64')}`
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
