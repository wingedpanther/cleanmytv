// What CleanMyTV keeps switched off goes to CleanMyTV Home as `switched_off`, so the TV itself can say when
// a system update switched those apps back on. Sent after each clean-up and when the app connects.
import * as T from './tv'
import * as debloat from './debloat'
import * as home from './home'
import { HOME_PKG } from './launcher'
import { log } from './log'
import { englishOf } from './i18n'

/** Tells CleanMyTV Home, when the TV has it. Never fails the job it follows: a line in the log at most. */
export async function tellHome(): Promise<void> {
  try {
    const { installed, disabled } = await T.packageSets()
    if (!installed.has(HOME_PKG) || disabled.has(HOME_PKG)) return
    const { off } = await debloat.switchedOff()
    const now = [...((await home.readConfig()).switched_off ?? [])].sort()
    if (now.join() === off.join()) return
    await home.setConfig({ switched_off: off })
  } catch (e) {
    log('debloat', 'info', `could not tell CleanMyTV Home what is switched off: ${englishOf(e)}`)
  }
}
