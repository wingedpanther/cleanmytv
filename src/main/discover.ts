// Find TVs by name: listen for the mDNS adverts Android TVs send, the same way a phone finds them.
// Nothing here logs in to anything: it reads adverts and checks whether a port accepts a connection.
import { createSocket, type Socket } from 'node:dgram'
import { networkInterfaces } from 'node:os'
import type { FoundTv } from '@shared/api'
import { isPrivateIpv4, probe } from './scan'

const GROUP = '224.0.0.251'
const PORT = 5353
export const TYPE_A = 1
export const TYPE_PTR = 12
export const TYPE_SRV = 33
const CLASS_IN = 1
const MAX_NAME = 255

/** Google TV and most Android TVs advertise this one with their name, even with debugging off. */
export const NAME_SERVICE = '_androidtvremote2._tcp.local'
export const CONNECT_SERVICE = '_adb-tls-connect._tcp.local'
export const PAIR_SERVICE = '_adb-tls-pairing._tcp.local'
const SERVICES = [NAME_SERVICE, CONNECT_SERVICE, PAIR_SERVICE]
const MAX_INSTANCES = 64
const MAX_QUESTIONS = 20
/** Ask again before a detail runs out, so a TV doesn't drop off the list while it's still there. */
const REFRESH_BEFORE_MS = 15_000

/** Classic network ADB doesn't advertise itself: look for it on any address the adverts turned up. */
const LEGACY_PORT = 5555

export interface DnsRecord {
  /** Lowercase, dot-joined, for matching. */
  key: string
  type: number
  ttl: number
  /** PTR: the target's labels. SRV: `port` and the target's labels. A: `ip`. */
  labels?: string[]
  port?: number
  ip?: string
}

/** Reads a possibly compressed name. Returns null for anything malformed: this is a stranger's packet. */
function readName(buf: Buffer, start: number): { labels: string[]; next: number } | null {
  const labels: string[] = []
  let at = start
  let next = -1
  let floor = start
  let total = 0
  for (;;) {
    if (at >= buf.length) return null
    const len = buf[at]
    if (len === 0) return { labels, next: next === -1 ? at + 1 : next }
    if ((len & 0xc0) === 0xc0) {
      if (at + 1 >= buf.length) return null
      const target = ((len & 0x3f) << 8) | buf[at + 1]
      // Only backwards, and always further back, so a pointer loop can't spin.
      if (target >= floor) return null
      if (next === -1) next = at + 2
      floor = target
      at = target
      continue
    }
    if (len & 0xc0) return null
    total += len + 1
    if (total > MAX_NAME || at + 1 + len > buf.length) return null
    labels.push(buf.toString('utf8', at + 1, at + 1 + len))
    at += 1 + len
  }
}

const keyOf = (labels: string[]): string => labels.join('.').toLowerCase()

/** The records in a DNS response. Empty for queries and for anything malformed. */
export function parseDnsResponse(buf: Buffer): DnsRecord[] {
  if (buf.length < 12 || !(buf[2] & 0x80)) return []
  let at = 12
  for (let i = buf.readUInt16BE(4); i > 0; i--) {
    const q = readName(buf, at)
    if (!q || q.next + 4 > buf.length) return []
    at = q.next + 4
  }
  const out: DnsRecord[] = []
  const count = buf.readUInt16BE(6) + buf.readUInt16BE(8) + buf.readUInt16BE(10)
  for (let i = 0; i < count; i++) {
    const n = readName(buf, at)
    if (!n || n.next + 10 > buf.length) return []
    const type = buf.readUInt16BE(n.next)
    const ttl = buf.readUInt32BE(n.next + 4)
    const length = buf.readUInt16BE(n.next + 8)
    const data = n.next + 10
    if (data + length > buf.length) return []
    at = data + length
    const rec: DnsRecord = { key: keyOf(n.labels), type, ttl }
    // Names in the data may point backwards, but must end inside this record.
    const inRecord = buf.subarray(0, data + length)
    if (type === TYPE_PTR) {
      const t = readName(inRecord, data)
      if (!t) continue
      rec.labels = t.labels
    } else if (type === TYPE_SRV && length > 6) {
      const t = readName(inRecord, data + 6)
      if (!t) continue
      rec.port = buf.readUInt16BE(data + 4)
      rec.labels = t.labels
    } else if (type === TYPE_A && length === 4) {
      rec.ip = [...buf.subarray(data, data + 4)].join('.')
    } else {
      continue
    }
    out.push(rec)
  }
  return out
}

