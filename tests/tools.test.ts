import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as D from '../src/main/debloat'
import * as H from '../src/main/home'
import { tellHome } from '../src/main/switchedoff'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
process.env.USERPROFILE = home
configurePaths(resolve('resources'), join(home, 'data'))

const OURS = 'io.github.wingedpanther.cleanmytv.home'

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
})
