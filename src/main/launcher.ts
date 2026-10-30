// Handing Home to CleanMyTV Home and back: the launcher handover from upstream's build-and-install.sh
// (start it first, switch off the stock launcher, check where Home landed, roll back if it didn't stick),
// plus removing the CleanYourTV launcher earlier versions of this app installed.
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import * as T from './tv'
import { log } from './log'
import { paths } from './paths'
import { GOOGLE_LAUNCHERS, NOT_LAUNCHERS, SETUPWRAITH, stockLaunchers } from './debloat'
import type { ApplyResult, InstallResult } from '@shared/api'

const TASK = 'install'
/** CleanMyTV Home, built from home/. */
export const HOME_PKG = 'io.github.wingedpanther.cleanmytv.home'
/** The CleanYourTV launcher that CleanMyTV 0.1.x installed. */
export const OLD_PKG = 'com.gathin.tvhome'
const OUR_LAUNCHERS = [OLD_PKG, HOME_PKG]

/** The stock launcher couldn't be told apart from other apps that answer Home: the owner picks it (#39). */
export class StockUnknown extends Error {
  constructor(message: string, readonly candidates: string[]) {
    super(message)
  }
}

/** Apps that can be Home on the TV, minus ours and the ones that only stand in for a launcher. */
async function launcherCandidates(): Promise<string[]> {
  return (await T.homeClaimants()).filter((c) => !OUR_LAUNCHERS.includes(c) && !NOT_LAUNCHERS.includes(c) && c !== 'android')
}

/**
 * Which app owns Home differs by build, so ask the TV. A launcher this app or an upstream list knows is
 * taken as it is; otherwise the app that owns Home now is the stock one. `chosen` is the owner's pick.
 */
export async function detectStock(chosen?: string): Promise<string> {
  const known = stockLaunchers()
  if (chosen) {
    if (!(await launcherCandidates()).includes(chosen)) throw new Error(`${chosen} can't be Home on this TV, so it can't be the stock launcher.`)
    log(TASK, 'info', `stock launcher picked by hand: ${chosen}`)
    return chosen
  }
  const answer = await T.homeAnswer()
  const homePkg = answer.split('/')[0]
  if (known.includes(homePkg)) return homePkg
  if (OUR_LAUNCHERS.includes(homePkg)) {
    // Ours owns Home for now, but an enabled Google launcher takes it back (#27).
    const claims = await T.homeClaimants()
    const on = GOOGLE_LAUNCHERS.find((k) => claims.includes(k))
    if (on) {
      log(TASK, 'info', `Home belongs to ${homePkg}, but ${on} is still on and can take Home back; it will be switched off.`)
      return on
    }
    log(TASK, 'info', `Home belongs to ${homePkg}, so the stock launcher is already off; nothing will be disabled.`)
    return 'none'
  }
  if (homePkg === SETUPWRAITH) {
    throw new Error("Home resolves to Google TV's setup wizard. Add com.google.android.tungsten.setupwraith to your cleanup list, apply it, then install again.")
  }
  const candidates = await launcherCandidates()
  if (homePkg === 'android') {
    // Android's chooser: two launchers tie and no default is set. Take the one stock launcher among them.
    const found = known.filter((k) => candidates.includes(k))
    const pick = found.length === 1 ? found[0] : found.length === 0 && candidates.length === 1 ? candidates[0] : null
    if (pick) {
      log(TASK, 'info', `Home is Android's chooser; the stock launcher among the candidates is ${pick}.`)
      return pick
    }
    throw new StockUnknown("Home is Android's launcher chooser, and the app can't tell which one is the TV's own launcher.", candidates)
  }
  if (candidates.includes(homePkg)) {
    log(TASK, 'info', `Home belongs to ${homePkg}, which no list names; taking it as the stock launcher.`)
    return homePkg
  }
  throw new StockUnknown(`Home resolves to '${homePkg || 'nothing'}', so the app can't tell which app is the TV's own launcher.`, candidates)
}

/**
 * What the handover switches off. Google's launchers take Home back, so they go. Vendor launchers stay on,
 * as upstream's STOCK_LAUNCHER=none does: on many TVs they also run the remote's buttons or the inputs,
 * and lists say to keep them.
 */
