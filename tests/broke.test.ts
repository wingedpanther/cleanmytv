import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import { setSinks } from '../src/main/log'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import * as H from '../src/main/home'
import * as B from '../src/main/broke'
import { SYMPTOMS, stepsFor, warningFor } from '../src/shared/symptoms'
import { defaultOff, defaultTick } from '../src/renderer/src/lib/ticks'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
const resources = join(home, 'resources')
mkdirSync(resources)
symlinkSync(resolve('resources/upstream'), join(resources, 'upstream'))
writeFileSync(join(resources, 'home.apk'), randomBytes(100 * 1024))
writeFileSync(join(resources, 'home.json'), JSON.stringify({ versionName: '1.0.0' }))
configurePaths(resources, join(home, 'data'))

const LX = 'com.google.android.apps.tv.launcherx'
const VOICE = 'com.google.android.katniss'
const REMOTE = 'com.google.android.tv.remote.service'
const CAST = 'com.google.android.apps.mediashell'
const PROMOS = 'com.sony.dtv.promos'

describe('what broke for other people', () => {
  it('names only packages from the bundled lists or the app\'s own notes', () => {
    const examples = resolve('resources/upstream/examples')
    const known = readdirSync(examples).flatMap((id) => readdirSync(join(examples, id)).filter((f) => /^debloat.*\.txt$/.test(f)).map((f) => readFileSync(join(examples, id, f), 'utf8'))).join('\n')
    const code = readFileSync('src/main/debloat.ts', 'utf8')
    const invented = SYMPTOMS.flatMap((s) => s.links).filter((l) => l.pkg).map((l) => l.pkg!).filter((p) => !T.PACKAGE_RE.test(p) || (!known.includes(p) && !code.includes(`'${p}'`)))
    expect(invented).toEqual([])
    expect(SYMPTOMS.flatMap((s) => s.links).every((l) => (l.pkg || l.like) && l.why().length > 0)).toBe(true)
  })

  it('offers what is off for a symptom in steps, the likeliest first, each package once', () => {
    const off = ['com.google.android.speech.pumpkin', REMOTE, VOICE, 'org.liskovsoft.androidtv.rukeyboard', 'com.vendor.ads']
    expect(stepsFor('remote', off).map((s) => s.map((l) => l.pkg))).toEqual([[VOICE, REMOTE], ['com.google.android.speech.pumpkin']])
    expect(stepsFor('keyboard', off).map((s) => s.map((l) => l.pkg))).toEqual([['org.liskovsoft.androidtv.rukeyboard'], [REMOTE]])
    expect(stepsFor('sound', off)).toEqual([])
  })

  it('warns with the report that fits the TV, and the issue it is in', () => {
    expect(warningFor(LX, 'Sony')).toMatchObject({ issue: 99 })
    expect(warningFor('com.mitv.tvhome.atv', 'Xiaomi')).toMatchObject({ issue: 38 })
    expect(warningFor('com.mitv.tvhome.atv', 'TCL')).toBeUndefined()
    expect(warningFor('com.sony.dtv.seeds.iot.remotestart', 'Sony')?.text).toMatch(/standby/)
    expect(warningFor('com.vendor.ads', 'Sony')).toBeUndefined()
  })
})

