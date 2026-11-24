import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { HomeConfig, QuickEntry, QuickId, TvApp } from '@shared/api'
import { t } from '../lib/i18n'
import { cardColours } from '@shared/screentime'

// The launcher lays out in dp on a 960x540 screen; the preview draws at that size and scales to fit.
const W = 960
const H = 540
const SAFE_X = 48
const SAFE_Y = 27
const GAP = 16

const QUICK_GLYPH: Record<QuickId, string> = {
  inputs: 'M4 7h12v10H4zM16 10h4M16 14h4',
  wifi: 'M4 9a12 12 0 0 1 16 0M7 12.5a7.5 7.5 0 0 1 10 0M10 16a3 3 0 0 1 4 0',
  bluetooth: 'M8 7l8 8-4 3V6l4 3-8 8',
  display: 'M4 6h16v10H4zM9 20h6',
  apps: 'M5 5h4v4H5zM15 5h4v4h-4zM5 15h4v4H5zM15 15h4v4h-4z',
  sleep: 'M17 14A7 7 0 0 1 10 5a7 7 0 1 0 7 9z',
  search: 'M11 5a6 6 0 1 1 0 12 6 6 0 0 1 0-12zM16 16l4 4',
  music: 'M9 18V6l10-2v12M9 18a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0zM19 16a2.5 2.5 0 1 1-5 0 2.5 2.5 0 0 1 5 0z',
  settings: 'M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1',
  panel: 'M4 7h10M18 7h2M4 17h4M12 17h8M14 5v4M8 15v4',
  themes: 'M12 4a8 8 0 1 0 0 16c1 0 1.5-.8 1.5-1.6 0-1.4 1-2.4 2.4-2.4H18a2 2 0 0 0 2-2A8 8 0 0 0 12 4zM8 11h.01M11 8h.01M15 8h.01',
  fireplace: 'M12 21c-3.5 0-6-2.4-6-5.6 0-3.2 2.6-5 3.5-8.4 1.8 1.4 2.6 3 2.6 4.6 1-.7 1.6-1.8 1.7-3 1.6 1.6 2.2 3.6 2.2 5.6 0 3.4-2.5 6.8-4 6.8z',
  memory: 'M7 7h10v10H7zM10 4v3M14 4v3M10 17v3M14 17v3M4 10h3M4 14h3M17 10h3M17 14h3',
  customize: 'M4 5h16v11H4zM9 20h6M8 9h3M13 9h3M8 12h8',
  timer: 'M12 7v5l3 2M9 3h6M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16z',
  screentime: 'M4 20h16M6 16v-5M10 16V6M14 16v-8M18 16v-3',
  guest: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h2v2h-2zM18 18h2v2h-2zM14 18h2M18 14h2',
  find: 'M4 6h7M4 11h5M4 16h7M15 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM18 15l3 3',
  allapps: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z'
}

