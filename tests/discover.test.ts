// mDNS discovery: these packets come from anyone on the network, and the result fills in the Setup form.
import { describe, expect, it, vi } from 'vitest'
import {
  AdvertCache, CONNECT_SERVICE, NAME_SERVICE, PAIR_SERVICE, TYPE_A, TYPE_PTR, TYPE_SRV,
  buildQuery, mergeAdverts, parseDnsResponse, sendOnEach, type Advert, type DnsRecord
} from '../src/main/discover'

const name = (n: string): Buffer =>
  Buffer.concat([...n.split('|').flatMap((l) => [Buffer.from([Buffer.byteLength(l)]), Buffer.from(l)]), Buffer.from([0])])

function record(owner: Buffer, type: number, ttl: number, data: Buffer): Buffer {
  const head = Buffer.alloc(10)
  head.writeUInt16BE(type, 0)
  head.writeUInt16BE(1, 2)
  head.writeUInt32BE(ttl, 4)
  head.writeUInt16BE(data.length, 8)
  return Buffer.concat([owner, head, data])
}

function response(records: Buffer[]): Buffer {
  const head = Buffer.alloc(12)
  head[2] = 0x84
  head.writeUInt16BE(records.length, 6)
  return Buffer.concat([head, ...records])
}

const SERVICE = '_androidtvremote2|_tcp|local'
const INSTANCE = `Living Room TV|${SERVICE}`

function advert(ttl = 120): DnsRecord[] {
  const srv = Buffer.concat([Buffer.from([0, 0, 0, 0, 0x19, 0x42]), name('tv|local')])
  return parseDnsResponse(
    response([
      record(name(SERVICE), TYPE_PTR, ttl, name(INSTANCE)),
      record(name(INSTANCE), TYPE_SRV, ttl, srv),
      record(name('tv|local'), TYPE_A, ttl, Buffer.from([192, 168, 1, 50]))
    ])
  )
}

describe('parseDnsResponse', () => {
  it('reads a TV advert, spaces in the name included', () => {
    const cache = new AdvertCache()
    cache.add(advert())
    expect(cache.adverts()).toEqual([{ service: NAME_SERVICE, instance: 'Living Room TV', ip: '192.168.1.50', port: 6466 }])
  })

  it('follows compressed names', () => {
    const owner = name(SERVICE)
    const pointer = Buffer.from([0xc0, 12])
    const target = Buffer.concat([Buffer.from([2]), Buffer.from('tv'), pointer])
    const [ptr] = parseDnsResponse(response([record(owner, TYPE_PTR, 120, target)]))
    expect(ptr.labels).toEqual(['tv', '_androidtvremote2', '_tcp', 'local'])
  })

  it('keeps a name with a dot in it as one label when asking about it', () => {
    const labels = ['TV 2.0', '_androidtvremote2', '_tcp', 'local']
    const q = buildQuery([{ labels, type: TYPE_SRV }])
    expect(q[12]).toBe('TV 2.0'.length)
    expect(q.toString('utf8', 13, 19)).toBe('TV 2.0')
  })

  it("won't read a name past the end of its record", () => {
    const owner = name(SERVICE)
    // Claims one byte of data; the name it starts runs on into whatever follows.
    const pkt = Buffer.concat([response([record(owner, TYPE_PTR, 120, Buffer.from([2]))]), Buffer.from('tv'), Buffer.from([0])])
    expect(parseDnsResponse(pkt)).toEqual([])
  })

  it('ignores queries', () => {
    expect(parseDnsResponse(buildQuery([{ labels: NAME_SERVICE.split('.'), type: TYPE_PTR }]))).toEqual([])
  })

  it('survives pointer loops, truncation and junk', () => {
    const loop = response([record(Buffer.from([0xc0, 12]), TYPE_PTR, 120, Buffer.from([0xc0, 12]))])
    expect(parseDnsResponse(loop)).toEqual([])
    const good = response([record(name(SERVICE), TYPE_PTR, 120, name(INSTANCE))])
    for (let i = 0; i < good.length; i++) expect(() => parseDnsResponse(good.subarray(0, i))).not.toThrow()
    for (let seed = 0; seed < 200; seed++) {
      const junk = Buffer.from(good)
      junk[(seed * 7) % junk.length] = (seed * 31) & 0xff
      expect(() => parseDnsResponse(junk)).not.toThrow()
    }
  })
})

