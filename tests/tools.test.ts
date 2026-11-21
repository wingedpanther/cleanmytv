import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import * as H from '../src/main/home'
import * as S from '../src/main/storage'
import { fullReport, reportConfig, uninstalledForUser } from '../src/main/report'
import { tellHome } from '../src/main/switchedoff'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
configurePaths(resolve('resources'), join(home, 'data'))

const OURS = 'io.github.wingedpanther.cleanmytv.home'
const MB = 1024 * 1024

// As Android 14 prints it once the daily measurement has run (DiskStatsService.reportCachedValues).
const DISKSTATS = `Latency: 1ms [512B Data Write]
Data-Free: 8131144K / 10167132K total = 79% free
App Size: 300000000
App Data Size: 120000000
App Cache Size: 50000000
Package Names: ["com.netflix.ninja","com.sony.dtv.promos","com.google.android.youtube.tv","com.gone.app"]
App Sizes: [${120 * MB},${30 * MB},${150 * MB},${1 * MB}]
App Data Sizes: [${200 * MB},${2 * MB},${40 * MB},0]
Cache Sizes: [${10 * MB},${1 * MB},${90 * MB},0]
`

describe('reading what takes the space', () => {
  it("reads diskstats' per-app sizes, and nothing from a TV that hasn't measured yet", () => {
    const apps = S.parseDiskstats(DISKSTATS)
    expect(apps).toHaveLength(4)
    expect(apps?.[2]).toEqual({ pkg: 'com.google.android.youtube.tv', app: 150 * MB, data: 40 * MB, cache: 90 * MB })
    expect(S.parseDiskstats('Data-Free: 8131144K / 10167132K total = 79% free\n')).toBeNull()
    expect(S.parseDiskstats('Package Names: ["a.b"]\nApp Sizes: [1,2]\n')).toBeNull()
    expect(S.parseDiskstats('Package Names: ["a.b"\nApp Sizes: [1]\n')).toBeNull()
    // Older Android has no data sizes: the code still counts.
    expect(S.parseDiskstats('Package Names: ["a.b","bad;name"]\nApp Sizes: [5,6]\nCache Sizes: [1]\n')).toEqual([{ pkg: 'a.b', app: 5, data: 0, cache: 0 }])
  })

  it('finds system apps that carry an update, from their code in /data/app', () => {
    const list = [
      'package:/data/app/~~5y-qjepkhZFH29rRqKq47Q==/com.android.vending-zZjhA3M4JwFwA5o0KeCXlg==/base.apk=com.android.vending',
      'package:/system/priv-app/Settings/Settings.apk=com.android.tv.settings',
      'package:/product/app/YouTube/YouTube.apk=com.google.android.youtube.tv',
      'package:/data/app/com.google.android.youtube.tv-1/base.apk=com.google.android.youtube.tv'
    ].join('\n')
    expect([...S.updatedSystemApps(list)].sort()).toEqual(['com.android.vending', 'com.google.android.youtube.tv'])
  })

  it("reads the du loop's sizes in KB", () => {
    expect(S.parseCodeSizes('56044 com.google.android.youtube.tv\n 2068 io.github.cmtvdemo.files\n\nx y z\n')).toEqual(new Map([
      ['com.google.android.youtube.tv', 56044 * 1024], ['io.github.cmtvdemo.files', 2068 * 1024]
    ]))
  })
})

describe('a report for an issue', () => {
  it('leaves secrets out of Home settings and hides where the owner lives', () => {
    const cfg = reportConfig({ accent: '#FF3000', pin_focus: true, lock_pin_set: true, lock_pin: '1234', api_token: 'abc', greeting: 'Sam', weather_place: 'Dublin', weather_lat: 53.3, weather_lon: 0 })
    expect(cfg).toEqual({ accent: '#FF3000', pin_focus: true, lock_pin_set: true, greeting: '(hidden)', weather_place: '(hidden)', weather_lat: '(hidden)', weather_lon: 0 })
  })

  it('lists packages uninstalled for user 0', () => {
    expect(uninstalledForUser('package:a.b\npackage:c.d\npackage:e.f\n', 'package:c.d\n')).toEqual(['a.b', 'e.f'])
  })
})

