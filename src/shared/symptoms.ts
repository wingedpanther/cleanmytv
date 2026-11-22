// What people found broken after a clean-up, and the packages behind it. "Something broke" in Clean up
// turns back on the ones this app switched off for a symptom, a step at a time, and Clean up shows a
// report as one line next to a package before it goes. Every package here is in a bundled list or an
// issue: an issue number is on this app's GitHub, anything else comes from the lists' own notes or
// upstream's TROUBLESHOOTING.md.
import { t } from './i18n'

export type SymptomId = 'sound' | 'streaming' | 'casting' | 'network' | 'remote' | 'keyboard' | 'home'

export interface SymptomLink {
  /** One package. */
  pkg?: string
  /** Or a family of them, like every keyboard app. */
  like?: RegExp
  /** "Still broken" moves on to the next step. */
  step: 1 | 2 | 3
  why: () => string
  /** The issue on this app's GitHub with the report. */
  issue?: number
  /** One line Clean up shows before the package goes. Only where switching it off broke something for someone. */
  warn?: () => string
  /** The warning only on TVs from these makers: ro.product.manufacturer, lower case, by prefix. */
  brands?: string[]
}

export interface Symptom {
  id: SymptomId
  label: () => string
  /** Said once with the steps, for what turning packages on can't fix. */
  hint?: () => string
  links: SymptomLink[]
}

const LAUNCHERX = 'com.google.android.apps.tv.launcherx'
const WRAITH = 'com.google.android.tungsten.setupwraith'
const many = (pkgs: string[], link: Omit<SymptomLink, 'pkg'>): SymptomLink[] => pkgs.map((pkg) => ({ pkg, ...link }))

// Functions, so the words come out in the window's language when they are read.
const webview = (): string => t("A web viewer. YouTube's ads load in it, and without it the picture went black on a TCL TV.")
const webviewWarn = (): string => t('On a TCL TV, YouTube went black during ads with this off.')
const keepsLauncher = (): string => t('It stays on behind CleanMyTV Home, which comes back in front after a Home press.')
const patchwall = (): string => t('PatchWall, which the Mi button opens.')
const patchwallWarn = (): string => t('On a Xiaomi TV, the Mi button stopped working with this off.')

