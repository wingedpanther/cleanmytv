// The "Share my list" issue: a [device] report in the shape of the repo's other ones, with the
// packages as a list file the maintainer can drop into examples/. English, as it is for the maintainer.
import type { ShareData } from './api'

export const NEW_ISSUE = 'https://github.com/wingedpanther/cleanmytv/issues/new'
/** GitHub refuses longer links, and some browsers cut them sooner. */
export const MAX_URL = 8000

/** Addresses, MAC addresses and email addresses, which no log reader needs. */
export function scrub(text: string): string {
  return text
    .replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, 'x.x.x.x')
    .replace(/\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b/gi, 'xx:xx:xx:xx:xx:xx')
    .replace(/[\w.+-]+@[\w-]+(?:\.[\w-]+)+/g, 'someone@example.com')
}

export interface ShareIssue {
  title: string
  body: string
  labels: string[]
  /** The link to open: with the whole body, or a short one when the whole body doesn't fit. */
  url: string
  /** False when the link carries the short body and the full text goes on the clipboard. */
  fits: boolean
}

const name = (d: ShareData): string => `${d.tv.manufacturer} ${d.tv.model}`.trim() || 'Android TV'

export function issueTitle(d: ShareData): string {
  return `[device] ${name(d)} (device ${d.tv.device || 'unknown'}, Android ${d.tv.android || '?'}): list from CleanMyTV`
}

function deviceLines(d: ShareData): string[] {
  const prop = (k: string, v: string): string => `- \`${k} = ${v || 'unknown'}\``
  return [
    `Device: **${name(d)}**`,
    prop('ro.product.manufacturer', d.tv.manufacturer),
    prop('ro.product.model', d.tv.model),
    prop('ro.product.device', d.tv.device),
    prop('ro.product.name', d.tv.product),
    prop('ro.build.fingerprint', d.tv.fingerprint),
    `- Android ${d.tv.android || '?'} (SDK ${d.tv.sdk || '?'}), build \`${d.tv.build || 'unknown'}\``,
    `- List the app matched: ${d.matched ? `\`${d.matched}\`` : 'none'}`,
    `- Home: \`${d.home ?? 'unknown'}\`, stock launcher: \`${d.stockLauncher ?? 'unknown'}\``,
    `- Shared from CleanMyTV ${d.app} with Share my list`
  ]
}

/** Every list's packages in one: switched off first, then the ones left on, each package once. */
function merged(d: ShareData): { off: { pkg: string; note: string; on?: boolean }[]; kept: { pkg: string; note: string }[] } {
  const off = new Map<string, { pkg: string; note: string; on?: boolean }>()
  for (const l of d.lists) for (const e of l.off) if (!off.has(e.pkg)) off.set(e.pkg, e)
  const kept = new Map<string, { pkg: string; note: string }>()
  for (const l of d.lists) for (const e of l.kept) if (!off.has(e.pkg) && !kept.has(e.pkg)) kept.set(e.pkg, e)
  return { off: [...off.values()], kept: [...kept.values()] }
}

/** The packages in upstream's debloat.txt format: active lines, then "# Kept (not disabled):". */
export function listFile(d: ShareData): string {
  const { off, kept } = merged(d)
  const width = Math.max(40, ...[...off, ...kept].map((e) => e.pkg.length + 2))
  const line = (e: { pkg: string; note: string }): string => (e.note ? `${e.pkg.padEnd(width)}# ${e.note}` : e.pkg)
  return [
    `# ${name(d)} (device ${d.tv.device || 'unknown'}), Android ${d.tv.android || '?'}, build ${d.tv.build || 'unknown'}`,
    `# Fingerprint ${d.tv.fingerprint || 'unknown'}`,
    '#',
    `# Shared from CleanMyTV ${d.app}, Clean up list${d.lists.length === 1 ? '' : 's'}: ${d.lists.map((l) => l.id).join(', ')}.`,
    '# Everything is DISABLED, not uninstalled. Comment out any line you want to keep.',
    '#',
    `# Stock launcher: ${d.stockLauncher ?? 'unknown'}.`,
    '',
    ...off.map(line),
    '',
    '# Kept (not disabled):',
    ...kept.map((e) => `# ${line(e)}`)
  ].join('\n')
}

