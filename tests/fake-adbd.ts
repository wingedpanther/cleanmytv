// A pretend TV for tests: speaks the adbd side of the protocol (CNXN, AUTH with real RSA checks,
// shell streams) and answers shell commands from a small scripted model of an Android TV.
import { createHash, createPublicKey, publicDecrypt, constants, randomBytes } from 'node:crypto'
import { createServer, Server, Socket } from 'node:net'

const A_CNXN = 0x4e584e43, A_AUTH = 0x48545541, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f, A_CLSE = 0x45534c43, A_WRTE = 0x45545257

function packet(cmd: number, a0: number, a1: number, data = Buffer.alloc(0)): Buffer {
  const h = Buffer.alloc(24)
  h.writeUInt32LE(cmd, 0); h.writeUInt32LE(a0 >>> 0, 4); h.writeUInt32LE(a1 >>> 0, 8); h.writeUInt32LE(data.length, 12)
  let sum = 0; for (const b of data) sum = (sum + b) >>> 0
  h.writeUInt32LE(sum, 16); h.writeUInt32LE((cmd ^ 0xffffffff) >>> 0, 20)
  return Buffer.concat([h, data])
}

/** Android's RSAPublicKey struct (base64) back to a Node public key. */
export function decodeAndroidKey(pub: string) {
  const raw = Buffer.from(pub.trim().split(/\s/)[0], 'base64')
  const words = raw.readUInt32LE(0)
  const n = Buffer.alloc(words * 4)
  for (let i = 0; i < words; i++) n.writeUInt32BE(raw.readUInt32LE(8 + i * 4), (words - 1 - i) * 4)
  const e = raw.readUInt32LE(8 + words * 8)
  const eBuf = Buffer.alloc(4); eBuf.writeUInt32BE(e)
  return { key: createPublicKey({ key: { kty: 'RSA', n: n.toString('base64url'), e: eBuf.subarray(eBuf.findIndex((b) => b !== 0)).toString('base64url') }, format: 'jwk' }), n0inv: raw.readUInt32LE(4), words, raw }
}

const DIGEST = Buffer.from('3021300906052b0e03021a05000414', 'hex')

export interface FakeTv {
  packages: Map<string, 'enabled' | 'disabled'>
  home: string
  homeClaims: string[]
  wifiOwners: string
  settings: Map<string, string>
  files: Map<string, Buffer>
  commands: string[]
  trusted: string[]
  autoAccept: boolean
  maxData: number
  adbSecure: string
  leanback: string[]
  launcherApps: string[]
  screenshot: Buffer
  /** The launcher's Android/data folder: missing ones only appear once the launcher has run. */
  appDir: 'ok' | 'missing' | 'denied'
  /** CleanMyTV Home's settings, as its ConfigProvider holds them. */
  homeConfig: Record<string, unknown>
  /** Home preference changes are ignored, as on TVs where another app grabs Home back. */
  homeStuck?: boolean
  /** ro.build.version.sdk; 31 when unset. */
  sdk?: string
  /** Packages that never stay running (pidof finds nothing), like a launcher crashing at boot. */
  crashing?: string[]
  /** Misbehaviour for client hardening tests. */
  tokenSize?: number
  sendMalformed?: 'oversize' | 'magic'
}

export function newTv(): FakeTv {
  return {
    packages: new Map(), home: 'com.google.android.tvlauncher/.MainActivity', homeClaims: [], wifiOwners: 'cname=android.uid.system:1000',
    settings: new Map(), files: new Map(), commands: [], trusted: [], autoAccept: true, maxData: 4096, adbSecure: '1',
    leanback: [], launcherApps: [], screenshot: Buffer.alloc(0), appDir: 'ok',
    homeConfig: { accent: '#FF3000', focus: 'glow', visualiser: true }
  }
}

