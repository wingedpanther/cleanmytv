import { describe, expect, it } from 'vitest'
import { diagnose, errnoOf } from '../src/main/doctor'

type Ifaces = Parameters<typeof diagnose>[1]
const nic = (address: string) => [{ address, family: 'IPv4', internal: false, netmask: '255.255.255.0', mac: '00:00:00:00:00:00', cidr: null }]
const wifi = (ip: string) => ({ en0: nic(ip) }) as unknown as Ifaces
const withVpn = (ip: string) => ({ en0: nic(ip), utun4: nic('10.8.0.2') }) as unknown as Ifaces


describe('setup doctor', () => {
  it('points a Mac at Local Network access when the TV is on its own network but unreachable', () => {
    const h = diagnose({ code: 'unreachable', errno: 'EHOSTUNREACH', host: '192.168.68.64', port: 44777 }, wifi('192.168.68.20'), 'darwin')
    expect(h[0]).toMatch(/Local Network/)
  })

  it('says when the computer and the TV are on different networks', () => {
    const h = diagnose({ code: 'timeout', host: '192.168.68.64', port: 5555 }, wifi('10.0.0.5'), 'win32')
    expect(h.join(' ')).toMatch(/on 10\.0\.0\.x, and the TV is on 192\.168\.68\.x/)
    expect(h.join(' ')).not.toMatch(/Local Network/)
  })

  it('names a VPN that is up', () => {
    expect(diagnose({ code: 'timeout', host: '192.168.1.9', port: 5555 }, withVpn('192.168.1.4'), 'win32').join(' ')).toMatch(/VPN is on/)
  })

  it('spots a pairing port typed into Connect', () => {
    expect(diagnose({ code: 'refused', host: '192.168.1.9', port: 41235 }, wifi('192.168.1.4'), 'darwin').join(' ')).toMatch(/only works for pairing/)
    expect(diagnose({ code: 'refused', host: '192.168.1.9', port: 5555 }, wifi('192.168.1.4'), 'darwin')).toEqual([])
  })

  it('reads the system error out of a message', () => {
    expect(errnoOf('could not reach 192.168.68.64:44777 (EHOSTUNREACH).')).toBe('EHOSTUNREACH')
    expect(errnoOf('the TV closed the connection.')).toBeUndefined()
  })
})
