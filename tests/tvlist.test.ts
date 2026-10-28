import { describe, expect, it } from 'vitest'
import type { ConnStatus } from '../src/shared/api'
import { autoConnect, forgetTv, listTvs, rememberTv, setThumb, type TvSettings } from '../src/main/tvlist'

const tvAt = (host: string, device: string, model: string): ConnStatus => ({
  state: 'connected', host,
  probe: { host, device, model, manufacturer: 'Acme', product: '', release: '12', sdk: 31, build: '', adbSecure: '1' }
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
})
