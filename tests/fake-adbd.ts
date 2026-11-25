// A pretend TV for tests: speaks the adbd side of the protocol (CNXN, AUTH with real RSA checks,
// shell streams) and answers shell commands from a small scripted model of an Android TV.
import { createHash, createPublicKey, publicDecrypt, constants, randomBytes } from 'node:crypto'
import { createServer, Server, Socket } from 'node:net'
import { addDays } from '../src/shared/screentime'

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
  /**
   * Components that beat the Home preference while their app is on, highest priority first: on Google TV 14
   * launcherx (2), then setupwraith's RecoveryActivity (1), then any launcher set as Home (0).
   */
  homeOutranks?: string[]
  /** CleanMyTV Home was installed fresh and has no settings file yet. */
  homeFresh?: boolean
  /** ro.build.version.sdk; 31 when unset. */
  sdk?: string
  /** ro.product.manufacturer; Sony when unset. */
  manufacturer?: string
  /** ro.serialno; empty when unset. */
  serial?: string
  /** ro.product.device; BRAVIA_VH2 when unset. */
  device?: string
  /** CleanMyTV Home's versionName once installed; 1.0.0 when unset. */
  homeVersion?: string
  /** Free space on /data in KB; df answers nothing when unset. pm install needs 10 MB of it. */
  freeKb?: number
  /** What pm trim-caches frees, in KB. */
  cacheKb?: number
  /** Packages the firmware refuses to disable, as Fire OS does with Amazon's own. */
  protectedPkgs?: string[]
  /** The TV drops off the network: it answers nothing more and never closes the connection. */
  gone?: boolean
  /** Packages `pm enable` won't switch back on. */
  stuckOff?: string[]
  /** What `ime list -s` prints: the enabled keyboards. */
  imes?: string[]
  /** Themes saved on the TV by name. */
  homeThemes?: string[]
  /** The built-in themes Home's carousel has, in order (pack_end). */
  homePack?: string[]
  /** Screen time Home keeps: its today, and days as the launcher sends them (no access when undefined). */
  homeScreenTime?: { today: string; days: { day: string; apps: Record<string, number>; hours?: number[]; s?: Record<string, number[]> }[] }
  /** The keyboard CleanMyTV Home was told about. */
  homeKeyboard?: string
  /** What `logcat -d` prints. */
  logcat?: string
  /** What `dumpsys diskstats` prints; only the free-space lines when unset, as before Android's first measurement. */
  diskstats?: string
  /** System apps carrying a Play Store update, with the KB it takes; `pm uninstall-system-updates` frees it. */
  updatedSystem?: Map<string, number>
  /** Packages uninstalled for user 0 that `pm list packages -u` still lists. */
  uninstalledForUser?: string[]
  /** Packages that never stay running (pidof finds nothing), like a launcher crashing at boot. */
  crashing?: string[]
  /** The Home a restart hands back, like a Xiaomi TV giving it to its own launcher at every boot (#149). */
  bootHome?: string
  /** App-ops set with `appops set`, as "<package> <op>" to mode. */
  appops?: Map<string, string>
  /** appops answers with a SecurityException, as on TVs that lock it down. */
  appopsRefused?: boolean
  /** ro.hdmi.device_type: "0" on a TV, "4" on a streaming box; unset leaves it empty. */
  hdmiType?: string
  /** HDMI-CEC's tv_wake_on_one_touch_play; unset means the TV has no hdmi_control service. */
  cecWake?: string
  /**
   * A HOME entry without the DEFAULT category, as Haier's remote-firmware updater has (upstream #4068). A Home
   * query without MATCH_DEFAULT_ONLY then drops the saved Home choice while two launchers tie.
   */
  homeNonDefault?: string
  /** The file CleanMyTV Home's process is taking in, between a *_file_begin and its end. It goes when the process dies. */
  homeHeld?: string
  /** Home before #121: once its process has died, a piece fails ("fire_file_begin first") instead of finding the file on disk. */
  homeForgets?: boolean
  /** Upload calls, counting from 1, before which the TV kills Home to free memory. */
  homeDiesAt?: number[]
  /** Home dies during those calls rather than between them: the call fails and its piece is lost. */
  homeDiesInCall?: boolean
  homeUploadCalls?: number
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

