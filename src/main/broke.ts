// "Something broke" in Clean up: the owner says what stopped working, and the app turns back on only what it
// switched off on this TV that is linked to it (shared/symptoms.ts), a step at a time, with Restore as the
// last step. It goes through Clean up's own apply, so each saved list drops the package and its restore
// record keeps it: a full Restore still puts everything back, and the next Clean up leaves it unticked.
import * as T from './tv'
import * as debloat from './debloat'
import * as home from './home'
import { GOOGLE_LAUNCHERS, SETUPWRAITH } from './debloat'
import { HOME_PKG } from './launcher'
import { rememberTurnedOn } from './kept'
import { log } from './log'
import { t, UiError } from './i18n'
import { inEnglish } from './english'
import { stepsFor, symptom, type SymptomId } from '@shared/symptoms'
import type { ApplyResult, BrokeSteps } from '@shared/api'

const TASK = 'something broke'

interface Offer {
  steps: { pkg: string; why: () => string; issue?: number }[][]
  /** The lists that keep each package off. */
  lists: Map<string, string[]>
  /** Google's launcher, off while CleanMyTV Home is Home: it comes back as "kept on", not through a list. */
  google?: string
  homeBefore: string | null
}

async function offer(id: SymptomId): Promise<Offer> {
  const lists = await debloat.stillOff()
  const { installed, disabled } = await T.packageSets()
  const homeBefore = await T.homeComponent().catch(() => null)
  const ours = !!homeBefore?.startsWith(HOME_PKG + '/')
  const googleOff = GOOGLE_LAUNCHERS.find((p) => installed.has(p) && disabled.has(p))
  // The handover switches Google's launcher off without a list, so it counts as ours while our Home is Home.
  const google = googleOff && ours ? googleOff : undefined
  const off = [...lists.keys(), ...(google && !lists.has(google) ? [google] : [])]
  const steps: Offer['steps'] = stepsFor(id, off).map((s) => s.map(({ pkg, link }) => ({ pkg, why: link.why, issue: link.issue })))
  // With setupwraith on and Google's launcher off, the wizard takes Home, and a restart in that state ended in a
  // factory reset on a Xiaomi (upstream TROUBLESHOOTING.md). The launcher goes back on first, or the wizard stays off.
  const at = steps.findIndex((s) => s.some((l) => l.pkg === SETUPWRAITH))
  if (at >= 0 && googleOff) {
    const offered = off.includes(googleOff)
    for (const s of steps.slice(at)) s.splice(0, s.length, ...s.filter((l) => l.pkg !== googleOff && (offered || l.pkg !== SETUPWRAITH)))
    if (offered && !steps.slice(0, at).some((s) => s.some((l) => l.pkg === googleOff))) {
      steps[at].unshift({ pkg: googleOff, why: () => t("Google TV's launcher goes back on first: with it off, the setup wizard takes over Home.") })
    }
  }
  return { steps: steps.filter((s) => s.length), lists, google, homeBefore }
}

/** What "Something broke" offers for the symptom now: what this app switched off that is linked to it. */
export async function steps(id: SymptomId): Promise<BrokeSteps> {
  if (!T.getStatus().probe) throw new UiError('Connect to a TV first.')
  const { steps } = await offer(id)
  const hint = symptom(id).hint?.()
  return { symptom: id, steps: steps.map((s) => s.map((l) => ({ pkg: l.pkg, why: l.why(), ...(l.issue ? { issue: l.issue } : {}) }))), ...(hint ? { hint } : {}) }
}

/** Turns back on the packages picked from what steps() offers, each through the list that switched it off. */
export async function fix(id: SymptomId, packages: string[]): Promise<ApplyResult> {
  if (!T.getStatus().probe) throw new UiError('Connect to a TV first.')
  const o = await offer(id)
  const offered = o.steps.flat().map((l) => l.pkg)
  const want = offered.filter((p) => packages.includes(p))
  // The wizard never comes on alone while Google's launcher is still off (see offer()).
  const launcher = offered.find((p) => GOOGLE_LAUNCHERS.includes(p))
  if (want.includes(SETUPWRAITH) && launcher && !want.includes(launcher)) want.unshift(launcher)
  if (!want.length) throw new UiError('None of these are switched off by Clean up any more.')
  log(TASK, 'info', `${inEnglish(symptom(id).label)}: turning back on ${want.join(', ')}`)
  const warnings: string[] = []
  let ok = true

  // Google's launcher first, and as Home screen's "Keep Google TV's launcher on": CleanMyTV Home stays in front.
  if (o.google && want.includes(o.google)) {
    const r = await home.keepGoogleLauncher(true)
    ok &&= r.ok
    warnings.push(r.summary, ...r.warnings)
  }
  const byList = new Map<string, string[]>()
  for (const p of want) {
    if (p === o.google) continue
    for (const listId of o.lists.get(p) ?? []) byList.set(listId, [...(byList.get(listId) ?? []), p])
  }
  for (const [listId, enable] of byList) {
    const r = await debloat.apply({ listId, disable: [], enable })
    ok &&= r.ok
    warnings.push(...r.warnings)
  }

  // Android forgets the Home choice when the set of Home apps changes, and a launcher or the wizard just came on.
  if (o.homeBefore?.startsWith(HOME_PKG + '/')) {
    const now = await T.homeComponent().catch(() => null)
    if (now !== o.homeBefore) await T.sh(TASK, `cmd package set-home-activity --user 0 ${o.homeBefore}`)
  }

  const { installed, disabled } = await T.packageSets()
  const on = want.filter((p) => installed.has(p) && !disabled.has(p))
  rememberTurnedOn(on, id)
  const say = (): string => on.length === want.length
    ? t('Turned back on: {pkgs}.', { pkgs: on.join(', ') })
    : t('Turned back on {done} of {n}. The notes below say why.', { done: on.length, n: want.length })
  log(TASK, on.length === want.length && ok ? 'ok' : 'warn', inEnglish(say))
  return { ok: ok && on.length === want.length, summary: say(), warnings }
}
