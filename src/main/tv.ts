// The connected TV: one AdbClient, plus the helpers the upstream scripts built on top of tools/tv.py
// (probe, --batch, b64push.py for large files, base64 pulls).
import { createHash } from 'node:crypto'
import { AdbClient, AdbError } from './adb/client'
import { loadKeys } from './adb/keys'
import { log } from './log'
import type { ConnStatus, Probe } from '@shared/api'

let client: AdbClient | null = null
let status: ConnStatus = { state: 'idle' }
let keyPath = ''
let statusListener: (s: ConnStatus) => void = () => {}

export function init(appKeyPath: string, onStatus: (s: ConnStatus) => void): void {
  keyPath = appKeyPath
  statusListener = onStatus
}

function setStatus(s: ConnStatus): ConnStatus {
  status = s
  statusListener(s)
  return s
}

export function getStatus(): ConnStatus {
  return status
}

/** Lets a caller that knows more (reconnect) replace the status the window shows. */
export function report(s: ConnStatus): ConnStatus {
  return setStatus(s)
}

export async function connect(host: string): Promise<ConnStatus> {
  host = host.trim()
  disconnect()
  setStatus({ state: 'connecting', host })
  try {
    // "ip" or "ip:port"; the record and messages use the IP, as upstream's TV_HOST does.
    const m = /^([A-Za-z0-9.-]+)(?::(\d{1,5}))?$/.exec(host)
    if (!m || (m[2] && (Number(m[2]) < 1 || Number(m[2]) > 65535))) {
      return setStatus({ state: 'error', host, error: 'That is not an address. Type the IP the TV shows under Settings → Network, like 192.168.1.50.' })
    }
    const c = await AdbClient.connect({
      host: m[1],
      port: m[2] ? Number(m[2]) : 5555,
      keys: loadKeys(keyPath),
      onAuthPrompt: () => setStatus({ state: 'waiting-auth', host })
    })
    c.onClose = (err) => {
      if (client === c) {
        client = null
        setStatus({ state: 'error', host, error: `Lost the TV: ${err.message}` })
      }
    }
    client = c
    const probe = await readProbe(c, m[1])
    return setStatus({ state: 'connected', host, probe })
  } catch (e) {
    const err = e as AdbError
    return setStatus({ state: 'error', host, error: friendly(err), needsPairing: err.code === 'tls' })
  }
}

function friendly(err: AdbError): string {
  switch (err.code) {
    case 'refused':
      return 'The TV is there but network debugging is off. Turn on USB debugging (and Network or ADB debugging if you see it) in Developer options. If your TV only has Wireless debugging (most Google TVs and Chromecasts), pair it with a code instead.'
    case 'timeout':
    case 'unreachable':
      return `${err.message} Check the IP address, that the TV is on, and that this computer is on the same network.`
    case 'tls':
      return 'This TV only offers Wireless debugging, which needs a one-time pairing code.'
    case 'auth':
      return err.message
    default:
      return err.message || String(err)
  }
}

export function disconnect(): void {
  if (client) {
    const c = client
    client = null
    c.close()
  }
  if (status.state !== 'idle') setStatus({ state: 'idle', host: status.host })
}

export function tv(): AdbClient {
  if (!client || client.closed) throw new Error('Not connected to a TV. Connect in Setup first.')
  return client
}

/** The TV's IP address, without a port. */
export function host(): string {
  if (!status.host) throw new Error('Not connected to a TV.')
  return status.host.replace(/:\d+$/, '')
}

async function readProbe(c: AdbClient, h: string): Promise<Probe> {
  const keys = ['ro.product.model', 'ro.product.device', 'ro.product.name', 'ro.product.manufacturer',
    'ro.build.version.release', 'ro.build.version.sdk', 'ro.build.display.id', 'ro.adb.secure']
  // One shell call, one line per property, so a slow TV answers in one round trip.
  const out = await c.shell(keys.map((k) => `getprop ${k}`).join('; '))
  const v = out.split('\n').map((l) => l.trim())
  return {
    host: h,
    model: v[0] ?? '',
    device: v[1] ?? '',
    product: v[2] ?? '',
    manufacturer: v[3] ?? '',
    release: v[4] ?? '',
    sdk: Number(v[5]) || 0,
    build: v[6] ?? '',
    adbSecure: v[7] ?? ''
  }
}

/** One shell command, logged under `task`. */
export async function sh(task: string, command: string, opts: { quiet?: boolean; timeoutMs?: number } = {}): Promise<string> {
  if (!opts.quiet) log(task, 'cmd', command)
  const out = await tv().shell(command, opts.timeoutMs)
  if (!opts.quiet && out.trim()) log(task, 'info', out.trimEnd())
  return out
}

