// Themes for CleanMyTV Home (src/shared/themes.ts): apply one, put the old look back, or save the TV's
// look to a file. Before the first theme on a TV, its look and wallpaper are saved on this computer,
// and stay there until the owner puts them back or forgets them, through restarts of the app.
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { log } from './log'
import { paths } from './paths'
import * as H from './home'
import { pickLook, themeName, themeToFile } from '@shared/themes'
import type { ApplyResult, HomeConfig, HomeTheme, ThemeBackup } from '@shared/api'

interface Saved {
  savedAt: number
  theme: HomeTheme
}

function backupFile(): string | null {
  const base = H.backupBase()
  return base ? base + '.before-themes.json' : null
}

function readSaved(): Saved | null {
  const f = backupFile()
  if (!f || !existsSync(f)) return null
  try {
    return JSON.parse(readFileSync(f, 'utf8')) as Saved
  } catch {
    return null
  }
}

/** The TV's look now. The wallpaper is left out when no copy of it is on the TV or this computer. */
async function current(name: string): Promise<{ theme: HomeTheme; missing: boolean }> {
  const cfg = await H.readConfig()
  const copy = cfg.has_wallpaper ? await H.wallpaperCopy() : null
  const wallpaper = cfg.has_wallpaper ? (copy ?? undefined) : null
  return { theme: { name, look: pickLook(cfg), wallpaper }, missing: cfg.has_wallpaper === true && !copy }
}

async function put(theme: HomeTheme): Promise<HomeConfig> {
  // Lenient: a file from a newer app may carry values this one doesn't know.
  let cfg = await H.setConfig(H.clean(pickLook(theme.look), true))
  if (theme.wallpaper !== undefined) cfg = await H.setWallpaper(theme.wallpaper, typeof theme.from === 'string' ? theme.from : '')
  return cfg
}

export async function backup(): Promise<ThemeBackup> {
  const s = readSaved()
  if (s) return { savedAt: s.savedAt, withWallpaper: s.theme.wallpaper !== undefined, wallpaperAtRisk: false }
  const cfg = await H.readConfig()
  return { savedAt: null, withWallpaper: false, wallpaperAtRisk: cfg.has_wallpaper === true && !(await H.wallpaperCopy()) }
}

export async function apply(theme: HomeTheme): Promise<HomeConfig> {
  const f = backupFile()
  if (f && !readSaved()) {
    const { theme: before, missing } = await current('Before themes')
    mkdirSync(paths.homeBackups, { recursive: true, mode: 0o700 })
    writeFileSync(f, JSON.stringify({ savedAt: Date.now(), theme: before } satisfies Saved), { mode: 0o600 })
    log('theme', 'info', `saved the look from before themes${missing ?  " (there's no copy of the wallpaper, so it isn't in it)" : ''}`)
  }
  log('theme', 'info', `theme: ${themeName(theme.name)}`)
  return put(theme)
}

/** Puts the look from before the first theme back, then forgets it. */
export async function restore(): Promise<HomeConfig> {
  const s = readSaved()
  if (!s) throw new Error("There's no saved look for this TV.")
  log('theme', 'info', 'putting the look from before themes back')
  const cfg = await put(s.theme)
  forget()
  return cfg
}

export function forget(): void {
  const f = backupFile()
  if (f) rmSync(f, { force: true })
}

/** `pick` asks where to save; it returns null when the owner cancels. */
export async function save(name: string, pick: (fileName: string) => Promise<string | null>): Promise<ApplyResult | null> {
  const { theme, missing } = await current(themeName(name))
  const file = await pick(theme.name)
  if (!file) return null
  writeFileSync(file, themeToFile(theme))
  return {
    ok: true,
    summary: `Saved “${theme.name}”. Load it on any TV from Look & wallpaper → Themes.`,
    warnings: missing ? ["The wallpaper isn't in the file: an older CleanMyTV set it and kept no copy. Set it again, then save."] : []
  }
}
