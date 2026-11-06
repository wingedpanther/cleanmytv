// Install an app the owner downloaded (an .apk) on the TV: read what it is, upload it the same way as the
// launcher, install it with pm, and say in plain words why Android refused it if it did.
import { readFileSync, statSync } from 'node:fs'
import { basename, extname } from 'node:path'
import * as T from './tv'
import { log, progress } from './log'
import { apkInfo } from './apk'
import type { ApplyResult, SideloadInfo } from '@shared/api'

const TASK = 'sideload'
const TMP = '/data/local/tmp/cmtv-sideload.apk'
const MAX_BYTES = 1024 * 1024 * 1024
const HOME_PKG = 'io.github.wingedpanther.cleanmytv.home'

function readApk(path: string): Buffer {
  if (typeof path !== 'string' || !path) throw new Error('Pick an .apk file.')
  const ext = extname(path).toLowerCase()
  if (ext === '.apks' || ext === '.xapk' || ext === '.apkm') {
    throw new Error(`${basename(path)} is a bundle of several APKs. CleanMyTV installs single .apk files; get the .apk version of the app.`)
  }
  if (ext !== '.apk') throw new Error(`${basename(path)} isn't an .apk file.`)
  const st = statSync(path)
  if (!st.isFile()) throw new Error(`${basename(path)} isn't a file.`)
  if (st.size > MAX_BYTES) throw new Error(`${basename(path)} is over 1 GB.`)
  return readFileSync(path)
}

async function installedVersion(pkg: string): Promise<string | null> {
  T.q(pkg)
  const out = await T.tv().shell(`dumpsys package ${pkg} | grep -m1 versionName`)
  return /versionName=(\S+)/.exec(out)?.[1] ?? null
}

/** What the file is and whether the TV has it already, before anything is uploaded. */
export async function inspect(path: string): Promise<SideloadInfo> {
  const data = readApk(path)
  const info = apkInfo(data)
  if (!info || !T.PACKAGE_RE.test(info.package)) throw new Error(`${basename(path)} doesn't look like an Android app.`)
  return {
    file: basename(path),
    size: data.length,
    package: info.package,
    versionName: info.versionName ?? null,
    installed: await installedVersion(info.package)
  }
}

const REASONS: [RegExp, string][] = [
  [/UPDATE_INCOMPATIBLE|INCONSISTENT_CERTIFICATES/, 'The TV has this app already, signed by someone else. Uninstall it on the TV first (Settings → Apps), then install this one.'],
  [/VERSION_DOWNGRADE/, 'The TV has a newer version of this app. Uninstall it on the TV first if you really want the older one.'],
  [/NO_MATCHING_ABIS/, "This version is built for a different processor. Look for an armeabi-v7a or arm64-v8a download of the app."],
  [/OLDER_SDK/, 'This app needs a newer Android version than the TV has.'],
  [/INSUFFICIENT_STORAGE/, 'The TV is out of space. TV extras → Storage can clear app caches.'],
  [/INVALID_APK|PARSE_FAILED/, "Android can't read this file. It may be damaged or only part of an app; download it again."],
  [/MISSING_SPLIT/, 'This app needs extra parts that come in a bundle (.apks or .xapk). CleanMyTV installs single .apk files.'],
  [/USER_RESTRICTED|VERIFICATION_FAILURE/, 'The TV blocked the install. Allow installs from unknown sources in its Settings, then try again.']
]

export async function install(path: string): Promise<ApplyResult> {
  const data = readApk(path)
  const info = apkInfo(data)
  if (!info || !T.PACKAGE_RE.test(info.package)) throw new Error(`${basename(path)} doesn't look like an Android app.`)
  if (info.package === HOME_PKG) throw new Error('That is CleanMyTV Home. Install it from the Home screen step, which also sets it up.')
  const name = `${info.package}${info.versionName ? ` ${info.versionName}` : ''}`
  const free = await T.freeKb(TASK)
  const need = Math.ceil((data.length * 2.4) / 1024)
  if (free < need) throw new Error(`The TV needs about ${Math.round(need / 1024)} MB free to install this, and has ${Math.round(free / 1024)} MB. TV extras → Storage can clear app caches.`)
  log(TASK, 'cmd', `install ${basename(path)} (${name})`)
  try {
    await T.pushB64(TASK, data, TMP, (i, n) => progress({ tool: 'sideload', received: i, total: n, phase: 'install' }))
    const out = await T.sh(TASK, `pm install -r ${TMP}`, { timeoutMs: 300000 })
    progress({ tool: 'sideload', received: 1, total: 1, phase: 'done' })
    if (/Success/.test(out)) return { ok: true, summary: `Installed ${name}. It's in Home's app list now.`, warnings: [] }
    const code = /\[?(INSTALL_[A-Z_]+)/.exec(out)?.[1] ?? out.trim()
    const why = REASONS.find(([re]) => re.test(out))?.[1]
    return { ok: false, summary: `The TV didn't install ${name}.`, warnings: [why ?? `Android said: ${code}`] }
  } finally {
    await T.tv().shell(`rm -f ${TMP} ${TMP}.b64`).catch(() => {})
  }
}