describe('on a TV', () => {
  let tv: FakeTv
  let server: Server
  let port: number
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    tv.freeKb = 400_000
    for (const p of ['com.google.android.apps.tv.launcherx', 'com.netflix.ninja', 'com.sony.dtv.promos', 'com.sony.dtv.analytics', 'com.google.android.youtube.tv', OURS]) tv.packages.set(p, 'enabled')
    tv.packages.set('com.google.android.apps.tv.launcherx', 'disabled')
    tv.home = `${OURS}/.MainActivity`
    tv.homeClaims = [`${OURS}/.MainActivity`, 'com.google.android.apps.tv.launcherx/.home.HomeActivity']
    tv.homeConfig = { accent: '#FF3000', greeting: 'Sam', order: ['com.netflix.ninja'], stock_redirect: '', lock_on: true }
    tv.updatedSystem = new Map([['com.sony.dtv.promos', 30_000], ['com.google.android.youtube.tv', 150_000]])
    tv.uninstalledForUser = ['com.sony.dtv.gone']
    tv.logcat = '11-21 12:00:00.000  100  100 I Wifi: connected to 192.168.1.20\n'
    T.init(join(home, 'data', 'adbkey'), () => {})
    ;({ server, port } = await startFakeAdbd(tv))
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('tells CleanMyTV Home what Clean up keeps off, and says what came back after a system update', async () => {
    expect((await D.apply({ listId: 'custom', disable: ['com.sony.dtv.promos', 'com.sony.dtv.analytics', 'com.acme.bloat'], enable: [] })).ok).toBe(false)
    await tellHome()
    expect(tv.homeConfig.switched_off).toEqual(['com.acme.bloat', 'com.sony.dtv.analytics', 'com.sony.dtv.promos'])
    expect((await D.switchedOff()).back).toEqual([])

    tv.packages.set('com.sony.dtv.promos', 'enabled')
    expect((await D.switchedOff()).back).toEqual([{ listId: 'custom', packages: ['com.sony.dtv.promos'] }])
    const sets = tv.commands.length
    await tellHome()
    // The list didn't change, so Home isn't told again.
    expect(tv.commands.slice(sets).some((c) => c.includes('set64'))).toBe(false)
  })

  it('accepts switched_off as package names only', () => {
    expect(H.clean({ switched_off: ['a.b', 'a.b', 'c.d'] })).toEqual({ switched_off: ['a.b', 'c.d'] })
    expect(() => H.clean({ switched_off: ['rm -rf'] })).toThrow(/switched_off/)
  })

  it('saves a report with the TV, Home settings and logs, without addresses or the greeting', async () => {
    const text = await fullReport('9.9.9')
    expect(text).toMatch(/Build fingerprint: Sony\/BRAVIA_VH2/)
    expect(text).toMatch(/Uninstalled for user 0 \(1\):\ncom\.sony\.dtv\.gone/)
    expect(text).toMatch(/custom \(3\): com\.sony\.dtv\.promos, com\.sony\.dtv\.analytics, com\.acme\.bloat/)
    expect(text).toMatch(/CleanMyTV Home: 1\.0\.0/)
    expect(text).toMatch(/"greeting": "\(hidden\)"/)
    expect(text).toMatch(/mCurrentFocus=Window/)
    expect(text).toMatch(/This session's activity:/)
    expect(text).toMatch(/I Wifi: connected to x\.x\.x\.x/)
    expect(text).not.toMatch(/127\.0\.0\.1|192\.168|Sam/)
  })

  it('measures the apps, biggest first, and takes the update off a switched-off system app', async () => {
    tv.packages.set('com.sony.dtv.promos', 'disabled')
    tv.diskstats = DISKSTATS
    const sizes = await S.sizes()
    expect(sizes.source).toBe('diskstats')
    expect(sizes.apps.map((a) => a.pkg)).toEqual(['com.netflix.ninja', 'com.google.android.youtube.tv', 'com.sony.dtv.promos'])
    expect(sizes.apps[2]).toMatchObject({ state: 'disabled', updated: true })
    expect(sizes.storage?.freeKb).toBe(400_000)

    await expect(S.removeUpdate('com.google.android.youtube.tv')).rejects.toThrow(/is on/)
    await expect(S.removeUpdate('com.sony.dtv.analytics')).rejects.toThrow(/no update to remove/)
    const r = await S.removeUpdate('com.sony.dtv.promos')
    expect(r).toMatchObject({ ok: true, summary: expect.stringMatching(/29 MB back/) })
    expect(tv.commands).toContain('pm uninstall-system-updates com.sony.dtv.promos')
    expect(tv.packages.get('com.sony.dtv.promos')).toBe('disabled')

    tv.diskstats = undefined
    expect((await S.sizes()).source).toBe('code')
  })

  it('takes the update off with uninstall on Android 9, where the system version stays', async () => {
    tv.sdk = '28'
    tv.updatedSystem!.set('com.sony.dtv.analytics', 10_000)
    const r = await S.removeUpdate('com.sony.dtv.analytics')
    expect(r.ok).toBe(true)
    expect(tv.commands).toContain('pm uninstall com.sony.dtv.analytics')
    expect(tv.packages.get('com.sony.dtv.analytics')).toBe('disabled')
    tv.sdk = undefined
  })

  it('keeps nothing off after Restore, so nothing came back, and keeps a package added by hand in the list', async () => {
    tv.packages.set('com.acme.bloat', 'enabled')
    expect((await D.apply({ listId: 'custom', disable: ['com.acme.bloat'], enable: [] })).ok).toBe(true)
    await tellHome()
    expect(tv.homeConfig.switched_off).toContain('com.acme.bloat')
    expect((await D.restore('custom')).ok).toBe(true)
    expect(await D.switchedOff()).toEqual({ off: [], back: [] })
    await tellHome()
    expect(tv.homeConfig.switched_off).toEqual([])
    const plan = await D.plan('custom')
    expect(plan.entries.find((e) => e.pkg === 'com.acme.bloat')).toMatchObject({ added: true })
  })

  it('saves a report with only the computer when no TV is connected', async () => {
    T.disconnect()
    const text = await fullReport('9.9.9')
    expect(text).toMatch(/^CleanMyTV report/)
    expect(text).toMatch(/No TV was connected/)
    expect(text).not.toMatch(/TV log/)
  })
})
