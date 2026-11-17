// The check-up after Clean up: the things people found broken afterwards, checked on the TV with a fix for
// each. A keyboard stuck on the phone one (#98), no Home, a remote mic with half its pair switched off (the
// phone-remote service also carries the remote's microphone), a screensaver pointing at an app that's off, and
// a TV that won't let a device on HDMI turn it on.
import type { CheckupId, CheckupItem } from '@shared/api'
import * as T from './tv'
import { HOME_PKG } from './launcher'
import { noteKeyboard } from './keyboard'
import * as launcher from './launcher'
import { t, UiError } from './i18n'

const TASK = 'check-up'
const BRIDGE = 'com.google.android.tv.remote.service/'
const REMOTE = 'com.google.android.tv.remote.service'
const VOICE = 'com.google.android.katniss'
/**
 * HDMI-CEC's "TV wakes when a device starts playing". TCL's Google TVs ship it at 0, so a Shield or a console
 * can't turn the TV on (upstream #4045). Android 12 and later have the command; older TVs fail it and get no item.
 */
const CEC_WAKE = 'tv_wake_on_one_touch_play'
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
  /** The TV's CEC wake setting, "0" or "1"; null on a box, or a TV without the command. */
  hdmiWake?: string | null
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
    out.push({ id: 'keyboard', label: t('Keyboard'), ok: true, detail: t('The TV types with {pkg}.', { pkg: pkgOf(kb) }) })
  } else if (typing) {
    const why = kb.startsWith(BRIDGE) ? t('The TV is on the phone keyboard ("Use the keyboard on your mobile device").') : !kb || kb === 'null' ? t('The TV has no keyboard set.') : t('The keyboard in use ({pkg}) is switched off.', { pkg: pkgOf(kb) })
    out.push({ id: 'keyboard', label: t('Keyboard'), ok: false, detail: `${why} ${t('{pkg} can type on the TV.', { pkg: pkgOf(typing) })}`, fix: t('Use it') })
  } else {
    out.push({ id: 'keyboard', label: t('Keyboard'), ok: !!kb && kb !== 'null', detail: kb && kb !== 'null' ? t('Only the phone keyboard is on, so typing works from the Google TV app on a phone.') : t('The TV has no keyboard on. Switch the TV maker\'s keyboard or Gboard back on in Clean up.') })
  }

  if (s.home && on(s, pkgOf(s.home))) {
    out.push({ id: 'home', label: t('Home screen'), ok: true, detail: pkgOf(s.home) === HOME_PKG ? t('CleanMyTV Home opens on the Home button.') : t('{pkg} opens on the Home button.', { pkg: pkgOf(s.home) }) })
  } else {
    out.push({ id: 'home', label: t('Home screen'), ok: false, detail: t('Nothing switched on answers the Home button, so it may do nothing or show an error.'), fix: on(s, HOME_PKG) ? t('Use CleanMyTV Home') : t('Put the stock home back') })
  }

  if (s.installed.has(VOICE) && s.installed.has(REMOTE) && on(s, VOICE) !== on(s, REMOTE)) {
    out.push({ id: 'voice', label: t("Remote's microphone"), ok: false, detail: t("Voice needs both {voice} and {remote}, which also carries the remote's microphone. Only one of them is on.", { voice: VOICE, remote: REMOTE }), fix: t('Switch both on') })
  } else if (s.installed.has(VOICE)) {
    out.push({ id: 'voice', label: t("Remote's microphone"), ok: true, detail: on(s, VOICE) ? t('Voice search and its microphone service are both on.') : t('Voice search is off, as Clean up left it.') })
  }

  const saver = s.screensaver.split(',')[0]?.trim() ?? ''
  if (s.screensaverOn && saver && saver !== 'null' && !on(s, pkgOf(saver))) {
    out.push({ id: 'screensaver', label: t('Screensaver'), ok: false, detail: t('The screensaver is {pkg}, which is switched off, so the screen may stay black or the TV may skip it.', { pkg: pkgOf(saver) }), fix: t('Turn the screensaver off') })
  } else {
    out.push({ id: 'screensaver', label: t('Screensaver'), ok: true, detail: !s.screensaverOn ? t('The screensaver is off.') : t('The screensaver app is on.') })
  }
  if (s.hdmiWake === '0') {
    out.push({ id: 'hdmi_wake', label: t('Turn on from HDMI'), ok: false, detail: t("A console or box on HDMI can't turn this TV on."), fix: t('Allow it') })
  } else if (s.hdmiWake === '1') {
    out.push({ id: 'hdmi_wake', label: t('Turn on from HDMI'), ok: true, detail: t('A console or box on HDMI can turn this TV on.') })
  }
  return out
}

/**
 * The CEC wake setting, only on a TV: a streaming box is a playback device (ro.hdmi.device_type has no 0)
 * and has no screen to wake. Null when the TV has no hdmi_control or doesn't know the setting.
 */
async function hdmiWake(): Promise<string | null> {
  const out = await T.sh(TASK, `getprop ro.hdmi.device_type; cmd hdmi_control cec_setting get ${CEC_WAKE} 2>&1`, { quiet: true }).catch(() => '')
  const [types = '', ...rest] = out.split('\n')
  if (!types.trim().split(',').includes('0')) return null
  return new RegExp(`^${CEC_WAKE} = ([01])\\s*$`, 'm').exec(rest.join('\n'))?.[1] ?? null
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
    screensaverOn: (await get('screensaver_enabled')) !== '0',
    hdmiWake: await hdmiWake()
  }
}

export async function run(): Promise<CheckupItem[]> {
  return assess(await read())
}

export async function fix(id: CheckupId): Promise<void> {
  const s = await read()
  if (id === 'keyboard') {
    const k = typingKeyboard(s)
    if (!k) throw new UiError('no keyboard on the TV can type; switch one back on in Clean up')
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
  } else if (id === 'hdmi_wake') {
    await T.sh(TASK, `cmd hdmi_control cec_setting set ${CEC_WAKE} 1`)
  }
}
