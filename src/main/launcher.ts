// Install the launcher and hand Home over to it: the port of upstream launcher/build-and-install.sh,
// minus the build (the APK comes bundled, or from the optional local build in toolchain.ts).
import { existsSync, readFileSync } from 'node:fs'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { KNOWN_STOCK_LAUNCHERS } from './debloat'
import type { ApplyResult, InstallRequest, InstallResult, LauncherStatus } from '@shared/api'

const TASK = 'install'
export const PKG = 'com.gathin.tvhome'
const MAIN = `${PKG}/.MainActivity`
/** CleanMyTV Home, the preview launcher built from home/. */
export const HOME_PKG = 'io.github.wingedpanther.cleanmytv.home'
const OUR_LAUNCHERS = [PKG, HOME_PKG]

function bundledVersion(): string | undefined {
  try {
    return JSON.parse(readFileSync(paths.bundledApkInfo, 'utf8')).versionName
  } catch {
    return undefined
  }
}

export async function status(): Promise<LauncherStatus> {
  const dump = await T.tv().shell(`dumpsys package ${PKG} | grep -m1 versionName`)
  const version = /versionName=(\S+)/.exec(dump)?.[1]
  const home = await T.homeComponent()
  const { installed } = await T.packageSets()
  return {
    installed: installed.has(PKG),
    versionName: version,
    isHome: !!home && home.startsWith(PKG + '/'),
    homeComponent: home,
    stockLauncher: KNOWN_STOCK_LAUNCHERS.find((p) => installed.has(p)) ?? null,
    bundledApk: existsSync(paths.bundledApk),
    bundledVersion: bundledVersion(),
    localApk: existsSync(paths.localApk)
  }
}

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
    throw new Error(`Home is Android's launcher chooser and ${found.length} known stock launchers claim it. Pick a default on the TV first, or choose "Leave the stock launcher alone".`)
  }
  if (homePkg === 'com.google.android.tungsten.setupwraith') {
    throw new Error("Home resolves to Google TV's setup wizard. Add com.google.android.tungsten.setupwraith to your cleanup list, apply it, then install again.")
  }
  throw new Error(`Home resolves to '${homePkg || 'nothing'}', which is not a stock launcher this app knows. Choose "Leave the stock launcher alone" to install without disabling anything.`)
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

export async function install(req: InstallRequest): Promise<InstallResult> {
  const apkPath = req.apk === 'local' ? paths.localApk : paths.bundledApk
  if (!existsSync(apkPath)) {
    return { ok: false, summary: req.apk === 'local' ? 'No local build yet. Build from source first.' : 'The bundled launcher is missing from this copy of the app.', warnings: [] }
  }
  const warnings: string[] = []
  let stock = req.stockLauncher
  if (stock === 'auto') stock = await detectStock()
  else if (stock !== 'none' && !T.PACKAGE_RE.test(stock)) throw new Error(`not a package name: ${stock}`)
  log(TASK, 'info', `stock launcher: ${stock}`)

  const tmp = '/data/local/tmp/tvhome.apk'
  await T.pushB64(TASK, readFileSync(apkPath), tmp)

  // Install on its own: in a batch a failed install would not stop the stock launcher being disabled.
  let out = await T.sh(TASK, `pm install -r ${tmp}`, { timeoutMs: 300000 })
  if (/INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match/i.test(out)) {
    if (!req.replaceIncompatible) {
      await T.sh(TASK, `rm -f ${tmp}`)
      return {
        ok: false,
        incompatible: true,
        summary: 'The launcher on the TV was built on another computer and signed with a different key.',
        warnings: ['Replacing it uninstalls the old copy first. Tile order and app library choices made on the TV are lost; settings pushed from this app are kept.']
      }
    }
    // Home must not point at a package that is about to vanish: give it to the stock launcher first.
    const s = stock === 'none' ? (await status()).stockLauncher : stock
    if (s) {
      await rollback(s)
      stock = s   // it was disabled before; the handover below disables it again
    }
    await T.sh(TASK, `pm uninstall ${PKG}`)
    out = await T.sh(TASK, `pm install -r ${tmp}`, { timeoutMs: 300000 })
  }
  await T.sh(TASK, `rm -f ${tmp}`)
  if (!out.includes('Success')) {
    return { ok: false, summary: 'Install failed; the stock launcher was left alone.', warnings: [out.trim()] }
  }

  // Start the launcher before the stock Home is touched: "Status: ok" proves the activity came up.
  const start = await T.sh(TASK, `am start -W -n ${MAIN}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: `The new launcher did not start; ${stock === 'none' ? 'nothing was disabled' : stock + ' was left enabled'}.`, warnings: [start.trim()] }
  }
  await T.sh(TASK, `pm grant ${PKG} android.permission.ACCESS_FINE_LOCATION 2>/dev/null; echo location-granted`)
  const failed = await handOver(stock, MAIN)
  if (failed) return failed

  // Optional accessibility services; Android switches them off on every reinstall.
  for (const [on, svc] of [[req.inputKeyService, 'InputKeyService'], [req.homeKeyService, 'HomeKeyService']] as const) {
    if (on) await enableService(svc)
  }
  return { ok: true, summary: 'Installed. Press Home on the remote to see it.', warnings }
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

export async function useCleanYourTv(): Promise<ApplyResult> {
  const { installed } = await T.packageSets()
  if (!installed.has(PKG)) return { ok: false, summary: 'The CleanYourTV launcher is not installed. Install it above.', warnings: [] }
  return switchTo(MAIN)
}

/** Appends to enabled_accessibility_services without dropping other services. */
export async function enableService(name: 'InputKeyService' | 'HomeKeyService'): Promise<void> {
  const svc = `${PKG}/.${name}`
  await T.sh(TASK, `s=$(settings get secure enabled_accessibility_services); case "$s" in *${name}*) ;; null|'') settings put secure enabled_accessibility_services ${svc} ;; *) settings put secure enabled_accessibility_services "$s:${svc}" ;; esac; settings put secure accessibility_enabled 1; echo ${name} enabled`)
}

export async function useStock(): Promise<ApplyResult> {
  const { installed } = await T.packageSets()
  const stock = KNOWN_STOCK_LAUNCHERS.find((p) => installed.has(p))
  if (!stock) return { ok: false, summary: 'No stock launcher this app knows is installed on the TV.', warnings: [] }
  const ok = await rollback(stock)
  return ok
    ? { ok, summary: `${stock} is Home again. The clean launcher stays installed; install again to switch back.`, warnings: [] }
    : { ok, summary: `Could not make ${stock} Home. Pick it in the TV's home settings.`, warnings: [] }
}
