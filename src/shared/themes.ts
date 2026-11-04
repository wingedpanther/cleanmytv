// Themes for CleanMyTV Home: the look in one go, from a built-in set or a .cmtvtheme file someone shared.
// A theme never touches apps, rows, folders, layout, buttons or anything personal like the weather place.
import type { HomeConfig, HomeTheme } from './api'

/** The settings a theme carries. */
export const THEME_KEYS = [
  'accent', 'focus', 'background', 'wallpaper_dim', 'top_space', 'dock_scale', 'tile_scale', 'show_names', 'clock',
  'visualiser', 'viz_style', 'viz_bars', 'viz_height', 'viz_opacity', 'viz_colour', 'viz_peaks', 'viz_sensitivity',
  'icon_style', 'tile_shape'
] as const satisfies readonly (keyof HomeConfig)[]

export const THEME_EXT = 'cmtvtheme'
const FORMAT = 'cleanmytv-theme'
// A 1920x1080 wallpaper is under 300 KB; 4 MB is the launcher's own limit.
const MAX_WALLPAPER_B64 = Math.ceil((4 * 1024 * 1024 * 4) / 3)

export function pickLook(o: Partial<HomeConfig> | Record<string, unknown>): Partial<HomeConfig> {
  const src = o as Record<string, unknown>
  return Object.fromEntries(THEME_KEYS.filter((k) => src[k] !== undefined).map((k) => [k, src[k]])) as Partial<HomeConfig>
}

export function themeName(s: unknown): string {
  // eslint-disable-next-line no-control-regex
  const n = typeof s === 'string' ? s.replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, 40) : ''
  return n || 'My theme'
}

/** "Living room.cmtvtheme": safe on Mac and Windows. */
export function themeFileName(name: string): string {
  return `${themeName(name).replace(/[\\/:*?"<>|]+/g, '-')}.${THEME_EXT}`
}

export function themeToFile(t: HomeTheme): string {
  return JSON.stringify({ format: FORMAT, version: 1, name: themeName(t.name), look: pickLook(t.look), wallpaper: t.wallpaper }, null, 2) + '\n'
}

/** Reads a .cmtvtheme file. Values are checked again on the way to the TV. */
export function parseTheme(text: string): HomeTheme {
  let o: Record<string, unknown>
  try {
    o = JSON.parse(text) as Record<string, unknown>
  } catch {
    throw new Error("This isn't a CleanMyTV theme file.")
  }
  if (!o || typeof o !== 'object' || o.format !== FORMAT) throw new Error("This isn't a CleanMyTV theme file.")
  if (o.version !== 1) throw new Error('This theme was saved by a newer CleanMyTV. Update the app, then load it again.')
  const look = o.look && typeof o.look === 'object' ? pickLook(o.look as Record<string, unknown>) : {}
  const w = o.wallpaper
  if (w !== undefined && w !== null && (typeof w !== 'string' || w.length > MAX_WALLPAPER_B64 || !/^[A-Za-z0-9+/]+=*$/.test(w))) {
    throw new Error("The wallpaper in this theme file is damaged.")
  }
  return { name: themeName(o.name), look, wallpaper: w as string | null | undefined }
}

/** Pictures the app draws over a theme's base, in the theme's colours (src/renderer/src/lib/themes.ts). */
export interface ThemeArt {
  kind: 'aurora' | 'ridges' | 'dunes' | 'flow' | 'stars' | 'contours' | 'bokeh' | 'horizon' | 'rings'
  colours: string[]
  /** The same seed draws the same picture. */
  seed: number
  /** stars: 1 adds a planet. rings: 1 centres them bottom-left. */
  variant?: number
}

/** A built-in theme draws its own wallpaper: a dark base with soft coloured light, and its art. */
export interface ThemePaint {
  /** Top-left to bottom-right. */
  base: [string, string]
  /** x and y as fractions of the screen, r as a fraction of its width, a as opacity. */
  glows: { colour: string; x: number; y: number; r: number; a: number }[]
  art?: ThemeArt
}

