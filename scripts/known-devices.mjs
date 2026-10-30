// Writes the table in docs/KNOWN-DEVICES.md from the synced lists: each list's tv.json for the model and
// OS, its "Stock launcher:" note, and a few words picked out of its notes. Run after npm run sync.
//   node scripts/known-devices.mjs
import { existsSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

const dir = 'resources/upstream/examples'
const source = JSON.parse(readFileSync('resources/upstream/SOURCE.json', 'utf8'))
const cell = (s) => s.replace(/\|/g, '/').replace(/\s+/g, ' ').trim()
const cut = (s, n) => (s.length > n ? s.slice(0, n - 3).trimEnd() + '...' : s)

const rows = []
for (const id of readdirSync(dir).sort()) {
  const list = join(dir, id, 'debloat.txt')
  if (!existsSync(list)) continue
  const text = readFileSync(list, 'utf8')
  let tv = {}
  try { tv = JSON.parse(readFileSync(join(dir, id, 'tv.json'), 'utf8')) } catch { tv = {} }
  const model = tv.tv || text.split('\n')[0].replace(/^#\s*/, '') || id
  const os = /(\d+(?:\.\d+)?)\s*$/.exec(tv.os ?? '')?.[1] ?? '-'
  const stock = /^#.*\bstock launcher:\s*([A-Za-z][\w]*(?:\.\w+)+)/im.exec(text)?.[1]
    ?? (/launcherx/.test(text) ? 'com.google.android.apps.tv.launcherx' : /tvlauncher/.test(text) ? 'com.google.android.tvlauncher' : null)
  const notes = []
  if (/wireless debugging|adb pair|pairing code/i.test(text)) notes.push('needs pairing')
  if (/setupwraith[^\n]*(owns|takes|grabs|claims) (the )?home/i.test(text)) notes.push('setupwraith grabs Home')
  if (/no (hdmi )?inputs|streaming (box|stick)|dongle/i.test(text)) notes.push('streaming box (no inputs)')
  rows.push(`| ${cut(cell(model), 90)} | ${os} | ${stock ? '`' + stock + '`' : '-'} | ${notes.join('; ') || '-'} |`)
}

const doc = 'docs/KNOWN-DEVICES.md'
const old = readFileSync(doc, 'utf8')
const start = old.indexOf('| Model |')
const end = old.indexOf('\n## ', start)
const table = [
  '| Model | OS | Stock launcher | Notes |',
  '|---|---|---|---|',
  ...rows,
  '',
  `_${rows.length} lists, synced from upstream at rev ${source.rev.slice(0, 12)} (${source.synced.slice(0, 10)}). Regenerate with \`node scripts/known-devices.mjs\` after a sync._`,
  ''
].join('\n')
writeFileSync(doc, old.slice(0, start) + table + old.slice(end))
console.log(`${rows.length} rows`)
