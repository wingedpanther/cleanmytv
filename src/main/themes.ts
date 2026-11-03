// Themes for CleanMyTV Home (src/shared/themes.ts): apply one, undo it, or save the TV's look to a file.
import { writeFileSync } from 'node:fs'
import { log } from './log'
import * as T from './tv'
import * as H from './home'
import { pickLook, themeName, themeToFile } from '@shared/themes'
import type { ApplyResult, HomeConfig, HomeTheme } from '@shared/api'

/** The look before the last theme, for the TV it was on. */
let previous: { host: string; theme: HomeTheme } | null = null

/** The TV's look now. The wallpaper is left out when it wasn't sent from this computer. */
async function current(name: string): Promise<{ theme: HomeTheme; missing: boolean }> {
  const cfg = await H.readConfig()
  const copy = cfg.has_wallpaper ? H.wallpaperCopy() : null
  const wallpaper = cfg.has_wallpaper ? (copy ?? undefined) : null
  return { theme: { name, look: pickLook(cfg), wallpaper }, missing: cfg.has_wallpaper === true && !copy }
}

async function put(theme: HomeTheme): Promise<HomeConfig> {
  // Lenient: a file from a newer app may carry values this one doesn't know.
  let cfg = await H.setConfig(H.clean(pickLook(theme.look), true))
  if (theme.wallpaper !== undefined) cfg = await H.setWallpaper(theme.wallpaper)
  return cfg
}

/**
 * `trying`: the TV shows another theme the owner hasn't changed since, so Undo keeps going back to
 * the look from before the first one.
 */
export async function apply(theme: HomeTheme, trying = false): Promise<HomeConfig> {
  const host = T.getStatus().host ?? ''
  const keep = trying && previous?.host === host
  const before = keep ? null : (await current('Before')).theme
  log('theme', 'info', `theme: ${themeName(theme.name)}`)
  const cfg = await put(theme)
  if (before) previous = { host, theme: before }
  return cfg
}

export async function undo(): Promise<HomeConfig> {
  if (!previous || previous.host !== (T.getStatus().host ?? '')) throw new Error('There is no theme to undo on this TV.')
  const { theme } = previous
  previous = null
  log('theme', 'info', 'theme: undo')
  return put(theme)
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
    warnings: missing ? ["The wallpaper isn't in the file: it was set from another computer. Set it again from here, then save."] : []
  }
}