export function tvJson(d: ShareData): string {
  const { off } = merged(d)
  return JSON.stringify({
    tv: `${name(d)} (device ${d.tv.device || 'unknown'})`,
    os: `Android ${d.tv.android || '?'}`,
    brand: d.tv.manufacturer,
    notes: `${off.length} packages disabled, shared from CleanMyTV`
  }, null, 2)
}

function keptLines(d: ShareData): string[] {
  const { kept } = merged(d)
  const out: string[] = []
  if (d.keptLauncher) {
    out.push(d.keptLauncher.why === 'tv'
      ? `- \`${d.keptLauncher.pkg}\`: this TV needs it on, so CleanMyTV keeps it on.`
      : `- \`${d.keptLauncher.pkg}\`: the owner keeps it on beside CleanMyTV Home.`)
  }
  if (kept.length) out.push(`- ${kept.length} package${kept.length === 1 ? '' : 's'} the list${d.lists.length === 1 ? '' : 's'} offered stayed on: the \`# Kept\` lines above.`)
  return out.length ? out : ['- Nothing the lists offered.']
}

function brokeLines(broke: string): string[] {
  const text = scrub(broke.trim())
  return ['## Anything that broke?', text || 'Nothing so far.']
}

export function issueBody(d: ShareData, broke: string): string {
  const { off } = merged(d)
  const back = off.filter((e) => e.on)
  const out = [...deviceLines(d), '']
  if (!d.lists.length) {
    out.push('## Switched off', "Nothing yet: Clean up hasn't switched anything off on this TV from this computer.", '')
  } else {
    out.push(`## Switched off (${off.length})`, 'As a list file, `debloat.txt`:', '', '```text', listFile(d), '```', '', '`tv.json`:', '', '```json', tvJson(d), '```', '')
    out.push('## Kept on', ...keptLines(d), '')
    if (back.length) out.push('## On again', `Switched off with CleanMyTV, and on again now (a system update?): ${back.map((e) => `\`${e.pkg}\``).join(', ')}`, '')
  }
  out.push(...brokeLines(broke))
  return out.join('\n') + '\n'
}

export function issueLabels(broke: string): string[] {
  return broke.trim() ? ['enhancement', 'bug'] : ['enhancement']
}

export function newIssueUrl(title: string, body: string, labels: string[]): string {
  const q = (s: string): string => encodeURIComponent(s)
  return `${NEW_ISSUE}?title=${q(title)}&body=${q(body)}&labels=${q(labels.join(','))}`
}

/** The issue and its link. When the body is too long for a link, the link gets the device and a note to paste. */
export function shareIssue(d: ShareData, broke: string, max = MAX_URL): ShareIssue {
  const title = issueTitle(d)
  const body = issueBody(d, broke)
  const labels = issueLabels(broke)
  const url = newIssueUrl(title, body, labels)
  if (url.length <= max) return { title, body, labels, url, fits: true }
  const head = [...deviceLines(d), '',
    '## Switched off', 'The full text was too long for a link. CleanMyTV copied it: paste it below.', '', '<!-- Paste here -->', '']
  const [heading, text] = brokeLines(broke)
  let short = [...head, heading, text].join('\n') + '\n'
  // A very long "broke" text alone can still be too much: cut it, the full text has all of it.
  for (let n = text.length; newIssueUrl(title, short, labels).length > max && n > 40; ) {
    n = Math.floor(n * 0.8)
    short = [...head, heading, `${text.slice(0, n)} (cut: the full text has the rest)`].join('\n') + '\n'
  }
  return { title, body, labels, url: newIssueUrl(title, short, labels), fits: false }
}
