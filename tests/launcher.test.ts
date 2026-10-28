import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as L from '../src/main/launcher'
import * as Th from '../src/main/theme'
import * as H from '../src/main/home'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-test-'))
process.env.HOME = home
// A resources folder of its own: the real lists and themes, and a stand-in APK, so this runs without
// the launcher having been built (CI builds it in a separate job).
const resources = join(home, 'resources')
mkdirSync(resources)
symlinkSync(resolve('resources/upstream'), join(resources, 'upstream'))
writeFileSync(join(resources, 'launcher.apk'), randomBytes(200 * 1024))
writeFileSync(join(resources, 'launcher.json'), JSON.stringify({ versionName: '2.4' }))
writeFileSync(join(resources, 'home.apk'), randomBytes(100 * 1024))
writeFileSync(join(resources, 'home.json'), JSON.stringify({ versionName: '0.2.0-preview' }))
configurePaths(resources, join(home, 'data'))

describe('launcher and theme', () => {
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
    const r = await L.install({ apk: 'bundled', stockLauncher: 'auto', inputKeyService: true, homeKeyService: false })
    expect(r.summary).toMatch(/Installed/)
    expect(r.ok).toBe(true)
    expect(tv.home).toBe('com.gathin.tvhome/.MainActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('disabled')
    const order = tv.commands.filter((c) => /pm install|am start -W|pm disable-user|set-home-activity/.test(c))
    expect(order.map((c) => c.split(' ').slice(0, 2).join(' '))).toEqual(['pm install', 'am start', 'pm disable-user', 'cmd package'])
    expect(tv.commands.some((c) => c.includes('InputKeyService'))).toBe(true)
    const st = await L.status()
    expect(st.isHome).toBe(true)
    expect(st.versionName).toBe('2.4')
  })

  it('gives Home back to the stock launcher', async () => {
    const r = await L.useStock()
    expect(r.ok).toBe(true)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
  })

  it('pushes config.json and a wallpaper, then reads them back', async () => {
    const jpeg = Buffer.from('ffd8ffe000104a464946', 'hex')
    const r = await Th.apply({
      config: { dock_apps: ['com.google.android.youtube.tv', 'com.netflix.ninja'], columns: 5, wallpaper: true, wallpaper_dim: 30, greeting_name: '' },
      wallpaper: { kind: 'set', image: { name: 'wallpaper.jpg', base64: jpeg.toString('base64') } },
      wallpapers: { kind: 'keep' }
    })
    expect(r.ok).toBe(true)
    const back = await Th.read()
    expect(back.present).toBe(true)
    expect(back.config.dock_apps).toEqual(['com.google.android.youtube.tv', 'com.netflix.ninja'])
    expect(back.config.columns).toBe(5)
    expect(back.wallpaper).toBe(`data:image/jpeg;base64,${jpeg.toString('base64')}`)
    expect(tv.files.get('/sdcard/Android/data/com.gathin.tvhome/files/theme_revision')?.length).toBe(32)
    const apps = await Th.apps()
    expect(apps.map((a) => a.label)).toEqual(['Netflix', 'YouTube'])
  })

  it('opens the launcher when the TV will not let the shell create its folder', async () => {
    tv.appDir = 'missing'
    const before = tv.commands.length
    const r = await Th.apply({ config: { columns: 4 }, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'keep' } })
    expect(r.ok).toBe(true)
    expect(tv.commands.slice(before).some((c) => c.startsWith('am start -W -n com.gathin.tvhome/.MainActivity'))).toBe(true)
  })

  it('says so plainly when the folder stays out of reach', async () => {
    tv.appDir = 'denied'
    await expect(Th.apply({ config: { columns: 4 }, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'keep' } })).rejects.toThrow(/won't let the app write/)
    tv.appDir = 'ok'
  }, 15000)

  it('refuses wallpaper names with shell syntax', async () => {
    await expect(Th.apply({ config: {}, wallpaper: { kind: 'keep' }, wallpapers: { kind: 'set', images: [{ name: 'a;reboot.jpg', base64: '' }] } })).rejects.toThrow(/unsupported/)
  })

  it('installs CleanMyTV Home over CleanYourTV and switches between them', async () => {
    await L.install({ apk: 'bundled', stockLauncher: 'auto', inputKeyService: false, homeKeyService: false })
    expect(tv.home).toBe('com.gathin.tvhome/.MainActivity')
    const before = tv.commands.length
    const r = await H.install()
    expect(r.ok).toBe(true)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
    const cmds = tv.commands.slice(before)
    expect(cmds).toContain('pm grant io.github.wingedpanther.cleanmytv.home android.permission.RECORD_AUDIO 2>/dev/null; echo audio-granted')
    // CleanYourTV already had the stock launcher off: nothing else is disabled.
    expect(cmds.some((c) => c.startsWith('pm disable-user'))).toBe(false)
    const st = await H.status()
    expect(st).toMatchObject({ installed: true, isHome: true, versionName: '0.2.0-preview', bundledVersion: '0.2.0-preview' })

    expect((await L.useCleanYourTv()).ok).toBe(true)
    expect(tv.home).toBe('com.gathin.tvhome/.MainActivity')
    expect((await L.switchTo('io.github.wingedpanther.cleanmytv.home/.MainActivity')).ok).toBe(true)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
  })

  it('leaves Home where it was when the switch does not stick', async () => {
    tv.homeStuck = true
    const r = await L.useCleanYourTv()
    tv.homeStuck = false
    expect(r.ok).toBe(false)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
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

  it('ships the upstream themes as presets', () => {
    const ids = Th.presets().map((p) => p.id)
    expect(ids).toEqual(expect.arrayContaining(['aurora', 'default', 'six-columns']))
    expect(Th.presets().find((p) => p.id === 'aurora')?.wallpaper).toMatch(/^data:image\/jpeg;base64,/)
  })
})
