// The keyboard the owner types with, for CleanMyTV Home. Some TVs switch to the phone-remote keyboard at every
// restart (#98); Home puts the owner's back, and this tells it which one that is.
import * as T from './tv'

const HOME_CONFIG = 'content://io.github.wingedpanther.cleanmytv.home.config'
const PHONE_REMOTE = 'com.google.android.tv.remote.service'

/** Tells Home the keyboard in use. An older Home, or none, doesn't answer; that's fine. */
export async function noteKeyboard(ime: string): Promise<void> {
  if (!/^[\w.]+\/[\w.$]+$/.test(ime) || ime.startsWith(PHONE_REMOTE + '/')) return
  const arg = Buffer.from(ime).toString('base64')
  await T.sh('keyboard', `content call --uri ${HOME_CONFIG} --method keyboard --arg ${arg}`, { quiet: true }).catch(() => '')
}

/** Packages behind the TV's enabled keyboards, from `ime list -s`; the phone-remote one isn't a keyboard to keep. */
export function keyboardPackages(out: string): string[] {
  return [...new Set(out.split('\n').map((l) => l.trim().split('/')[0]).filter((p) => T.PACKAGE_RE.test(p) && p !== PHONE_REMOTE))]
}