export const SYMPTOMS: Symptom[] = [
  {
    id: 'sound',
    label: () => t('No sound, or the sound is wrong'),
    links: [
      { pkg: 'com.dolby.android.audio.service', step: 1, why: () => t('Dolby audio. Lists that name it keep it on.') },
      { pkg: 'com.tcl.showmode', step: 1, why: () => t("TCL's sound modes: Dolby, DTS and night.") },
      { pkg: 'com.tcl.settings', step: 2, why: () => t("TCL's picture and sound settings.") },
      {
        pkg: 'mitv.service', step: 2, why: () => t("Xiaomi's TV service. Settings reads the sound hardware through it."),
        warn: () => t('On China-market Xiaomi TVs, Settings froze and the Sound page crashed with this off.'), brands: ['xiaomi']
      },
      { pkg: LAUNCHERX, step: 2, issue: 99, why: () => `${t("A Xiaomi TV refused Apple TV's Dolby sound while Google TV's launcher was off.")} ${keepsLauncher()}` },
      { pkg: 'com.tcl.t_solo', step: 3, why: () => t("TCL's T-Solo, which plays the TV's sound on Bluetooth headphones.") }
    ]
  },
  {
    id: 'streaming',
    label: () => t('A streaming app won\'t play (Netflix, YouTube, Apple TV, Prime Video...)'),
    hint: () => t('If every app with paid films stops at once, restart the TV first. That cleared it on another TV.'),
    links: [
      {
        pkg: LAUNCHERX, step: 1, issue: 99, why: () => `${t("Apple TV and Netflix stopped playing on some TVs while Google TV's launcher was off.")} ${keepsLauncher()}`,
        warn: () => t('On a Xiaomi TV, Apple TV stalled with this off.')
      },
      ...many(['com.tcl.android.webview', 'com.mediatek.support.webview', 'com.google.android.webview', 'com.android.webview'], { step: 1, issue: 55, why: webview, warn: webviewWarn }),
      {
        pkg: 'com.google.android.partnersetup', step: 2, why: () => t('A Panasonic list keeps it on for YouTube and Play Store sign-in and playback.'),
        warn: () => t('On a Panasonic TV, YouTube needed this to sign in and play.'), brands: ['panasonic']
      },
      { pkg: 'com.netflix.tokenmanager', step: 2, why: () => t("Netflix's sign-in helper.") },
      ...many(['com.mediatek.backgrounddetection', 'com.mediatek.network', 'com.tcl.guard'], { step: 3, issue: 55, why: () => t('Also off on the TCL TV where YouTube went black.') })
    ]
  },
  {
    id: 'casting',
    label: () => t('Casting or Chromecast stopped working'),
    links: [
      { pkg: 'com.google.android.apps.mediashell', step: 1, why: () => t('Chromecast built-in: phones cast to it.') },
      {
        pkg: WRAITH, step: 2, why: () => t('The setup wizard. On Chromecast and the Google TV Streamer, the Cast receiver loads its keys from it, and phones say "Untrusted device" without it.'),
        warn: () => t('On a Chromecast with Google TV, casting failed with "Untrusted device" with this off.'), brands: ['google']
      },
      ...many(['com.tcl.ttvs', 'com.tcl.magiconnectfree'], {
        step: 2, issue: 81, why: () => t("TCL's phone app (TCL Home, once T-Cast) finds the TV through it."),
        warn: () => t("A TCL owner's phone app couldn't find the TV with this off.")
      }),
      { pkg: LAUNCHERX, step: 3, why: () => `${t("On Android 14, the Cast receiver can freeze a few minutes after each wake while Google TV's launcher is off.")} ${keepsLauncher()}` },
      ...many(['com.tcl.miracast', 'com.xiaomi.mitv.smartshare', 'com.xiaomi.wfdsinkhelperservice'], { step: 3, why: () => t('Screen mirroring (Miracast) from phones and laptops.') }),
      ...many(['com.mediatek.AirplayAPK', 'com.mediatek.airplaydaemon', 'com.tcl.airplay2'], { step: 3, why: () => t('AirPlay from an iPhone or a Mac.') })
    ]
  },
  {
    id: 'network',
    label: () => t('Wi-Fi or the internet drops'),
    hint: () => t("A Wi-Fi network saved in the TV's first setup is deleted when the setup wizard is switched off, and turning apps back on won't bring it back. If the TV forgot it, join it again in Settings > Network & Internet."),
    links: [
      {
        pkg: 'com.sony.dtv.seeds.iot.remotestart', step: 1, why: () => t("Sony's remote start. With it off, a Sony TV dropped off the network in standby."),
        warn: () => t('On a Sony TV, the network dropped in standby with this off.'), brands: ['sony']
      },
      {
        pkg: 'com.tcl.providers.config', step: 1, why: () => t("TCL's settings provider. Owners reported Wi-Fi drops on a TCL 65C725 with it off."),
        warn: () => t('On a TCL 65C725, Wi-Fi dropped with this off.'), brands: ['tcl']
      },
      { pkg: 'com.tcl.initsetup', step: 2, why: () => t("TCL's first-run setup, which also looks after Wi-Fi.") },
      { pkg: 'com.android.captiveportallogin', step: 2, why: () => t('The sign-in page for hotel or café Wi-Fi.') },
      { pkg: 'com.android.pacprocessor', step: 3, why: () => t('Proxy auto-config, for networks that use a PAC proxy.') }
    ]
  },
  {
    id: 'remote',
    label: () => t("Remote buttons or voice don't work"),
    links: [
      { pkg: 'com.google.android.katniss', step: 1, why: () => t("Google Assistant, behind the remote's mic button.") },
      {
        pkg: 'com.google.android.tv.remote.service', step: 1, why: () => t("Carries the remote's microphone, as well as the phone remote."),
        warn: () => t("On a Xiaomi TV Box S and a TCL 43T6C, the remote's mic stopped with this off.")
      },
      { pkg: 'com.google.android.speech.pumpkin', step: 2, why: () => t('Offline speech for voice search.') },
      { pkg: LAUNCHERX, step: 2, why: () => `${t("Voice results for films and songs open in Google TV's launcher. Without it the Assistant says it can't help.")} ${keepsLauncher()}` },
      { pkg: 'com.mitv.tvhome.atv', step: 2, issue: 38, why: patchwall, warn: patchwallWarn, brands: ['xiaomi'] },
      { pkg: 'com.netflix.partner.ncm', step: 2, why: () => t("The remote's Netflix button.") },
      ...many(['com.mitv.overseaservice', 'mitv.service'], { step: 2, why: () => t("Xiaomi's services for the remote keys, the Netflix button and the Bluetooth remote.") }),
      { pkg: 'com.mediatek.hotkey.dispatcher', step: 2, why: () => t("MediaTek's service for the remote's app buttons.") },
      ...many(['com.tcl.tcl_bt_rcu_service', 'com.tcl.autopair'], { step: 2, why: () => t("TCL's Bluetooth remote and its pairing.") }),
      ...many(['com.tcl.keycustomfunctionservice', 'com.tcl.keyhelp', 'com.tcl.partnercustomizer'], { step: 3, why: () => t("TCL's remote key handling.") }),
      ...many(['com.xgimi.misckey', 'com.xgimi.xrmservice'], { step: 3, why: () => t("XGIMI's remote keys.") })
    ]
  },
  {
    id: 'keyboard',
    label: () => t("The keyboard is missing, or typing doesn't work"),
    links: [
      { like: /(inputmethod|keyboard)/i, step: 1, why: () => t('A keyboard app.') },
      {
        pkg: 'com.google.android.tts', step: 1, issue: 98, why: () => t("Google's speech services carry a voice keyboard. With them off, a TCL TV went to the phone keyboard at every restart."),
        warn: () => t('On a TCL TV, the keyboard went back to the phone one at every restart with this off.'), brands: ['tcl']
      },
      { pkg: 'com.google.android.tv.remote.service', step: 2, why: () => t('Typing from the Google TV app on a phone goes through it.') }
    ]
  },
  {
    id: 'home',
    label: () => t('The TV opens the setup wizard, or Home goes to the wrong place'),
    links: [
      ...many([LAUNCHERX, 'com.google.android.tvlauncher'], { step: 1, why: () => `${t("With Google TV's launcher off, the setup wizard can take over Home.")} ${keepsLauncher()}` }),
      { pkg: 'com.mitv.tvhome.atv', step: 2, issue: 38, why: patchwall },
      { pkg: 'com.tcl.keycustomfunctionservice', step: 2, why: () => t("TCL's remote key handling, and its fallback Home.") }
    ]
  }
]