function outranking(tv: FakeTv): string | undefined {
  return tv.homeOutranks?.find((c) => tv.packages.get(c.split('/')[0]) === 'enabled')
}

function sh(tv: FakeTv, cmd: string): string {
  tv.commands.push(cmd)
  let m: RegExpExecArray | null
  if (cmd.startsWith('getprop ro.hdmi.device_type; cmd hdmi_control cec_setting get ')) {
    const name = cmd.split(' ').at(-2)
    return `${tv.hdmiType ?? ''}\n` + (tv.cecWake === undefined ? "cmd: Can't find service: hdmi_control\n" : `${name} = ${tv.cecWake}\n`)
  }
  if ((m = /^cmd hdmi_control cec_setting set (\S+) (\S+)$/.exec(cmd))) { if (tv.cecWake !== undefined) tv.cecWake = m[2]; return '' }
  if (cmd.startsWith('getprop')) {
    const props: Record<string, string> = { 'ro.product.model': 'BRAVIA 4K VH2', 'ro.product.device': tv.device ?? 'BRAVIA_VH2', 'ro.product.name': 'BRAVIA_VH2_EU', 'ro.product.manufacturer': tv.manufacturer ?? 'Sony', 'ro.build.version.release': tv.sdk === '24' ? '7.0' : '12', 'ro.build.version.sdk': tv.sdk ?? '31', 'sys.boot_completed': '1', 'ro.build.display.id': 'TEST', 'ro.adb.secure': tv.adbSecure, 'ro.serialno': tv.serial ?? '', 'ro.build.fingerprint': 'Sony/BRAVIA_VH2/BRAVIA_VH2:12/TEST/1:user/release-keys' }
    return cmd.split(';').map((c) => props[c.trim().split(' ')[1]] ?? '').join('\n') + '\n'
  }
  if (cmd === 'pm list packages') return [...tv.packages.keys()].map((p) => `package:${p}`).join('\n') + '\n'
  if (cmd === 'pm list packages -u') return [...tv.packages.keys(), ...(tv.uninstalledForUser ?? [])].map((p) => `package:${p}`).join('\n') + '\n'
  if (cmd === 'pm list packages -f -s') {
    return [...tv.packages.keys()].map((p) => tv.updatedSystem?.has(p) ? `package:/data/app/~~Ab12==/${p}-Cd34==/base.apk=${p}` : `package:/system/app/${p}/${p}.apk=${p}`).join('\n') + '\n'
  }
  if (cmd === 'dumpsys diskstats') return tv.diskstats ?? 'Latency: 1ms [512B Data Write]\nData-Free: 8131144K / 10167132K total = 79% free\n'
  if ((m = /^pm uninstall-system-updates (\S+)$/.exec(cmd))) {
    if (Number(tv.sdk ?? 31) < 29) return `Unknown command: uninstall-system-updates\n`
    const kb = tv.updatedSystem?.get(m[1])
    if (kb === undefined) return `Failure [Package ${m[1]} is not a system app with updates]\n`
    tv.updatedSystem!.delete(m[1])
    if (tv.freeKb !== undefined) tv.freeKb += kb
    return 'Success\n'
  }
  if (cmd.startsWith('logcat -d -v threadtime -t ')) return tv.logcat ?? ''
  if (cmd.startsWith('dumpsys window | grep')) return '  mCurrentFocus=Window{ab04e86 u0 com.example/com.example.Main}\n  mFocusedApp=ActivityRecord{5b90de5 u0 com.example/.Main t19}\n'
  if (cmd === 'pm list packages -d') return [...tv.packages].filter(([, s]) => s === 'disabled').map(([p]) => `package:${p}`).join('\n') + '\n'
  if (cmd.startsWith('cmd package resolve-activity')) {
    // --query-flags arrived in Android 10; Android 9 fails it and the shell runs the plain query after ||.
    const defaultOnly = cmd.includes('--query-flags 65536') && Number(tv.sdk ?? 31) >= 29
    const tied = tv.homeClaims.filter((c) => tv.packages.get(c.split('/')[0]) === 'enabled').length > 1
    if (!defaultOnly && tv.homeNonDefault && tied && !outranking(tv)) tv.home = 'android/com.android.internal.app.ResolverActivity'
    return `priority=0 preferredOrder=0\n${tv.home}\n`
  }
  if (cmd.includes('LEANBACK_LAUNCHER')) return tv.leanback.map((p) => `  ${p}/.Main`).join('\n') + '\n'
  if (cmd.includes('category.LAUNCHER')) return tv.launcherApps.map((p) => `  ${p}/.Main`).join('\n') + '\n'
  if (cmd.startsWith('cmd package query-activities')) {
    const defaultOnly = cmd.includes('--query-flags 65536') && Number(tv.sdk ?? 31) >= 29
    const claims = [...tv.homeClaims, ...(tv.homeNonDefault && !defaultOnly ? [tv.homeNonDefault] : [])]
    return claims.filter((c) => tv.packages.get(c.split('/')[0]) === 'enabled').map((c) => `  ${c}`).join('\n') + '\n'
  }
  if ((m = /^pidof (\S+)$/.exec(cmd))) return tv.packages.get(m[1]) === 'enabled' && !tv.crashing?.includes(m[1]) ? '4321\n' : ''
  if (cmd === 'reboot') { if (tv.bootHome) tv.home = tv.bootHome; return '' }
  if (cmd === 'dumpsys wifi') return `WifiConfigManager ... ${tv.wifiOwners} ...\n`
  if ((m = /^settings get global (\S+)$/.exec(cmd))) return (tv.settings.get(m[1]) ?? 'null') + '\n'
  if ((m = /^pm disable-user --user 0 (\S+)$/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return `Error: Unknown package: ${m[1]}\n`
    if (tv.protectedPkgs?.includes(m[1])) return `Security exception: Cannot disable a protected package: ${m[1]}\n\njava.lang.SecurityException: Cannot disable a protected package: ${m[1]}\n`
    tv.packages.set(m[1], 'disabled'); return `Package ${m[1]} new state: disabled-user\n`
  }
  if ((m = /^pm enable (?:--user 0 )?(\S+)$/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return `Error: Unknown package: ${m[1]}\n`
    if (tv.stuckOff?.includes(m[1])) return `Security exception: Shell cannot change component state for ${m[1]}\n`
    tv.packages.set(m[1], 'enabled'); return `Package ${m[1]} new state: enabled\n`
  }
  if ((m = /^settings put (?:global|secure) (\S+) (.*)$/.exec(cmd)) && !cmd.includes(';')) { tv.settings.set(m[1], m[2]); return '' }
  if (cmd === 'ime list -s') return (tv.imes ?? []).map((i) => i + '\n').join('')
  if ((m = /^ime enable (\S+)$/.exec(cmd))) return `Input method ${m[1]}: already enabled\n`
  if ((m = /^ime set (\S+)$/.exec(cmd))) { tv.settings.set('default_input_method', m[1]); return `Input method ${m[1]} selected for user #0\n` }
  if (cmd === "date '+%m-%d %H:%M:%S.000'") return '11-16 10:00:00.000\n'
  if (cmd.startsWith('logcat -d -v threadtime -T ')) return tv.logcat ?? ''
  if ((m = /^settings get secure (\S+)$/.exec(cmd))) return (tv.settings.get(m[1]) ?? 'null') + '\n'
  // Android 8.0 has no `cmd notification` commands.
  if (cmd.startsWith('cmd notification ') && tv.sdk === '26') return 'No shell command implementation.\n'
  if (cmd === 'df -k /data' && tv.freeKb !== undefined) {
    return `Filesystem      1K-blocks    Used Available Use% Mounted on\n/dev/block/dm-5  5806884 5700000 ${String(tv.freeKb).padStart(9)}  99% /data\n`
  }
  if (cmd === 'pm trim-caches 4096G') { if (tv.freeKb !== undefined) { tv.freeKb += tv.cacheKb ?? 0; tv.cacheKb = 0 } return '' }
  if ((m = /^pm install -r(?: -d)? (\S+)$/.exec(cmd))) {
    if (!tv.files.has(m[1])) return 'Failure [INSTALL_FAILED_INVALID_URI]\n'
    if (tv.freeKb !== undefined && tv.freeKb < 10240) {
      return 'Exception occurred while executing: android.os.ParcelableException: java.io.IOException: Requested internal only, but not enough space\n'
    }
    const pkg = m[1].includes('cmtv-home') ? 'io.github.wingedpanther.cleanmytv.home' : 'com.gathin.tvhome'
    tv.packages.set(pkg, 'enabled'); tv.homeClaims = [...new Set([...tv.homeClaims, `${pkg}/.MainActivity`])]
    return 'Performing Streamed Install\nSuccess\n'
  }
  if ((m = /^content call --uri content:\/\/io\.github\.wingedpanther\.cleanmytv\.home\.config --method (\w+)(?: --arg (\S+))?$/.exec(cmd))) {
    if (!tv.packages.has('io.github.wingedpanther.cleanmytv.home')) {
      return 'Error while accessing provider:io.github.wingedpanther.cleanmytv.home.config\njava.lang.IllegalStateException: Could not find provider: io.github.wingedpanther.cleanmytv.home.config\n'
    }
    const up = '/data/data/home/wallpaper.upload'
    const u = /^(fire|pack|slide)_file_(begin|chunk|end)$/.exec(m[1])
    if (u) {
      const failed = (e: string): string => `Error while accessing provider:io.github.wingedpanther.cleanmytv.home.config\n${e}\n`
      const n = tv.homeUploadCalls = (tv.homeUploadCalls ?? 0) + 1
      if (tv.homeDiesAt?.includes(n)) {
        tv.homeHeld = undefined
        if (tv.homeDiesInCall) return failed('android.os.DeadObjectException')
      }
      const dir = `/data/data/home/${u[1]}/`
      const open = [...tv.files.keys()].filter((f) => f.startsWith(dir) && f.endsWith('.upload'))
      if (u[2] === 'begin') {
        const arg = u[1] === 'slide' ? m[2] : Buffer.from(m[2], 'base64').toString('utf8')
        for (const f of open) tv.files.delete(f)
        tv.homeHeld = dir + (u[1] === 'fire' ? arg : `${arg.replace(':', '/')}.jpg`) + '.upload'
        tv.files.set(tv.homeHeld, Buffer.alloc(0))
      } else {
        const f = tv.homeHeld ?? (!tv.homeForgets && open.length === 1 ? open[0] : undefined)
        if (!f) return failed(`java.lang.IllegalStateException: ${u[1]}_file_begin first`)
        tv.homeHeld = f
        if (u[2] === 'chunk') tv.files.set(f, Buffer.concat([tv.files.get(f) ?? Buffer.alloc(0), Buffer.from(m[2], 'base64')]))
        else {
          tv.homeHeld = undefined
          const data = tv.files.get(f) ?? Buffer.alloc(0)
          tv.files.delete(f)
          const got = createHash('md5').update(data).digest('hex')
          if (got !== m[2]) return failed(`java.lang.IllegalArgumentException: ${u[1] === 'fire' ? 'fireplace' : 'picture'} upload is damaged (md5 ${got})`)
          tv.files.set(f.replace(/\.upload$/, ''), data)
        }
      }
    }
    if (m[1] === 'set64') { Object.assign(tv.homeConfig, JSON.parse(Buffer.from(m[2], 'base64').toString('utf8'))); tv.homeFresh = false }
    else if (m[1] === 'wallpaper_begin') tv.files.set(up, Buffer.alloc(0))
    else if (m[1] === 'wallpaper_chunk') tv.files.set(up, Buffer.concat([tv.files.get(up) ?? Buffer.alloc(0), Buffer.from(m[2], 'base64')]))
    else if (m[1] === 'wallpaper_end') {
      const data = tv.files.get(up) ?? Buffer.alloc(0)
      if (createHash('md5').update(data).digest('hex') !== m[2]) return 'Error: wallpaper upload is damaged\n'
      tv.files.set('/data/data/home/wallpaper.jpg', data)
    } else if (m[1] === 'wallpaper_remove') tv.files.delete('/data/data/home/wallpaper.jpg')
    else if (m[1] === 'theme_save') {
      const name = Buffer.from(m[2], 'base64').toString('utf8')
      if (!tv.files.has('/data/data/home/wallpaper.jpg')) return 'Error while accessing provider:io.github.wingedpanther.cleanmytv.home.config\njava.lang.IllegalArgumentException: put a wallpaper on first; a theme needs a picture\n'
      tv.homeThemes = [...new Set([...(tv.homeThemes ?? []), name])]
      return `Result: Bundle[{theme=my-x, config=${JSON.stringify({ ...tv.homeConfig, has_wallpaper: true, configured: true, themes_on_tv: tv.homeThemes.length })}}]\n`
    }
    else if (m[1] === 'pack_end') {
      // As ThemePack.end: the new set replaces the old, and themes saved on the TV stay.
      tv.homePack = JSON.parse(Buffer.from(m[2], 'base64').toString('utf8')) as string[]
      const themes = tv.homePack.length + (tv.homeThemes?.length ?? 0)
      return `Result: Bundle[{config=${JSON.stringify({ ...tv.homeConfig, configured: true, themes_on_tv: themes })}}]\n`
    }
    else if (m[1] === 'screentime') {
      // As ScreenTime.page: up to 60 days from <arg> days before today, with today and the first day kept.
      const st = tv.homeScreenTime
      if (!st) return `Result: Bundle[{screentime=${JSON.stringify({ allowed: false })}}]\n`
      const from = addDays(st.today, -Number(m[2]))
      const days = st.days.filter((d) => d.day >= from && d.day <= st.today).sort((a, b) => a.day.localeCompare(b.day)).slice(0, 60)
      const first = st.days.map((d) => d.day).sort()[0] ?? st.today
      return `Result: Bundle[{screentime=${JSON.stringify({ allowed: true, today: st.today, first, days })}}]\n`
    } else if (m[1] === 'screentime_clear') { if (tv.homeScreenTime) tv.homeScreenTime.days = []; return 'Result: Bundle[{screentime={}}]\n' }
    else if (m[1] === 'keyboard') tv.homeKeyboard = Buffer.from(m[2], 'base64').toString('utf8')
    else if (m[1] === 'keys') return `Result: Bundle[{keys=${JSON.stringify([{ seq: 7, code: 83, name: 'KEYCODE_NOTIFICATION' }, { seq: 'x', code: 1 }, { seq: 8, code: 296, scan: 405, name: 'KEYCODE_VIDEO_APP_8' }, { seq: 9, code: 296, scan: 0, name: 'KEYCODE_VIDEO_APP_8' }])}}]\n`
    else if (m[1] === 'apps') return `Result: Bundle[{apps=${JSON.stringify([...tv.leanback.map((p) => ({ pkg: p, label: p.split('.').pop(), tv: true })), { pkg: 'bad;pkg', label: 'x', tv: true }])}}]\n`
    else if (!u && !['get', 'pack_begin', 'pack_meta', 'slides_begin', 'slides_end', 'fire_ready'].includes(m[1])) return `Error: unknown method: ${m[1]}\n`
    return `Result: Bundle[{config=${JSON.stringify({ ...tv.homeConfig, has_wallpaper: tv.files.has('/data/data/home/wallpaper.jpg'), configured: !tv.homeFresh })}}]\n`
  }
  if ((m = /^mkdir -p (\S+)\/wallpapers 2>&1; touch/.exec(cmd))) {
    return tv.appDir === 'ok' ? 'writable\n' : `mkdir: '${m[1]}': Permission denied\ntouch: '${m[1]}/.cytv': No such file or directory\n`
  }
  if (cmd.startsWith('appops ')) {
    if (tv.appopsRefused) return 'Security exception: uid 2000 does not have android.permission.MANAGE_APP_OPS_MODES.\n'
    // AUTO_START and APP_AUTO_START are TCL's own; other TVs don't know them.
    const tcl = /tcl/i.test(tv.manufacturer ?? '')
    for (const part of cmd.split(';')) {
      const a = /^\s*appops set (\S+) (\w+) (\w+)/.exec(part)
      if (a && (tcl || !/AUTO_START/.test(a[2]))) (tv.appops ??= new Map()).set(`${a[1]} ${a[2]}`, a[3])
    }
    return cmd.includes('echo ') ? cmd.slice(cmd.lastIndexOf('echo ') + 5) + '\n' : ''
  }
  if (cmd.startsWith('am start -W') && tv.appDir === 'missing') tv.appDir = 'ok'
  if (cmd.startsWith('am start -W')) return 'Starting: Intent { cmp=com.gathin.tvhome/.MainActivity }\nStatus: ok\nLaunchState: COLD\n'
  if ((m = /^cmd package set-home-activity --user 0 (\S+\/\S+)$/.exec(cmd))) { if (!tv.homeStuck) tv.home = outranking(tv) ?? m[1]; return 'Success\n' }
  // set-home-activity $(resolve-activity ... <pkg> | tail -n 1): the TV's shell resolves the component.
  if ((m = /^cmd package set-home-activity --user 0 \$\(.* (\S+) \| tail -n 1\)$/.exec(cmd))) {
    const c = tv.homeClaims.find((h) => h.startsWith(m![1] + '/'))
    if (c && tv.packages.get(m[1]) === 'enabled') tv.home = outranking(tv) ?? c
    return 'Success\n'
  }
  if ((m = /^dumpsys package (\S+) \| grep/.exec(cmd))) {
    if (!tv.packages.has(m[1])) return ''
    return m[1] === 'com.gathin.tvhome' ? '    versionName=2.4\n' : `    versionName=${tv.homeVersion ?? '1.0.0'}\n`
  }
  if ((m = /^screencap -p (\S+)$/.exec(cmd))) { tv.files.set(m[1], tv.screenshot); return '' }
  if ((m = /^ls (\S+)\/wallpapers/.exec(cmd))) return [...tv.files.keys()].filter((f) => f.startsWith(m![1] + '/wallpapers/')).map((f) => f.split('/').pop()).join('\n') + '\n'
  if ((m = /printf '%s' '(\w+)' > (\S+)/.exec(cmd))) tv.files.set(m[2], Buffer.from(m[1]))
  if (cmd.includes('rm -f') && cmd.includes('config.json')) for (const f of [...tv.files.keys()]) if (/config\.json|wallpaper/.test(f)) tv.files.delete(f)
  if (cmd.includes('echo reset')) return 'reset\n'
  if (cmd.includes('echo applied')) return 'applied\n'
  if ((m = /^pm uninstall (\S+)$/.exec(cmd))) {
    // On a system app with an update, uninstall takes the update off and the system version stays.
    const kb = tv.updatedSystem?.get(m[1])
    if (kb !== undefined) { tv.updatedSystem!.delete(m[1]); if (tv.freeKb !== undefined) tv.freeKb += kb; return 'Success\n' }
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
        if (tv.gone) return
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
            // "sleep <ms>": a command that takes a while and prints nothing until it ends, as pm install does.
            const slow = /^shell:sleep (\d+)$/.exec(service)
            const finish = (): void => {
              if (tv.gone) return
              const out = Buffer.from(service.startsWith('shell:') && !slow ? sh(tv, service.slice(6)) : '')
              for (let i = 0; i < out.length; i += tv.maxData) sock.write(packet(A_WRTE, remote, local, out.subarray(i, i + tv.maxData)))
              sock.write(packet(A_CLSE, remote, local))
            }
            if (slow) setTimeout(finish, Number(slow[1])); else finish()
          } else if (cmd === A_OKAY || cmd === A_CLSE) {
            // acknowledgements; nothing to do
          }
        }
      })
    })
    server.listen(listenPort, '127.0.0.1', () => resolve({ server, port: (server.address() as { port: number }).port }))
  })
}
