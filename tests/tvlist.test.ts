import { describe, expect, it, vi } from 'vitest'
import type { ConnStatus } from '../src/shared/api'
import { autoConnect, forgetTv, listTvs, rememberTv, setThumb, type TvSettings } from '../src/main/tvlist'
import { reconnect } from '../src/main/reconnect'
import { tvId } from '../src/main/tv'

const tvAt = (host: string, device: string, model: string, id?: string): ConnStatus => ({
  state: 'connected', host,
  probe: { host, device, model, manufacturer: 'Acme', product: '', release: '12', sdk: 31, build: '', adbSecure: '1', ...(id ? { id } : {}) }
})

describe('known TVs', () => {
  it('reconnects by itself with one TV, and waits for a pick with two', () => {
    let s = rememberTv({} as TvSettings, '10.0.0.5', tvAt('10.0.0.5', 'oneday', 'MIBOX4'), 1)
    expect(autoConnect(s)).toBe(true)
    s = rememberTv(s, '10.0.0.9', tvAt('10.0.0.9', 'BRAVIA', 'XR-55'), 2)
    expect(autoConnect(s)).toBe(false)
    expect(listTvs(s).map((t) => t.host)).toEqual(['10.0.0.9', '10.0.0.5'])
    expect(s.lastHost).toBe('10.0.0.9')
  })

  it('treats settings from before the list as one TV', () => {
    expect(listTvs({ lastHost: '10.0.0.5', lastDevice: 'oneday', lastModel: 'MIBOX4' })).toEqual([{ host: '10.0.0.5', device: 'oneday', model: 'MIBOX4', lastUsed: 0 }])
  })

  it('follows a TV to a new address, and drops the snapshot when another TV takes an address', () => {
    let s = rememberTv({} as TvSettings, '10.0.0.5', tvAt('10.0.0.5', 'oneday', 'MIBOX4'), 1)
    s = setThumb(s, '10.0.0.5', 'data:image/jpeg;base64,AAAA', 2)
    s = rememberTv(s, '10.0.0.7', tvAt('10.0.0.7', 'oneday', 'MIBOX4'), 3)
    expect(listTvs(s)).toHaveLength(1)
    expect(listTvs(s)[0]).toMatchObject({ host: '10.0.0.7', thumb: 'data:image/jpeg;base64,AAAA' })
    s = rememberTv(s, '10.0.0.7', tvAt('10.0.0.7', 'BRAVIA', 'XR-55'), 4)
    expect(listTvs(s)[0].thumb).toBeUndefined()
  })

  it('forgets a TV and refuses snapshots that are not small JPEGs', () => {
    let s = rememberTv({} as TvSettings, '10.0.0.5', tvAt('10.0.0.5', 'oneday', 'MIBOX4'), 1)
    expect(() => setThumb(s, '10.0.0.5', 'data:text/html;base64,PHNjcmlwdD4=')).toThrow(/JPEG/)
    expect(() => setThumb(s, '10.0.0.5', 'data:image/jpeg;base64,' + 'A'.repeat(300_000))).toThrow(/JPEG/)
    s = forgetTv(s, '10.0.0.5')
    expect(listTvs(s)).toEqual([])
    expect(s.lastHost).toBeUndefined()
  })

  it('keeps two TVs of the same model apart by their id, and follows each to a new address', () => {
    let s = rememberTv({} as TvSettings, '10.0.0.5', tvAt('10.0.0.5', 'oneday', 'MIBOX4', 'aaaaaaaaaaaa'), 1)
    s = setThumb(s, '10.0.0.5', 'data:image/jpeg;base64,AAAA', 2)
    s = rememberTv(s, '10.0.0.6', tvAt('10.0.0.6', 'oneday', 'MIBOX4', 'bbbbbbbbbbbb'), 3)
    expect(listTvs(s).map((t) => [t.host, t.id])).toEqual([['10.0.0.6', 'bbbbbbbbbbbb'], ['10.0.0.5', 'aaaaaaaaaaaa']])
    expect(s.lastId).toBe('bbbbbbbbbbbb')
    s = rememberTv(s, '10.0.0.8', tvAt('10.0.0.8', 'oneday', 'MIBOX4', 'aaaaaaaaaaaa'), 4)
    expect(listTvs(s).map((t) => t.host)).toEqual(['10.0.0.8', '10.0.0.6'])
    expect(listTvs(s)[0].thumb).toBe('data:image/jpeg;base64,AAAA')
  })

  it('gives a TV saved before ids its id the next time it connects', () => {
    let s = rememberTv({} as TvSettings, '10.0.0.5', tvAt('10.0.0.5', 'oneday', 'MIBOX4'), 1)
    s = rememberTv(s, '10.0.0.7', tvAt('10.0.0.7', 'oneday', 'MIBOX4', 'aaaaaaaaaaaa'), 2)
    expect(listTvs(s)).toEqual([expect.objectContaining({ host: '10.0.0.7', id: 'aaaaaaaaaaaa' })])
  })

  it('takes another TV of the same model at the saved address for a different device', async () => {
    const d = { connect: vi.fn(async () => tvAt('10.0.0.5', 'oneday', 'MIBOX4', 'bbbbbbbbbbbb')), disconnect: vi.fn(), report: vi.fn((s: ConnStatus) => s) }
    const s = await reconnect({ lastHost: '10.0.0.5', lastDevice: 'oneday', lastModel: 'MIBOX4', lastId: 'aaaaaaaaaaaa' }, d)
    expect(d.disconnect).toHaveBeenCalled()
    expect(s?.moved).toBe(true)
    const same = await reconnect({ lastHost: '10.0.0.5', lastDevice: 'oneday', lastId: 'bbbbbbbbbbbb' }, d)
    expect(same?.state).toBe('connected')
  })

  it('makes the id from the serial without keeping the serial, and none from placeholder serials', () => {
    const id = tvId('SN0123456789')
    expect(id).toMatch(/^[0-9a-f]{12}$/)
    expect(id).not.toContain('0123456789')
    expect(tvId('SN0123456789')).toBe(id)
    for (const s of ['', 'unknown', '0123456789ABCDEF', '00000000', 'abc']) expect(tvId(s)).toBeUndefined()
  })
})
