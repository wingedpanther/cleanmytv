import { randomBytes } from 'node:crypto'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, symlinkSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import { setSinks } from '../src/main/log'
import * as T from '../src/main/tv'
import * as L from '../src/main/launcher'
import * as H from '../src/main/home'
import * as D from '../src/main/debloat'
import { bugReport } from '../src/main/report'
import * as S from '../src/main/system'
import * as Th from '../src/main/themes'
import { BUILT_IN_THEMES, THEME_KEYS, parseTheme, themeFileName, themeToFile } from '../src/shared/themes'
import { bootCheck } from '../src/main/bootcheck'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const TV_COPY = '/data/local/tmp/cleanmytv-wallpaper.jpg'
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

  it('puts apps in the top bar next to the quick settings, and sets the idle screen', async () => {
    const cfg = await H.setConfig({ quick: ['wifi', 'app:com.netflix.ninja', 'settings', 'app:com.netflix.ninja'], idle_minutes: 90 })
    expect(cfg).toMatchObject({ quick: ['wifi', 'app:com.netflix.ninja', 'settings'], idle_minutes: 60 })
    await expect(H.setConfig({ quick: ['app:bad;pkg'] as never })).rejects.toThrow(/quick/)
    await expect(H.setConfig({ quick: Array.from({ length: 7 }, (_, i) => `app:com.example.a${i}` as const) })).rejects.toThrow(/up to 6 apps/)
    expect((await H.setConfig({ idle_minutes: 0 })).idle_minutes).toBe(0)
  })

  it("lists the launcher's own apps and drops malformed entries", async () => {
    expect((await H.apps()).map((a) => a.pkg)).toEqual(['com.netflix.ninja', 'com.google.android.youtube.tv'])
  })

  it('maps remote buttons, switches the key service on and off, and reads the button log', async () => {
    const svc = 'io.github.wingedpanther.cleanmytv.home/io.github.wingedpanther.cleanmytv.home.KeyService'
    tv.settings.set('enabled_accessibility_services', 'com.other/.Svc')
    const cfg = await H.setConfig({ buttons: [{ code: 83, action: 'settings' }, { code: 83, action: 'home' }, { code: 183, action: 'app', app: 'com.netflix.ninja' }] })
    expect(cfg.buttons).toEqual([{ code: 83, action: 'settings' }, { code: 183, action: 'app', app: 'com.netflix.ninja' }])
    // TCL remotes send vendor codes like 4001 (#47).
    expect((await H.setConfig({ buttons: [{ code: 4001, action: 'settings' }] })).buttons).toEqual([{ code: 4001, action: 'settings' }])
    await expect(H.setConfig({ buttons: [{ code: 70000, action: 'settings' }] })).rejects.toThrow(/buttons/)
    await expect(H.setConfig({ buttons: [{ code: 0, action: 'settings' }] })).rejects.toThrow(/buttons/)
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

  it('puts the settings and wallpaper back when a fresh install starts empty', async () => {
    const jpeg = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(20 * 1024)])
    await H.setWallpaper(jpeg.toString('base64'))
    await H.setConfig({ accent: '#7C5CFF', layout: 'split', quick: ['wifi', 'sleep'], order: ['com.netflix.ninja'] })
    // An uninstall or a factory reset: no settings file and no wallpaper.
    tv.homeConfig = {}
    tv.homeFresh = true
    tv.files.delete('/data/data/home/wallpaper.jpg')
    // A fresh launcher's defaults must not replace the good copy.
    expect((await H.readConfig()).configured).toBe(false)
    const r = await H.install()
    expect(r.summary).toMatch(/settings and wallpaper came back/)
    expect(tv.homeConfig).toMatchObject({ accent: '#7C5CFF', layout: 'split', quick: ['wifi', 'sleep'], order: ['com.netflix.ninja'] })
    expect(tv.files.get('/data/data/home/wallpaper.jpg')?.equals(jpeg)).toBe(true)
    // A launcher that kept its settings is left alone.
    const before = tv.commands.length
    expect((await H.install()).summary).not.toMatch(/came back/)
    expect(tv.commands.slice(before).some((c) => c.includes('wallpaper_begin'))).toBe(false)
    await H.setWallpaper(null)
  })

  it('keeps the location and greeting out of the activity log', async () => {
    const lines: string[] = []
    setSinks((l) => lines.push(l.text), () => {})
    const change = { greeting: 'Hello Sam', weather_place: 'Dublin', weather_lat: 53.3331, weather_lon: -6.2489, accent: '#2ec4b6' }
    try {
      await H.setConfig(change)
      await H.readConfig()
      await H.setWallpaper(Buffer.concat([Buffer.from([0xff, 0xd8]), randomBytes(62)]).toString('base64'))
      await H.setWallpaper(null)
    } finally {
      setSinks(() => {}, () => {})
    }
    expect(tv.homeConfig).toMatchObject({ greeting: 'Hello Sam', weather_place: 'Dublin', weather_lat: 53.3331 })
    const text = lines.join('\n')
    expect(text).toContain('#2EC4B6')
    expect(text).toContain('"greeting":"(hidden)"')
    for (const secret of ['Sam', 'Dublin', '53.3331', '6.2489']) expect(text).not.toContain(secret)
    // Short enough to have been logged as a command, which would carry the same values in base64.
    expect(text).not.toContain('set64')
    expect(text).toContain('wallpaper_begin')
  })

  it('saves the look before the first theme, keeps it through more themes, and puts it back', async () => {
    Th.forget()
    const mine = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(30 * 1024)])
    await H.setWallpaper(mine.toString('base64'))
    await H.setConfig({ accent: '#123456', focus: 'none', tile_scale: 80, columns: 4, rows: [{ name: 'Kids', apps: ['com.netflix.ninja'] }] })
    expect(await Th.backup()).toEqual({ savedAt: null, withWallpaper: false, wallpaperAtRisk: false })
    const art = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(10 * 1024)])
    // Layout and personal keys in a theme are ignored.
    const cfg = await Th.apply({ name: 'Aurora', look: { ...BUILT_IN_THEMES[0].look, columns: 8, greeting: 'Hi' } as never, wallpaper: art.toString('base64') })
    expect(cfg).toMatchObject({ accent: '#2EC4B6', focus: 'lift', tile_scale: 80, columns: 4 })
    expect(cfg.greeting).not.toBe('Hi')
    expect(cfg.rows).toEqual([{ name: 'Kids', apps: ['com.netflix.ninja'] }])
    expect(tv.files.get('/data/data/home/wallpaper.jpg')?.equals(art)).toBe(true)
    const saved = await Th.backup()
    expect(saved).toMatchObject({ withWallpaper: true, wallpaperAtRisk: false })
    expect(saved.savedAt).toBeGreaterThan(0)

    // More themes and a change of the owner's own don't replace the saved look.
    await Th.apply({ name: 'Ember', look: BUILT_IN_THEMES[1].look, wallpaper: art.toString('base64') })
    await H.setConfig({ accent: '#ABCDEF' })
    expect((await Th.backup()).savedAt).toBe(saved.savedAt)

    const back = await Th.restore()
    expect(back).toMatchObject({ accent: '#123456', focus: 'none', tile_scale: 80 })
    expect(tv.files.get('/data/data/home/wallpaper.jpg')?.equals(mine)).toBe(true)
    expect((await Th.backup()).savedAt).toBeNull()
    await expect(Th.restore()).rejects.toThrow(/no saved look/)

    // A theme without a wallpaper key keeps the TV's; null removes it.
    await Th.apply({ name: 'Plain', look: { accent: '#FFFFFF' } })
    expect(tv.files.has('/data/data/home/wallpaper.jpg')).toBe(true)
    await Th.apply({ name: 'Bare', look: {}, wallpaper: null })
    expect(tv.files.has('/data/data/home/wallpaper.jpg')).toBe(false)
    // Putting back a look that had no wallpaper removes the theme's.
    Th.forget()
    await Th.apply({ name: 'Aurora', look: {}, wallpaper: art.toString('base64') })
    await Th.restore()
    expect(tv.files.has('/data/data/home/wallpaper.jpg')).toBe(false)
  })

  it("warns before the first theme when the TV's wallpaper came from elsewhere", async () => {
    Th.forget()
    await H.setWallpaper(Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(4 * 1024)]).toString('base64'))
    // Set by an older app on another computer: no copy here or on the TV.
    rmSync(H.backupBase() + '.jpg')
    tv.files.delete(TV_COPY)
    expect(await Th.backup()).toMatchObject({ savedAt: null, wallpaperAtRisk: true })
    await Th.apply({ name: 'Nord', look: { accent: '#81A1C1' } })
    expect(await Th.backup()).toMatchObject({ withWallpaper: false, wallpaperAtRisk: false })
    Th.forget()
    await H.setWallpaper(null)
  })

  it('keeps a copy of the wallpaper on the TV for other computers', async () => {
    const jpeg = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(12 * 1024)])
    await H.setWallpaper(jpeg.toString('base64'))
    expect(tv.files.get(TV_COPY)?.equals(jpeg)).toBe(true)
    // Another computer: no copy here, but the TV has one.
    rmSync(H.backupBase() + '.jpg')
    expect(await Th.backup()).toMatchObject({ savedAt: null, wallpaperAtRisk: false })
    const out = join(home, 'elsewhere.cmtvtheme')
    expect(await Th.save('Elsewhere', async () => out)).toMatchObject({ ok: true, warnings: [] })
    expect(Buffer.from(parseTheme(readFileSync(out, 'utf8')).wallpaper as string, 'base64').equals(jpeg)).toBe(true)
    expect(readFileSync(H.backupBase() + '.jpg').equals(jpeg)).toBe(true)
    // Set by the older app from here: the copy here goes onto the TV.
    tv.files.delete(TV_COPY)
    expect(await H.wallpaperCopy()).toBe(jpeg.toString('base64'))
    expect(tv.files.get(TV_COPY)?.equals(jpeg)).toBe(true)
    await H.setWallpaper(null)
    expect(tv.files.has(TV_COPY)).toBe(false)
  })

  it('saves the look with the wallpaper this computer sent, and reads it back', async () => {
    const jpeg = Buffer.concat([Buffer.from('ffd8ffe0', 'hex'), randomBytes(8 * 1024)])
    await H.setWallpaper(jpeg.toString('base64'))
    await H.setConfig({ accent: '#7C5CFF', viz_style: 'mirror', greeting: 'Hello Sam' })
    const out = join(home, 'saved.cmtvtheme')
    let asked = ''
    const r = await Th.save('  Living room ', async (n) => { asked = n; return out })
    expect(asked).toBe('Living room')
    expect(r).toMatchObject({ ok: true, warnings: [] })
    const t = parseTheme(readFileSync(out, 'utf8'))
    expect(t.name).toBe('Living room')
    expect(t.look).toMatchObject({ accent: '#7C5CFF', viz_style: 'mirror' })
    expect(Object.keys(t.look).sort()).toEqual([...THEME_KEYS].sort())
    expect(Buffer.from(t.wallpaper as string, 'base64').equals(jpeg)).toBe(true)
    // Not the whole file: the random wallpaper's base64 can spell anything.
    expect(JSON.stringify(t.look)).not.toContain('Sam')
    expect(t.look).not.toHaveProperty('greeting')
    expect(await Th.save('x', async () => null)).toBeNull()
    await H.setWallpaper(null)
  })

  it('describes the TV for a bug report without its address or the owner\'s settings', async () => {
    await H.setConfig({ greeting: 'Hello Sam', weather_place: 'Dublin' })
    const text = await bugReport('9.9.9')
    expect(text).toMatch(/^CleanMyTV 9\.9\.9, /)
    expect(text).toContain('TV: Sony BRAVIA 4K VH2')
    expect(text).toMatch(/Home: \S+\/\S+/)
    expect(text).toMatch(/CleanMyTV Home: \S+ installed/)
    for (const secret of [T.host(), 'Sam', 'Dublin']) expect(text).not.toContain(secret)
  })

  it('takes the 1.3.0 focus and feel settings', async () => {
    const cfg = await H.setConfig({ focus: 'lift', name_focus: true, follow_colour: true, pin_focus: true, tilt: true })
    expect(cfg).toMatchObject({ focus: 'lift', name_focus: true, follow_colour: true, pin_focus: true, tilt: true })
    await expect(H.setConfig({ focus: 'sparkle' as never })).rejects.toThrow(/focus/)
    await expect(H.setConfig({ tilt: 'yes' as never })).rejects.toThrow(/tilt/)
    await H.setConfig({ focus: 'glow', name_focus: false, follow_colour: false, pin_focus: false, tilt: false })
  })

  it('takes weather places and gives now playing its access only with the section', async () => {
    const cfg = await H.setConfig({ weather_place: ' Dublin\n', weather_lat: 53.333123, weather_lon: -6.24889, weather_unit: 'f' })
    expect(cfg).toMatchObject({ weather_place: 'Dublin', weather_lat: 53.3331, weather_lon: -6.2489, weather_unit: 'f' })
    await expect(H.setConfig({ weather_lat: 91 })).rejects.toThrow(/weather_lat/)
    await expect(H.setConfig({ weather_unit: 'k' as never })).rejects.toThrow(/weather_unit/)
    await H.setConfig({ sections: ['dock', 'grid'] })
    expect(tv.commands.at(-1)).toBe('cmd notification disallow_listener io.github.wingedpanther.cleanmytv.home/io.github.wingedpanther.cleanmytv.home.NowPlayingListener')
    await H.setConfig({ sections: ['nowplaying', 'dock', 'grid'] })
    expect(tv.commands.at(-1)).toBe('cmd notification allow_listener io.github.wingedpanther.cleanmytv.home/io.github.wingedpanther.cleanmytv.home.NowPlayingListener')
  })

  it('gives Home back to the stock launcher from CleanMyTV Home', async () => {
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
  })

  it('switches the stock launcher off when using an installed CleanMyTV Home (#27)', async () => {
    const r = await L.switchTo('io.github.wingedpanther.cleanmytv.home/.MainActivity')
    expect(r.ok).toBe(true)
    expect(tv.home).toBe('io.github.wingedpanther.cleanmytv.home/.MainActivity')
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('disabled')
  })

  it('switches setupwraith off only once CleanMyTV Home is Home (#33)', async () => {
    tv.packages.set(D.SETUPWRAITH, 'enabled')
    await L.useStock()
    let r = await D.apply({ listId: 'custom', disable: [D.SETUPWRAITH], enable: [] })
    expect(r.warnings.join('\n')).toMatch(/Kept on for now: com.google.android.tungsten.setupwraith/)
    expect(r.summary).toBe('Nothing switched off yet: com.google.android.tungsten.setupwraith stayed on for now. The notes below say why.')
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('enabled')
    expect((await L.switchTo('io.github.wingedpanther.cleanmytv.home/.MainActivity')).ok).toBe(true)
    r = await D.apply({ listId: 'custom', disable: [D.SETUPWRAITH], enable: [] })
    expect(r.ok).toBe(true)
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('disabled')
  })

  const instant = { sleep: async () => {}, portOpen: async () => true }

  it('restarts the TV, reconnects and finds the home screen running', async () => {
    const r = await bootCheck(instant)
    expect(r).toMatchObject({ ok: true, reconnected: true })
    expect(tv.commands).toContain('reboot')
    expect(T.getStatus().state).toBe('connected')
  })

  it('puts the stock launcher back when no home screen comes up after the restart', async () => {
    tv.crashing = ['io.github.wingedpanther.cleanmytv.home']
    const r = await bootCheck(instant)
    tv.crashing = []
    expect(r).toMatchObject({ ok: false, reconnected: true })
    expect(r.advice).toMatch(/switched com.google.android.apps.tv.launcherx back on/)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
  })

  it("doesn't take Settings' FallbackHome for a home screen after the restart", async () => {
    tv.packages.set('com.android.tv.settings', 'enabled')
    tv.home = 'com.android.tv.settings/.FallbackHome'
    const r = await bootCheck(instant)
    tv.packages.delete('com.android.tv.settings')
    expect(r).toMatchObject({ ok: false, reconnected: true })
    expect(r.summary).toMatch(/Home resolves to com.android.tv.settings\/.FallbackHome/)
    expect(r.advice).toMatch(/switched com.google.android.apps.tv.launcherx back on/)
    expect(tv.home).toBe('com.google.android.apps.tv.launcherx/.home.HomeActivity')
  })

  it('says so when the TV does not come back after the restart', async () => {
    const r = await bootCheck({ sleep: async () => {}, portOpen: async () => false })
    expect(r).toMatchObject({ ok: false, reconnected: false })
    expect(T.getStatus().state).not.toBe('connected')
    expect((await T.connect(String(T.getStatus().host))).state).toBe('connected')
  })

  it('refuses to install on Android older than 9 (#30)', async () => {
    const host = String(T.getStatus().host)
    tv.sdk = '24'
    expect((await T.connect(host)).state).toBe('connected')
    const r = await H.install()
    expect(r.ok).toBe(false)
    expect(r.summary).toMatch(/needs Android 9 or newer, and this TV runs Android 7\.0 \(SDK 24\)/)
    expect((await H.status()).unsupported).toMatch(/Android 9/)
    tv.sdk = undefined
    expect((await T.connect(host)).state).toBe('connected')
    expect((await H.status()).unsupported).toBeUndefined()
  })

  describe('a TV low on storage (#48)', () => {
    afterEach(() => { tv.freeKb = undefined; tv.cacheKb = undefined })

    it('clears the caches first when space is short, then installs', async () => {
      // The test APK is 100 KB, so the install wants 500 KB.
      tv.freeKb = 300; tv.cacheKb = 300000
      const before = tv.commands.length
      expect((await H.install()).ok).toBe(true)
      const cmds = tv.commands.slice(before)
      expect(cmds.indexOf('pm trim-caches 4096G')).toBeLessThan(cmds.findIndex((c) => c.startsWith('pm install')))
    })

    it('stops before uploading when clearing the caches is not enough, and says how much is needed', async () => {
      tv.freeKb = 300; tv.cacheKb = 100
      const before = tv.commands.length
      const r = await H.install()
      expect(r.ok).toBe(false)
      expect(r.summary).toMatch(/out of storage: it has 0.4 MB free and CleanMyTV Home needs about 0.5 MB.*Uninstall an app/)
      expect(tv.commands.slice(before).some((c) => c.startsWith('pm install') || c.includes('cmtv-home.apk.b64'))).toBe(false)
    })

    it('clears the caches and retries when pm install still runs out of space', async () => {
      // df says there's room, but pm wants more than that.
      tv.freeKb = 10100; tv.cacheKb = 50000
      const before = tv.commands.length
      expect((await H.install()).ok).toBe(true)
      expect(tv.commands.slice(before).filter((c) => c.startsWith('pm install'))).toHaveLength(2)
      expect(tv.commands).toContain('rm -f /data/local/tmp/cmtv-home.apk')
    })

    it('shows free space in TV extras and clears the caches from there', async () => {
      tv.freeKb = 200000; tv.cacheKb = 300000
      expect((await S.state()).storage).toEqual({ freeKb: 200000, totalKb: 5806884 })
      const r = await S.clearCaches()
      expect(r).toEqual({ ok: true, summary: "Cleared every app's cache: 293 MB back, 488 MB free now. Apps rebuild some of it as you use them.", warnings: [] })
      tv.freeKb = undefined
      expect((await S.state()).storage).toBeNull()
    })

    it('says so plainly when the retry fails too', async () => {
      tv.freeKb = 10100; tv.cacheKb = 0
      const r = await H.install()
      expect(r.ok).toBe(false)
      expect(r.summary).toMatch(/out of storage/)
      expect(r.warnings[0]).toMatch(/not enough space/)
    })
  })

})