export function toSwitchOff(stock: string): string {
  return GOOGLE_LAUNCHERS.includes(stock) ? stock : 'none'
}

// ---- the stock launcher of each TV, so Undo and the restart check find a vendor launcher again ----

const stockFile = (): string => join(paths.data, 'stock-launchers.json')

function tvKey(): string {
  const st = T.getStatus()
  return st.probe?.device || st.host || ''
}

function readStockFile(): Record<string, string> {
  try {
    return existsSync(stockFile()) ? (JSON.parse(readFileSync(stockFile(), 'utf8')) as Record<string, string>) : {}
  } catch {
    return {}
  }
}

export function rememberStock(stock: string): void {
  const key = tvKey()
  if (!key || stock === 'none' || !T.PACKAGE_RE.test(stock)) return
  const all = readStockFile()
  if (all[key] === stock) return
  all[key] = stock
  mkdirSync(paths.data, { recursive: true })
  writeFileSync(stockFile(), JSON.stringify(all, null, 2))
}

/** This TV's stock launcher: the one remembered from the handover, else a known one that is installed. */
export function stockOnTv(installed: Set<string>): string | undefined {
  const mine = readStockFile()[tvKey()]
  if (mine && installed.has(mine)) return mine
  return stockLaunchers().find((p) => installed.has(p))
}

export async function rollback(stock: string): Promise<boolean> {
  if (stock === 'none') return false
  try {
    await T.sh(TASK, `pm enable --user 0 ${stock}`)
    await T.sh(TASK, `cmd package set-home-activity --user 0 $(${T.HOME_QUERY} ${stock} | tail -n 1)`)
    const back = await T.homeComponent()
    if (back?.startsWith(stock + '/')) {
      log(TASK, 'ok', `${stock} owns Home again.`)
      return true
    }
  } catch (e) {
    log(TASK, 'error', `rollback failed: ${(e as Error).message}`)
  }
  return false
}

/**
 * Makes `main` Home: switches off `stock` (unless 'none'), sets the preference, and checks where Home
 * landed. Android drops the preference whenever the set of HOME apps changes, and on Google TV the next
 * candidate can be the setup wizard, which crash-loops; so anything but `main` puts the old Home back.
 * Returns null on success, or the result to show.
 */
export async function handOver(stock: string, main: string): Promise<InstallResult | null> {
  const pkg = main.split('/')[0]
  const previous = await T.homeComponent().catch(() => null)
  await T.batch(TASK, [
    `cmd package compile -m speed -f ${pkg}`,
    stock === 'none' ? 'echo stock launcher left alone' : `pm disable-user --user 0 ${stock}`,
    `cmd package set-home-activity --user 0 ${main}`
  ])
  let now: string | null = null
  try { now = await T.homeComponent() } catch { now = null }
  if (now?.startsWith(pkg + '/')) {
    log(TASK, 'ok', `Home now resolves to ${now}`)
    return null
  }
  let back = false
  if (stock !== 'none') back = await rollback(stock)
  // No '$' here, unlike homeComponent(): the value goes into a shell command.
  else if (previous && /^[A-Za-z_][\w.]*\/[\w.]+$/.test(previous) && previous !== now) {
    await T.sh(TASK, `cmd package set-home-activity --user 0 ${previous}`).catch(() => '')
    back = (await T.homeComponent().catch(() => null)) === previous
  }
  const was = stock !== 'none' ? stock : previous ?? 'the previous launcher'
  return {
    ok: false,
    summary: now ? `Home resolves to ${now}, not to the new launcher.` : 'The TV stopped answering while Home was switched.',
    warnings: [
      back ? `${was} was put back as Home.` : stock !== 'none' ? `Could not give Home back automatically. Run: pm enable --user 0 ${stock}` : 'Could not put the previous Home back automatically.',
      'On Google TV this usually means com.google.android.tungsten.setupwraith also claims Home: add it to your cleanup list, apply it, then install again.'
    ]
  }
}