export interface BuiltInTheme {
  id: string
  name: string
  /** Where the colours come from; credited in the README. */
  from?: 'omarchy'
  look: Partial<HomeConfig>
  paint: ThemePaint
}

// Colours from Omarchy's dark themes (github.com/omacom/omarchy/tree/quattro/themes, MIT, by David
// Heinemeier Hansson; licence in resources/licenses/omarchy-LICENSE.txt). Only the colours: the wallpapers
// are drawn here. Light themes are left out (Home's text is white), and so is its Gruvbox, which is close
// to ours. Each row: name, background, darker background, accent, and a second colour for the light.
const OMARCHY: [string, string, string, string, string][] = [
  ['Catppuccin', '#1E1E2E', '#101019', '#89B4FA', '#F5C2E7'],
  ['Ethereal', '#060B1E', '#030610', '#7D82D9', '#C89DC1'],
  ['Everforest', '#2D353B', '#181D20', '#7FBBB3', '#A7C080'],
  ['Hackerman', '#0B0C16', '#06060C', '#82FB9C', '#7CF8F7'],
  ['Jade Horse', '#142620', '#0A1713', '#8BCBB1', '#96B7D0'],
  ['Kanagawa', '#1F1F28', '#111116', '#DCD7BA', '#7E9CD8'],
  ['Last Horizon', '#0C0B0C', '#060606', '#B59790', '#87A9B0'],
  ['Lumon', '#16242D', '#0B1216', '#8BC9EB', '#4D86B0'],
  ['Matte Black', '#121212', '#090909', '#E68E0D', '#D35F5F'],
  ['Miasma', '#222222', '#121212', '#78824B', '#C9A554'],
  ['Nord', '#2E3440', '#191C23', '#81A1C1', '#88C0D0'],
  ['Retro 82', '#05182E', '#020C17', '#FAA968', '#028391'],
  ['Ristretto', '#2C2525', '#181414', '#F38D70', '#F9CC6C'],
  ['Solitude', '#101315', '#080A0B', '#798186', '#D9DBDC'],
  ['Starship', '#1C1C1E', '#0E0E10', '#FF9F0A', '#8FB8DE'],
  ['Tokyo Night', '#1A1B26', '#0E0E14', '#7AA2F7', '#AD8EE6'],
  ['Vantablack', '#000000', '#070707', '#8D8D8D', '#CECECE']
]

const OMARCHY_ART: Record<string, [ThemeArt['kind'], number?]> = {
  Catppuccin: ['bokeh'], Ethereal: ['aurora'], Everforest: ['ridges'], Hackerman: ['horizon'], 'Jade Horse': ['flow'],
  Kanagawa: ['flow'], 'Last Horizon': ['dunes'], Lumon: ['contours'], 'Matte Black': ['rings'], Miasma: ['contours'],
  Nord: ['ridges'], 'Retro 82': ['horizon'], Ristretto: ['rings', 1], Solitude: ['stars'], Starship: ['stars', 1],
  'Tokyo Night': ['bokeh'], Vantablack: ['rings']
}

function omarchy(): BuiltInTheme[] {
  const styles = ['bars', 'mirror', 'wave'] as const
  return OMARCHY.map(([name, bg, darker, accent, second], i) => {
    // The light sits in opposite corners, swapping sides from one theme to the next.
    const flip = i % 2 === 1
    return {
      id: `omarchy-${name.toLowerCase().replace(/ /g, '-')}`,
      name,
      from: 'omarchy',
      look: { accent, focus: 'lift', background: darker, wallpaper_dim: 0, viz_style: styles[i % 3], viz_colour: 'accent', viz_peaks: i % 2 === 0 },
      paint: {
        base: flip ? [darker, bg] : [bg, darker],
        glows: [
          { colour: accent, x: flip ? 0.88 : 0.12, y: 0.12, r: 0.45, a: 0.16 },
          { colour: second, x: flip ? 0.1 : 0.9, y: 0.92, r: 0.4, a: 0.11 }
        ],
        art: { kind: OMARCHY_ART[name][0], colours: [accent, second], seed: 100 + i, variant: OMARCHY_ART[name][1] }
      }
    }
  })
}