/** A stand-in for the Screen time card, in the owner's colours: on the TV it's today's total, the week's bars and today's top apps. */
export function ScreenTimeCard({ cfg, labels }: { cfg: HomeConfig; labels: string[] }): ReactNode {
  const c = cardColours(cfg)
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 20, width: 600, height: 124, padding: '14px 18px', margin: '6px 0', borderRadius: 14, background: c.bg, color: c.text, boxSizing: 'border-box' }}>
      <div style={{ width: 130 }}>
        <div style={{ fontSize: 13, color: c.muted }}>Screen time today</div>
        <div style={{ fontSize: 26, fontWeight: 500 }}>2 h 15 min</div>
        <div style={{ fontSize: 13, color: c.muted }}>Yesterday: 3 h</div>
      </div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, width: 150, height: 70 }}>
        {[0.6, 0.9, 0.4, 0.75, 1, 0.5, 0.7].map((h, i) => (
          <div key={i} style={{ flex: 1, height: `${h * 100}%`, borderRadius: 4, background: c.bars, opacity: i === 6 ? 1 : 0.5 }} />
        ))}
      </div>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6, fontSize: 14 }}>
        {labels.map((l, i) => (
          <div key={l} style={{ display: 'flex', justifyContent: 'space-between', gap: 10 }}>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{l}</span>
            <span style={{ color: c.muted }}>{['1 h 10 min', '40 min', '25 min'][i]}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

/** What the TV calls each icon. CleanMyTV Home is in English, so these stay English: the preview and the rename boxes show them. */
export const QUICK_LABEL: Record<QuickId, string> = {
  inputs: 'Inputs', wifi: 'Wi-Fi', bluetooth: 'Bluetooth', display: 'Picture & sound', apps: 'Apps', sleep: 'Screensaver', search: 'Search', music: 'Music bars', settings: 'Settings',
  panel: 'Quick settings', themes: 'Themes', fireplace: 'Fireplace', memory: 'Clean memory', customize: 'Customize', timer: 'Sleep timer', screentime: 'Screen time', guest: 'Guest Wi-Fi', find: 'Search this TV', allapps: 'All apps'
}

/** The same icons named in this app's language, for its own lists. */
export const QUICK_NAME: Record<QuickId, string> = {
  inputs: t('Inputs'), wifi: 'Wi-Fi', bluetooth: 'Bluetooth', display: t('Picture & sound'), apps: t('Apps'), sleep: t('Screensaver'), search: t('Search'), music: t('Music bars'),
  settings: t('Settings'), panel: t('Quick settings'), themes: t('Themes'), fireplace: t('Fireplace'), memory: t('Clean memory'), customize: t('Customize'), timer: t('Sleep timer'), screentime: t('Screen time'), guest: t('Guest Wi-Fi'), find: t('Search this TV'), allapps: t('All apps')
}

export function QuickIcon({ id, size = 16 }: { id: QuickId; size?: number }): ReactNode {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={QUICK_GLYPH[id]} />
    </svg>
  )
}

/** The package of an app shortcut, or null for a quick-settings action. */
export const quickApp = (q: QuickEntry): string | null => (q.startsWith('app:') ? q.slice(4) : null)

export function quickLabel(q: QuickEntry, apps: TvApp[]): string {
  const pkg = quickApp(q)
  return pkg ? (apps.find((a) => a.pkg === pkg)?.label ?? pkg) : QUICK_LABEL[q as QuickId]
}

/** A stand-in for an app's own icon, which the desktop app can't read: its first letter on a disc. */
function AppMark({ label, size }: { label: string; size: number }): ReactNode {
  return (
    <span aria-hidden style={{ width: size, height: size, borderRadius: size / 2, background: '#D7CFC4', color: '#1C2A1F', display: 'inline-grid', placeItems: 'center', fontSize: size * 0.55, fontWeight: 600, lineHeight: 1, flex: 'none' }}>
      {label.trim().charAt(0).toUpperCase() || '?'}
    </span>
  )
}

/** The icon a top-bar entry shows: the drawn glyph, or for an app a disc 4 px larger, as on the TV. */
export function QuickMark({ q, apps, size = 16 }: { q: QuickEntry; apps: TvApp[]; size?: number }): ReactNode {
  return quickApp(q) ? <AppMark label={quickLabel(q, apps)} size={size + 4} /> : <QuickIcon id={q as QuickId} size={size} />
}

/** The order and grouping the launcher uses, from the same settings. */
function model(apps: TvApp[], cfg: HomeConfig) {
  const by = new Map(apps.map((a) => [a.pkg, a]))
  const ordered = [...cfg.order.flatMap((p) => (by.has(p) ? [by.get(p)!] : [])), ...apps.filter((a) => !cfg.order.includes(a.pkg)).sort((a, b) => a.label.localeCompare(b.label))].filter(
    (a) => !cfg.hidden.includes(a.pkg)
  )
  const inFolders = new Set(cfg.folders.flatMap((f) => f.apps))
  const dock = ordered.filter((a) => !inFolders.has(a.pkg)).slice(0, cfg.dock_size)
  const grid = ordered.filter((a) => !inFolders.has(a.pkg) && !dock.includes(a))
  const rows = cfg.rows.map((r) => ({ name: r.name, apps: r.apps.flatMap((p) => (by.has(p) ? [by.get(p)!] : [])) })).filter((r) => r.apps.length)
  const folders = cfg.folders.map((f) => ({ name: f.name, apps: f.apps.flatMap((p) => (by.has(p) ? [by.get(p)!] : [])) })).filter((f) => f.apps.length)
  return { dock, grid, rows, folders }
}

function Tile({ app, w, names, focused, cfg }: { app: TvApp; w: number; names: boolean; focused?: boolean; cfg: HomeConfig }): ReactNode {
  const ring: CSSProperties = focused
    ? cfg.focus === 'glow'
      ? { boxShadow: `0 0 0 2px #F0EBE5, 0 0 16px 4px ${cfg.accent}`, transform: 'scale(1.08)' }
      : cfg.focus === 'outline'
        ? { outline: `3px solid ${cfg.accent}`, outlineOffset: 0, transform: 'scale(1.08)' }
        : cfg.focus === 'lift'
          ? { boxShadow: `0 10px 18px -4px rgba(0,0,0,.6), 0 0 22px 1px ${cfg.accent}59, inset 0 0 0 1px rgba(255,255,255,.28)`, transform: 'scale(1.09) translateY(-3px)' }
          : {}
    : {}
  // The icon styles: square, squircle or round tiles, a little narrower; the logo shows on the TV, the
  // preview draws the first letter in its place.
  const icons = cfg.icon_style !== 'banner'
  const shape = icons ? cfg.tile_shape : 'wide'
  const side = shape === 'wide' ? w : w * 0.72
  const radius = shape === 'round' ? '50%' : shape === 'squircle' ? '32%' : shape === 'square' ? '22%' : 10
  const fill = cfg.icon_style === 'mono' ? `rgba(255,255,255,${(cfg.tile_glass ?? 10) / 100})` : cfg.icon_style === 'duo' ? `color-mix(in srgb, ${cfg.background} 70%, ${cfg.accent})` : '#2D3A2E'
  const ink = cfg.icon_style === 'mono' || cfg.icon_style === 'duo' ? `color-mix(in srgb, ${cfg.accent} 65%, white)` : '#F0EBE5'
  return (
    <div style={{ width: side, flex: 'none' }}>
      <div style={{ height: shape === 'wide' ? (w * 9) / 16 : side, borderRadius: radius, background: fill, display: 'grid', placeItems: 'center', color: ink, fontWeight: icons ? 600 : 500, fontSize: icons && shape !== 'wide' ? side * 0.4 : Math.max(12, w / 11), padding: '0 8px', textAlign: 'center', lineHeight: 1.15, overflow: 'hidden', ...ring }}>
        {icons ? app.label.charAt(0).toUpperCase() : app.label}
      </div>
      {names && <div style={{ visibility: focused || !cfg.name_focus ? 'visible' : 'hidden', marginTop: 6, fontSize: 14, color: '#F0EBE5', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', textAlign: shape === 'wide' ? 'left' : 'center' }}>{app.label}</div>}
    </div>
  )
}

function Folder({ name, apps, w }: { name: string; apps: TvApp[]; w: number }): ReactNode {
  return (
    <div style={{ width: w, flex: 'none' }}>
      <div style={{ height: (w * 9) / 16, borderRadius: 10, background: 'rgba(45,58,46,0.9)', padding: 7, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 5 }}>
        {apps.slice(0, 4).map((a) => (
          <div key={a.pkg} style={{ borderRadius: 4, background: '#3A4A3B' }} />
        ))}
      </div>
      <div style={{ marginTop: 6, fontSize: 14, color: '#F0EBE5' }}>
        {name} · {apps.length}
      </div>
    </div>
  )
}

function Title({ children }: { children: ReactNode }): ReactNode {
  return <div style={{ fontSize: 18, color: '#F0EBE5', fontWeight: 500, margin: '12px 0 4px' }}>{children}</div>
}

const BAU = { ink: '#141414', cream: '#F2EEE3', muted: '#A8A398', red: '#E03A2B', yellow: '#F5C518', blue: '#2F6BDE' }

/** As the launcher's bauMark: Java's String.hashCode of the package picks the form and the colour. */
function bauMark(pkg: string): [number, string] {
  let h = 0
  for (let i = 0; i < pkg.length; i++) h = (Math.imul(31, h) + pkg.charCodeAt(i)) | 0
  h = h === -2147483648 ? 0 : Math.abs(h)
  return [h % 7, [BAU.red, BAU.yellow, BAU.blue][Math.floor(h / 7) % 3]]
}

/** The seven Bauhaus forms, as the launcher draws them. */
function BauGlyph({ form, colour, size }: { form: number; colour: string; size: number }): ReactNode {
  const s = size
  const shapes = [
    <circle key="c" cx={s / 2} cy={s / 2} r={s / 2} fill={colour} />,
    <rect key="s" width={s} height={s} fill={colour} />,
    <path key="t" d={`M${s / 2} 0L${s} ${s}H0Z`} fill={colour} />,
    <path key="q" d={`M${s} ${s}H0A${s} ${s} 0 0 1 ${s} 0Z`} fill={colour} />,
    <path key="h" d={`M0 ${s / 2}H${s}A${s / 2} ${s / 2} 0 0 1 0 ${s / 2}Z`} fill={colour} />,
    <circle key="r" cx={s / 2} cy={s / 2} r={s / 2 - s / 8} fill="none" stroke={colour} strokeWidth={s / 4} />,
    <path key="p" d={`M0 0L${s} ${s / 2}L0 ${s}Z`} fill={colour} />
  ]
  return (
    <svg width={s} height={s} viewBox={`0 0 ${s} ${s}`} aria-hidden style={{ display: 'block', flex: 'none' }}>
      {shapes[form % 7]}
    </svg>
  )
}

function Sections({ apps, cfg, width, dockW, showFolders = true }: { apps: TvApp[]; cfg: HomeConfig; width: number; dockW: number; showFolders?: boolean }): ReactNode {
  const m = model(apps, cfg)
  const gridW = (width - GAP * (cfg.columns - 1)) / cfg.columns
  const rowW = 148 * (cfg.tile_scale / 100)
  let focused = false
  const focusOnce = (): boolean => (focused ? false : (focused = true))
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {cfg.sections.map((s) => {
        // A stand-in: on the TV the card shows only while an app has something playing.
        if (s === 'nowplaying')
          return (
            <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 14, width: 520, padding: 12, margin: '8px 0', borderRadius: 14, background: 'rgba(45,58,46,0.88)' }}>
              <div style={{ width: 64, height: 64, borderRadius: 8, background: '#3A4A3B', flex: 'none' }} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 17, fontWeight: 500 }}>What's playing</div>
                <div style={{ fontSize: 13, color: '#D7CFC4', marginTop: 3 }}>Artist · App, while something plays</div>
              </div>
              {['❚❚', '▸▸'].map((g) => (
                <div key={g} style={{ width: 44, height: 44, borderRadius: 22, background: 'rgba(255,255,255,0.08)', display: 'grid', placeItems: 'center', fontSize: 13 }}>{g}</div>
              ))}
            </div>
          )
        // A stand-in: on the TV it's the apps opened last, newest first.
        if (s === 'recent')
          return (
            <div key={s}>
              <Title>Recently used</Title>
              <div style={{ display: 'flex', gap: GAP, padding: '6px 0' }}>
                {[...m.grid, ...m.dock].slice(0, 5).map((a) => <Tile key={a.pkg} app={a} w={rowW} names={cfg.show_names} focused={false} cfg={cfg} />)}
              </div>
            </div>
          )
        // A stand-in: on the TV it's today's total, the last 7 days' bars and today's top apps.
        if (s === 'screentime')
          return (
            <div key={s}>
              <ScreenTimeCard cfg={cfg} labels={[...m.dock, ...m.grid].slice(0, 3).map((a) => a.label)} />
            </div>
          )
        // A stand-in: on the TV it's what apps list in Android's Watch Next, and it hides while that's empty.
        if (s === 'watchnext')
          return (
            <div key={s}>
              <Title>Continue watching</Title>
              <div style={{ display: 'flex', gap: GAP, padding: '6px 0' }}>
                {[0.4, 0.75, 0.15, 0.6].map((done, i) => (
                  <div key={i} style={{ width: 196 * (cfg.tile_scale / 100), flex: 'none' }}>
                    <div style={{ height: (196 * (cfg.tile_scale / 100) * 9) / 16, borderRadius: 10, background: '#3A4A3B', position: 'relative' }}>
                      <div style={{ position: 'absolute', left: 10, right: 10, bottom: 8, height: 4, borderRadius: 2, background: 'rgba(0,0,0,0.55)' }}>
                        <div style={{ width: `${done * 100}%`, height: '100%', borderRadius: 2, background: cfg.accent }} />
                      </div>
                    </div>
                    <div style={{ marginTop: 7, fontSize: 14, color: '#F0EBE5' }}>Film or show</div>
                    <div style={{ marginTop: 2, fontSize: 12, color: '#D7CFC4' }}>S1 E{i + 2}</div>
                  </div>
                ))}
              </div>
            </div>
          )
        if (s === 'dock' && m.dock.length)
          return (
            <div key={s} style={{ display: 'flex', gap: GAP, padding: '8px 0' }}>
              {m.dock.map((a, i) => <Tile key={a.pkg} app={a} w={dockW * (cfg.dock_scale / 100)} names={cfg.show_names} focused={i === 0 && focusOnce()} cfg={cfg} />)}
            </div>
          )
        if (s === 'rows')
          return m.rows.map((r, ri) => (
            <div key={`row${ri}`}>
              <Title>{r.name}</Title>
              <div style={{ display: 'flex', gap: GAP, padding: '6px 0' }}>
                {r.apps.map((a, i) => <Tile key={a.pkg} app={a} w={rowW} names={cfg.show_names} focused={ri === 0 && i === 0 && focusOnce()} cfg={cfg} />)}
              </div>
            </div>
          ))
        if (s === 'folders' && showFolders && m.folders.length)
          return (
            <div key={s}>
              <Title>Folders</Title>
              <div style={{ display: 'flex', gap: GAP, padding: '6px 0' }}>
                {m.folders.map((f) => <Folder key={f.name} name={f.name} apps={f.apps} w={rowW} />)}
              </div>
            </div>
          )
        if (s === 'grid' && m.grid.length)
          return (
            <div key={s}>
              <Title>{cfg.names.all_apps || (m.dock.length || m.rows.length ? 'All apps' : 'Apps')}</Title>
              <div style={{ display: 'grid', gridTemplateColumns: `repeat(${cfg.columns}, ${gridW}px)`, gap: `14px ${GAP}px`, padding: '6px 0' }}>
                {m.grid.map((a, i) => <Tile key={a.pkg} app={a} w={gridW} names={cfg.show_names} focused={i === 0 && focusOnce()} cfg={cfg} />)}
              </div>
            </div>
          )
        return null
      })}
    </div>
  )
}