/**
 * Starts an installed launcher, and only if it comes up, makes it Home. A stock launcher that is still on
 * gets switched off too: on Google TV it otherwise keeps Home (#27).
 */
export async function switchTo(main: string, chosen?: string): Promise<ApplyResult> {
  let stock: string
  try {
    stock = await detectStock(chosen)
  } catch (e) {
    if (e instanceof StockUnknown) return { ok: false, summary: e.message, warnings: [], stockChoices: e.candidates }
    throw e
  }
  const start = await T.sh(TASK, `am start -W -n ${main}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: 'That launcher did not start, so Home was left as it was.', warnings: [start.trim()] }
  }
  const failed = await handOver(toSwitchOff(stock), main)
  if (!failed) rememberStock(stock)
  return failed ?? { ok: true, summary: 'Switched. Press Home on the remote to see it.', warnings: [] }
}

/** Takes a package's accessibility services out of the TV's enabled list, keeping everyone else's. */
export async function dropServices(pkg: string): Promise<void> {
  const services = (await T.tv().shell('settings get secure enabled_accessibility_services')).trim()
  if (!services.includes(pkg + '/')) return
  // Only well-formed components go back into a shell command.
  const rest = services.split(':').filter((c) => c && c !== 'null' && !c.startsWith(pkg + '/') && /^[A-Za-z_][\w.]*\/[\w.]+$/.test(c))
  await T.sh(TASK, rest.length ? `settings put secure enabled_accessibility_services ${rest.join(':')}` : 'settings delete secure enabled_accessibility_services')
}

/** Adds a service to the TV's enabled list (once) and switches accessibility on. */
export async function addService(component: string): Promise<void> {
  if (!/^[A-Za-z_][\w.]*\/[\w.]+$/.test(component)) throw new Error(`not a component: ${component}`)
  const services = (await T.tv().shell('settings get secure enabled_accessibility_services')).trim()
  const list = services.split(':').filter((c) => c && c !== 'null' && /^[A-Za-z_][\w.]*\/[\w.]+$/.test(c))
  if (!list.includes(component)) list.push(component)
  await T.sh(TASK, `settings put secure enabled_accessibility_services ${list.join(':')}`)
  await T.sh(TASK, 'settings put secure accessibility_enabled 1')
}

/**
 * Uninstalls the CleanYourTV launcher once it no longer owns Home, and takes its accessibility services
 * and Clock screensaver out of the TV settings so nothing points at an app that is gone.
 */
export async function removeOld(): Promise<ApplyResult> {
  const { installed } = await T.packageSets()
  if (!installed.has(OLD_PKG)) return { ok: true, summary: 'The CleanYourTV launcher is not on this TV.', warnings: [] }
  const home = await T.homeComponent()
  if (home?.startsWith(OLD_PKG + '/')) {
    return { ok: false, summary: 'The CleanYourTV launcher is still Home. Switch to CleanMyTV Home first, then remove it.', warnings: [] }
  }
  await dropServices(OLD_PKG)
  if ((await T.tv().shell('settings get secure screensaver_components')).includes(OLD_PKG)) {
    await T.sh(TASK, 'settings delete secure screensaver_components')
  }
  const out = await T.sh(TASK, `pm uninstall ${OLD_PKG}`)
  return out.includes('Success')
    ? { ok: true, summary: 'The CleanYourTV launcher is removed from the TV.', warnings: [] }
    : { ok: false, summary: 'The TV did not remove the CleanYourTV launcher.', warnings: [out.trim()] }
}

export async function useStock(): Promise<ApplyResult> {
  const { installed } = await T.packageSets()
  const stock = stockOnTv(installed)
  if (!stock) return { ok: false, summary: 'No stock launcher this app knows is installed on the TV.', warnings: [] }
  const ok = await rollback(stock)
  // The stock launcher is back: CleanMyTV Home's remote-button service goes with it.
  if (ok) await dropServices(HOME_PKG)
  return ok
    ? { ok, summary: `${stock} is Home again. CleanMyTV Home stays installed; use it again from the Home screen step.`, warnings: [] }
    : { ok, summary: `Could not make ${stock} Home. Pick it in the TV's home settings.`, warnings: [] }
}