describe('AdvertCache', () => {
  it('forgets a TV that says goodbye', () => {
    const cache = new AdvertCache()
    cache.add(advert())
    cache.add(advert(0))
    expect(cache.adverts()).toEqual([])
  })

  it('honours a goodbye with no port in it', () => {
    const cache = new AdvertCache()
    const [ptr, srv, a] = advert()
    cache.add([ptr, srv, a])
    cache.add([{ ...srv, ttl: 0, port: 0 }])
    expect(cache.adverts()).toEqual([])
  })

  it('asks again before the details run out, so the TV stays listed', () => {
    let now = 1000
    const cache = new AdvertCache(() => now)
    cache.add(advert(120))
    expect(cache.questions().filter((q) => q.type !== TYPE_PTR)).toEqual([])
    now += 110_000
    expect(cache.adverts()).toHaveLength(1)
    expect(cache.questions().map((q) => q.type)).toEqual(expect.arrayContaining([TYPE_SRV, TYPE_A]))
  })

  it('stays bounded when flooded with adverts', () => {
    const cache = new AdvertCache()
    const [ptr] = advert()
    for (let i = 0; i < 1000; i++) cache.add([{ ...ptr, labels: [`tv${i}`, '_tcp', 'local'] }])
    expect(cache.questions().length).toBeLessThanOrEqual(20)
    expect(() => buildQuery(cache.questions())).not.toThrow()
  })

  it('forgets a TV when its advert runs out', () => {
    let now = 1000
    const cache = new AdvertCache(() => now)
    cache.add(advert(120))
    now += 121_000
    expect(cache.adverts()).toEqual([])
  })

  it('assembles an advert that arrives in pieces, and asks for the missing piece', () => {
    const cache = new AdvertCache()
    const [ptr, srv, a] = advert()
    cache.add([ptr])
    expect(cache.adverts()).toEqual([])
    expect(cache.questions()).toContainEqual({ labels: ptr.labels, type: TYPE_SRV })
    cache.add([srv])
    expect(cache.questions()).toContainEqual({ labels: ['tv', 'local'], type: TYPE_A })
    cache.add([a])
    expect(cache.adverts()).toHaveLength(1)
  })

  it('drops a public address: a TV on the home network has a private one', () => {
    const cache = new AdvertCache()
    const [ptr, srv, a] = advert()
    cache.add([ptr, srv, { ...a, ip: '8.8.8.8' }])
    expect(cache.adverts()).toEqual([])
  })

  it('ignores services that are not TVs', () => {
    const cache = new AdvertCache()
    const [ptr, srv, a] = advert()
    cache.add([{ ...ptr, key: '_printer._tcp.local' }, srv, a])
    expect(cache.adverts()).toEqual([])
  })
})

