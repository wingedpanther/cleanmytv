import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import type { Server } from 'node:net'
import { configurePaths } from '../src/main/paths'
import * as T from '../src/main/tv'
import * as C from '../src/main/checkup'
import * as R from '../src/main/record'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const home = mkdtempSync(join(tmpdir(), 'cytv-checkup-'))
process.env.HOME = home
configurePaths(join(home, 'resources'), join(home, 'data'))

const HOME = 'io.github.wingedpanther.cleanmytv.home'
const TCL = 'com.tcl.inputmethod.international/.T_IME'
const BRIDGE = 'com.google.android.tv.remote.service/.ImeBridgeService'
const TTS = 'com.google.android.tts/com.google.android.apps.speech.tts.googletts.settings.asr.voiceime.VoiceInputMethodService'

const state = (over: Partial<C.TvState> = {}): C.TvState => ({
  keyboard: TCL, keyboards: [TTS, BRIDGE, TCL], home: `${HOME}/.MainActivity`,
  installed: new Set([HOME, 'com.tcl.inputmethod.international', 'com.google.android.tts', 'com.google.android.tv.remote.service']),
  disabled: new Set(), screensaver: `${HOME}/.FireplaceDream`, screensaverOn: true, ...over
})

describe('check-up after Clean up', () => {
  it('passes a TV that types, has Home and a working screensaver', () => {
    expect(C.assess(state()).every((i) => i.ok)).toBe(true)
  })

  it('finds the phone keyboard and offers a keyboard that types, never the voice one', () => {
    const kb = C.assess(state({ keyboard: BRIDGE })).find((i) => i.id === 'keyboard')!
    expect(kb).toMatchObject({ ok: false, fix: 'Use it' })
    expect(kb.detail).toContain('com.tcl.inputmethod.international can type')
    expect(C.typingKeyboard(state())).toBe(TCL)
  })

  it('finds a Home button that opens nothing, and a microphone with half its pair off', () => {
    const items = C.assess(state({ home: null, installed: new Set([...state().installed, 'com.google.android.katniss']), disabled: new Set(['com.google.android.tv.remote.service']) }))
    expect(items.find((i) => i.id === 'home')).toMatchObject({ ok: false, fix: 'Use CleanMyTV Home' })
    expect(items.find((i) => i.id === 'voice')).toMatchObject({ ok: false, fix: 'Switch both on' })
  })

  it('finds a screensaver whose app is switched off', () => {
    const s = state({ screensaver: 'com.google.android.apps.tv.dreamx/.service.DreamxService', installed: new Set([...state().installed, 'com.google.android.apps.tv.dreamx']), disabled: new Set(['com.google.android.apps.tv.dreamx']) })
    expect(C.assess(s).find((i) => i.id === 'screensaver')).toMatchObject({ ok: false })
    expect(C.assess({ ...s, screensaverOn: false }).find((i) => i.id === 'screensaver')).toMatchObject({ ok: true })
  })
})

describe('check-up and recording on a TV', () => {
  let tv: FakeTv
  let server: Server
  beforeAll(async () => {
    tv = newTv()
    for (const p of [HOME, 'com.tcl.inputmethod.international', 'com.google.android.tv.remote.service', 'com.google.android.katniss']) tv.packages.set(p, 'enabled')
    tv.home = `${HOME}/.MainActivity`
    tv.imes = [BRIDGE, TCL]
    tv.settings.set('default_input_method', BRIDGE)
    tv.logcat = '11-16 10:00:01.000  1  1 I Wifi: connected to 192.168.1.20 aa:bb:cc:dd:ee:ff as sam@example.org\n11-16 10:00:02.000  1  1 E AudioTrack: start(3283): status -1\n'
    let port: number
    ;({ server, port } = await startFakeAdbd(tv))
    T.init(join(home, 'data', 'adbkey'), () => {})
    expect((await T.connect(`127.0.0.1:${port}`)).state).toBe('connected')
  })
  afterAll(() => { T.disconnect(); server.close() })

  it('puts the keyboard back and tells Home', async () => {
    expect((await C.run()).find((i) => i.id === 'keyboard')?.ok).toBe(false)
    await C.fix('keyboard')
    expect(tv.settings.get('default_input_method')).toBe(TCL)
    expect((await C.run()).find((i) => i.id === 'keyboard')?.ok).toBe(true)
    expect(tv.commands.some((c) => c.includes('--method keyboard'))).toBe(true)
  })

  it('switches the remote microphone pair back on', async () => {
    tv.packages.set('com.google.android.tv.remote.service', 'disabled')
    await C.fix('voice')
    expect(tv.packages.get('com.google.android.tv.remote.service')).toBe('enabled')
  })

  it('records the log since Record, without addresses or emails', async () => {
    await expect(R.stop('details')).rejects.toThrow(/Record first/)
    await R.start()
    expect(R.recording()).toBe(true)
    const text = await R.stop('CleanMyTV 9.9.9')
    expect(R.recording()).toBe(false)
    expect(text).toMatch(/^CleanMyTV 9\.9\.9\n\nTV log from 11-16 10:00:00\.000:\n/)
    expect(text).toContain('AudioTrack: start(3283): status -1')
    for (const s of ['192.168.1.20', 'aa:bb:cc:dd:ee:ff', 'sam@example.org']) expect(text).not.toContain(s)
    expect(tv.commands).toContain("logcat -d -v threadtime -T '11-16 10:00:00.000'")
  })

  it('keeps only the newest lines of a long log', () => {
    expect(R.lastLines('a\nb\nc\nd', 2)).toBe('(the first 2 lines are left out)\nc\nd')
    expect(R.lastLines('a\nb', 5)).toBe('a\nb')
  })
})
