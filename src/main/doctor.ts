// Setup doctor: when Connect fails, name the likely cause from what this computer can see, instead of a
// bare "could not reach". The same few things come up in help every week: macOS blocking the app from
// the local network, a computer on another network or a VPN, and the pairing port typed into Connect.
import type { NetworkInterfaceInfo } from 'node:os'
import { isVirtualBridge } from './scan'
import { t } from './i18n'

export interface Failure {
  /** AdbError's code: refused, timeout, unreachable, tls, auth, closed, protocol. */
  code: string
  /** The system error inside the message, like EHOSTUNREACH, when there is one. */
  errno?: string
  host: string
  port: number
}

type Ifaces = NodeJS.Dict<NetworkInterfaceInfo[]>

/** Starts the Mac app from Terminal, so it borrows Terminal's Local Network access. */
export const MAC_FROM_TERMINAL = '/Applications/CleanMyTV.app/Contents/MacOS/CleanMyTV'

/** This computer's own IPv4 addresses on a LAN, without loopback, link-local and VPN tunnels. */
function lanAddresses(ifaces: Ifaces): string[] {
  const out: string[] = []
  for (const [name, list] of Object.entries(ifaces)) {
    if (isTunnel(name) || isVirtualBridge(name)) continue
    for (const a of list ?? []) if (a.family === 'IPv4' && !a.internal && !a.address.startsWith('169.254.')) out.push(a.address)
  }
  return out
}

function isTunnel(name: string): boolean {
  return /^(utun|tun|tap|wg|ppp|ipsec|zt)/i.test(name) || /vpn|wireguard|tailscale|nordlynx/i.test(name)
}

/** A VPN that is up: a tunnel interface with an IPv4 address. Tailscale alone doesn't take over the LAN. */
function vpnUp(ifaces: Ifaces): boolean {
  return Object.entries(ifaces).some(([name, list]) =>
    isTunnel(name) && !/tailscale/i.test(name) && (list ?? []).some((a) => a.family === 'IPv4' && !a.internal && !a.address.startsWith('100.')))
}

const prefix = (ip: string): string => ip.split('.').slice(0, 3).join('.')
const isIp = (s: string): boolean => /^\d{1,3}(\.\d{1,3}){3}$/.test(s)

/** Hints for a failed Connect, most likely first. Empty when nothing here explains it. */
export function diagnose(f: Failure, ifaces: Ifaces, platform: NodeJS.Platform): string[] {
  const hints: string[] = []
  const lan = lanAddresses(ifaces)
  const sameNet = !isIp(f.host) || lan.some((a) => prefix(a) === prefix(f.host))
  const noRoute = f.errno === 'EHOSTUNREACH' || f.errno === 'ENETUNREACH'

  if (platform === 'darwin' && noRoute && sameNet) {
    hints.push(t('macOS is probably blocking CleanMyTV from your network. Open System Settings → Privacy & Security → Local Network, switch CleanMyTV on, then quit and reopen the app.'))
    // Some Macs never show the switch for an app without a paid Apple certificate (#65). Started from
    // Terminal, the app uses Terminal's network access instead.
    hints.push(t('No CleanMyTV switch there? Quit the app, open Terminal and run: {command}', { command: MAC_FROM_TERMINAL }))
  }
  if (platform === 'linux' && (noRoute || f.errno === 'EACCES' || f.errno === 'EPERM') && sameNet) {
    hints.push(t('A firewall on this computer (such as ufw or firewalld) may be blocking local network connections, or the TV is off.'))
  }
  if (isIp(f.host) && lan.length && !sameNet) {
    const nets = lan.map((a) => `${prefix(a)}.x`).filter((v, i, all) => all.indexOf(v) === i)
    hints.push(t('This computer is on {nets}, and the TV is on {tv}. They need to be on the same network: check for a guest Wi-Fi or a second router.', { nets: nets.join(t(' and ')), tv: `${prefix(f.host)}.x` }))
  }
  if (vpnUp(ifaces) && (noRoute || f.code === 'timeout')) {
    hints.push(t('A VPN is on. Turn it off while you set up the TV, or allow local network access in the VPN app.'))
  }
  if (isPairingPort(f)) {
    hints.push(t('If that port came from the "Pair device with pairing code" window, it only works for pairing. Use Pair with a code with the IP, that port and the 6-digit code. Connect takes the port on the main Wireless debugging screen.'))
  }
  if (f.code === 'timeout' && sameNet && !hints.length) {
    hints.push(t('The TV may be asleep, or the router keeps devices apart (often called AP or client isolation). Wake the TV, and try again with the computer on the same Wi-Fi band as the TV.'))
  }
  return hints
}

/** Wireless debugging uses random high ports, and the one in the "Pair with code" window only takes a pairing. */
export function isPairingPort(f: Failure): boolean {
  return f.port >= 30000 && f.port <= 49999 && (f.code === 'refused' || f.code === 'closed' || f.code === 'protocol' || f.code === 'tls')
}

/** The system error code inside an AdbError message, like "(EHOSTUNREACH)". */
export function errnoOf(message: string): string | undefined {
  return /\((E[A-Z]+)\)/.exec(message)?.[1]
}