export interface Question {
  /** Labels, not a dotted string: a TV's name can have a dot in it. */
  labels: string[]
  type: number
}

export function buildQuery(questions: Question[]): Buffer {
  const head = Buffer.alloc(12)
  head.writeUInt16BE(questions.length, 4)
  const parts = [head]
  for (const q of questions) {
    for (const l of q.labels) {
      const b = Buffer.from(l)
      parts.push(Buffer.from([b.length]), b)
    }
    parts.push(Buffer.from([0, 0, q.type, 0, CLASS_IN]))
  }
  return Buffer.concat(parts)
}

export interface Advert {
  service: string
  instance: string
  ip: string
  port: number
}

interface Timed<T> { value: T; expires: number }

/** What the network has said so far: adverts arrive in pieces, and each piece has its own lifetime. */
export class AdvertCache {
  private instances = new Map<string, Timed<{ service: string; labels: string[]; key: string }>>()
  private srv = new Map<string, Timed<{ port: number; target: string[] }>>()
  private a = new Map<string, Timed<string>>()

  constructor(private now: () => number = Date.now) {}

  add(records: DnsRecord[]): void {
    for (const r of records) {
      const expires = this.now() + r.ttl * 1000
      if (r.type === TYPE_PTR && r.labels?.length && SERVICES.includes(r.key)) {
        const key = keyOf(r.labels)
        // Re-inserted, so the newest advert for a TV comes last.
        this.instances.delete(key)
        if (r.ttl > 0) this.instances.set(key, { value: { service: r.key, labels: r.labels, key }, expires })
        // Anyone on the network can send these: the oldest goes first.
        if (this.instances.size > MAX_INSTANCES) this.instances.delete(this.instances.keys().next().value!)
      } else if (r.type === TYPE_SRV && r.labels) {
        if (r.ttl > 0 && r.port) this.srv.set(r.key, { value: { port: r.port, target: r.labels }, expires })
        else if (r.ttl === 0) this.srv.delete(r.key)
      } else if (r.type === TYPE_A && r.ip && isPrivateIpv4(r.ip)) {
        if (r.ttl > 0) this.a.set(r.key, { value: r.ip, expires })
        else this.a.delete(r.key)
      }
    }
  }

  private live<T>(entry: Timed<T> | undefined): T | undefined {
    return entry && entry.expires > this.now() ? entry.value : undefined
  }

  adverts(): Advert[] {
    const out: Advert[] = []
    for (const entry of this.instances.values()) {
      const inst = this.live(entry)
      const srv = inst && this.live(this.srv.get(inst.key))
      const ip = srv && this.live(this.a.get(keyOf(srv.target)))
      if (inst && srv && ip) out.push({ service: inst.service, instance: inst.labels[0], ip, port: srv.port })
    }
    return out
  }

  private fresh<T>(entry: Timed<T> | undefined): boolean {
    return Boolean(entry && entry.expires - this.now() > REFRESH_BEFORE_MS)
  }

  /** The questions still worth asking: who is out there, plus any detail missing or about to run out. */
  questions(): Question[] {
    const out: Question[] = SERVICES.map((name) => ({ labels: name.split('.'), type: TYPE_PTR }))
    for (const entry of this.instances.values()) {
      const inst = this.live(entry)
      if (!inst) continue
      const srv = this.srv.get(inst.key)
      if (!this.fresh(srv)) out.push({ labels: inst.labels, type: TYPE_SRV })
      const target = this.live(srv)?.target
      if (target && !this.fresh(this.a.get(keyOf(target)))) out.push({ labels: target, type: TYPE_A })
    }
    return out.slice(0, MAX_QUESTIONS)
  }
}

/** A name came off the network: no control characters, and short enough for the list. */
function cleanName(name: string): string {
  return name.replace(/[\p{Cc}\p{Cf}]/gu, '').trim().slice(0, 64)
}

/**
 * One entry per address. The pairing port is deliberately never connected to: a stray connection can
 * close the pairing screen on the TV.
 */
