// Handing Home to CleanMyTV Home and back: the launcher handover from upstream's build-and-install.sh
// (start it first, switch off the stock launcher, check where Home landed, roll back if it didn't stick),
// plus removing the CleanYourTV launcher earlier versions of this app installed.
import * as T from './tv'
import { log } from './log'
import { KNOWN_STOCK_LAUNCHERS } from './debloat'
import type { ApplyResult, InstallResult } from '@shared/api'

const TASK = 'install'
/** CleanMyTV Home, built from home/. */
export const HOME_PKG = 'io.github.wingedpanther.cleanmytv.home'
/** The CleanYourTV launcher that CleanMyTV 0.1.x installed. */
export const OLD_PKG = 'com.gathin.tvhome'
const OUR_LAUNCHERS = [OLD_PKG, HOME_PKG]

/** Which app owns Home differs by build, so ask the TV, and accept only a launcher we recognise. */
export async function detectStock(): Promise<string> {
  const answer = await T.homeAnswer()
  const homePkg = answer.split('/')[0]
  if (KNOWN_STOCK_LAUNCHERS.includes(homePkg)) return homePkg
  if (OUR_LAUNCHERS.includes(homePkg)) {
    log(TASK, 'info', `Home belongs to ${homePkg}, so the stock launcher is already off; nothing will be disabled.`)
    return 'none'
  }
  if (homePkg === 'android') {
    // Android's chooser: two launchers tie and no default is set. Take the one known stock launcher
    // among the HOME claimants, if there is exactly one.
    const claims = await T.homeClaimants()
    const found = KNOWN_STOCK_LAUNCHERS.filter((k) => claims.includes(k))
    if (found.length === 1) {
      log(TASK, 'info', `Home is Android's chooser; the stock launcher among the candidates is ${found[0]}.`)
      return found[0]
    }
    throw new Error(`Home is Android's launcher chooser and ${found.length} known stock launchers claim it. Pick a default Home on the TV first, then try again.`)
  }
  if (homePkg === 'com.google.android.tungsten.setupwraith') {
    throw new Error("Home resolves to Google TV's setup wizard. Add com.google.android.tungsten.setupwraith to your cleanup list, apply it, then install again.")
  }
  throw new Error(`Home resolves to '${homePkg || 'nothing'}', which is not a stock launcher this app knows. Make the stock launcher Home on the TV, then try again.`)
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

/** Starts an installed launcher, and only if it comes up, makes it Home (the stock launcher is already off). */
export async function switchTo(main: string): Promise<ApplyResult> {
  const start = await T.sh(TASK, `am start -W -n ${main}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: 'That launcher did not start, so Home was left as it was.', warnings: [start.trim()] }
  }
  const failed = await handOver('none', main)
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
  const stock = KNOWN_STOCK_LAUNCHERS.find((p) => installed.has(p))
  if (!stock) return { ok: false, summary: 'No stock launcher this app knows is installed on the TV.', warnings: [] }
  const ok = await rollback(stock)
  // The stock launcher is back: CleanMyTV Home's remote-button service goes with it.
  if (ok) await dropServices(HOME_PKG)
  return ok
    ? { ok, summary: `${stock} is Home again. CleanMyTV Home stays installed; use it again from the Home screen step.`, warnings: [] }
    : { ok, summary: `Could not make ${stock} Home. Pick it in the TV's home settings.`, warnings: [] }
}