describe('Something broke, on a TV', () => {
  let tv: FakeTv
  let server: Server
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    tv.settings.set('default_input_method', 'com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME')
    for (const p of [LX, D.SETUPWRAITH, VOICE, REMOTE, CAST, PROMOS, 'com.google.android.speech.pumpkin', 'com.google.android.inputmethod.latin']) tv.packages.set(p, 'enabled')
    tv.home = `${LX}/.home.HomeActivity`
    tv.homeClaims = [`${LX}/.home.HomeActivity`, `${D.SETUPWRAITH}/.RecoveryActivity`]
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
    expect((await H.install()).ok).toBe(true)
    const r = await D.apply({ listId: 'custom', disable: [PROMOS, VOICE, REMOTE, CAST, D.SETUPWRAITH, 'com.google.android.speech.pumpkin'], enable: [] })
    expect(r.ok).toBe(true)
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('turns back on only what Clean up switched off for the symptom, and keeps the restore record', async () => {
    const offer = await B.steps('remote')
    expect(offer.steps.map((s) => s.map((l) => l.pkg))).toEqual([[VOICE, REMOTE], ['com.google.android.speech.pumpkin', LX]])
    await expect(B.fix('remote', [PROMOS])).rejects.toThrow(/None of these/)

    const lines: string[] = []
    setSinks((l) => lines.push(`[${l.task}] ${l.text}`), () => {})
    const r = await B.fix('remote', [VOICE, REMOTE])
    expect(lines).toContain(`[something broke] Remote buttons or voice don't work: turning back on ${VOICE}, ${REMOTE}`)
    expect(lines).toContain(`[something broke] Turned back on: ${VOICE}, ${REMOTE}.`)
    expect(r).toMatchObject({ ok: true, summary: `Turned back on: ${VOICE}, ${REMOTE}.` })
    expect(tv.packages.get(VOICE)).toBe('enabled')
    expect(tv.packages.get(REMOTE)).toBe('enabled')
    expect(tv.packages.get(PROMOS)).toBe('disabled')
    expect([...(await D.stillOff()).keys()]).not.toContain(VOICE)
    const list = D.savedListPath('custom', T.getStatus().probe!)
    expect(D.loadRecord(D.recordPath(list, '127.0.0.1')).packages[VOICE]).toBe('enabled')
    expect(readFileSync(list, 'utf8')).toMatch(new RegExp(`^# ${VOICE.replace(/\./g, '\\.')}`, 'm'))
  })

  it("leaves them unticked in Clean up, so the next run doesn't switch them off again", async () => {
    const plan = await D.plan('custom')
    const voice = plan.entries.find((e) => e.pkg === VOICE)!
    expect(voice).toMatchObject({ state: 'enabled', turnedOn: 'remote' })
    expect(defaultTick(voice)).toBe(false)
    expect(defaultOff(plan.entries)).not.toContain(REMOTE)
    expect(defaultTick({ pkg: 'com.vendor.ads', note: '', suggested: true, state: 'enabled', common: 3, turnedOn: 'sound' })).toBe(false)
    await D.apply({ listId: 'custom', disable: defaultOff(plan.entries), enable: [] })
    expect(tv.packages.get(VOICE)).toBe('enabled')
  })

  it("turns Google TV's launcher on before the setup wizard, and keeps CleanMyTV Home as Home", async () => {
    const offer = await B.steps('casting')
    expect(offer.steps.map((s) => s.map((l) => l.pkg))).toEqual([[CAST], [LX, D.SETUPWRAITH]])
    // Asked for the wizard alone, the launcher still goes first.
    const r = await B.fix('casting', [D.SETUPWRAITH])
    expect(r.ok).toBe(true)
    expect(tv.packages.get(LX)).toBe('enabled')
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('enabled')
    expect(tv.homeConfig.stock_redirect).toBe(LX)
    const on = tv.commands.filter((c) => c === `pm enable --user 0 ${LX}` || c === `pm enable ${D.SETUPWRAITH}`)
    expect(on).toEqual([`pm enable --user 0 ${LX}`, `pm enable ${D.SETUPWRAITH}`])
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
  })

  it('says what an apply that only switches things back on did', async () => {
    tv.packages.set('com.vendor.extra', 'enabled')
    await D.apply({ listId: 'custom', disable: ['com.vendor.extra'], enable: [] })
    const r = await D.apply({ listId: 'custom', disable: [], enable: ['com.vendor.extra'] })
    expect(r.summary).toBe('Switched 1 package back on.')
  })

  it('forgets one ticked by hand, and Restore still puts back the rest', async () => {
    await D.apply({ listId: 'custom', disable: [VOICE], enable: [] })
    expect(tv.packages.get(VOICE)).toBe('disabled')
    expect((await D.plan('custom')).entries.find((e) => e.pkg === VOICE)?.turnedOn).toBeUndefined()
    expect((await D.restore('custom')).ok).toBe(true)
    expect(tv.packages.get(PROMOS)).toBe('enabled')
    expect(tv.packages.get(VOICE)).toBe('enabled')
    expect((await B.steps('remote')).steps).toEqual([])
  })
})