function sh(tv: FakeTv, cmd: string): string {
  tv.commands.push(cmd)
  let m: RegExpExecArray | null
  if (cmd.startsWith('getprop')) {
    const props: Record<string, string> = { 'ro.product.model': 'BRAVIA 4K VH2', 'ro.product.device': 'BRAVIA_VH2', 'ro.product.name': 'BRAVIA_VH2_EU', 'ro.product.manufacturer': 'Sony', 'ro.build.version.release': tv.sdk === '24' ? '7.0' : '12', 'ro.build.version.sdk': tv.sdk ?? '31', 'sys.boot_completed': '1', 'ro.build.display.id': 'TEST', 'ro.adb.secure': tv.adbSecure }
    return cmd.split(';').map((c) => props[c.trim().split(' ')[1]] ?? '').join('\n') + '\n'
  }
  if (cmd === 'pm list packages') return [...tv.packages.keys()].map((p) => `package:${p}`).join('\n') + '\n'
  if (cmd === 'pm list packages -d') return [...tv.packages].filter(([, s]) => s === 'disabled').map(([p]) => `package:${p}`).join('\n') + '\n'
  if (cmd.startsWith('cmd package resolve-activity')) return `priority=0 preferredOrder=0\n${tv.home}\n`
  if (cmd.includes('LEANBACK_LAUNCHER')) return tv.leanback.map((p) => `  ${p}/.Main`).join('\n') + '\n'
  if (cmd.includes('category.LAUNCHER')) return tv.launcherApps.map((p) => `  ${p}/.Main`).join('\n') + '\n'
  if (cmd.startsWith('cmd package query-activities')) return tv.homeClaims.filter((c) => tv.packages.get(c.split('/')[0]) === 'enabled').map((c) => `  ${c}`).join('\n') + '\n'
  if ((m = /^pidof (\S+)$/.exec(cmd))) return tv.packages.get(m[1]) === 'enabled' && !tv.crashing?.includes(m[1]) ? '4321\n' : ''
  if (cmd === 'reboot') return ''
  if (cmd === 'dumpsys wifi') return `WifiConfigManager ... ${tv.wifiOwners} ...\n`
  if ((m = /^settings get global (\S+)$/.exec(cmd))) return (tv.settings.get(m[1]) ?? 'null') + '\n'
  if ((m = /^pm disable-user --user 0 (\S+)$/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return `Error: Unknown package: ${m[1]}\n`
    tv.packages.set(m[1], 'disabled'); return `Package ${m[1]} new state: disabled-user\n`
  }
  if ((m = /^pm enable (?:--user 0 )?(\S+)$/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return `Error: Unknown package: ${m[1]}\n`
    tv.packages.set(m[1], 'enabled'); return `Package ${m[1]} new state: enabled\n`
  }
  if ((m = /^settings put (?:global|secure) (\S+) (.*)$/.exec(cmd)) && !cmd.includes(';')) { tv.settings.set(m[1], m[2]); return '' }
  if ((m = /^settings get secure (\S+)$/.exec(cmd))) return (tv.settings.get(m[1]) ?? 'null') + '\n'
  if ((m = /^pm install -r (\S+)$/.exec(cmd))) {
    if (!tv.files.has(m[1])) return 'Failure [INSTALL_FAILED_INVALID_URI]\n'
    const pkg = m[1].includes('cmtv-home') ? 'io.github.wingedpanther.cleanmytv.home' : 'com.gathin.tvhome'
    tv.packages.set(pkg, 'enabled'); tv.homeClaims = [...new Set([...tv.homeClaims, `${pkg}/.MainActivity`])]
    return 'Performing Streamed Install\nSuccess\n'
  }
  if ((m = /^content call --uri content:\/\/io\.github\.wingedpanther\.cleanmytv\.home\.config --method (\w+)(?: --arg (\S+))?$/.exec(cmd))) {
    if (!tv.packages.has('io.github.wingedpanther.cleanmytv.home')) return 'Error while accessing provider\n'
    const up = '/data/data/home/wallpaper.upload'
    if (m[1] === 'set64') Object.assign(tv.homeConfig, JSON.parse(Buffer.from(m[2], 'base64').toString('utf8')))
    else if (m[1] === 'wallpaper_begin') tv.files.set(up, Buffer.alloc(0))
    else if (m[1] === 'wallpaper_chunk') tv.files.set(up, Buffer.concat([tv.files.get(up) ?? Buffer.alloc(0), Buffer.from(m[2], 'base64')]))
    else if (m[1] === 'wallpaper_end') {
      const data = tv.files.get(up) ?? Buffer.alloc(0)
      if (createHash('md5').update(data).digest('hex') !== m[2]) return 'Error: wallpaper upload is damaged\n'
      tv.files.set('/data/data/home/wallpaper.jpg', data)
    } else if (m[1] === 'wallpaper_remove') tv.files.delete('/data/data/home/wallpaper.jpg')
    else if (m[1] === 'keys') return `Result: Bundle[{keys=${JSON.stringify([{ seq: 7, code: 83, name: 'KEYCODE_NOTIFICATION' }, { seq: 'x', code: 1 }])}}]\n`
    else if (m[1] === 'apps') return `Result: Bundle[{apps=${JSON.stringify([...tv.leanback.map((p) => ({ pkg: p, label: p.split('.').pop(), tv: true })), { pkg: 'bad;pkg', label: 'x', tv: true }])}}]\n`
    else if (m[1] !== 'get') return `Error: unknown method: ${m[1]}\n`
    return `Result: Bundle[{config=${JSON.stringify({ ...tv.homeConfig, has_wallpaper: tv.files.has('/data/data/home/wallpaper.jpg') })}}]\n`
  }
  if ((m = /^mkdir -p (\S+)\/wallpapers 2>&1; touch/.exec(cmd))) {
    return tv.appDir === 'ok' ? 'writable\n' : `mkdir: '${m[1]}': Permission denied\ntouch: '${m[1]}/.cytv': No such file or directory\n`
  }
  if (cmd.startsWith('am start -W') && tv.appDir === 'missing') tv.appDir = 'ok'
  if (cmd.startsWith('am start -W')) return 'Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\nStatus: ok\nLaunchState: COLD\n'
  if ((m = /^cmd package set-home-activity --user 0 (\S+\/\S+)$/.exec(cmd))) { if (!tv.homeStuck) tv.home = m[1]; return 'Success\n' }
  // set-home-activity $(resolve-activity ... <pkg> | tail -n 1): the TV's shell resolves the component.
  if ((m = /^cmd package set-home-activity --user 0 \$\(.* (\S+) \| tail -n 1\)$/.exec(cmd))) {
    const c = tv.homeClaims.find((h) => h.startsWith(m![1] + '/'))
    if (c && tv.packages.get(m[1]) === 'enabled') tv.home = c
    return 'Success\n'
  }
  if ((m = /^dumpsys package (\S+) \| grep/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return ''
    return m[1] === 'com.gathin.tvhome' ? '    versionName=2.4\n' : '    versionName=1.0.0\n'
  }
  if ((m = /^screencap -p (\S+)$/.exec(cmd))) { tv.files.set(m[1], tv.screenshot); return '' }
  if ((m = /^ls (\S+)\/wallpapers/.exec(cmd))) return [...tv.files.keys()].filter((f) => f.startsWith(m![1] + '/wallpapers/')).map((f) => f.split('/').pop()).join('\n') + '\n'
  if ((m = /printf '%s' '(\w+)' > (\S+)/.exec(cmd))) tv.files.set(m[2], Buffer.from(m[1]))
  if (cmd.includes('rm -f') && cmd.includes('config.json')) for (const f of [...tv.files.keys()]) if (/config\.json|wallpaper/.test(f)) tv.files.delete(f)
  if (cmd.includes('echo reset')) return 'reset\n'
  if (cmd.includes('echo applied')) return 'applied\n'
  if ((m = /^pm uninstall (\S+)$/.exec(cmd))) {
    if (!tv.packages.delete(m[1])) return 'Failure [DELETE_FAILED_INTERNAL_ERROR]\n'
    tv.homeClaims = tv.homeClaims.filter((c) => !c.startsWith(m![1] + '/'))
    return 'Success\n'
  }
  if ((m = /^settings delete (?:global|secure) (\S+)$/.exec(cmd))) { tv.settings.delete(m[1]); return 'Deleted 1 rows\n' }
  if ((m = /^rm -f (.+)$/.exec(cmd))) { for (const f of m[1].split(' ')) tv.files.delete(f); return '' }
  if ((m = /^echo '([A-Za-z0-9+/=]*)' >> (\S+) && echo (ok\d+)$/.exec(cmd))) {
    tv.files.set(m[2], Buffer.concat([tv.files.get(m[2]) ?? Buffer.alloc(0), Buffer.from(m[1] + '\n')])); return m[3] + '\n'
  }
  if ((m = /^base64 -d (\S+) > (\S+) && rm \S+ && md5sum (\S+)$/.exec(cmd))) {
    const data = Buffer.from((tv.files.get(m[1]) ?? Buffer.alloc(0)).toString().replace(/\s+/g, ''), 'base64')
    tv.files.delete(m[1]); tv.files.set(m[2], data)
    return `${createHash('md5').update(data).digest('hex')}  ${m[2]}\n`
  }
  if ((m = /^\[ -f (\S+) \] && base64 \S+ \|\| echo __missing__$/.exec(cmd))) {
    const f = tv.files.get(m[1]); return f ? f.toString('base64').replace(/(.{76})/g, '$1\n') + '\n' : '__missing__\n'
  }
  return ''
}

export function startFakeAdbd(tv: FakeTv, listenPort = 0): Promise<{ server: Server; port: number }> {
  return new Promise((resolve) => {
    const server = createServer((sock: Socket) => {
      let buf = Buffer.alloc(0)
      let token = randomBytes(20)
      let nextRemote = 100
      const banner = Buffer.from('device::ro.product.name=BRAVIA_VH2_EU;ro.product.model=BRAVIA 4K VH2;ro.product.device=BRAVIA_VH2;\0')
      sock.on('data', (chunk: Buffer) => {
        buf = Buffer.concat([buf, chunk])
        while (buf.length >= 24 && buf.length >= 24 + buf.readUInt32LE(12)) {
          const cmd = buf.readUInt32LE(0), a0 = buf.readUInt32LE(4), len = buf.readUInt32LE(12)
          const data = buf.subarray(24, 24 + len)
          buf = buf.subarray(24 + len)
          if (cmd === A_CNXN) {
            if (tv.sendMalformed) {
              const bad = packet(A_AUTH, 1, 0, randomBytes(20))
              if (tv.sendMalformed === 'oversize') bad.writeUInt32LE(64 * 1024 * 1024, 12)
              else bad.writeUInt32LE(0, 20)
              sock.write(bad)
              return
            }
            token = randomBytes(tv.tokenSize ?? 20); sock.write(packet(A_AUTH, 1, 0, token))
          }
          else if (cmd === A_AUTH && a0 === 2) {
            const ok = tv.trusted.some((pub) => {
              try { return publicDecrypt({ key: decodeAndroidKey(pub).key, padding: constants.RSA_PKCS1_PADDING }, data).equals(Buffer.concat([DIGEST, token])) } catch { return false }
            })
            if (ok) sock.write(packet(A_CNXN, 0x01000000, tv.maxData, banner))
            else { token = randomBytes(20); sock.write(packet(A_AUTH, 1, 0, token)) }
          } else if (cmd === A_AUTH && a0 === 3) {
            if (tv.autoAccept) { tv.trusted.push(data.toString().replace(/\0$/, '')); sock.write(packet(A_CNXN, 0x01000000, tv.maxData, banner)) }
          } else if (cmd === A_OPEN) {
            if (len > tv.maxData) { sock.destroy(); return }
            const local = a0, remote = nextRemote++
            const service = data.toString().replace(/\0$/, '')
            sock.write(packet(A_OKAY, remote, local))
            const out = Buffer.from(service.startsWith('shell:') ? sh(tv, service.slice(6)) : '')
            for (let i = 0; i < out.length; i += tv.maxData) sock.write(packet(A_WRTE, remote, local, out.subarray(i, i + tv.maxData)))
            sock.write(packet(A_CLSE, remote, local))
          } else if (cmd === A_OKAY || cmd === A_CLSE) {
            // acknowledgements; nothing to do
          }
        }
      })
    })
    server.listen(listenPort, '127.0.0.1', () => resolve({ server, port: (server.address() as { port: number }).port }))
  })
}
