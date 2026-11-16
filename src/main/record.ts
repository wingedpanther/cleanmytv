// Record a problem: the TV's own log from the moment Record is pressed until Stop, with the bug report's
// details on top, so an issue gets the log it needs without anyone typing adb commands (#99, #111).
import * as T from './tv'
import { log } from './log'

let since: string | null = null

/** Addresses, MAC addresses and email addresses, which no log reader needs. */
export function scrub(text: string): string {
  return text
    .replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, 'x.x.x.x')
    .replace(/\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b/gi, 'xx:xx:xx:xx:xx:xx')
    .replace(/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g, 'someone@example.com')
}

/** The newest lines only, so a long recording still fits in a GitHub issue. */
export function lastLines(text: string, max: number): string {
  const lines = text.split('\n')
  return lines.length <= max ? text : [`(the first ${lines.length - max} lines are left out)`, ...lines.slice(-max)].join('\n')
}

/** Notes the TV's clock; Stop asks for everything logged after it. Clearing the log isn't needed. */
export async function start(): Promise<void> {
  const now = (await T.sh('record', "date '+%m-%d %H:%M:%S.000'", { quiet: true })).trim()
  if (!/^\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.000$/.test(now)) throw new Error(`the TV gave an odd time (${now || 'nothing'})`)
  since = now
  log('record', 'info', `recording the TV's log from ${now}`)
}

export const recording = (): boolean => since !== null

export function cancel(): void {
  since = null
}

/** The log since Start, the package list and the TV's details, ready to save. */
export async function stop(details: string): Promise<string> {
  if (!since) throw new Error('press Record first')
  const from = since
  since = null
  const body = await T.sh('record', `logcat -d -v threadtime -T '${from}'`, { quiet: true, timeoutMs: 60_000 })
  log('record', 'info', `recorded ${body.split('\n').length} lines`)
  return scrub(`${details}\n\nTV log from ${from}:\n${lastLines(body.trimEnd(), 40_000)}\n`)
}
