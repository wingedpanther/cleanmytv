// Find TVs: every address on this computer's local /24 networks with port 5555 open.
import { Socket } from 'node:net'
import { networkInterfaces } from 'node:os'
import type { ScanHit } from '@shared/api'

/** Private ranges only: a home TV sits on one, and the app has no business anywhere else. */
export function isPrivateIpv4(address: string): boolean {
  return /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(address)
}

export function localSubnets(): string[] {
  const out = new Set<string>()
  for (const list of Object.values(networkInterfaces())) {
    for (const a of list ?? []) {
      if (a.family !== 'IPv4' || a.internal) continue
      // A /24 around this address is where a home TV sits.
      if (!isPrivateIpv4(a.address)) continue
      out.add(a.address.split('.').slice(0, 3).join('.'))
    }
  }
  return [...out]
}

export function probe(host: string, port: number, timeoutMs: number): Promise<boolean> {
  return new Promise((resolve) => {
    const s = new Socket()
    const done = (open: boolean): void => { s.destroy(); resolve(open) }
    s.setTimeout(timeoutMs)
    s.once('connect', () => done(true))
    s.once('timeout', () => done(false))
    s.once('error', () => done(false))
    s.connect(port, host)
  })
}

export async function scan(port = 5555): Promise<ScanHit[]> {
  const hosts = localSubnets().flatMap((net) => Array.from({ length: 254 }, (_, i) => `${net}.${i + 1}`))
  const hits: ScanHit[] = []
  let next = 0
  const worker = async (): Promise<void> => {
    while (next < hosts.length) {
      const h = hosts[next++]
      if (await probe(h, port, 600)) hits.push({ host: h })
    }
  }
  await Promise.all(Array.from({ length: 96 }, worker))
  return hits.sort((a, b) => Number(a.host.split('.')[3]) - Number(b.host.split('.')[3]))
}
