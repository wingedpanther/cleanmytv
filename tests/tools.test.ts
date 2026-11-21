import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import * as H from '../src/main/home'
import { fullReport, reportConfig, uninstalledForUser } from '../src/main/report'
import { tellHome } from '../src/main/switchedoff'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
configurePaths(resolve('resources'), join(home, 'data'))

const OURS = 'io.github.wingedpanther.cleanmytv.home'

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
    for (const p of ['com.google.android.apps.tv.launcherx', 'com.netflix.ninja', 'com.sony.dtv.promos', 'com.sony.dtv.analytics', 'com.google.android.youtube.tv', OURS]) tv.packages.set(p, 'enabled')
    tv.packages.set('com.google.android.apps.tv.launcherx', 'disabled')
    tv.home = `${OURS}/.MainActivity`
    tv.homeClaims = [`${OURS}/.MainActivity`, 'com.google.android.apps.tv.launcherx/.home.HomeActivity']
    tv.homeConfig = { accent: '#FF3000', greeting: 'Sam', order: ['com.netflix.ninja'], stock_redirect: '', lock_on: true }
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