describe('mergeAdverts', () => {
  const ad = (service: string, port: number, instance = 'x', ip = '192.168.1.50'): Advert => ({ service, instance, ip, port })

  it('combines the name and ports of one TV', async () => {
    const tvs = await mergeAdverts(
      [ad(NAME_SERVICE, 6466, 'Den TV'), ad(CONNECT_SERVICE, 41000), ad(PAIR_SERVICE, 37000)],
      async (_, port) => port === 41000
    )
    expect(tvs).toEqual([{ host: '192.168.1.50', name: 'Den TV', ready: false, connectPort: 41000, pairPort: 37000 }])
  })

  it('never connects to the pairing port', async () => {
    const portOpen = vi.fn(async () => false)
    await mergeAdverts([ad(PAIR_SERVICE, 37000)], portOpen)
    expect(portOpen).not.toHaveBeenCalledWith(expect.anything(), 37000)
  })

  it('never connects to the pairing port, even if another advert claims it', async () => {
    const portOpen = vi.fn(async () => true)
    await mergeAdverts([ad(PAIR_SERVICE, 37000), ad(CONNECT_SERVICE, 37000)], portOpen)
    expect(portOpen).not.toHaveBeenCalledWith(expect.anything(), 37000)
    const legacy = vi.fn(async () => true)
    await mergeAdverts([ad(PAIR_SERVICE, 5555)], legacy)
    expect(legacy).not.toHaveBeenCalled()
  })

  it('checks a repeated connect port once', async () => {
    const portOpen = vi.fn(async (_: string, __: number) => false)
    await mergeAdverts([ad(CONNECT_SERVICE, 41000), ad(CONNECT_SERVICE, 41000)], portOpen)
    expect(portOpen.mock.calls.filter(([, port]) => port === 41000)).toHaveLength(1)
  })

  it('finds classic network ADB on a TV that advertised anything', async () => {
    const [tv] = await mergeAdverts([ad(NAME_SERVICE, 6466, 'Den TV')], async (_, port) => port === 5555)
    expect(tv.ready).toBe(true)
  })

  it('does not let a stale connect port hide the live one', async () => {
    for (const ports of [[41000, 39000], [39000, 41000]]) {
      const [tv] = await mergeAdverts(ports.map((p) => ad(CONNECT_SERVICE, p)), async (_, port) => port === 41000)
      expect(tv.connectPort).toBe(41000)
    }
  })

  it('reports a TV with debugging off', async () => {
    const [tv] = await mergeAdverts([ad(NAME_SERVICE, 6466, 'Den TV'), ad(CONNECT_SERVICE, 39000)], async () => false)
    expect(tv).toEqual({ host: '192.168.1.50', name: 'Den TV', ready: false })
  })

  it('cleans a name that came off the network', async () => {
    const [tv] = await mergeAdverts([ad(NAME_SERVICE, 6466, `  Den\u0000‮ TV${'x'.repeat(100)}`)], async () => false)
    expect(tv.name).toMatch(/^Den TVx+$/)
    expect(tv.name.length).toBeLessThanOrEqual(64)
  })

  it('lists named TVs first', async () => {
    const tvs = await mergeAdverts(
      [ad(CONNECT_SERVICE, 1, 'adb', '192.168.1.2'), ad(NAME_SERVICE, 2, 'Den TV', '192.168.1.9')],
      async () => false
    )
    expect(tvs.map((t) => t.host)).toEqual(['192.168.1.9', '192.168.1.2'])
  })
})

describe('sendOnEach', () => {
  function fakeSocket(failOn: string[] = []) {
    const sent: string[] = []
    const pending: (() => void)[] = []
    let current = ''
    const sock = {
      addMembership: vi.fn((_: string, address?: string) => {
        if (failOn.includes(address!)) throw new Error('no multicast')
      }),
      setMulticastInterface: vi.fn((address: string) => {
        current = address
      }),
      // Like the real one: the send happens later, on whatever interface is set by then.
      send: vi.fn((...args: unknown[]) => {
        const done = args[args.length - 1] as () => void
        pending.push(() => {
          sent.push(current)
          done()
        })
      })
    }
    const flush = (): void => {
      while (pending.length) pending.shift()!()
    }
    return { sock: sock as unknown as Parameters<typeof sendOnEach>[0], sent, flush, raw: sock }
  }

  it('sends out of every interface, not just the last', () => {
    const { sock, sent, flush } = fakeSocket()
    sendOnEach(sock, ['192.168.1.2', '10.0.0.2'], new Set(), Buffer.from([0]))
    flush()
    expect(sent).toEqual(['192.168.1.2', '10.0.0.2'])
  })

  it('carries on past an interface that fails', () => {
    const { sock, sent, flush } = fakeSocket(['10.0.0.2'])
    sendOnEach(sock, ['10.0.0.2', '192.168.1.2'], new Set(), Buffer.from([0]))
    flush()
    expect(sent).toEqual(['192.168.1.2'])
  })

  it('joins each network once', () => {
    const { sock, raw, flush } = fakeSocket()
    const joined = new Set<string>()
    sendOnEach(sock, ['192.168.1.2'], joined, Buffer.from([0]))
    flush()
    sendOnEach(sock, ['192.168.1.2'], joined, Buffer.from([0]))
    flush()
    expect(raw.addMembership).toHaveBeenCalledTimes(1)
  })
})