function Clock({ cfg, size, dateSize, align }: { cfg: HomeConfig; size: number; dateSize: number; align: 'left' | 'right' }): ReactNode {
  if (cfg.clock === 'off') return null
  return (
    <div style={{ textAlign: align }}>
      <div style={{ fontSize: size, fontWeight: 300, lineHeight: 1.1, fontFamily: 'var(--font)' }}>9:41</div>
      {cfg.clock === 'time_date' && <div style={{ fontSize: dateSize, color: '#D7CFC4', fontFamily: 'var(--font)' }}>Wednesday, 28 October</div>}
      {cfg.weather_place && (
        <div style={{ fontSize: dateSize, color: '#D7CFC4', fontFamily: 'var(--font)', marginTop: 4 }}>
          ☁ 14°  16° / 9°  {cfg.weather_place}
        </div>
      )}
      {cfg.memory && <div style={{ fontSize: dateSize, color: '#D7CFC4', fontFamily: 'var(--font)', marginTop: 4 }}>▦ 1.4 of 2 GB free</div>}
    </div>
  )
}

/** The TV's fonts as near as a computer has them (the launcher's homeFont). */
const PREVIEW_FONT: Record<string, string> = {
  default: 'Roboto, system-ui, sans-serif', condensed: '"Roboto Condensed", "Arial Narrow", sans-serif', serif: 'Georgia, serif', casual: '"Comic Sans MS", "Comic Neue", cursive'
}

