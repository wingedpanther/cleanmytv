// Updating CleanMyTV from inside the app (#93). The installer for this computer comes from the GitHub
// release, is checked against that release's SHA256SUMS.txt, and then:
// - Windows: the installer runs silently over this copy and opens the app again;
// - Mac: the new app takes this one's place and opens. When this copy can't be replaced (it runs from the
//   disk image, from Downloads under App Translocation, or from a folder this user can't write), the disk
//   image opens in Finder instead.
// The Mac build has no Developer ID signature, so Electron's own updater (Squirrel.Mac) won't take it.
import { spawn, execFile } from 'node:child_process'
import { createHash } from 'node:crypto'
import { accessSync, constants, createWriteStream, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { promisify } from 'node:util'
import { log, progress } from './log'
import { t, UiError } from './i18n'
import type { ApplyResult } from '@shared/api'

const TASK = 'update'
const RELEASE = 'https://github.com/wingedpanther/cleanmytv/releases/download/'
// GitHub sends release downloads on to its asset hosts; nothing else is followed.
const HOSTS = new Set(['github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com'])
const VERSION = /^\d+\.\d+\.\d+$/
const run = promisify(execFile)

/** The release file for this computer, or null where the app can't update itself (Linux). */
export function assetName(version: string, platform: NodeJS.Platform, arch: string): string | null {
  if (!VERSION.test(version)) return null
  if (platform === 'darwin') return arch === 'arm64' ? `CleanMyTV-${version}-arm64.dmg` : `CleanMyTV-${version}.dmg`
  if (platform === 'win32') return `CleanMyTV-Setup-${version}.exe`
  return null
}

/** The SHA-256 SHA256SUMS.txt gives for `name` ("<hex>  <name>" lines, as sha256sum writes them). */
export function sumFor(sums: string, name: string): string | null {
  for (const line of sums.split(/\r?\n/)) {
    const m = /^([0-9a-f]{64})\s+\*?(\S+)$/.exec(line.trim())
    if (m && m[2] === name) return m[1]
  }
  return null
}

/** Follows GitHub's redirects to its asset hosts, and nowhere else. */
async function fetchRelease(url: string, fetchImpl: typeof fetch, timeoutMs: number): Promise<Response> {
  let u = new URL(url)
  for (let hop = 0; hop < 5; hop++) {
    if (u.protocol !== 'https:' || !HOSTS.has(u.hostname)) throw new UiError('the update download went somewhere unexpected ({host})', { host: u.hostname })
    const res = await fetchImpl(u, { redirect: 'manual', signal: AbortSignal.timeout(timeoutMs) })
    const next = res.headers.get('location')
    if (res.status >= 300 && res.status < 400 && next) { u = new URL(next, u); continue }
    if (!res.ok) throw new UiError("couldn't download the update (HTTP {status})", { status: res.status })
    return res
  }
  throw new UiError('too many redirects')
}

function sha256File(file: string): string {
  return createHash('sha256').update(readFileSync(file)).digest('hex')
}

export interface UpdateTarget {
  platform: NodeJS.Platform
  arch: string
  /** Where downloads go: the app's data folder. */
  dir: string
  fetchImpl?: typeof fetch
}

/**
 * Downloads version `version`'s installer for this computer and checks it. Returns its path. A copy that
 * already passed the check is used again, so a second press doesn't download it twice.
 */
export async function download(version: string, to: UpdateTarget): Promise<string> {
  const fetchImpl = to.fetchImpl ?? fetch
  const name = assetName(version, to.platform, to.arch)
  if (!name) throw new UiError('Updating from the app works on Mac and Windows. Download the new version from GitHub.')
  const sums = await (await fetchRelease(`${RELEASE}v${version}/SHA256SUMS.txt`, fetchImpl, 30000)).text()
  const want = sumFor(sums.slice(0, 64 * 1024), name)
  if (!want) throw new UiError('the release has no checksum for {file}', { file: name })
  mkdirSync(to.dir, { recursive: true })
  const file = join(to.dir, name)
  if (existsSync(file) && sha256File(file) === want) return file
  log(TASK, 'cmd', `download ${name}`)
  const res = await fetchRelease(`${RELEASE}v${version}/${name}`, fetchImpl, 30 * 60 * 1000)
  const total = Number(res.headers.get('content-length')) || 0
  const part = file + '.part'
  const hash = createHash('sha256')
  const out = createWriteStream(part)
  let received = 0
  let shown = -1
  try {
    if (!res.body) throw new UiError('the download was empty')
    for await (const chunk of res.body as unknown as AsyncIterable<Uint8Array>) {
      hash.update(chunk)
      if (!out.write(chunk)) await new Promise((r) => out.once('drain', r))
      received += chunk.length
      const pct = total ? Math.floor((received / total) * 100) : 0
      if (pct !== shown) { shown = pct; progress({ tool: 'update', received, total, phase: 'download' }) }
    }
    await new Promise<void>((ok, fail) => out.end((e?: Error | null) => (e ? fail(e) : ok())))
  } catch (e) {
    out.destroy()
    rmSync(part, { force: true })
    progress({ tool: 'update', received, total, phase: 'error', message: (e as Error).message })
    throw e
  }
  if (hash.digest('hex') !== want) {
    rmSync(part, { force: true })
    progress({ tool: 'update', received, total, phase: 'error', message: t('damaged download') })
    throw new UiError('the update download is damaged ({file}); try again', { file: name })
  }
  renameSync(part, file)
  // Older downloads only take up room.
  for (const f of readdirSync(to.dir)) if (f !== name && /^CleanMyTV-/.test(f)) rmSync(join(to.dir, f), { force: true })
  log(TASK, 'ok', `${name} downloaded and checked`)
  progress({ tool: 'update', received, total, phase: 'done' })
  return file
}

/** The .app bundle around the running executable, or null if it isn't in one. */
export function appBundle(exe: string): string | null {
  const bundle = resolve(exe, '..', '..', '..')
  return bundle.endsWith('.app') ? bundle : null
}

/**
 * Why this copy of the app can't be swapped for the new one, or null when it can: a copy running from the
 * disk image, or from Downloads under App Translocation (a read-only copy macOS makes), stays as it is.
 */
export function cantReplace(bundle: string | null, writable: (p: string) => boolean): string | null {
  if (!bundle) return 'it is not running from an app bundle'
  if (bundle.includes('/AppTranslocation/')) return 'macOS runs it from a read-only copy'
  if (bundle.startsWith('/Volumes/')) return 'it runs from the disk image'
  if (!writable(dirname(bundle)) || !writable(bundle)) return `this user can't write to ${dirname(bundle)}`
  return null
}

const canWrite = (p: string): boolean => {
  try { accessSync(p, constants.W_OK); return true } catch { return false }
}

// Waits for the app to quit, puts the new copy in its place (keeping the old one until the new one is in),
// lets go of the disk image and opens the app. Paths come in as arguments, never pasted into the script.
const MAC_SWAP = `#!/bin/bash
pid="$1"; src="$2"; dest="$3"; mnt="$4"
for _ in $(seq 1 100); do kill -0 "$pid" 2>/dev/null || break; sleep 0.3; done
new="$dest.new"; old="$dest.old"
rm -rf "$new" "$old"
if /usr/bin/ditto "$src" "$new" && mv "$dest" "$old" && mv "$new" "$dest"; then
  rm -rf "$old"
else
  rm -rf "$new"
  [ -d "$dest" ] || mv "$old" "$dest"
fi
/usr/bin/hdiutil detach -quiet "$mnt" || /usr/bin/hdiutil detach -quiet -force "$mnt"
/usr/bin/open "$dest"
`

/**
 * Installs a downloaded, checked update. On success the app is about to quit; `quit` does that, and the
 * installer or the swap script opens the new version.
 */
export async function install(file: string, to: { platform: NodeJS.Platform; exe: string; dir: string; quit: () => void; openPath: (p: string) => Promise<string> }): Promise<ApplyResult> {
  if (!existsSync(file)) throw new UiError('Download the update first.')
  if (to.platform === 'win32') {
    log(TASK, 'info', 'running the installer; CleanMyTV opens again when it is done')
    // electron-builder's NSIS installer: silent, over the copy that is there, then starts the app.
    spawn(file, ['--updated', '/S', '--force-run'], { detached: true, stdio: 'ignore' }).unref()
    setTimeout(to.quit, 500)
    return { ok: true, summary: t('Installing the update. CleanMyTV closes and opens again in a moment.'), warnings: [] }
  }
  if (to.platform !== 'darwin') throw new UiError('Updating from the app works on Mac and Windows.')
  const bundle = appBundle(to.exe)
  const why = cantReplace(bundle, canWrite)
  if (why || !bundle) {
    log(TASK, 'info', `can't replace this copy: ${why}; opening the disk image`)
    await to.openPath(file)
    return { ok: true, summary: t("The new version's disk image is open in Finder. Quit CleanMyTV, drag the new one into Applications and replace the old one, then open it."), warnings: [] }
  }
  const mnt = mkdtempSync(join(to.dir, 'mnt-'))
  await run('/usr/bin/hdiutil', ['attach', '-nobrowse', '-readonly', '-noautoopen', '-mountpoint', mnt, file])
  const app = readdirSync(mnt).find((f) => f.endsWith('.app'))
  if (!app) {
    await run('/usr/bin/hdiutil', ['detach', '-quiet', mnt]).catch(() => undefined)
    throw new UiError('the disk image has no app in it')
  }
  const script = join(to.dir, 'swap.sh')
  writeFileSync(script, MAC_SWAP, { mode: 0o700 })
  log(TASK, 'info', `replacing ${bundle}; CleanMyTV opens again when it is done`)
  spawn('/bin/bash', [script, String(process.pid), join(mnt, app), bundle, mnt], { detached: true, stdio: 'ignore' }).unref()
  setTimeout(to.quit, 300)
  return { ok: true, summary: t('Installing the update. CleanMyTV closes and opens again in a moment.'), warnings: [] }
}
