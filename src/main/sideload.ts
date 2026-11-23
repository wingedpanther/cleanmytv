// Install an app the owner downloaded (an .apk) on the TV: read what it is, upload it the same way as the
// launcher, install it with pm, and say in plain words why Android refused it if it did.
import { readFileSync, statSync } from 'node:fs'
import { basename, extname } from 'node:path'
import * as T from './tv'
import { log, progress } from './log'
import { apkInfo } from './apk'
import type { ApplyResult, OpenShelfInfo, SideloadInfo } from '@shared/api'
import { t, UiError } from './i18n'
import { isNewer, parseVersion } from './updates'

const TASK = 'sideload'
const TMP = '/data/local/tmp/cmtv-sideload.apk'
const MAX_BYTES = 1024 * 1024 * 1024
const HOME_PKG = 'io.github.wingedpanther.cleanmytv.home'

function readApk(path: string): Buffer {
  if (typeof path !== 'string' || !path) throw new UiError('Pick an .apk file.')
  const ext = extname(path).toLowerCase()
  if (ext === '.apks' || ext === '.xapk' || ext === '.apkm') {
    throw new UiError('{file} is a bundle of several APKs. CleanMyTV installs single .apk files; get the .apk version of the app.', { file: basename(path) })
  }
  if (ext !== '.apk') throw new UiError("{file} isn't an .apk file.", { file: basename(path) })
  const st = statSync(path)
  if (!st.isFile()) throw new UiError("{file} isn't a file.", { file: basename(path) })
  if (st.size > MAX_BYTES) throw new UiError('{file} is over 1 GB.', { file: basename(path) })
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
  if (!info || !T.PACKAGE_RE.test(info.package)) throw new UiError("{file} doesn't look like an Android app.", { file: basename(path) })
  return {
    file: basename(path),
    size: data.length,
    package: info.package,
    versionName: info.versionName ?? null,
    installed: await installedVersion(info.package)
  }
}

/** Why Android refused an install, by its error code; a function, so the text comes out in the window's language. */
const reasons = (): [RegExp, string][] => [
  [/UPDATE_INCOMPATIBLE|INCONSISTENT_CERTIFICATES/, t('The TV has this app already, signed by someone else. Uninstall it on the TV first (Settings → Apps), then install this one.')],
  [/VERSION_DOWNGRADE/, t('The TV has a newer version of this app. Uninstall it on the TV first if you really want the older one.')],
  [/NO_MATCHING_ABIS/, t('This version is built for a different processor. Look for an armeabi-v7a or arm64-v8a download of the app.')],
  [/OLDER_SDK/, t('This app needs a newer Android version than the TV has.')],
  [/INSUFFICIENT_STORAGE/, t('The TV is out of space. TV extras → Storage can clear app caches.')],
  [/INVALID_APK|PARSE_FAILED/, t("Android can't read this file. It may be damaged or only part of an app; download it again.")],
  [/MISSING_SPLIT/, t('This app needs extra parts that come in a bundle (.apks or .xapk). CleanMyTV installs single .apk files.')],
  [/USER_RESTRICTED|VERIFICATION_FAILURE/, t('The TV blocked the install. Allow installs from unknown sources in its Settings, then try again.')]
]

export async function install(path: string): Promise<ApplyResult> {
  return put(readApk(path), basename(path))
}

