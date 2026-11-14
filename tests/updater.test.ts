import { createHash } from 'node:crypto'
import { existsSync, mkdtempSync, readdirSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { setSinks } from '../src/main/log'
import { appBundle, assetName, cantReplace, download, sumFor } from '../src/main/updater'
import type { DownloadProgress } from '@shared/api'

const BODY = Buffer.alloc(300 * 1024, 7)
const SUM = createHash('sha256').update(BODY).digest('hex')
const NAME = 'CleanMyTV-0.8.0-arm64.dmg'

/** GitHub as the app sees it: release downloads redirect to the asset host. */
function github(opts: { body?: Buffer; sums?: string; to?: string } = {}): { fetch: typeof fetch; hits: string[] } {
  const hits: string[] = []
  const f = async (input: URL | string): Promise<Response> => {
    const u = new URL(String(input))
    hits.push(u.href)
    if (u.hostname === 'github.com') {
      return new Response(null, { status: 302, headers: { location: `https://${opts.to ?? 'release-assets.githubusercontent.com'}/x${u.pathname}` } })
    }
    if (u.pathname.endsWith('SHA256SUMS.txt')) return new Response(opts.sums ?? `${SUM}  ${NAME}\n${'0'.repeat(64)}  CleanMyTV-Setup-0.8.0.exe\n`)
    const b = opts.body ?? BODY
    return new Response(new Uint8Array(b), { headers: { 'content-length': String(b.length) } })
  }
  return { fetch: f as unknown as typeof fetch, hits }
}

describe('updating from the app', () => {
  it('picks the file for this computer, and none on Linux', () => {
    expect(assetName('0.8.0', 'darwin', 'arm64')).toBe('CleanMyTV-0.8.0-arm64.dmg')
    expect(assetName('0.8.0', 'darwin', 'x64')).toBe('CleanMyTV-0.8.0.dmg')
    expect(assetName('0.8.0', 'win32', 'x64')).toBe('CleanMyTV-Setup-0.8.0.exe')
    expect(assetName('0.8.0', 'linux', 'x64')).toBeNull()
    expect(assetName('0.8.0; rm -rf /', 'darwin', 'arm64')).toBeNull()
  })

  it('reads the checksum sha256sum wrote', () => {
    expect(sumFor(`${SUM}  ${NAME}\n`, NAME)).toBe(SUM)
    expect(sumFor(`${SUM} *${NAME}\r\n`, NAME)).toBe(SUM)
    expect(sumFor(`${SUM}  other.dmg\n`, NAME)).toBeNull()
  })

  it('downloads, checks and keeps the file, with progress', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'cytv-upd-'))
    writeFileSync(join(dir, 'CleanMyTV-0.7.3-arm64.dmg'), 'old')
    const seen: DownloadProgress[] = []
    setSinks(() => {}, (p) => seen.push(p))
    const gh = github()
    const file = await download('0.8.0', { platform: 'darwin', arch: 'arm64', dir, fetchImpl: gh.fetch })
    expect(file).toBe(join(dir, NAME))
    expect(readdirSync(dir)).toEqual([NAME])
    expect(seen.at(-1)).toMatchObject({ tool: 'update', phase: 'done', received: BODY.length })
    // A second press uses the checked copy.
    const again = github()
    await download('0.8.0', { platform: 'darwin', arch: 'arm64', dir, fetchImpl: again.fetch })
    expect(again.hits.some((h) => h.endsWith(NAME))).toBe(false)
  })

  it('throws away a download that does not match its checksum', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'cytv-upd-'))
    await expect(download('0.8.0', { platform: 'darwin', arch: 'arm64', dir, fetchImpl: github({ body: Buffer.from('tampered') }).fetch })).rejects.toThrow('damaged')
    expect(existsSync(join(dir, NAME))).toBe(false)
    expect(existsSync(join(dir, NAME + '.part'))).toBe(false)
  })

  it('follows GitHub to its asset host and nowhere else', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'cytv-upd-'))
    await expect(download('0.8.0', { platform: 'darwin', arch: 'arm64', dir, fetchImpl: github({ to: 'evil.example' }).fetch })).rejects.toThrow('somewhere unexpected')
    await expect(download('0.8.0', { platform: 'darwin', arch: 'arm64', dir, fetchImpl: github({ sums: 'nothing here' }).fetch })).rejects.toThrow('no checksum')
  })

  it('replaces only a Mac copy it can write to', () => {
    expect(appBundle('/Applications/CleanMyTV.app/Contents/MacOS/CleanMyTV')).toBe('/Applications/CleanMyTV.app')
    expect(appBundle('/opt/cleanmytv/cleanmytv')).toBeNull()
    const yes = (): boolean => true
    expect(cantReplace('/Applications/CleanMyTV.app', yes)).toBeNull()
    expect(cantReplace('/private/var/folders/x/AppTranslocation/ABC/d/CleanMyTV.app', yes)).toContain('read-only')
    expect(cantReplace('/Volumes/CleanMyTV 0.7.3/CleanMyTV.app', yes)).toContain('disk image')
    expect(cantReplace('/Applications/CleanMyTV.app', (p) => p !== '/Applications')).toContain("can't write")
  })
})