export async function mergeAdverts(
  adverts: Advert[],
  portOpen: (host: string, port: number) => Promise<boolean>
): Promise<FoundTv[]> {
  const tvs = new Map<string, FoundTv>()
  const connectPorts = new Map<string, number[]>()
  for (const a of adverts) {
    const tv = tvs.get(a.ip) ?? { host: a.ip, name: '', ready: false }
    tvs.set(a.ip, tv)
    if (a.service === NAME_SERVICE) tv.name = cleanName(a.instance)
    else if (a.service === PAIR_SERVICE) tv.pairPort = a.port
    // Newest first: a TV that restarted Wireless debugging can still have its old port cached.
    else if (a.service === CONNECT_SERVICE) connectPorts.set(a.ip, [a.port, ...(connectPorts.get(a.ip) ?? []).filter((p) => p !== a.port)])
  }
  await Promise.all(
    [...tvs.values()].map(async (tv) => {
      // An advert can claim any port: the rule holds whichever service names the pairing port.
      tv.ready = tv.pairPort !== LEGACY_PORT && (await portOpen(tv.host, LEGACY_PORT))
      for (const port of connectPorts.get(tv.host) ?? []) {
        if (port !== tv.pairPort && (await portOpen(tv.host, port))) {
          tv.connectPort = port
          break
        }
      }
    })
  )
  // Named TVs first: a phone with Wireless debugging on has no name to show.
  return [...tvs.values()].sort((a, b) => Number(!a.name) - Number(!b.name) || a.name.localeCompare(b.name) || a.host.localeCompare(b.host))
}

function localAddresses(): string[] {
  return Object.values(networkInterfaces())
    .flatMap((list) => list ?? [])
    .filter((a) => a.family === 'IPv4' && !a.internal && isPrivateIpv4(a.address))
    .map((a) => a.address)
}

const FIRST_ASKS_MS = [0, 1000, 3000]
const ASK_EVERY_MS = 5000

let socket: Socket | null = null
let timer: NodeJS.Timeout | null = null
// Kept when listening stops: lifetimes decide what's stale, and the list doesn't blink after a failed connect.
const cache = new AdvertCache()

/**
 * One interface at a time, waiting for each send: the outgoing interface is a setting on the socket,
 * and sends finish later, so setting it in a plain loop would send everything out of the last one.
 */
export function sendOnEach(
  sock: Pick<Socket, 'addMembership' | 'setMulticastInterface' | 'send'>,
  addresses: string[],
  joined: Set<string>,
  query: Buffer
): void {
  const next = (i: number): void => {
    if (i >= addresses.length) return
    try {
      if (!joined.has(addresses[i])) {
        sock.addMembership(GROUP, addresses[i])
        joined.add(addresses[i])
      }
      sock.setMulticastInterface(addresses[i])
      // A callback also keeps a failed send (a VPN, usually) from closing the socket for the others.
      sock.send(query, PORT, GROUP, () => next(i + 1))
    } catch {
      next(i + 1)
    }
  }
  next(0)
}

/** Starts listening if it isn't already. Without mDNS (firewall, port taken) the list just stays empty. */
export function startDiscovery(): void {
  if (socket) return
  const sock = createSocket({ type: 'udp4', reuseAddr: true })
  const joined = new Set<string>()
  socket = sock
  sock.on('error', () => {
    if (socket === sock) stopDiscovery()
  })
  sock.on('message', (msg) => {
    if (socket === sock) cache.add(parseDnsResponse(msg))
  })
  // Port 5353 itself: TVs answer a question from any other port without their name.
  sock.bind(PORT, () => {
    if (socket !== sock) return
    sock.unref()
    let step = 0
    const tick = (): void => {
      if (socket === sock) sendOnEach(sock, localAddresses(), joined, buildQuery(cache.questions()))
      step++
      timer = setTimeout(tick, step < FIRST_ASKS_MS.length ? FIRST_ASKS_MS[step] - FIRST_ASKS_MS[step - 1] : ASK_EVERY_MS)
      timer.unref()
    }
    tick()
  })
}

export function stopDiscovery(): void {
  if (timer) clearTimeout(timer)
  timer = null
  const sock = socket
  socket = null
  try {
    sock?.close()
  } catch {
    // Already closed.
  }
}

export async function discover(): Promise<FoundTv[]> {
  startDiscovery()
  return mergeAdverts(cache.adverts(), (host, port) => probe(host, port, 500))
}
