// The check-up after Clean up: the things people found broken afterwards, checked on the TV with a fix for
// each. A keyboard stuck on the phone one (#98), no Home, a remote mic with half its pair switched off (the
// phone-remote service also carries the remote's microphone), and a screensaver pointing at an app that's off.
import type { CheckupId, CheckupItem } from '@shared/api'
import * as T from './tv'
import { HOME_PKG } from './launcher'
import { noteKeyboard } from './keyboard'
import * as launcher from './launcher'

const TASK = 'check-up'
const BRIDGE = 'com.google.android.tv.remote.service/'
const REMOTE = 'com.google.android.tv.remote.service'
const VOICE = 'com.google.android.katniss'
/** Google's speech services offer a voice "keyboard" that can't type. */
const NOT_TYPING = ['com.google.android.tts/']

export interface TvState {
  keyboard: string
  keyboards: string[]
  home: string | null
  installed: Set<string>
  disabled: Set<string>
  screensaver: string
  screensaverOn: boolean
}

const on = (s: TvState, pkg: string): boolean => s.installed.has(pkg) && !s.disabled.has(pkg)
const pkgOf = (component: string): string => component.split('/')[0]

/** A keyboard that types on the TV, best first: never the phone bridge or the voice one. */
export function typingKeyboard(s: TvState): string | undefined {
  return s.keyboards.find((k) => !k.startsWith(BRIDGE) && !NOT_TYPING.some((p) => k.startsWith(p)) && on(s, pkgOf(k)))
}

export function assess(s: TvState): CheckupItem[] {
  const out: CheckupItem[] = []
  const typing = typingKeyboard(s)
  const kb = s.keyboard.trim()
  if (kb && kb !== 'null' && !kb.startsWith(BRIDGE) && on(s, pkgOf(kb))) {
    out.push({ id: 'keyboard', label: 'Keyboard', ok: true, detail: `The TV types with ${pkgOf(kb)}.` })
  } else if (typing) {
    const why = kb.startsWith(BRIDGE) ? 'The TV is on the phone keyboard ("Use the keyboard on your mobile device").' : !kb || kb === 'null' ? 'The TV has no keyboard set.' : `The keyboard in use (${pkgOf(kb)}) is switched off.`
    out.push({ id: 'keyboard', label: 'Keyboard', ok: false, detail: `${why} ${pkgOf(typing)} can type on the TV.`, fix: 'Use it' })
  } else {
    out.push({ id: 'keyboard', label: 'Keyboard', ok: !!kb && kb !== 'null', detail: kb && kb !== 'null' ? 'Only the phone keyboard is on, so typing works from the Google TV app on a phone.' : 'The TV has no keyboard on. Switch the TV maker\'s keyboard or Gboard back on in Clean up.' })
  }

  if (s.home && on(s, pkgOf(s.home))) {
    out.push({ id: 'home', label: 'Home screen', ok: true, detail: pkgOf(s.home) === HOME_PKG ? 'CleanMyTV Home opens on the Home button.' : `${pkgOf(s.home)} opens on the Home button.` })
  } else {
    out.push({ id: 'home', label: 'Home screen', ok: false, detail: 'Nothing switched on answers the Home button, so it may do nothing or show an error.', fix: on(s, HOME_PKG) ? 'Use CleanMyTV Home' : 'Put the stock home back' })
  }

  if (s.installed.has(VOICE) && s.installed.has(REMOTE) && on(s, VOICE) !== on(s, REMOTE)) {
    out.push({ id: 'voice', label: "Remote's microphone", ok: false, detail: `Voice needs both ${VOICE} and ${REMOTE}, which also carries the remote's microphone. Only one of them is on.`, fix: 'Switch both on' })
  } else if (s.installed.has(VOICE)) {
    out.push({ id: 'voice', label: "Remote's microphone", ok: true, detail: on(s, VOICE) ? 'Voice search and its microphone service are both on.' : 'Voice search is off, as Clean up left it.' })
  }

  const saver = s.screensaver.split(',')[0]?.trim() ?? ''
  if (s.screensaverOn && saver && saver !== 'null' && !on(s, pkgOf(saver))) {
    out.push({ id: 'screensaver', label: 'Screensaver', ok: false, detail: `The screensaver is ${pkgOf(saver)}, which is switched off, so the screen may stay black or the TV may skip it.`, fix: 'Turn the screensaver off' })
  } else {
    out.push({ id: 'screensaver', label: 'Screensaver', ok: true, detail: !s.screensaverOn ? 'The screensaver is off.' : 'The screensaver app is on.' })
  }
  return out
}

async function read(): Promise<TvState> {
  const get = (k: string): Promise<string> => T.sh(TASK, `settings get secure ${k}`, { quiet: true }).then((o) => o.trim()).catch(() => '')
  const { installed, disabled } = await T.packageSets()
  const keyboards = (await T.sh(TASK, 'ime list -s', { quiet: true }).catch(() => '')).split('\n').map((l) => l.trim()).filter((l) => /^[\w.]+\/[\w.$]+$/.test(l))
  return {
    keyboard: await get('default_input_method'),
    keyboards,
    home: await T.homeComponent().catch(() => null),
    installed,
    disabled,
    screensaver: await get('screensaver_components'),
    screensaverOn: (await get('screensaver_enabled')) !== '0'
  }
}

export async function run(): Promise<CheckupItem[]> {
  return assess(await read())
}

export async function fix(id: CheckupId): Promise<void> {
  const s = await read()
  if (id === 'keyboard') {
    const k = typingKeyboard(s)
    if (!k) throw new Error('no keyboard on the TV can type; switch one back on in Clean up')
    T.q(k)
    await T.sh(TASK, `ime enable ${k}`)
    await T.sh(TASK, `ime set ${k}`)
    await noteKeyboard(k)
  } else if (id === 'home') {
    if (on(s, HOME_PKG)) await T.sh(TASK, `cmd package set-home-activity --user 0 ${HOME_PKG}/.MainActivity`)
    else {
      const r = await launcher.useStock()
      if (!r.ok) throw new Error(r.summary)
    }
  } else if (id === 'voice') {
    for (const p of [VOICE, REMOTE]) if (s.disabled.has(p)) await T.sh(TASK, `pm enable ${p}`)
  } else if (id === 'screensaver') {
    await T.sh(TASK, 'settings put secure screensaver_enabled 0')
  }
}
