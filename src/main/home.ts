// CleanMyTV Home, the preview launcher in home/. Its settings live in its own private storage and come
// in over ADB through its ConfigProvider (`content call`), so there's no shared folder to fail (#20)
// and nothing to reread after a reboot (#21). Changes show on the TV straight away.
import { existsSync, readFileSync } from 'node:fs'
import { paths } from './paths'
import { log } from './log'
import * as T from './tv'
import { HOME_PKG, detectStock, handOver } from './launcher'
import type { HomeConfig, HomeStatus, InstallResult } from '@shared/api'

const TASK = 'install'
const MAIN = `${HOME_PKG}/.MainActivity`
const URI = `content://${HOME_PKG}.config`
const ACCENT_RE = /^#[0-9A-Fa-f]{6}$/
const FOCUS = ['glow', 'outline', 'none'] as const

function bundledVersion(): string | undefined {
  try {
    return JSON.parse(readFileSync(paths.bundledHomeInfo, 'utf8')).versionName
  } catch {
    return undefined
  }
}

export async function status(): Promise<HomeStatus> {
  const dump = await T.tv().shell(`dumpsys package ${HOME_PKG} | grep -m1 versionName`)
  const versionName = /versionName=(\S+)/.exec(dump)?.[1]
  const home = await T.homeComponent()
  return {
    installed: !!versionName,
    versionName,
    isHome: !!home && home.startsWith(HOME_PKG + '/'),
    bundled: existsSync(paths.bundledHomeApk),
    bundledVersion: bundledVersion()
  }
}

/** Installs or updates CleanMyTV Home, starts it, and only then makes it Home. */
export async function install(): Promise<InstallResult> {
  if (!existsSync(paths.bundledHomeApk)) {
    return { ok: false, summary: 'This copy of the app has no CleanMyTV Home bundled.', warnings: [] }
  }
  const stock = await detectStock()
  log(TASK, 'info', `stock launcher: ${stock}`)
  const tmp = '/data/local/tmp/cmtv-home.apk'
  await T.pushB64(TASK, readFileSync(paths.bundledHomeApk), tmp)
  const out = await T.sh(TASK, `pm install -r ${tmp}`, { timeoutMs: 300000 })
  await T.sh(TASK, `rm -f ${tmp}`)
  if (!out.includes('Success')) {
    return { ok: false, summary: 'Install failed; Home was left as it was.', warnings: [out.trim()] }
  }
  // The music bars read the TV's sound output. Granted here so the TV never asks.
  await T.sh(TASK, `pm grant ${HOME_PKG} android.permission.RECORD_AUDIO 2>/dev/null; echo audio-granted`)
  const start = await T.sh(TASK, `am start -W -n ${MAIN}`)
  if (!start.includes('Status: ok') || /^Error/im.test(start)) {
    return { ok: false, summary: 'CleanMyTV Home did not start, so Home was left as it was.', warnings: [start.trim()] }
  }
  const failed = await handOver(stock, MAIN)
  return failed ?? { ok: true, summary: 'CleanMyTV Home is Home now. Press Home on the remote to see it.', warnings: [] }
}

function parse(out: string): HomeConfig {
  const m = /config=(\{.*\})\}\]/.exec(out)
  if (!m) throw new Error(`CleanMyTV Home didn't answer: ${out.trim() || 'no output'}`)
  const o = JSON.parse(m[1])
  return {
    accent: ACCENT_RE.test(o.accent) ? o.accent : '#FF3000',
    focus: FOCUS.includes(o.focus) ? o.focus : 'glow',
    visualiser: o.visualiser !== false
  }
}

export async function readConfig(): Promise<HomeConfig> {
  return parse(await T.tv().shell(`content call --uri ${URI} --method get`))
}

export async function setConfig(change: Partial<HomeConfig>): Promise<HomeConfig> {
  // Built from checked values only: nothing the caller sends reaches the shell as text.
  const o: Record<string, unknown> = {}
  if (change.accent !== undefined) {
    if (!ACCENT_RE.test(change.accent)) throw new Error('accent must be a colour like #FF3000')
    o.accent = change.accent.toUpperCase()
  }
  if (change.focus !== undefined) {
    if (!FOCUS.includes(change.focus)) throw new Error('focus must be glow, outline or none')
    o.focus = change.focus
  }
  if (change.visualiser !== undefined) o.visualiser = change.visualiser === true
  return parse(await T.sh('theme', `content call --uri ${URI} --method set --arg '${JSON.stringify(o)}'`))
}
