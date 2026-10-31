// The core package guard: what a TV needs to boot never gets a pm disable-user, whatever asks for it.
import { appendFileSync, mkdtempSync, readFileSync, readdirSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
configurePaths(resolve('resources'), join(home, 'data'))
const UPSTREAM = process.env.UPSTREAM_DIR

describe('which packages are core', () => {
  it.each(['android', 'android.ext.services', 'com.android.systemui', 'com.android.tv.settings', 'com.android.settings',
    'com.google.android.packageinstaller', 'com.android.permissioncontroller', 'com.google.android.overlay.modules.ext.services',
    'com.android.providers.settings', 'com.android.providers.tv', 'com.cltv.mal', 'com.mediatek.tis'])('%s is', (pkg) => {
    expect(D.isCore(pkg)).toBe(true)
  })

  it.each(['com.android.providers.calendar', 'com.android.providers.contacts', 'com.android.settings.intelligence',
    'android.autoinstalls.config.google', 'com.sony.dtv.promos', 'com.mediatek.wwtv.tvcenter'])('%s is not', (pkg) => {
    expect(D.isCore(pkg)).toBe(false)
  })

  it('uses the same patterns as the pinned upstream', () => {
    if (!UPSTREAM) return
    const py = readFileSync(join(UPSTREAM, 'tools', 'recovery.py'), 'utf8')
    const pattern = (name: string): string | undefined => new RegExp(`^${name} = re\\.compile\\(r"(.+)"\\)$`, 'm').exec(py)?.[1]
    expect(D.CORE_RE.source).toBe(pattern('CORE_RE'))
    expect(D.CORE_OK.source).toBe(pattern('CORE_OK'))
  })

  it('is never something a bundled list switches off, so no default cleanup changes', () => {
    const examples = resolve('resources/upstream/examples')
    const hits = readdirSync(examples, { withFileTypes: true }).filter((e) => e.isDirectory()).flatMap((e) =>
      D.parseList(readFileSync(join(examples, e.name, 'debloat.txt'), 'utf8')).entries.filter((x) => x.suggested).map((x) => x.pkg).filter(D.isCore))
    expect(hits).toEqual([])
  })
})

describe('on a TV', () => {
  let tv: FakeTv
  let server: Server
  const SYSTEMUI = 'com.android.systemui'
  const MAL = 'com.cltv.mal'
  const disables = (): string[] => tv.commands.filter((c) => c.startsWith('pm disable-user'))

  beforeAll(async () => {
    tv = newTv()
    for (const p of [SYSTEMUI, MAL, 'com.sony.dtv.promos', 'com.google.android.tvlauncher']) tv.packages.set(p, 'enabled')
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it("doesn't offer a core package a list author kept, unless it is already off", async () => {
    const LIST = 'anker-nebula-mars-3-air'
    const SERVICE = 'com.mediatek.tv.service'
    const kept = D.parseList(readFileSync(resolve('resources/upstream/examples', LIST, 'debloat.txt'), 'utf8')).entries.find((e) => e.pkg === SERVICE)
    expect(kept?.suggested).toBe(false)
    tv.packages.set(SERVICE, 'enabled')
    expect((await D.plan(LIST)).entries.map((e) => e.pkg)).not.toContain(SERVICE)
    tv.packages.set(SERVICE, 'disabled')
    expect((await D.plan(LIST)).entries.find((e) => e.pkg === SERVICE)?.state).toBe('disabled')
  })

  it('leaves core packages out of the package picker', async () => {
    expect((await D.tvPackages()).map((e) => e.pkg)).toContain('com.sony.dtv.promos')
    expect((await D.tvPackages()).map((e) => e.pkg)).not.toContain(SYSTEMUI)
  })

  it('refuses a core package, even with every override on, and cleans up the rest', async () => {
    const r = await D.apply({ listId: 'custom', disable: [SYSTEMUI, MAL, SYSTEMUI, 'com.sony.dtv.promos'], enable: [], forceHome: true, forceSetupwraith: true })
    expect(disables()).toEqual(['pm disable-user --user 0 com.sony.dtv.promos'])
    expect(tv.packages.get(SYSTEMUI)).toBe('enabled')
    expect(r.warnings.join('\n')).toContain(`Skipped ${SYSTEMUI}, ${MAL}:`)
    const saved = D.parseList(readFileSync(D.savedListPath('custom', T.getStatus().probe!), 'utf8'))
    expect(saved.entries.filter((e) => e.suggested).map((e) => e.pkg)).toEqual(['com.sony.dtv.promos'])
  })

  it('still switches a core package back on', async () => {
    tv.packages.set(MAL, 'disabled')
    expect((await D.apply({ listId: 'custom', disable: [], enable: [MAL] })).ok).toBe(true)
    expect(tv.packages.get(MAL)).toBe('enabled')
  })

  it('keeps a core package an older version switched off, so it can come back', async () => {
    const file = D.savedListPath('custom', T.getStatus().probe!)
    appendFileSync(file, `${SYSTEMUI}\n`)
    await D.saveRecord(file, T.host(), [SYSTEMUI], T.getStatus().probe!.device || undefined)
    tv.packages.set(SYSTEMUI, 'disabled')
    await D.apply({ listId: 'custom', disable: [], enable: [] })
    expect((await D.plan('custom')).entries.find((e) => e.pkg === SYSTEMUI)?.state).toBe('disabled')
    expect(tv.commands).not.toContain(`pm disable-user --user 0 ${SYSTEMUI}`)
    expect((await D.restore('custom')).ok).toBe(true)
    expect(tv.packages.get(SYSTEMUI)).toBe('enabled')
    // Back on, it must not come back ticked to go off again.
    expect((await D.plan('custom')).entries.map((e) => e.pkg)).not.toContain(SYSTEMUI)
  })
})
