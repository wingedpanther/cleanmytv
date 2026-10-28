import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import type { HomeConfig, QuickId, TvApp } from '@shared/api'

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
  settings: 'M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1'
}

export const QUICK_LABEL: Record<QuickId, string> = {
  inputs: 'Inputs', wifi: 'Wi-Fi', bluetooth: 'Bluetooth', display: 'Picture & sound', apps: 'Apps', sleep: 'Screensaver', search: 'Search', settings: 'Settings'
}

export function QuickIcon({ id, size = 16 }: { id: QuickId; size?: number }): ReactNode {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={QUICK_GLYPH[id]} />
    </svg>
  )
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
        : {}
    : {}
  return (
    <div style={{ width: w, flex: 'none' }}>
      <div style={{ height: (w * 9) / 16, borderRadius: 10, background: '#2D3A2E', display: 'grid', placeItems: 'center', color: '#F0EBE5', fontWeight: 500, fontSize: Math.max(12, w / 11), padding: '0 8px', textAlign: 'center', lineHeight: 1.15, overflow: 'hidden', ...ring }}>
        {app.label}
      </div>
      {names && <div style={{ marginTop: 6, fontSize: 14, color: '#F0EBE5', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{app.label}</div>}
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

function Sections({ apps, cfg, width, dockW, showFolders = true }: { apps: TvApp[]; cfg: HomeConfig; width: number; dockW: number; showFolders?: boolean }): ReactNode {
  const m = model(apps, cfg)
  const gridW = (width - GAP * (cfg.columns - 1)) / cfg.columns
  const rowW = 148 * (cfg.tile_scale / 100)
  let focused = false
  const focusOnce = (): boolean => (focused ? false : (focused = true))
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      {cfg.sections.map((s) => {
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
              <Title>{m.dock.length || m.rows.length ? 'All apps' : 'Apps'}</Title>
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
    </div>
  )
}

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
  const greet = greetingText(cfg)
  // As on the TV: icons it can't open are left out.
  const quick = cfg.quick.filter((q) => !cfg.quick_available || cfg.quick_available.includes(q))
  const ground: CSSProperties = {
    background: cfg.has_wallpaper
      ? `linear-gradient(rgba(0,0,0,${cfg.wallpaper_dim / 100}), rgba(0,0,0,${cfg.wallpaper_dim / 100})), linear-gradient(160deg, #6b5a3e, #2f3b2a 55%, #1a2418)`
      : cfg.background
  }
  let body: ReactNode
  if (cfg.layout === 'split') {
    body = (
      <div style={{ display: 'flex', height: '100%', background: 'linear-gradient(90deg, rgba(28,42,31,0.7), transparent 60%)' }}>
        <div style={{ width: W * 0.32, padding: `${SAFE_Y + 24}px 0 ${SAFE_Y + 48}px ${SAFE_X}px`, display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Clock cfg={cfg} size={56} dateSize={15} align="left" />
          {greet && <div style={{ fontSize: 18, fontWeight: 500 }}>{greet}</div>}
          <div style={{ flex: 1 }} />
          {quick.map((q) => (
            <div key={q} style={{ display: 'flex', alignItems: 'center', gap: 12, fontSize: 15 }}>
              <QuickIcon id={q} size={20} /> {QUICK_LABEL[q]}
            </div>
          ))}
        </div>
        <div style={{ flex: 1, padding: `${SAFE_Y + 12}px ${SAFE_X}px 0 0`, overflow: 'hidden' }}>
          <Sections apps={apps} cfg={cfg} width={W * 0.68 - SAFE_X} dockW={168} />
        </div>
      </div>
    )
  } else if (cfg.layout === 'rail') {
    body = (
      <div style={{ display: 'flex', height: '100%' }}>
        <div style={{ width: SAFE_X + 56, padding: `${SAFE_Y + 8}px 0 0 ${SAFE_X + 7}px`, display: 'flex', flexDirection: 'column', gap: 18, background: 'linear-gradient(90deg, rgba(28,42,31,0.6), rgba(28,42,31,0.1))' }}>
          {quick.map((q) => <QuickIcon key={q} id={q} size={22} />)}
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
  } else {
    body = (
      <div style={{ height: '100%', padding: `${SAFE_Y}px ${SAFE_X}px 0`, overflow: 'hidden', background: 'linear-gradient(rgba(28,42,31,0.6), transparent 210px)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{ fontSize: 20, fontWeight: 500, flex: 1 }}>{greet}</div>
          {quick.map((q) => (
            <span key={q} style={{ width: 40, height: 40, borderRadius: 20, background: 'rgba(255,255,255,0.08)', display: 'grid', placeItems: 'center' }}>
              <QuickIcon id={q} size={20} />
            </span>
          ))}
          <div style={{ width: 10 }} />
          <Clock cfg={cfg} size={26} dateSize={12} align="right" />
        </div>
        <div style={{ height: (H * cfg.top_space) / 100 }} />
        <Sections apps={apps} cfg={cfg} width={W - SAFE_X * 2} dockW={200} />
      </div>
    )
  }
  return (
    <div ref={box} className="home-preview" style={{ height: H * scale }} aria-label="Preview of the TV home screen">
      <div style={{ width: W, height: H, transform: `scale(${scale})`, transformOrigin: '0 0', color: '#F0EBE5', fontFamily: 'Roboto, system-ui, sans-serif', ...ground }}>{body}</div>
      <span className="home-preview-note">Sample time and greeting · app art shows on the TV</span>
    </div>
  )
}