async function put(data: Buffer, file: string): Promise<ApplyResult> {
  const info = apkInfo(data)
  if (!info || !T.PACKAGE_RE.test(info.package)) throw new UiError("{file} doesn't look like an Android app.", { file })
  if (info.package === HOME_PKG) throw new UiError('That is CleanMyTV Home. Install it from the Home screen step, which also sets it up.')
  const name = `${info.package}${info.versionName ? ` ${info.versionName}` : ''}`
  const free = await T.freeKb(TASK)
  const need = Math.ceil((data.length * 2.4) / 1024)
  if (free < need) throw new UiError('The TV needs about {need} MB free to install this, and has {free} MB. TV extras → Storage can clear app caches.', { need: Math.round(need / 1024), free: Math.round(free / 1024) })
  log(TASK, 'cmd', `install ${file} (${name})`)
  try {
    await T.pushB64(TASK, data, TMP, (i, n) => progress({ tool: 'sideload', received: i, total: n, phase: 'install' }))
    const out = await T.sh(TASK, `pm install -r ${TMP}`, { timeoutMs: 300000 })
    progress({ tool: 'sideload', received: 1, total: 1, phase: 'done' })
    if (/Success/.test(out)) return { ok: true, summary: t("Installed {name}. It's in Home's app list now.", { name }), warnings: [] }
    const code = /\[?(INSTALL_[A-Z_]+)/.exec(out)?.[1] ?? out.trim()
    const why = reasons().find(([re]) => re.test(out))?.[1]
    return { ok: false, summary: t("The TV didn't install {name}.", { name }), warnings: [why ?? t('Android said: {code}', { code })] }
  } finally {
    await T.tv().shell(`rm -f ${TMP} ${TMP}.b64`).catch(() => {})
  }
}

// ---- Open Shelf ---------------------------------------------------------------------------------
// The project's own app store for the TV, asked for on Discord: its latest release straight from GitHub,
// so the TV gets it without a browser or a download app on it.

const SHELF_PKG = 'io.github.wingedpanther.openshelf'
const SHELF_LATEST = 'https://api.github.com/repos/wingedpanther/openshelf/releases/latest'
const SHELF_APK = 'https://github.com/wingedpanther/openshelf/releases/latest/download/OpenShelf.apk'
/** GitHub sends release downloads on to one of these. */
const GITHUB_HOSTS = new Set(['github.com', 'objects.githubusercontent.com', 'release-assets.githubusercontent.com'])
const SHELF_MAX = 64 * 1024 * 1024

/** The Open Shelf on the TV, if any, and the newest release; `latest` is null when GitHub didn't answer. */
export async function openShelf(fetchImpl: typeof fetch = fetch): Promise<OpenShelfInfo> {
  const installed = await installedVersion(SHELF_PKG)
  let latest: string | null = null
  try {
    const res = await fetchImpl(SHELF_LATEST, { headers: { accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(10000) })
    const tag = res.ok ? String(((await res.json()) as { tag_name?: unknown }).tag_name ?? '') : ''
    if (parseVersion(tag)) latest = tag.replace(/^v/, '')
  } catch {
    // offline: the card still offers the install, which tries again
  }
  return { installed, latest, update: !!installed && !!latest && isNewer(latest, installed) }
}

/** The latest OpenShelf.apk, checked to be Open Shelf before anything goes to the TV. */
export async function downloadOpenShelf(fetchImpl: typeof fetch = fetch): Promise<Buffer> {
  log(TASK, 'cmd', 'download Open Shelf')
  let u = new URL(SHELF_APK)
  for (let hop = 0; hop < 5; hop++) {
    if (u.protocol !== 'https:' || !GITHUB_HOSTS.has(u.hostname)) throw new UiError('the Open Shelf download went somewhere unexpected ({host})', { host: u.hostname })
    const res = await fetchImpl(u, { redirect: 'manual', signal: AbortSignal.timeout(120000) })
    const next = res.headers.get('location')
    if (res.status >= 300 && res.status < 400 && next) { u = new URL(next, u); continue }
    if (!res.ok) throw new UiError("couldn't download Open Shelf (HTTP {status})", { status: res.status })
    const data = Buffer.from(await res.arrayBuffer())
    if (data.length > SHELF_MAX) throw new UiError('the Open Shelf download is over 64 MB')
    if (apkInfo(data)?.package !== SHELF_PKG) throw new UiError("the Open Shelf download isn't Open Shelf; try again later")
    return data
  }
  throw new UiError('too many redirects')
}

export async function installOpenShelf(fetchImpl: typeof fetch = fetch): Promise<ApplyResult> {
  const data = await downloadOpenShelf(fetchImpl)
  const r = await put(data, 'OpenShelf.apk')
  if (!r.ok) return r
  const v = apkInfo(data)?.versionName
  return { ok: true, summary: v ? t('Open Shelf {v} is on the TV. Open it from the app list to browse and install apps.', { v }) : t('Open Shelf is on the TV. Open it from the app list to browse and install apps.'), warnings: [] }
}
