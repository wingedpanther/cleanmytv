// Log lines and download progress go to the window as events; tests and scripts print them instead.
import { homedir } from 'node:os'
import type { DownloadProgress, LogLine } from '@shared/api'

let sink: (line: LogLine) => void = (l) => console.log(`[${l.task}] ${l.level === 'cmd' ? '$ ' : ''}${l.text}`)
let progressSink: (p: DownloadProgress) => void = () => {}
/** This session's lines for Send a report, which the window's drawer may have cleared. */
const session: LogLine[] = []
const SESSION_MAX = 3000

export function setSinks(onLog: (line: LogLine) => void, onProgress: (p: DownloadProgress) => void): void {
  sink = onLog
  progressSink = onProgress
}

/** The home folder names the computer's user, which a copied log has no need for: it shows as ~. */
export function noHomeFolder(text: string): string {
  const home = homedir().replace(/[\\/]+$/, '')
  if (!home) return text
  // Windows paths ignore case. Only a whole folder name matches, so /Users/janet stays as it is.
  const re = new RegExp(home.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(?![\\p{L}\\p{N}_.-])', process.platform === 'win32' ? 'giu' : 'gu')
  return text.replace(re, '~')
}

export function log(task: string, level: LogLine['level'], text: string): void {
  const line = { task, level, text: noHomeFolder(text), time: Date.now() }
  session.push(line)
  if (session.length > SESSION_MAX) session.splice(0, session.length - SESSION_MAX)
  sink(line)
}

/** This session's log as text, one line each, as the drawer's Copy all writes it. */
export function sessionLog(): string {
  return session.map((l) => `${new Date(l.time).toISOString()} [${l.task}] ${l.level === 'cmd' ? '$ ' : ''}${l.text}`).join('\n')
}

export function progress(p: DownloadProgress): void {
  progressSink(p)
}
