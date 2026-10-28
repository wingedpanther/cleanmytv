import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as L from '../src/main/launcher'
import * as H from '../src/main/home'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
// A resources folder of its own: the real lists and a stand-in APK, so this runs without the launcher
// having been built (CI builds it in a separate job).
const resources = join(home, 'resources')
mkdirSync(resources)
symlinkSync(resolve('resources/upstream'), join(resources, 'upstream'))
writeFileSync(join(resources, 'home.apk'), randomBytes(100 * 1024))
writeFileSync(join(resources, 'home.json'), JSON.stringify({ versionName: '1.0.0' }))
configurePaths(resources, join(home, 'data'))

describe('CleanMyTV Home', () => {
  let tv: FakeTv
  let server: Server
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    for (const p of ['com.google.android.apps.tv.launcherx', 'com.netflix.ninja', 'com.google.android.youtube.tv']) tv.packages.set(p, 'enabled')
    tv.home = 'com.google.android.apps.tv.launcherx/.home.HomeActivity'
    tv.homeClaims = ['com.google.android.apps.tv.launcherx/.home.HomeActivity']
    tv.leanback = ['com.netflix.ninja', 'com.google.android.youtube.tv']
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('installs, starts, then hands Home over and disables the stock launcher', async () => {
    const r = await H.install()
    expect(r.ok).toBe(true)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('disabled')
    const order = tv.commands.filter((c) => /pm install|am start -W|pm disable-user|set-home-activity/.test(c))
    expect(order.map((c) => c.split(' ').slice(0, 2).join(' '))).toEqual(['pm install', 'am start', 'pm disable-user', 'cmd package'])
    expect(tv.commands).toContain('pm grant io.github.wingedpanther.cleanmytv.home android.permission.RECORD_AUDIO 2>/dev/null; echo audio-granted')
    expect(await H.status()).toMatchObject({ installed: true, isHome: true, versionName: '1.0.0', bundledVersion: '1.0.0', oldLauncher: false })
  })

  it('gives Home back to the stock launcher, then switches to CleanMyTV Home again', async () => {
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
    expect((await H.install()).ok).toBe(true)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
  })

  it('removes the old CleanYourTV launcher only once it is not Home, and clears its settings', async () => {
    tv.packages.set('com.gathin.tvhome', 'enabled')
    tv.homeClaims.push('com.gathin.tvhome/.MainActivity')
    tv.settings.set('enabled_accessibility_services', 'com.gathin.tvhome/.InputKeyService:com.other/.Svc')
    tv.home = 'com.gathin.tvhome/.MainActivity'
    expect((await H.status()).oldLauncher).toBe(true)
    expect((await L.removeOld()).ok).toBe(false)
    expect(tv.packages.has('com.gathin.tvhome')).toBe(true)
    // Installing over it: the stock launcher is already off, so nothing else is disabled.
    const before = tv.commands.length
    expect((await H.install()).ok).toBe(true)
    expect(tv.commands.slice(before).some((c) => c.startsWith('pm disable-user'))).toBe(false)
    expect((await L.removeOld()).ok).toBe(true)
    expect(tv.packages.has('com.gathin.tvhome')).toBe(false)
    expect(tv.settings.get('enabled_accessibility_services')).toBe('com.other/.Svc')
  })

  it('leaves Home where it was when the switch does not stick', async () => {
    expect((await L.useStock()).ok).toBe(true)
    tv.homeStuck = true
    const r = await L.switchTo('io.github.wingedpanther.cleanmytv.home/.MainActivity')
    tv.homeStuck = false
    expect(r.ok).toBe(false)
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
    expect((await H.install()).ok).toBe(true)
  })

  it('reads and changes CleanMyTV Home settings live, and refuses bad values', async () => {
    const first = await H.readConfig()
    expect(first).toMatchObject({ accent: '#FF3000', focus: 'glow', visualiser: true, columns: 6, has_wallpaper: false })
    const next = await H.setConfig({ accent: '#2ec4b6', focus: 'outline', columns: 12, viz_style: 'mirror', order: ['com.netflix.ninja'] })
    expect(next).toMatchObject({ accent: '#2EC4B6', focus: 'outline', columns: 8, viz_style: 'mirror', order: ['com.netflix.ninja'] })
    expect(tv.homeConfig.columns).toBe(8)
    // Quotes and shell syntax in the greeting travel as base64 and arrive as plain text.
    expect((await H.setConfig({ greeting: "Sam's TV $(reboot) 🎬" })).greeting).toBe("Sam's TV $(reboot) 🎬")
    expect(tv.commands.some((c) => c.includes('reboot'))).toBe(false)
    await expect(H.setConfig({ accent: "#FFF'; reboot; '" })).rejects.toThrow(/colour/)
    await expect(H.setConfig({ focus: 'spin' as never })).rejects.toThrow(/focus/)
    await expect(H.setConfig({ order: ['ok.pkg', 'bad;pkg'] })).rejects.toThrow(/package/)
  })

  it('sends layouts, quick settings, rows and folders, and refuses bad ones', async () => {
    const cfg = await H.setConfig({
      layout: 'rail', quick: ['settings', 'wifi'], sections: ['grid', 'dock'], top_space: 90,
      rows: [{ name: '  Watch\n', apps: ['com.netflix.ninja', 'com.netflix.ninja'] }],
      folders: [{ name: "Kids' stuff", apps: ['com.google.android.youtube.tv'] }]
    })
    expect(cfg).toMatchObject({ layout: 'rail', quick: ['settings', 'wifi'], sections: ['grid', 'dock'], top_space: 40 })
    expect(cfg.rows).toEqual([{ name: 'Watch', apps: ['com.netflix.ninja'] }])
    expect(cfg.folders[0].name).toBe("Kids' stuff")
    await expect(H.setConfig({ layout: 'grid' as never })).rejects.toThrow(/layout/)
    await expect(H.setConfig({ quick: ['settings', 'reboot' as never] })).rejects.toThrow(/quick/)
    await expect(H.setConfig({ folders: [{ name: '', apps: [] }] })).rejects.toThrow(/folders/)
    await expect(H.setConfig({ rows: [{ name: 'x', apps: ['bad;pkg'] }] })).rejects.toThrow(/rows/)
    await expect(H.setConfig({ rows: Array.from({ length: 9 }, (_, i) => ({ name: `r${i}`, apps: [] })) })).rejects.toThrow(/rows/)
  })

  it("lists the launcher's own apps and drops malformed entries", async () => {
    expect((await H.apps()).map((a) => a.pkg)).toEqual(['com.netflix.ninja', 'com.google.android.youtube.tv'])
  })

  it('maps remote buttons, switches the key service on and off, and reads the button log', async () => {
    const svc = 'io.github.wingedpanther.cleanmytv.home/io.github.wingedpanther.cleanmytv.home.KeyService'
    tv.settings.set('enabled_accessibility_services', 'com.other/.Svc')
    const cfg = await H.setConfig({ buttons: [{ code: 83, action: 'settings' }, { code: 83, action: 'home' }, { code: 183, action: 'app', app: 'com.netflix.ninja' }] })
    expect(cfg.buttons).toEqual([{ code: 83, action: 'settings' }, { code: 183, action: 'app', app: 'com.netflix.ninja' }])
    await expect(H.setConfig({ buttons: [{ code: 900, action: 'settings' }] })).rejects.toThrow(/buttons/)
    await expect(H.setConfig({ buttons: [{ code: 5, action: 'reboot' as never }] })).rejects.toThrow(/buttons/)
    await expect(H.setConfig({ buttons: [{ code: 5, action: 'app', app: 'bad;pkg' }] })).rejects.toThrow(/buttons/)
    expect((await H.setRemoteButtons(true)).buttons_on).toBe(true)
    expect(tv.settings.get('enabled_accessibility_services')).toBe(`com.other/.Svc:${svc}`)
    expect(tv.settings.get('accessibility_enabled')).toBe('1')
    expect((await H.setRemoteButtons(false)).buttons_on).toBe(false)
    expect(tv.settings.get('enabled_accessibility_services')).toBe('com.other/.Svc')
    expect(await H.keys()).toEqual([{ seq: 7, code: 83, name: 'KEYCODE_NOTIFICATION' }])
  })

  it('uploads a wallpaper in pieces and removes it', async () => {
    const jpeg = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(150 * 1024)])
    const cfg = await H.setWallpaper(jpeg.toString('base64'))
    expect(cfg.has_wallpaper).toBe(true)
    expect(tv.files.get('/data/data/home/wallpaper.jpg')?.equals(jpeg)).toBe(true)
    expect(tv.commands.filter((c) => c.includes('wallpaper_chunk')).length).toBeGreaterThan(1)
    await expect(H.setWallpaper(Buffer.from('not a jpeg').toString('base64'))).rejects.toThrow(/JPEG/)
    expect((await H.setWallpaper(null)).has_wallpaper).toBe(false)
  })

  it('gives Home back to the stock launcher from CleanMyTV Home', async () => {
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
  })

})