/** One command per call on the same connection, as tools/tv.py --batch. */
export async function batch(task: string, commands: string[]): Promise<string[]> {
  const outs: string[] = []
  for (const c of commands) outs.push(await sh(task, c))
  return outs
}

/**
 * Upload a file as base64 chunks through plain shell commands, then check the MD5 on the TV
 * (upstream tools/b64push.py: large ADB sync pushes hang on some TVs).
 */
export async function pushB64(task: string, data: Buffer, remote: string, onChunk?: (i: number, n: number) => void): Promise<void> {
  const c = tv()
  const want = createHash('md5').update(data).digest('hex')
  // base64 of the chunk plus the echo must fit one payload; a multiple of 3 keeps '=' padding out of
  // the middle of the stream, which base64 -d would corrupt.
  let chunk = Math.min(48 * 1024, Math.floor((c.maxData * 3) / 4) - 64 - remote.length)
  chunk -= chunk % 3
  if (chunk < 3) throw new Error(`this TV accepts commands too short (${c.maxData} bytes) to upload files.`)
  const tmp = remote + '.b64'
  q(remote); q(tmp)
  await c.shell(`rm -f ${remote} ${tmp}`)
  const n = Math.max(1, Math.ceil(data.length / chunk))
  log(task, 'cmd', `upload ${remote} (${Math.round(data.length / 1024)} KB, ${n} chunks)`)
  for (let i = 0; i < n; i++) {
    const part = data.subarray(i * chunk, (i + 1) * chunk).toString('base64')
    const out = await c.shell(`echo '${part}' >> ${tmp} && echo ok${i}`, 60000)
    if (!out.includes(`ok${i}`)) throw new Error(`upload chunk ${i + 1} of ${n} failed: ${out.trim()}`)
    onChunk?.(i + 1, n)
  }
  const out = await c.shell(`base64 -d ${tmp} > ${remote} && rm ${tmp} && md5sum ${remote}`, 120000)
  const got = out.trim().split(/\s+/)[0] ?? ''
  if (got !== want) throw new Error(`upload of ${remote} is damaged (md5 ${got || 'missing'}, expected ${want}).`)
  log(task, 'ok', `uploaded ${remote}, checksum matches`)
}

/** Read a file from the TV, binary-safe: base64 on the TV, decoded here. Null when it is missing. */
export async function pullB64(remote: string): Promise<Buffer | null> {
  q(remote)
  const out = await tv().shell(`[ -f ${remote} ] && base64 ${remote} || echo __missing__`, 120000)
  if (out.includes('__missing__')) return null
  return Buffer.from(out.replace(/\s+/g, ''), 'base64')
}

/** Paths and names that go into a TV shell command: refuse anything with shell syntax. */
export function q(value: string): string {
  // A leading '-' would turn a name into an option of pm, rm or base64.
  if (!/^[A-Za-z0-9_./@:][A-Za-z0-9_./@:-]*$/.test(value)) throw new Error(`refusing unsafe name for a TV command: ${value}`)
  return value
}

export const PACKAGE_RE = /^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$/
export const HOME_QUERY = 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'
export const HOME_CLAIMS = 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME'

/** The component that owns Home, or null (Android's chooser answers "android"). */
export async function homeComponent(): Promise<string | null> {
  const out = await tv().shell(HOME_QUERY)
  const last = out.trim().split('\n').pop()?.trim() ?? ''
  return /^[A-Za-z_][\w.]*\/[\w.$]+$/.test(last) ? last : null
}

/** What resolve-activity printed last: a component, "android" for the chooser, or "". */
export async function homeAnswer(): Promise<string> {
  const out = await tv().shell(HOME_QUERY)
  return (out.trim().split('\n').pop() ?? '').trim()
}

export async function homeClaimants(): Promise<string[]> {
  const out = await tv().shell(HOME_CLAIMS)
  const pkgs = new Set<string>()
  for (const line of out.split('\n')) {
    const m = /^\s*([A-Za-z0-9_.]+)\//.exec(line)
    if (m) pkgs.add(m[1])
  }
  return [...pkgs].sort()
}

export async function packageSets(): Promise<{ installed: Set<string>; disabled: Set<string> }> {
  const parse = (out: string): Set<string> => new Set(out.split('\n').filter((l) => l.startsWith('package:')).map((l) => l.slice(8).trim()))
  const installed = parse(await tv().shell('pm list packages'))
  if (!installed.size) throw new Error('The TV returned no packages. Check the connection and try again.')
  const disabled = parse(await tv().shell('pm list packages -d'))
  return { installed, disabled }
}
