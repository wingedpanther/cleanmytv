import type { HomeConfig, ScreenDay } from './api'

/** As the launcher's cardColours: the Screen time card's background with its opacity, text, softer text and bars. */
export function cardColours(cfg: Pick<HomeConfig, 'st_bg' | 'st_opacity' | 'st_text' | 'st_bars' | 'accent' | 'layout'>): { bg: string; text: string; muted: string; bars: string } {
  const rgb = (hex: string): [number, number, number] => [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16)) as [number, number, number]
  const bg = cfg.st_bg || '#000000'
  const lin = (c: number): number => { const s = c / 255; return s <= 0.04045 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4 }
  const [r, g, b] = rgb(bg)
  const light = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b) > 0.5
  const text = cfg.st_text || (light && cfg.st_opacity >= 50 ? '#1C1C1C' : '#F0EBE5')
  const rgba = (hex: string, a: number): string => `rgba(${rgb(hex).join(',')},${a})`
  return { bg: rgba(bg, cfg.st_opacity / 100), text, muted: rgba(text, 0.72), bars: cfg.st_bars || (cfg.layout === 'bau' ? '#F2EEE3' : cfg.accent) }
}

/** The days from `from` to `to`, both YYYY-MM-DD, as kept. */
export const daysBetween = (days: ScreenDay[], from: string, to: string): ScreenDay[] => days.filter((d) => d.day >= from && d.day <= to)

/** Seconds per app over several days, longest first. */
export function appTotals(days: ScreenDay[]): [string, number][] {
  const m = new Map<string, number>()
  for (const d of days) for (const [p, s] of Object.entries(d.apps)) m.set(p, (m.get(p) ?? 0) + s)
  return [...m.entries()].sort((a, b) => b[1] - a[1])
}

/** "YYYY-MM-DD" moved by `n` days. Dates only, so no time zone gets in the way. */
export function addDays(day: string, n: number): string {
  const d = new Date(`${day}T12:00:00Z`)
  d.setUTCDate(d.getUTCDate() + n)
  return d.toISOString().slice(0, 10)
}

const cell = (v: string | number): string => (typeof v === 'number' || !/[",\n]/.test(v) ? String(v) : `"${v.replace(/"/g, '""')}"`)

/**
 * One row per app per day, for a spreadsheet: minutes, times opened, the longest stretch in minutes.
 * Days that only kept minutes (Home 1.13.7) leave the last two empty.
 */
export function screenTimeCsv(days: ScreenDay[], label: (pkg: string) => string): string {
  const rows = [['date', 'app', 'package', 'minutes', 'times_opened', 'longest_minutes'].join(',')]
  for (const d of days) {
    for (const [p, s] of Object.entries(d.apps).sort((a, b) => b[1] - a[1])) {
      if (s < 30) continue
      const st = d.stats[p]
      rows.push([d.day, label(p), p, Math.round(s / 60), st ? st.sessions : '', st ? Math.round(st.longest / 60) : ''].map(cell).join(','))
    }
  }
  return rows.join('\n') + '\n'
}
