// The restart check before Finish (#33): reboot the TV while debugging is still on, reconnect, and make
// sure a launcher comes up. A TV that boots to a black screen with debugging off can only be reset, so
// this is the last moment the app can still fix it: if Home doesn't come up, the stock launcher goes back on.
import { Socket } from 'node:net'
import * as T from './tv'
import { log } from './log'
import { KNOWN_STOCK_LAUNCHERS, SETUPWRAITH } from './debloat'
import { rollback } from './launcher'
import type { BootCheckResult } from '@shared/api'

const TASK = 'restart check'

export interface BootCheckDeps {
  /** Waits `ms`; tests skip the wait. */
  sleep(ms: number): Promise<void>
  /** Whether the TV's ADB port accepts a connection yet. */
  portOpen(host: string, port: number): Promise<boolean>
}

const realDeps: BootCheckDeps = {
  sleep: (ms) => new Promise((r) => setTimeout(r, ms)),
  portOpen: (host, port) => new Promise((resolve) => {
    const s = new Socket()
    const done = (open: boolean): void => { s.destroy(); resolve(open) }
    s.setTimeout(2000)
    s.once('connect', () => done(true))
    s.once('timeout', () => done(false))
    s.once('error', () => done(false))
    s.connect(port, host)
  })
}

async function until(deps: BootCheckDeps, ms: number, step: number, test: () => Promise<boolean>): Promise<boolean> {
  for (let waited = 0; waited <= ms; waited += step) {
    if (await test().catch(() => false)) return true
    await deps.sleep(step)
  }
  return false
}

export async function bootCheck(deps: BootCheckDeps = realDeps): Promise<BootCheckResult> {
  const target = T.getStatus().host
  if (!target) throw new Error('Connect to the TV first.')
  const [ip, p] = target.split(':')
  const port = Number(p) || 5555

  log(TASK, 'cmd', 'reboot')
  await T.tv().shell('reboot').catch(() => '')
  T.disconnect()
  // Give the TV time to go down before knocking, or the old session answers.
  await deps.sleep(15000)
  const up = await until(deps, 240000, 5000, () => deps.portOpen(ip, port))
  if (!up || (await T.connect(target)).state !== 'connected') {
    log(TASK, 'error', `${target} did not come back within 4 minutes`)
    return {
      ok: false, reconnected: false,
      summary: "The TV didn't come back on the network within 4 minutes, so the app couldn't check it.",
      advice: 'Look at the TV. If the home screen is up, you are fine. If it is black, leave debugging on and connect again from Setup: the app can still put the stock launcher back. TVs that needed a pairing code close the port on restart, so pair again first.'
    }
  }
  const booted = await until(deps, 120000, 3000, async () => (await T.tv().shell('getprop sys.boot_completed')).trim() === '1')
  if (!booted) log(TASK, 'info', 'sys.boot_completed never turned 1; checking Home anyway')

  // Home must resolve to a real launcher, and that launcher must be running.
  const seen = { home: null as string | null }
  const running = await until(deps, 45000, 3000, async () => {
    seen.home = await T.homeComponent()
    const pkg = seen.home?.split('/')[0]
    if (!pkg || pkg === SETUPWRAITH) return false
    return (await T.tv().shell(`pidof ${pkg}`)).trim() !== ''
  })
  const home = seen.home
  if (running) {
    log(TASK, 'ok', `The TV restarted and ${home} is running.`)
    return { ok: true, reconnected: true, summary: `The TV restarted and its home screen came up (${home?.split('/')[0]}).` }
  }

  log(TASK, 'error', `After the restart Home is ${home ?? 'nothing'} and no launcher is running`)
  const { installed } = await T.packageSets()
  const stock = KNOWN_STOCK_LAUNCHERS.find((k) => installed.has(k))
  const fixed = stock ? await rollback(stock) : false
  return {
    ok: false, reconnected: true,
    summary: `After the restart no home screen came up (Home resolves to ${home ?? 'nothing'}).`,
    advice: fixed
      ? `The app switched ${stock} back on and made it Home. Keep debugging on, and report this on GitHub with your TV model.`
      : 'The app could not put a stock launcher back. Keep debugging on, open Clean up and choose Undo, then report this on GitHub with your TV model.'
  }
}