export const BUILT_IN_THEMES: BuiltInTheme[] = [
  {
    id: 'aurora',
    name: 'Aurora',
    look: { accent: '#2EC4B6', focus: 'lift', background: '#08161E', wallpaper_dim: 0, viz_style: 'wave', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#06121B', '#0A2530'],
      glows: [
        { colour: '#2EC4B6', x: 0.18, y: 0.1, r: 0.55, a: 0.32 },
        { colour: '#3DDC84', x: 0.72, y: 0.02, r: 0.4, a: 0.18 },
        { colour: '#7C5CFF', x: 0.92, y: 0.78, r: 0.45, a: 0.2 }
      ],
      art: { kind: 'aurora', colours: ['#2EC4B6', '#3DDC84', '#7C5CFF'], seed: 11 }
    }
  },
  {
    id: 'ember',
    name: 'Ember',
    look: { accent: '#FFB23C', focus: 'lift', background: '#140806', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'accent', viz_peaks: true },
    paint: {
      base: ['#100605', '#26100A'],
      glows: [
        { colour: '#FF3000', x: 0.88, y: 0.95, r: 0.55, a: 0.3 },
        { colour: '#FFB23C', x: 0.55, y: 1.05, r: 0.35, a: 0.2 },
        { colour: '#7A1A00', x: 0.05, y: 0.2, r: 0.4, a: 0.25 }
      ],
      art: { kind: 'dunes', colours: ['#FF3000', '#FFB23C'], seed: 12 }
    }
  },
  {
    id: 'midnight',
    name: 'Midnight',
    look: { accent: '#7C5CFF', focus: 'lift', background: '#070B1E', wallpaper_dim: 0, viz_style: 'mirror', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#060A1C', '#111938'],
      glows: [
        { colour: '#7C5CFF', x: 0.12, y: 0.88, r: 0.5, a: 0.28 },
        { colour: '#3A6BFF', x: 0.82, y: 0.15, r: 0.45, a: 0.22 }
      ],
      art: { kind: 'stars', colours: ['#7C5CFF', '#3A6BFF', '#C9B8FF'], seed: 13 }
    }
  },
  {
    id: 'forest',
    name: 'Forest',
    look: { accent: '#4CAF50', focus: 'lift', background: '#1C2A1F', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'accent', viz_peaks: true },
    paint: {
      base: ['#0C1710', '#1C2A1F'],
      glows: [
        { colour: '#3DDC84', x: 0.1, y: 0.05, r: 0.5, a: 0.16 },
        { colour: '#8BC34A', x: 0.95, y: 0.95, r: 0.45, a: 0.14 }
      ],
      art: { kind: 'ridges', colours: ['#3DDC84', '#8BC34A'], seed: 14 }
    }
  },
  {
    id: 'graphite',
    name: 'Graphite',
    look: { accent: '#FFFFFF', focus: 'outline', background: '#111214', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'white', viz_peaks: false },
    paint: {
      base: ['#0D0E10', '#1D1F23'],
      glows: [{ colour: '#FFFFFF', x: 0.5, y: -0.25, r: 0.7, a: 0.07 }],
      art: { kind: 'contours', colours: ['#FFFFFF', '#9AA0A6'], seed: 15 }
    }
  },
  // Gruvbox dark (github.com/morhetz/gruvbox). Only the dark palette: Home's text is white.
  {
    id: 'gruvbox',
    name: 'Gruvbox',
    look: { accent: '#FE8019', focus: 'lift', background: '#1D2021', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'accent', viz_peaks: true },
    paint: {
      base: ['#1D2021', '#32302F'],
      glows: [
        { colour: '#FE8019', x: 0.9, y: 0.9, r: 0.45, a: 0.16 },
        { colour: '#FABD2F', x: 0.08, y: 0.1, r: 0.4, a: 0.1 }
      ],
      art: { kind: 'ridges', colours: ['#FE8019', '#FABD2F'], seed: 16 }
    }
  },
  {
    id: 'gruvbox-aqua',
    name: 'Gruvbox Aqua',
    look: { accent: '#8EC07C', focus: 'lift', background: '#1D2021', wallpaper_dim: 0, viz_style: 'mirror', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#1D2021', '#282828'],
      glows: [
        { colour: '#8EC07C', x: 0.15, y: 0.9, r: 0.45, a: 0.15 },
        { colour: '#83A598', x: 0.85, y: 0.1, r: 0.45, a: 0.14 }
      ],
      art: { kind: 'flow', colours: ['#8EC07C', '#83A598'], seed: 17 }
    }
  },
  {
    id: 'gruvbox-purple',
    name: 'Gruvbox Purple',
    look: { accent: '#D3869B', focus: 'outline', background: '#282828', wallpaper_dim: 0, viz_style: 'wave', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#282828', '#1D2021'],
      glows: [
        { colour: '#D3869B', x: 0.8, y: 0.85, r: 0.45, a: 0.14 },
        { colour: '#FB4934', x: 0.1, y: 0.05, r: 0.35, a: 0.08 }
      ],
      art: { kind: 'bokeh', colours: ['#D3869B', '#B16286', '#FB4934'], seed: 18 }
    }
  },
  {
    id: 'gruvbox-yellow',
    name: 'Gruvbox Yellow',
    look: { accent: '#FABD2F', focus: 'lift', background: '#1D2021', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#282828', '#1D2021'],
      glows: [
        { colour: '#FABD2F', x: 0.12, y: 0.12, r: 0.45, a: 0.13 },
        { colour: '#D79921', x: 0.9, y: 0.95, r: 0.4, a: 0.12 }
      ],
      art: { kind: 'dunes', colours: ['#FABD2F', '#D79921'], seed: 19 }
    }
  },
  {
    id: 'gruvbox-blue',
    name: 'Gruvbox Blue',
    look: { accent: '#83A598', focus: 'lift', background: '#1D2021', wallpaper_dim: 0, viz_style: 'wave', viz_colour: 'accent', viz_peaks: false },
    paint: {
      base: ['#1D2021', '#32302F'],
      glows: [
        { colour: '#83A598', x: 0.88, y: 0.12, r: 0.5, a: 0.16 },
        { colour: '#458588', x: 0.1, y: 0.92, r: 0.4, a: 0.16 }
      ],
      art: { kind: 'contours', colours: ['#83A598', '#458588'], seed: 20 }
    }
  },
  {
    id: 'gruvbox-green',
    name: 'Gruvbox Green',
    look: { accent: '#B8BB26', focus: 'outline', background: '#282828', wallpaper_dim: 0, viz_style: 'bars', viz_colour: 'accent', viz_peaks: true },
    paint: {
      base: ['#1D2021', '#282828'],
      glows: [
        { colour: '#B8BB26', x: 0.5, y: 1.1, r: 0.5, a: 0.13 },
        { colour: '#98971A', x: 0.05, y: 0.05, r: 0.35, a: 0.1 }
      ],
      art: { kind: 'horizon', colours: ['#B8BB26', '#98971A', '#FABD2F'], seed: 21 }
    }
  },
  ...omarchy()
]

/** Pictures per theme on the TV's carousel and behind "Another picture". */
export const THEME_PICTURES = 3

/** Picture [n] of a theme: the same art, from another seed. Picture 0 is the theme as it always was. */
export function withPicture(p: ThemePaint, n: number): ThemePaint {
  return p.art && n ? { ...p, art: { ...p.art, seed: p.art.seed + n * 7919 } } : p
}