/** The TV's shade over the wallpaper: the background colour at [pct]% (#102). */
const shade = (cfg: HomeConfig, pct: number): string => `color-mix(in srgb, ${cfg.background} ${pct}%, transparent)`

const greetingText = (cfg: HomeConfig): string | null => (cfg.greeting.trim() ? `Good evening, ${cfg.greeting.trim()}` : null)

/**
 * A picture of the TV's home screen from the same settings the launcher reads: layout, sections, sizes
 * and colours. App art is a coloured monogram, since the desktop app can't read the TV's banners.
 */
export function HomePreview({ apps, cfg }: { apps: TvApp[]; cfg: HomeConfig }): ReactNode {
  const box = useRef<HTMLDivElement>(null)
  const [scale, setScale] = useState(0.5)
  useEffect(() => {
    const el = box.current
    if (!el) return
    const ro = new ResizeObserver(() => setScale(el.clientWidth / W))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  // As on the TV, Top space stops where the first row still fits on screen (#134).
  const head = useRef<HTMLDivElement>(null)
  const secs = useRef<HTMLDivElement>(null)
  const [room, setRoom] = useState(H)
  useLayoutEffect(() => {
    const first = secs.current?.firstElementChild?.firstElementChild
    if (!head.current || !(first instanceof HTMLElement)) return
    const r = Math.max(0, H - SAFE_Y - head.current.offsetHeight - first.offsetHeight - (SAFE_Y - 16))
    if (r !== room) setRoom(r)
  })
  const greet = greetingText(cfg)
  // As on the TV: icons it can't open, and shortcuts to apps that aren't installed, are left out.
  const quick = cfg.quick.filter((q) => {
    const pkg = quickApp(q)
    return pkg ? apps.some((a) => a.pkg === pkg) : !cfg.quick_available || cfg.quick_available.includes(q as QuickId)
  })
  // As on the TV: the bottom shade behind the rows, over the dimmed wallpaper.
  const bottom = cfg.shade_bottom > 0 && cfg.shade_bottom_reach > 0 ? `linear-gradient(transparent ${100 - cfg.shade_bottom_reach}%, ${shade(cfg, cfg.shade_bottom)}), ` : ''
  const ground: CSSProperties = {
    background: cfg.has_wallpaper
      ? `${bottom}linear-gradient(rgba(0,0,0,${cfg.wallpaper_dim / 100}), rgba(0,0,0,${cfg.wallpaper_dim / 100})), linear-gradient(160deg, #6b5a3e, #2f3b2a 55%, #1a2418)`
      : cfg.background
  }
  let body: ReactNode
  if (cfg.layout === 'split') {
    body = (
      <div style={{ display: 'flex', height: '100%', background: `linear-gradient(90deg, ${shade(cfg, 70)}, transparent 60%)` }}>
        <div style={{ width: W * 0.32, padding: `${SAFE_Y + 24}px 0 ${SAFE_Y + 48}px ${SAFE_X}px`, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Clock cfg={cfg} size={56} dateSize={15} align="left" />
          {greet && <div style={{ fontSize: 18, fontWeight: 500 }}>{greet}</div>}
          <div style={{ flex: 1 }} />
          {quick.map((q) => (
            <div key={q} style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 15 }}>
              <span style={{ width: 22, display: 'inline-grid', placeItems: 'center' }}>
                <QuickMark q={q} apps={apps} size={18} />
              </span>{' '}
              {(!quickApp(q) && cfg.names[q as QuickId]) || quickLabel(q, apps)}
            </div>
          ))}
        </div>
        <div style={{ flex: 1, padding: `${SAFE_Y + 12}px ${SAFE_X}px 0 0`, overflow: 'hidden' }}>
          <Sections apps={apps} cfg={cfg} width={W * 0.68 - SAFE_X} dockW={168} />
        </div>
      </div>
    )
  } else if (cfg.layout === 'bau') {
    // As on the TV: its own charcoal and cream, each app marked with the same shape and colour.
    const m = model(apps, cfg)
    const rule = '1px solid #3A3A3A'
    const cells = (list: TvApp[], cols: number, h: number, mark: number, size: number, focusFirst: boolean): ReactNode => (
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${cols}, minmax(0, 1fr))`, gap: 1, padding: 1, background: BAU.cream }}>
        {list.map((a, i) => {
          const on = focusFirst && i === 0
          const [form, colour] = bauMark(a.pkg)
          return (
            <div key={a.pkg} style={{ height: h, boxSizing: 'border-box', padding: h > 100 ? '14px 15px' : '10px 12px', background: on ? BAU.cream : BAU.ink, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
              <BauGlyph form={form} colour={colour} size={mark} />
              <div style={{ fontSize: size, fontWeight: 800, letterSpacing: '-0.02em', color: on ? BAU.ink : BAU.cream, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{a.label.toLowerCase()}</div>
            </div>
          )
        })}
        {Array.from({ length: (cols - (list.length % cols)) % cols }, (_, i) => <div key={`e${i}`} style={{ height: h, background: BAU.ink }} />)}
      </div>
    )
    body = (
      <div style={{ display: 'flex', gap: 36, height: '100%', padding: `0 ${SAFE_X}px`, boxSizing: 'border-box', background: BAU.ink, color: BAU.cream }}>
        <div style={{ width: 276, flex: 'none', padding: `${SAFE_Y}px 0`, display: 'flex', flexDirection: 'column' }}>
          {cfg.clock !== 'off' && <div style={{ fontSize: 106, fontWeight: 800, letterSpacing: '-0.055em', lineHeight: 0.86, marginLeft: -5 }}>21:47</div>}
          {cfg.clock === 'time_date' && <div style={{ fontSize: 14, fontWeight: 600, color: BAU.muted, marginTop: 14 }}>monday 23 november</div>}
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', marginTop: 20 }}>
            <BauGlyph form={0} colour={BAU.red} size={28} />
            <BauGlyph form={1} colour={BAU.yellow} size={28} />
            <BauGlyph form={2} colour={BAU.blue} size={28} />
          </div>
          {greet && <div style={{ fontSize: 14, fontWeight: 500, marginTop: 18 }}>{greet}</div>}
          <div style={{ flex: 1 }} />
          {quick.map((q, i) => (
            <div key={q} style={{ display: 'flex', alignItems: 'center', height: 32, padding: '0 4px', fontSize: 12, fontWeight: 600, borderTop: rule, borderBottom: i === quick.length - 1 ? rule : undefined }}>
              <span style={{ flex: 1 }}>{((!quickApp(q) && cfg.names[q as QuickId]) || quickLabel(q, apps)).toLowerCase()}</span>
              <QuickMark q={q} apps={apps} size={16} />
            </div>
          ))}
          <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 16, fontSize: 12, fontWeight: 600, color: BAU.muted }}>
            <span style={{ border: `1px solid ${BAU.cream}`, color: BAU.cream, fontWeight: 800, padding: '3px 6px' }}>ok</span>open
            <span style={{ border: `1px solid ${BAU.cream}`, color: BAU.cream, fontWeight: 800, padding: '3px 6px' }}>hold ok</span>options
          </div>
        </div>
        <div style={{ flex: 1, minWidth: 0, paddingTop: SAFE_Y, overflow: 'hidden' }}>
          <div style={{ display: 'flex', alignItems: 'baseline', marginBottom: 8 }}>
            <b style={{ flex: 1, fontSize: 14, fontWeight: 800 }}>favourites</b>
            <span style={{ fontSize: 12, fontWeight: 600, color: BAU.muted }}>all apps, {m.dock.length + m.grid.length}</span>
          </div>
          {cells(m.dock, 3, 155, 52, 20, true)}
          <b style={{ display: 'block', margin: '22px 0 8px', fontSize: 14, fontWeight: 800 }}>{(cfg.names.all_apps ?? 'all apps').toLowerCase()}</b>
          {cells(m.grid, cfg.columns, 82, 22, 15, false)}
        </div>
      </div>
    )
  } else if (cfg.layout === 'rail') {
    body = (
      <div style={{ display: 'flex', height: '100%' }}>
        <div style={{ width: SAFE_X + 56, padding: `${SAFE_Y + 8}px 0 0 ${SAFE_X + 7}px`, display: 'flex', flexDirection: 'column', gap: 18, background: `linear-gradient(90deg, ${shade(cfg, 60)}, ${shade(cfg, 10)})` }}>
          {quick.map((q) => (
            <span key={q} style={{ width: 22, height: 22, display: 'grid', placeItems: 'center' }}>
              <QuickMark q={q} apps={apps} size={22} />
            </span>
          ))}
          {cfg.folders.length > 0 && <div style={{ height: 6 }} />}
          {cfg.folders.map((f) => (
            <svg key={f.name} viewBox="0 0 24 24" width={22} height={22} fill="none" stroke="currentColor" strokeWidth={1.8}>
              <path d="M3 7h6l2 2h10v10H3z" />
            </svg>
          ))}
        </div>
        <div style={{ flex: 1, padding: `${SAFE_Y}px ${SAFE_X}px 0 24px`, overflow: 'hidden' }}>
          <div style={{ display: 'flex', alignItems: 'center', marginBottom: 20 }}>
            <div style={{ fontSize: 20, fontWeight: 500, flex: 1 }}>{greet}</div>
            <Clock cfg={cfg} size={26} dateSize={12} align="right" />
          </div>
          <Sections apps={apps} cfg={cfg} width={W - SAFE_X - 56 - 24 - SAFE_X} dockW={196} showFolders={false} />
        </div>
      </div>
    )
  } else if (cfg.layout === 'glass') {
    // As on the TV: the focused dock app's headline, and the dock in a glass tray. Amber unless the owner set an accent.
    const m = model(apps, cfg)
    const dock = m.dock.slice(0, 6)
    const accent = cfg.accent.toUpperCase() === '#4CAF50' ? '#FF9F0A' : cfg.accent
    const pane = { background: 'rgba(255,255,255,0.12)', border: '1px solid rgba(255,255,255,0.16)' }
    body = (
      <div style={{ height: '100%', boxSizing: 'border-box', padding: `${SAFE_Y}px ${SAFE_X}px`, display: 'flex', flexDirection: 'column', color: '#FFFFFF',
        background: cfg.has_wallpaper ? undefined : 'radial-gradient(550px 320px at 80% 14%, rgba(255,255,255,0.22), rgba(255,255,255,0) 62%), radial-gradient(750px 450px at 6% 104%, #4A3560, rgba(74,53,96,0) 70%), linear-gradient(160deg, #2B3A55, #0B0D12 64%)' }}>
        <div style={{ display: 'flex', alignItems: 'center', height: 28 }}>
          <Clock cfg={cfg} size={18} dateSize={12} align="left" />
          <div style={{ flex: 1 }} />
          <span style={{ ...pane, height: 28, padding: '0 13px', borderRadius: 14, display: 'flex', alignItems: 'center', gap: 8, fontSize: 12 }}>
            Wi-Fi <span style={{ width: 6, height: 6, borderRadius: 3, background: accent }} /> {t('Debugging on')}
          </span>
        </div>
        <div style={{ marginTop: 30, fontSize: 12, fontWeight: 600, color: 'rgba(255,255,255,0.72)' }}>{t('Continue in {app}', { app: dock[0]?.label ?? 'Plex' })}</div>
        <div style={{ fontSize: 52, fontWeight: 700, letterSpacing: '-0.035em', lineHeight: 1.05 }}>The Salt Road</div>
        <div style={{ marginTop: 9, fontSize: 14, color: 'rgba(255,255,255,0.86)' }}>S2 E4 · 31 min</div>
        <div style={{ marginTop: 13, width: 260, height: 4, borderRadius: 2, background: 'rgba(255,255,255,0.24)' }}><div style={{ width: '58%', height: '100%', borderRadius: 2, background: accent }} /></div>
        <div style={{ flex: 1 }} />
        <div style={{ alignSelf: 'center', display: 'flex', gap: 12, padding: 14, borderRadius: 24, background: 'rgba(255,255,255,0.10)', border: '1px solid rgba(255,255,255,0.18)' }}>
          {[...dock.map((a) => a.label), t('All apps')].map((name, i) => (
            <span key={name + i} style={{ width: 104, height: 59, borderRadius: 12, display: 'grid', placeItems: 'center', fontSize: 14, fontWeight: 700, overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis', padding: '0 6px', boxSizing: 'border-box',
              ...(i === 0 ? { background: '#FFFFFF', color: '#1D1D1F', transform: 'translateY(-5px) scale(1.14)', boxShadow: '0 17px 30px rgba(0,0,0,0.55)' } : pane) }}>{name}</span>
          ))}
        </div>
      </div>
    )
  } else {
    body = (
      <div style={{ height: '100%', padding: `${SAFE_Y}px ${SAFE_X}px 0`, overflow: 'hidden', background: `linear-gradient(${shade(cfg, cfg.shade_top)}, transparent ${cfg.shade_top_reach}%)` }}>
        <div ref={head} style={{ display: 'flex', alignItems: 'flex-start' }}>
          <div style={{ fontSize: 20, fontWeight: 500, flex: 1, paddingTop: 4 }}>{greet}</div>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 10 }}>
            <Clock cfg={cfg} size={26} dateSize={12} align="right" />
            {quick.length > 0 && (
              <div style={{ display: 'flex', gap: 10 }}>
                {quick.map((q) => (
                  <span key={q} style={{ width: 40, height: 40, borderRadius: 20, background: 'rgba(255,255,255,0.08)', display: 'grid', placeItems: 'center' }}>
                    <QuickMark q={q} apps={apps} size={22} />
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>
        <div style={{ height: Math.min((H * cfg.top_space) / 100, room) }} />
        <div ref={secs}>
          <Sections apps={apps} cfg={cfg} width={W - SAFE_X * 2} dockW={200} />
        </div>
      </div>
    )
  }
  return (
    <div ref={box} className="home-preview" style={{ height: H * scale }} aria-label={t('Preview of the TV home screen')}>
      <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: '0 0', color: '#F0EBE5', fontFamily: PREVIEW_FONT[cfg.font] ?? PREVIEW_FONT.default, ...ground }}>{body}</div>
      <span className="home-preview-note">{t('Sample time and greeting · app art shows on the TV')}</span>
    </div>
  )
}