describe('stock launchers other than Google\'s (#39)', () => {
  let tv: FakeTv
  let server: Server
  const OURS = 'io.github.wingedpanther.cleanmytv.home/.MainActivity'
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    for (const p of ['com.tcl.cyberui', 'com.netflix.ninja', 'com.tcl.tguard']) tv.packages.set(p, 'enabled')
    tv.home = 'com.tcl.cyberui/.Launcher'
    tv.homeClaims = ['com.tcl.cyberui/.Launcher', 'com.android.tv.settings/.FallbackHome']
    tv.packages.set('com.android.tv.settings', 'enabled')
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('knows the vendor launchers upstream lists name', () => {
    expect(D.stockLaunchers()).toEqual(expect.arrayContaining(['com.tcl.cyberui', 'com.jamdeo.tv.vod', 'ru.sberdevices.starlauncher']))
    expect(D.stockLaunchers()).not.toContain('com.android.tv.settings')
  })

  it('makes CleanMyTV Home Home and leaves a vendor launcher on, then gives Home back to it', async () => {
    const r = await H.install()
    expect(r.ok).toBe(true)
    expect(tv.home).toBe(OURS)
    expect(tv.packages.get('com.tcl.cyberui')).toBe('enabled')
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe('com.tcl.cyberui/.Launcher')
  })

  it('takes a launcher no list names as the stock one, and remembers it for Undo', async () => {
    tv.packages.delete('com.tcl.cyberui')
    tv.packages.set('com.acme.home', 'enabled')
    tv.homeClaims.push('com.acme.home/.Main')
    tv.home = 'com.acme.home/.Main'
    expect((await H.install()).ok).toBe(true)
    expect(tv.home).toBe(OURS)
    expect(tv.packages.get('com.acme.home')).toBe('enabled')
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe('com.acme.home/.Main')
  })

  it("asks which launcher is the TV's own when Android's chooser can't tell, and uses the pick", async () => {
    tv.packages.set('com.acme.other', 'enabled')
    tv.homeClaims.push('com.acme.other/.Main')
    tv.home = 'android/com.android.internal.app.ResolverActivity'
    const r = await H.install()
    expect(r.ok).toBe(false)
    expect(r.stockChoices).toEqual(['com.acme.home', 'com.acme.other'])
    expect(tv.home).toBe('android/com.android.internal.app.ResolverActivity')
    await expect(H.install('com.netflix.ninja')).rejects.toThrow(/can't be Home/)
    expect((await H.install('com.acme.home')).ok).toBe(true)
    expect(tv.home).toBe(OURS)
    expect(tv.packages.get('com.acme.home')).toBe('enabled')
  })

  it('switches off a package added by hand, shows it in the list after, and restores it', async () => {
    const all = await D.tvPackages()
    expect(all.map((e) => e.pkg)).toContain('com.tcl.tguard')
    expect(all.map((e) => e.pkg)).not.toContain('com.android.tv.settings')
    expect((await D.apply({ listId: 'custom', disable: ['com.tcl.tguard'], enable: [] })).ok).toBe(true)
    expect(tv.packages.get('com.tcl.tguard')).toBe('disabled')
    const plan = await D.plan('custom')
    expect(plan.entries.find((e) => e.pkg === 'com.tcl.tguard')).toMatchObject({ added: true, state: 'disabled', recorded: 'enabled' })
    expect((await D.restore('custom')).ok).toBe(true)
    expect(tv.packages.get('com.tcl.tguard')).toBe('enabled')
  })
})

describe('Google TV 14, where setupwraith outranks the Home preference (#40)', () => {
  let tv: FakeTv
  let server: Server
  const OURS = 'io.github.wingedpanther.cleanmytv.home/.MainActivity'
  const LX = 'com.google.android.apps.tv.launcherx/.home.HomeActivity'
  beforeAll(async () => {
    tv = newTv()
    tv.maxData = 256 * 1024
    for (const p of ['com.google.android.apps.tv.launcherx', D.SETUPWRAITH, 'com.android.tv.settings']) tv.packages.set(p, 'enabled')
    tv.home = LX
    tv.homeClaims = [LX, `${D.SETUPWRAITH}/.RecoveryActivity`, 'com.android.tv.settings/.FallbackHome']
    tv.homeOutranks = [LX, `${D.SETUPWRAITH}/.RecoveryActivity`]
    tv.wifiOwners = `cname=${D.SETUPWRAITH}`
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('leaves both on and says to re-join the Wi-Fi when the saved network belongs to setupwraith', async () => {
    const r = await H.install()
    expect(r.ok).toBe(false)
    expect(r.warnings.join('\n')).toMatch(/forget the network and join it again/)
    expect(tv.home).toBe(LX)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('enabled')
  })

  it('switches setupwraith off itself once the Wi-Fi is the system\'s, so Home sticks', async () => {
    // Joined again after setup: the network in use is the system's, an old one is setupwraith's.
    tv.wifiOwners = 'WifiConfigManager - Configured networks Begin ----\n' +
      `- DSBLE ID: 0 SSID: "Old" PROVIDER-NAME: null\n cuid=10023 cname=${D.SETUPWRAITH}\n` +
      '* ID: 1 SSID: "Home" PROVIDER-NAME: null\n cuid=1000 cname=android.uid.system:1000\n' +
      'WifiConfigManager - Configured networks End ----\n'
    const r = await H.install()
    expect(r.ok).toBe(true)
    expect(r.warnings.join('\n')).toMatch(/One other saved Wi-Fi network from the TV's first setup went with setupwraith/)
    expect(tv.home).toBe(OURS)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('disabled')
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('disabled')
  })

  it('turns setupwraith back on when Home goes back to the stock launcher', async () => {
    expect((await L.useStock()).ok).toBe(true)
    expect(tv.home).toBe(LX)
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('enabled')
  })

  it('puts both back when Home still does not stick', async () => {
    tv.homeOutranks = [LX, `${D.SETUPWRAITH}/.RecoveryActivity`, 'com.android.tv.settings/.FallbackHome']
    const r = await H.install()
    tv.homeOutranks = [LX, `${D.SETUPWRAITH}/.RecoveryActivity`]
    expect(r.ok).toBe(false)
    expect(tv.home).toBe(LX)
    expect(tv.packages.get('com.google.android.apps.tv.launcherx')).toBe('enabled')
    expect(tv.packages.get(D.SETUPWRAITH)).toBe('enabled')
  })
})

describe('theme files', () => {
  it('reads back what it writes and refuses anything else', () => {
    const t = { name: 'Neon / night', look: { accent: '#FF3000', columns: 8 } as never, wallpaper: null }
    expect(parseTheme(themeToFile(t))).toEqual({ name: 'Neon / night', look: { accent: '#FF3000' }, wallpaper: null })
    expect(themeFileName('Neon / night')).toBe('Neon - night.cmtvtheme')
    expect(() => parseTheme('{"look":{}}')).toThrow(/isn't a CleanMyTV theme/)
    expect(() => parseTheme('not json')).toThrow(/isn't a CleanMyTV theme/)
    expect(() => parseTheme('{"format":"cleanmytv-theme","version":2}')).toThrow(/newer CleanMyTV/)
    expect(() => parseTheme('{"format":"cleanmytv-theme","version":1,"wallpaper":"<script>"}')).toThrow(/damaged/)
  })

  it('built-in themes only carry valid look settings', () => {
    expect(new Set(BUILT_IN_THEMES.map((b) => b.id)).size).toBe(BUILT_IN_THEMES.length)
    expect(new Set(BUILT_IN_THEMES.map((b) => b.name)).size).toBe(BUILT_IN_THEMES.length)
    expect(BUILT_IN_THEMES.filter((b) => b.from === 'omarchy')).toHaveLength(17)
    for (const b of BUILT_IN_THEMES) {
      expect(H.clean(b.look as Record<string, unknown>)).toEqual(b.look)
      expect(Object.keys(b.look).every((k) => (THEME_KEYS as readonly string[]).includes(k))).toBe(true)
    }
  })
})

describe('launcher 1.4.0 settings', () => {
  it('takes icon styles, tile shapes, the intro and the app lock, and refuses bad values', () => {
    expect(H.clean({ icon_style: 'mono', tile_shape: 'squircle', intro: false, lock_on: true, lock_apps: ['com.netflix.ninja'], lock_for: '15', lock_show: 'hide', lock_system: false }))
      .toEqual({ icon_style: 'mono', tile_shape: 'squircle', intro: false, lock_on: true, lock_apps: ['com.netflix.ninja'], lock_for: '15', lock_show: 'hide', lock_system: false })
    expect(() => H.clean({ icon_style: 'neon' })).toThrow(/icon_style/)
    expect(() => H.clean({ lock_apps: ['not a package'] })).toThrow(/lock_apps/)
    expect(H.clean({ wallpaper_from: 'aurora:2' })).toEqual({ wallpaper_from: 'aurora:2' })
    expect(H.clean({ wallpaper_from: '' })).toEqual({ wallpaper_from: '' })
    expect(() => H.clean({ wallpaper_from: '../x:1' })).toThrow(/wallpaper_from/)
    expect(H.clean({ quick: ['panel', 'themes'] })).toEqual({ quick: ['panel', 'themes'] })
    expect(H.clean({ buttons: [{ code: 183, action: 'next_wallpaper' }] })).toEqual({ buttons: [{ code: 183, action: 'next_wallpaper' }] })
  })

  it('reads the lock and theme state the launcher reports, and never a PIN', () => {
    const cfg = H.parse('Result: Bundle[{config={"lock_pin_set":true,"themes_on_tv":28,"can_change_settings":false,"pin":"1234"}}]')
    expect(cfg.lock_pin_set).toBe(true)
    expect(cfg.themes_on_tv).toBe(28)
    expect(cfg.can_change_settings).toBe(false)
    expect(JSON.stringify(cfg)).not.toContain('1234')
  })

  it('takes the fireplace settings and keeps the volume in range', () => {
    expect(H.clean({ fire_wall: true, fire_sound: false, fire_wall_sound: true, fire_volume: 400 }))
      .toEqual({ fire_wall: true, fire_sound: false, fire_wall_sound: true, fire_volume: 100 })
    expect(H.clean({ quick: ['fireplace'], buttons: [{ code: 186, action: 'fireplace' }] })).toEqual({ quick: ['fireplace'], buttons: [{ code: 186, action: 'fireplace' }] })
  })

  it('takes Live TV on or off, and nothing but a boolean', () => {
    expect(H.clean({ live_tv: false })).toEqual({ live_tv: false })
    expect(() => H.clean({ live_tv: 'no' })).toThrow(/live_tv/)
  })

  it('only sends a PIN of 4 to 6 digits', async () => {
    for (const bad of ['123', '1234567', '12a4', '']) await expect(H.setLockPin(bad)).rejects.toThrow(/4 to 6 digits/)
  })
})
