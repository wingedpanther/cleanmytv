// Which packages a run switches off unless the owner says otherwise. Clean up pre-ticks these and
// the guided run disables them, so both leave the same things on.
import type { DebloatEntry } from '@shared/api'

export function defaultTick(e: DebloatEntry): boolean {
  // Keep earlier choices: a package this app switched off stays ticked.
  if (e.state === 'disabled') return e.suggested || e.recorded === 'enabled'
  // Features people use (phone remote, casting, voice) are the user's call, never a default.
  return e.suggested && e.state === 'enabled' && !e.choice
}

/** What a run with the defaults switches off now: ticked and still on. What is already off stays in the saved list. */
export function defaultOff(entries: DebloatEntry[]): string[] {
  return entries.filter((e) => e.state === 'enabled' && defaultTick(e)).map((e) => e.pkg)
}
