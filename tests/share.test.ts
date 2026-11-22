// Share my list: the issue text and link, what the app reads from the TV for it, and each TV keeping its
// own saved list and restore point when two TVs of one model share a house.
import { existsSync, mkdirSync, mkdtempSync, realpathSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import type { ShareData } from '../src/shared/api'
import { MAX_URL, issueBody, listFile, shareIssue } from '../src/shared/share'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import * as share from '../src/main/sharelist'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
configurePaths(resolve('resources'), join(home, 'data'))

const data = (off: number, kept = 0): ShareData => ({
  app: '1.2.3',
  tv: { manufacturer: 'Acme', model: 'TV-55', device: 'acme55', product: 'acme55_eu', android: '12', sdk: 31, build: 'ABC.123', fingerprint: 'Acme/acme55/acme55:12/ABC.123/1:user/release-keys' },
  matched: null,
  home: 'com.acme.launcher/.Home',
  stockLauncher: 'com.acme.launcher',
  lists: [{
    id: 'custom', title: 'Custom list',
    off: Array.from({ length: off }, (_, i) => ({ pkg: `com.acme.bloat${i}`, note: i % 2 ? 'ads' : '', ...(i === 0 ? { on: true } : {}) })),
    kept: Array.from({ length: kept }, (_, i) => ({ pkg: `com.acme.keep${i}`, note: 'voice' }))
  }],
  keptLauncher: { pkg: 'com.google.android.apps.tv.launcherx', why: 'owner' }
})

describe('the issue', () => {
  it('reads like a [device] issue, with a list file the app itself can parse', () => {
    const d = data(3, 2)
    const issue = shareIssue(d, '')
    expect(issue.title).toBe('[device] Acme TV-55 (device acme55, Android 12): list from CleanMyTV')
    expect(issue.labels).toEqual(['enhancement'])
    expect(issue.fits).toBe(true)
    expect(issue.body).toContain('- `ro.product.device = acme55`')
    expect(issue.body).toContain('## Switched off (3)')
    expect(issue.body).toContain('`com.google.android.apps.tv.launcherx`: the owner keeps it on')
    expect(issue.body).toMatch(/## On again\n.*`com\.acme\.bloat0`/)
    expect(issue.body).toContain('Nothing so far.')
    const parsed = D.parseList(listFile(d))
    expect(parsed.entries.filter((e) => e.suggested).map((e) => e.pkg)).toEqual(['com.acme.bloat0', 'com.acme.bloat1', 'com.acme.bloat2'])
    expect(parsed.entries.filter((e) => !e.suggested).map((e) => e.pkg)).toEqual(['com.acme.keep0', 'com.acme.keep1'])
    const url = new URL(issue.url)
    expect(url.origin + url.pathname).toBe('https://github.com/wingedpanther/cleanmytv/issues/new')
    expect(url.searchParams.get('body')).toBe(issue.body)
    expect(url.searchParams.get('labels')).toBe('enhancement')
  })

  it('takes addresses and email addresses out of what the owner types, and labels it a bug', () => {
    const issue = shareIssue(data(1), 'Voice stopped. My TV is at 192.168.1.20, mail me at jo@example.org')
    expect(issue.body).not.toContain('192.168.1.20')
    expect(issue.body).not.toContain('jo@example.org')
    expect(issue.body).toContain('Voice stopped.')
    expect(issue.labels).toEqual(['enhancement', 'bug'])
  })

  it('says so when Clean up has not switched anything off yet', () => {
    expect(issueBody({ ...data(0), lists: [] }, '')).toContain("Nothing yet: Clean up hasn't switched anything off")
  })

  it('opens a short issue when the full text is too long for a link, and keeps the full text whole', () => {
    const issue = shareIssue(data(600, 200), 'x'.repeat(20_000))
    expect(issue.fits).toBe(false)
    expect(issue.url.length).toBeLessThanOrEqual(MAX_URL)
    expect(issue.body).toContain('com.acme.bloat599')
    expect(issue.body).toContain('x'.repeat(20_000))
    const short = new URL(issue.url).searchParams.get('body') ?? ''
    expect(short).toContain('paste it below')
    expect(short).toContain('`ro.product.model = TV-55`')
    expect(short).not.toContain('com.acme.bloat0')
  })
})

describe('on a TV', () => {
  let tv: FakeTv
  let server: Server
  const SERIAL = 'QX7Z0042KD9'

  beforeAll(async () => {
    tv = newTv()
    tv.serial = SERIAL
    for (const p of ['com.google.android.tvlauncher', 'com.sony.dtv.promos', 'com.google.android.feedback']) tv.packages.set(p, 'enabled')
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('tells the TV apart by a hash of its serial, never the serial', () => {
    const id = T.getStatus().probe?.id
    expect(id).toMatch(/^[0-9a-f]{12}$/)
    expect(JSON.stringify(T.getStatus())).not.toContain(SERIAL)
  })

  it("gives the TV its own saved list, and shares what's off and what's kept without the address", async () => {
    const r = await D.apply({ listId: 'custom', disable: ['com.sony.dtv.promos'], enable: [] })
    expect(r.ok).toBe(true)
    const probe = T.getStatus().probe!
    expect(D.savedListPath('custom', probe)).toMatch(new RegExp(`custom-bravia-vh2-${probe.id}\\.txt$`))
    expect(D.loadRecord(D.recordPath(D.savedListPath('custom', probe), '127.0.0.1')).tv).toBe(probe.id)

    const info = await share.info('9.9.9')
    expect(info.tv).toMatchObject({ manufacturer: 'Sony', model: 'BRAVIA 4K VH2', device: 'BRAVIA_VH2', android: '12' })
    expect(info.tv.fingerprint).toContain('BRAVIA_VH2:12')
    expect(info.lists).toHaveLength(1)
    expect(info.lists[0].off.map((e) => e.pkg)).toEqual(['com.sony.dtv.promos'])
    expect(info.lists[0].kept.map((e) => e.pkg)).toContain('com.google.android.feedback')
    const text = JSON.stringify(info) + shareIssue(info, '').body
    expect(text).not.toContain('127.0.0.1')
    expect(text).not.toContain(SERIAL)
  })

  it('keeps using the shared saved list where this TV already has a restore point for it', async () => {
    const probe = T.getStatus().probe!
    const shared = join(home, '.cleanyourtv', 'lists', 'acme-shared.txt')
    mkdirSync(join(shared, '..'), { recursive: true })
    writeFileSync(shared, 'com.sony.dtv.promos\n')
    expect(D.savedListPath('acme-shared', probe)).toMatch(/acme-shared-[0-9a-f]{12}\.txt$/)
    await D.saveRecord(shared, '127.0.0.1', ['com.sony.dtv.promos'], probe.device)
    expect(D.savedListPath('acme-shared', probe)).toBe(shared)
  })

  it("won't use or move the restore point of another TV of the same model", () => {
    const probe = T.getStatus().probe!
    const list = join(home, '.cleanyourtv', 'lists', 'other.txt')
    writeFileSync(list, 'com.sony.dtv.promos\n')
    const theirs = { version: 1, created: '2026-01-01T00:00:00+0000', host: '127.0.0.1', home: null, settings: {}, packages: {}, device: probe.device, tv: 'ffffffffffff' }
    D.writeRecord(D.recordPath(list, '127.0.0.1'), { ...theirs, list: realpathSync.native(list) })
    expect(() => D.findRecord(list, '127.0.0.1', probe.device)).toThrow(/another TV of the same model/)
    // The same TV at another address: its record would move to the new name, but this one isn't ours.
    const moved = D.recordPath(list, '127.0.0.1')
    expect(D.findRecord(list, '10.0.0.77', probe.device).record).toBeNull()
    expect(existsSync(moved)).toBe(true)
  })
})