export const isSymptom = (v: unknown): v is SymptomId => SYMPTOMS.some((s) => s.id === v)

export function symptom(id: SymptomId): Symptom {
  const s = SYMPTOMS.find((x) => x.id === id)
  if (!s) throw new Error(`no such symptom: ${id}`)
  return s
}

export const fits = (l: SymptomLink, pkg: string): boolean => l.pkg === pkg || (!!l.like && l.like.test(pkg))

/**
 * Of the packages switched off now, the ones linked to the symptom, in steps: the likeliest first, each
 * package once, empty steps left out.
 */
export function stepsFor(id: SymptomId, off: string[]): { pkg: string; link: SymptomLink }[][] {
  const steps: { pkg: string; link: SymptomLink }[][] = [[], [], []]
  const seen = new Set<string>()
  for (const step of [1, 2, 3]) {
    for (const link of symptom(id).links.filter((l) => l.step === step)) {
      for (const pkg of off) {
        if (seen.has(pkg) || !fits(link, pkg)) continue
        seen.add(pkg)
        steps[step - 1].push({ pkg, link })
      }
    }
  }
  return steps.filter((s) => s.length)
}

/** The first report about switching `pkg` off that fits a TV from `manufacturer`, if any. */
export function warningFor(pkg: string, manufacturer = ''): { text: string; issue?: number } | undefined {
  const maker = manufacturer.trim().toLowerCase()
  for (const s of SYMPTOMS) {
    for (const l of s.links) {
      if (!l.warn || !fits(l, pkg)) continue
      if (l.brands && !l.brands.some((b) => maker.startsWith(b))) continue
      return { text: l.warn(), issue: l.issue }
    }
  }
  return undefined
}
