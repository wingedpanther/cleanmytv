import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, HomeConfig, HomeGroup, HomeLayout, HomeSection, HomeStatus, QuickId, TvApp } from '@shared/api'
import type { ScreenProps } from '../App'
import { AppOrder } from '../components/AppOrder'
import { HomePreview, QUICK_LABEL, QuickIcon } from '../components/HomePreview'
import { Busy, Confirm, ResultNotice, Slider, StatusChip, Toggle, errorText } from '../components/ui'
import { prepareWallpaper, thumbnail } from '../lib/image'
import { fetchBingDaily } from '../lib/backgrounds'

type Tab = 'overview' | 'apps' | 'layout' | 'look' | 'music'

const TABS: [Tab, string][] = [
  ['overview', 'Overview'],
  ['layout', 'Layout'],
  ['apps', 'Apps, rows & folders'],
  ['look', 'Look & wallpaper'],
  ['music', 'Music bars']
]

const ACCENTS = ['#FF3000', '#2EC4B6', '#FFB23C', '#7C5CFF', '#3DDC84', '#FFFFFF']

type SetFn = (change: Partial<HomeConfig>, wait?: number) => void

/**
 * Everything for CleanMyTV Home. Each change is sent to the TV as soon as it's made (sliders and typing
 * wait until they settle), and the launcher redraws at once: there's no Apply button.
 */
export function HomeLauncher({ markDone, refreshTvs, status }: ScreenProps): ReactNode {
  const [home, setHome] = useState<HomeStatus | null>(null)
  const [cfg, setCfg] = useState<HomeConfig | null>(null)
  const [apps, setApps] = useState<TvApp[] | null>(null)
  const [tab, setTab] = useState<Tab>('overview')
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [saving, setSaving] = useState(false)
  const [askStock, setAskStock] = useState(false)
  const pending = useRef<Partial<HomeConfig>>({})
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  async function load(): Promise<void> {
    try {
      const h = await window.cytv.homeStatus()
      setHome(h)
      if (h.installed) {
        // The launcher's own list: the apps it shows, with the names the TV uses.
        const [c, a] = await Promise.all([window.cytv.readHomeConfig(), window.cytv.homeApps()])
        setCfg(c)
        setApps(a)
      }
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  useEffect(() => {
    load()
  }, [])

  async function flush(): Promise<void> {
    const change = { ...pending.current }
    pending.current = {}
    if (!Object.keys(change).length) return
    // A row or folder being renamed can be blank for a moment; the TV only gets named ones.
    for (const k of ['rows', 'folders'] as const) if (change[k]) change[k] = change[k].filter((g) => g.name.trim())
    setSaving(true)
    try {
      const back = await window.cytv.setHomeConfig(change)
      setCfg((prev) => (prev ? { ...back, rows: prev.rows, folders: prev.folders, order: prev.order, greeting: prev.greeting } : back))
      markDone('home')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setSaving(false)
    }
  }

  // Shown here at once, sent once the control settles.
  const set: SetFn = (change, wait = 150) => {
    setCfg((prev) => (prev ? { ...prev, ...change } : prev))
    pending.current = { ...pending.current, ...change }
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(flush, wait)
  }

  async function run(label: string, fn: () => Promise<ApplyResult>): Promise<void> {
    setBusy(label)
    setResult(null)
    try {
      const r = await fn()
      setResult(r)
      if (r.ok) markDone('home')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      load()
    }
  }

  async function wallpaper(label: string, get: () => Promise<string | null>): Promise<void> {
    setBusy(label)
    setResult(null)
    try {
      const jpeg = await get()
      setCfg(await window.cytv.setHomeWallpaper(jpeg))
      markDone('home')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  const ready = !!home?.installed && !!cfg

  return (
    <>
      <h1>Home launcher</h1>
      <p className="lede">
        CleanMyTV Home is our own launcher. Changes here show on the TV straight away and stay after a reboot. It's a preview:
        if anything breaks, switch back from the Overview tab.
      </p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {!home ? (
        <Busy text="Asking the TV about CleanMyTV Home" />
      ) : (
        <>
          <div className="tabs" role="tablist">
            {TABS.map(([id, label]) => (
              <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'on' : ''} disabled={id !== 'overview' && !ready} onClick={() => setTab(id)}>
                {label}
              </button>
            ))}
          </div>

          {tab === 'overview' && (
            <Overview home={home} busy={!!busy} run={run} askStock={() => setAskStock(true)} host={status.host} onShot={refreshTvs} />
          )}
          {ready && tab === 'apps' && apps && <AppsTab apps={apps} cfg={cfg} set={set} />}
          {ready && tab === 'layout' && apps && <LayoutTab apps={apps} cfg={cfg} set={set} />}
          {ready && tab === 'look' && <LookTab cfg={cfg} set={set} busy={!!busy} wallpaper={wallpaper} />}
          {ready && tab === 'music' && <MusicTab cfg={cfg} set={set} />}
          {ready && tab !== 'overview' && <p className="small muted">{saving ? 'Sending to the TV…' : 'Saved on the TV.'}</p>}
        </>
      )}

      {askStock && (
        <Confirm
          title="Use the stock launcher again?"
          confirmLabel="Switch back"
          onConfirm={() => {
            setAskStock(false)
            run('Giving Home back to the stock launcher', () => window.cytv.useStockLauncher())
          }}
          onCancel={() => setAskStock(false)}
        >
          <p>The app switches the TV's own launcher back on and makes it Home. CleanMyTV Home stays installed, so you can switch again.</p>
        </Confirm>
      )}
    </>
  )
}

function Overview(props: { home: HomeStatus; busy: boolean; run: (label: string, fn: () => Promise<ApplyResult>) => Promise<void>; askStock: () => void; host?: string; onShot: () => void }): ReactNode {
  const { home, busy, run } = props
  const [shot, setShot] = useState<string | null>(null)
  const [shooting, setShooting] = useState(false)
  const [shotError, setShotError] = useState<string | null>(null)
  const outdated = home.installed && !!home.bundledVersion && home.versionName !== home.bundledVersion

  async function snapshot(): Promise<void> {
    setShooting(true)
    setShotError(null)
    try {
      const s = await window.cytv.screenshot()
      setShot(s)
      if (props.host) {
        await window.cytv.saveTvThumb(props.host, await thumbnail(s))
        props.onShot()
      }
    } catch (e) {
      setShot(null)
      setShotError(errorText(e))
    } finally {
      setShooting(false)
    }
  }

  return (
    <>
      <div className="card white">
        <div className="card-head">
          <h2>On this TV</h2>
          <StatusChip status={home.isHome ? 'ok' : home.installed ? 'warn' : 'optional'} text={home.isHome ? 'Is Home' : home.installed ? 'Installed, not Home' : 'Not installed'} />
        </div>
        <div className="facts">
          <div>
            <span>On the TV</span>
            <b>{home.installed ? `version ${home.versionName ?? '?'}` : 'not installed'}</b>
          </div>
          <div>
            <span>In this app</span>
            <b>{home.bundled ? `version ${home.bundledVersion ?? '?'}` : 'no bundled copy'}</b>
          </div>
        </div>
        <div className="row" style={{ marginTop: 16 }}>
          {(!home.installed || outdated) && (
            <button className="btn primary" disabled={busy || !home.bundled} onClick={() => run('Installing CleanMyTV Home. Keep the TV on.', () => window.cytv.installHome())}>
              {home.installed ? 'Update and use' : 'Install and use'}
            </button>
          )}
          {home.installed && !home.isHome && (
            <button className="btn primary" disabled={busy} onClick={() => run('Switching Home to CleanMyTV Home', () => window.cytv.useHome())}>
              Use CleanMyTV Home
            </button>
          )}
          {home.isHome && (
            <>
              <button className="btn" disabled={busy} onClick={() => run('Switching Home to CleanYourTV', () => window.cytv.useCleanYourTv())}>
                Use CleanYourTV
              </button>
              <button className="btn" disabled={busy} onClick={props.askStock}>
                Give Home back to the stock launcher
              </button>
            </>
          )}
        </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>What the TV shows</h2>
          <button className="btn small" onClick={snapshot} disabled={shooting}>
            {shooting ? 'Taking a snapshot…' : 'Take a snapshot'}
          </button>
        </div>
        {shotError && <div className="notice warn">{shotError}</div>}
        {shot ? (
          <img src={shot} alt="The TV screen" style={{ width: '100%', borderRadius: 10, border: '1px solid var(--line)' }} />
        ) : (
          <p className="muted small">A picture of the TV screen right now. It also becomes this TV's thumbnail in the sidebar.</p>
        )}
      </div>
    </>
  )
}

function AppsTab({ apps, cfg, set }: { apps: TvApp[]; cfg: HomeConfig; set: SetFn }): ReactNode {
  // Same order as the TV: the saved order first, then the rest alphabetically.
  const ordered = useMemo(() => {
    const by = new Map(apps.map((a) => [a.pkg, a]))
    const first = cfg.order.flatMap((p) => (by.has(p) ? [by.get(p)!] : []))
    const rest = apps.filter((a) => !cfg.order.includes(a.pkg)).sort((a, b) => a.label.localeCompare(b.label))
    return [...first, ...rest]
  }, [apps, cfg.order])
  const hidden = new Set(cfg.hidden)
  return (
    <>
      <div className="home-preview-sticky">
        <HomePreview apps={apps} cfg={cfg} />
      </div>
      <div className="card">
        <h2>Dock and order</h2>
        <Slider label="Apps in the dock" hint="The first apps sit in a row of large tiles. 0 for no dock." value={cfg.dock_size} min={0} max={8} onChange={(v) => set({ dock_size: v }, 300)} />
        <div className="row" style={{ margin: '6px 0 10px' }}>
          <span className="muted small">Drag to reorder, or use the arrows. Highlighted tiles are the dock. Apps in a folder show only in the folder.</span>
          <span className="spacer" />
          <button className="btn small ghost" onClick={() => set({ order: [] })}>
            Reset order
          </button>
          <button className="btn small ghost" onClick={() => set({ hidden: [] })} disabled={!cfg.hidden.length}>
            Show all
          </button>
        </div>
        <AppOrder
          apps={ordered}
          hidden={hidden}
          columns={cfg.columns}
          dockCount={cfg.dock_size}
          onOrder={(pkgs) => set({ order: pkgs }, 400)}
          onHidden={(pkg, hide) => {
            const next = new Set(hidden)
            if (hide) next.add(pkg)
            else next.delete(pkg)
            set({ hidden: [...next] })
          }}
        />
      </div>
      <Groups
        kind="rows"
        title="Rows"
        hint="Named rows of apps, like Watch or Music. An app can be in a row and the grid at the same time."
        groups={cfg.rows}
        apps={ordered}
        max={8}
        onChange={(rows) => set({ rows }, 500)}
      />
      <Groups
        kind="folders"
        title="Folders"
        hint="A folder shows as one tile; OK on the remote opens it. Apps in a folder leave the dock and grid."
        groups={cfg.folders}
        apps={ordered}
        max={12}
        onChange={(folders) => set({ folders }, 500)}
      />
    </>
  )
}

/** Named rows or folders: rename, fill with apps, reorder, remove. */
function Groups(props: { kind: 'rows' | 'folders'; title: string; hint: string; groups: HomeGroup[]; apps: TvApp[]; max: number; onChange: (g: HomeGroup[]) => void }): ReactNode {
  const { groups, apps, onChange } = props
  const label = (pkg: string): string => apps.find((a) => a.pkg === pkg)?.label ?? pkg
  const update = (i: number, g: HomeGroup): void => onChange(groups.map((x, j) => (j === i ? g : x)))
  const move = (i: number, by: number): void => {
    const next = [...groups]
    const [g] = next.splice(i, 1)
    next.splice(i + by, 0, g)
    onChange(next)
  }
  const fresh = props.kind === 'rows' ? 'New row' : 'New folder'
  return (
    <div className="card">
      <div className="card-head">
        <h2>{props.title}</h2>
        <button className="btn small" disabled={groups.length >= props.max} onClick={() => onChange([...groups, { name: `${fresh} ${groups.length + 1}`, apps: [] }])}>
          {props.kind === 'rows' ? 'Add a row' : 'Add a folder'}
        </button>
      </div>
      <p className="muted small">{props.hint}</p>
      {groups.map((g, i) => (
        <div key={i} className="group-card">
          <div className="row">
            <input type="text" maxLength={30} value={g.name} aria-label={`${props.kind === 'rows' ? 'Row' : 'Folder'} name`} onChange={(e) => update(i, { ...g, name: e.target.value })} style={{ flex: 1 }} />
            <button className="btn small ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Move up">
              ↑
            </button>
            <button className="btn small ghost" disabled={i === groups.length - 1} onClick={() => move(i, 1)} aria-label="Move down">
              ↓
            </button>
            <button className="btn small ghost" onClick={() => onChange(groups.filter((_, j) => j !== i))}>
              Remove
            </button>
          </div>
          <div className="chips">
            {g.apps.length === 0 && <span className="muted small">No apps yet: it stays hidden on the TV until you add some.</span>}
            {g.apps.map((p) => (
              <span key={p} className="chip-app">
                {label(p)}
                <button aria-label={`Take ${label(p)} out`} onClick={() => update(i, { ...g, apps: g.apps.filter((x) => x !== p) })}>
                  ×
                </button>
              </span>
            ))}
          </div>
          <select
            value=""
            aria-label="Add an app"
            onChange={(e) => e.target.value && update(i, { ...g, apps: [...g.apps, e.target.value] })}
          >
            <option value="">Add an app…</option>
            {apps.filter((a) => !g.apps.includes(a.pkg)).map((a) => (
              <option key={a.pkg} value={a.pkg}>
                {a.label}
              </option>
            ))}
          </select>
        </div>
      ))}
    </div>
  )
}

const LAYOUTS: { id: HomeLayout; label: string; hint: string; mini: [number, number, number, number, boolean][] }[] = [
  { id: 'shelf', label: 'Shelf', hint: 'Top bar, open space, apps lower down.', mini: [[6, 6, 30, 6, true], [64, 6, 30, 6, true], [6, 40, 88, 24, false], [6, 72, 88, 18, true]] },
  { id: 'split', label: 'Split', hint: 'Clock and quick settings on the left, apps on the right.', mini: [[6, 10, 26, 18, true], [6, 62, 20, 24, true], [38, 8, 56, 26, false], [38, 42, 56, 18, true]] },
  { id: 'rail', label: 'Rail', hint: 'Icons down the left edge that open with labels.', mini: [[3, 6, 6, 84, true], [70, 6, 24, 8, true], [16, 24, 78, 28, false], [16, 60, 78, 18, true]] }
]

const SECTION_LABEL: Record<HomeSection, string> = { dock: 'Dock', rows: 'Rows', folders: 'Folders', grid: 'All apps' }
const ALL_SECTIONS: HomeSection[] = ['dock', 'rows', 'folders', 'grid']
const ALL_QUICK: QuickId[] = ['inputs', 'wifi', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'settings']

/** Turn items on and off and put the ones that are on in order. */
function OrderList<T extends string>(props: { all: T[]; on: T[]; label: (t: T) => ReactNode; onChange: (on: T[]) => void }): ReactNode {
  const { on, onChange } = props
  const items = [...on, ...props.all.filter((t) => !on.includes(t))]
  const move = (t: T, by: number): void => {
    const i = on.indexOf(t)
    const next = [...on]
    next.splice(i, 1)
    next.splice(i + by, 0, t)
    onChange(next)
  }
  return (
    <ul className="order-list">
      {items.map((t) => {
        const i = on.indexOf(t)
        return (
          <li key={t} className={i < 0 ? 'off' : ''}>
            <label className="grow">
              <input type="checkbox" checked={i >= 0} onChange={(e) => onChange(e.target.checked ? [...on, t] : on.filter((x) => x !== t))} />
              {props.label(t)}
            </label>
            <button className="btn small ghost" disabled={i <= 0} onClick={() => move(t, -1)} aria-label="Earlier">
              ↑
            </button>
            <button className="btn small ghost" disabled={i < 0 || i === on.length - 1} onClick={() => move(t, 1)} aria-label="Later">
              ↓
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function LayoutTab({ apps, cfg, set }: { apps: TvApp[]; cfg: HomeConfig; set: SetFn }): ReactNode {
  return (
    <>
      <div className="home-preview-sticky">
        <HomePreview apps={apps} cfg={cfg} />
      </div>
      <div className="card">
        <h2>Layout</h2>
        <div className="layout-pick">
          {LAYOUTS.map((l) => (
            <button key={l.id} className={cfg.layout === l.id ? 'on' : ''} onClick={() => set({ layout: l.id })} aria-pressed={cfg.layout === l.id}>
              <span className="mini" aria-hidden>
                {l.mini.map(([x, y, w, h, dim], i) => (
                  <i key={i} className={dim ? 'dim' : ''} style={{ left: `${x}%`, top: `${y}%`, width: `${w}%`, height: `${h}%` }} />
                ))}
              </span>
              <b>{l.label}</b>
              <span className="muted small">{l.hint}</span>
            </button>
          ))}
        </div>
        {cfg.layout === 'shelf' && (
          <div style={{ marginTop: 14 }}>
            <Slider label="Space above the apps" hint="Open wallpaper between the top bar and the first row." value={cfg.top_space} min={0} max={40} unit="%" onChange={(v) => set({ top_space: v }, 250)} />
          </div>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Sections</h2>
          <p className="muted small">Top to bottom. Untick one to hide it.</p>
          <OrderList all={ALL_SECTIONS} on={cfg.sections} label={(s) => SECTION_LABEL[s]} onChange={(sections) => set({ sections })} />
        </div>
        <div className="card">
          <h2>Quick settings</h2>
          <p className="muted small">{cfg.layout === 'rail' ? 'Icons in the rail.' : cfg.layout === 'split' ? 'Under the clock.' : 'Next to the clock.'} Inputs only shows on TVs with HDMI inputs.</p>
          <OrderList
            all={ALL_QUICK}
            on={cfg.quick}
            label={(q) => (
              <>
                <QuickIcon id={q} /> {QUICK_LABEL[q]}
              </>
            )}
            onChange={(quick) => set({ quick })}
          />
        </div>
      </div>

      <div className="card">
        <h2>Sizes</h2>
        <Slider label="Dock tiles" value={cfg.dock_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ dock_scale: v }, 250)} />
        <Slider label="Row and folder tiles" value={cfg.tile_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ tile_scale: v }, 250)} />
        <Slider label="Apps per row in All apps" value={cfg.columns} min={3} max={8} onChange={(v) => set({ columns: v }, 250)} />
        <Toggle label="Show app names" hint="Under each tile. Apps without a TV banner always show their name." checked={cfg.show_names} onChange={(v) => set({ show_names: v })} />
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Clock</h2>
          <div className="seg">
            {([['time_date', 'Time and date'], ['time', 'Time only'], ['off', 'Off']] as const).map(([id, label]) => (
              <button key={id} className={cfg.clock === id ? 'on' : ''} onClick={() => set({ clock: id })}>
                {label}
              </button>
            ))}
          </div>
        </div>
        <div className="card">
          <h2>Greeting</h2>
          <label className="field">
            <span>Your name</span>
            <span className="hint">Shows "Good evening, name". Leave it empty for no greeting.</span>
            <input type="text" maxLength={40} value={cfg.greeting} placeholder="e.g. Sam" onChange={(e) => set({ greeting: e.target.value }, 700)} />
          </label>
        </div>
      </div>
    </>
  )
}

function Colour({ label, hint, value, onChange }: { label: string; hint?: string; value: string; onChange: (c: string, wait?: number) => void }): ReactNode {
  return (
    <label className="field">
      <span>{label}</span>
      {hint && <span className="hint">{hint}</span>}
      <div className="row">
        <input type="color" value={value} onChange={(e) => onChange(e.target.value.toUpperCase(), 400)} />
        <input type="text" value={value} style={{ width: 110 }} onChange={(e) => /^#[0-9a-fA-F]{6}$/.test(e.target.value) && onChange(e.target.value.toUpperCase())} />
      </div>
    </label>
  )
}

function LookTab(props: { cfg: HomeConfig; set: SetFn; busy: boolean; wallpaper: (label: string, get: () => Promise<string | null>) => Promise<void> }): ReactNode {
  const { cfg, set, busy, wallpaper } = props
  const file = useRef<HTMLInputElement>(null)
  return (
    <>
      <div className="card">
        <h2>Accent colour</h2>
        <p className="muted">For the focus glow or outline and the music bars.</p>
        <div className="row">
          {ACCENTS.map((c) => (
            <button
              key={c}
              aria-label={c}
              title={c}
              onClick={() => set({ accent: c })}
              style={{ background: c, width: 32, height: 32, borderRadius: 8, cursor: 'pointer', border: cfg.accent.toUpperCase() === c ? '3px solid var(--navy)' : '1px solid var(--line)' }}
            />
          ))}
          <input type="color" value={cfg.accent} onChange={(e) => set({ accent: e.target.value.toUpperCase() }, 400)} aria-label="Pick any colour" />
        </div>
      </div>

      <div className="card">
        <h2>Focus</h2>
        <p className="muted">How the tile under the remote's cursor stands out.</p>
        <div className="seg">
          {([['glow', 'Glow'], ['outline', 'Outline'], ['none', 'None']] as const).map(([id, label]) => (
            <button key={id} className={cfg.focus === id ? 'on' : ''} onClick={() => set({ focus: id })}>
              {label}
            </button>
          ))}
        </div>
      </div>

      <div className="card">
        <h2>Wallpaper</h2>
        <p className="muted">{cfg.has_wallpaper ? 'A wallpaper is set.' : 'No wallpaper: the background colour shows.'}</p>
        <input
          ref={file}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = ''
            if (f) wallpaper('Preparing and sending the picture', async () => (await prepareWallpaper(f, 'wallpaper.jpg')).base64)
          }}
        />
        <div className="row" style={{ marginBottom: 12 }}>
          <button className="btn" disabled={busy} onClick={() => file.current?.click()}>
            Choose a picture…
          </button>
          <button className="btn" disabled={busy} onClick={() => wallpaper("Fetching today's Bing picture", async () => (await fetchBingDaily()).base64)}>
            Bing picture of the day
          </button>
          {cfg.has_wallpaper && (
            <button className="btn ghost" disabled={busy} onClick={() => wallpaper('Removing the wallpaper', async () => null)}>
              Remove
            </button>
          )}
        </div>
        <Slider label="Darken the wallpaper" hint="Keeps names and the clock readable on bright pictures." value={cfg.wallpaper_dim} min={0} max={80} unit="%" onChange={(v) => set({ wallpaper_dim: v }, 300)} />
        <Colour label="Background colour" hint="Behind everything when there's no wallpaper. Keep it dark: text is white." value={cfg.background} onChange={(c, w) => set({ background: c }, w)} />
      </div>
    </>
  )
}

function MusicTab({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  return (
    <>
      <div className="card">
        <Toggle
          label="Music bars"
          hint="Bars along the bottom of the home screen that move with whatever the TV is playing. They hide when it's quiet."
          checked={cfg.visualiser}
          onChange={(v) => set({ visualiser: v })}
        />
        <BarsPreview cfg={cfg} />
      </div>
      {cfg.visualiser && (
        <>
          <div className="card">
            <h2>Style</h2>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['bars', 'Bars'], ['mirror', 'Mirrored'], ['wave', 'Wave']] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_style === id ? 'on' : ''} onClick={() => set({ viz_style: id })}>
                  {label}
                </button>
              ))}
            </div>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['accent', 'Accent colour'], ['rainbow', 'Rainbow'], ['white', 'White']] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_colour === id ? 'on' : ''} onClick={() => set({ viz_colour: id })}>
                  {label}
                </button>
              ))}
            </div>
            {cfg.viz_style !== 'wave' && <Toggle label="Peak dots" hint="A small mark above each bar that falls slowly." checked={cfg.viz_peaks} onChange={(v) => set({ viz_peaks: v })} />}
          </div>
          <div className="card">
            <h2>Size and feel</h2>
            <Slider label="Bars" value={cfg.viz_bars} min={8} max={64} step={4} onChange={(v) => set({ viz_bars: v }, 300)} />
            <Slider label="Height" hint="How much of the screen they can reach." value={cfg.viz_height} min={4} max={40} unit="%" onChange={(v) => set({ viz_height: v }, 300)} />
            <Slider label="Opacity" value={cfg.viz_opacity} min={20} max={100} unit="%" onChange={(v) => set({ viz_opacity: v }, 300)} />
            <Slider label="Sensitivity" hint="Higher makes quiet music move more." value={cfg.viz_sensitivity} min={25} max={300} step={5} unit="%" onChange={(v) => set({ viz_sensitivity: v }, 300)} />
          </div>
        </>
      )}
    </>
  )
}

/** A rough picture of the chosen bars, moving to a made-up beat. */
function BarsPreview({ cfg }: { cfg: HomeConfig }): ReactNode {
  const [t, setT] = useState(0)
  useEffect(() => {
    let id = 0
    const tick = (now: number): void => {
      setT(now / 1000)
      id = requestAnimationFrame(tick)
    }
    id = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(id)
  }, [])
  if (!cfg.visualiser) return null
  const n = cfg.viz_bars
  const W = 640
  const H = Math.max(24, (360 * cfg.viz_height) / 100)
  const levels = Array.from({ length: n }, (_, i) => {
    const x = i / n
    const beat = 0.55 + 0.45 * Math.abs(Math.sin(t * 2.2))
    return Math.min(1, (0.25 + 0.6 * Math.abs(Math.sin(i * 1.7 + t * 3.1)) * (1 - x * 0.35)) * beat)
  })
  const colour = (i: number): string => (cfg.viz_colour === 'rainbow' ? `hsl(${(300 * i) / Math.max(1, n - 1)} 75% 60%)` : cfg.viz_colour === 'white' ? '#FFFFFF' : cfg.accent)
  const op = cfg.viz_opacity / 100
  const gap = (W / n) * 0.22
  const w = (W - gap * (n + 1)) / n
  let body: ReactNode
  if (cfg.viz_style === 'wave') {
    const pts = levels.map((v, i) => `${(i * W) / (n - 1)},${H - v * H * 0.9}`)
    body = <polyline points={pts.join(' ')} fill="none" stroke={colour(0)} strokeOpacity={op} strokeWidth={3} strokeLinejoin="round" />
  } else {
    const mirror = cfg.viz_style === 'mirror'
    const base = mirror ? H / 2 : H
    const span = mirror ? H / 2 : H
    body = levels.map((v, i) => (
      <g key={i}>
        <rect x={gap + i * (w + gap)} y={base - v * span} width={w} height={v * span} rx={Math.min(w / 3, 4)} fill={colour(i)} fillOpacity={op} />
        {mirror && <rect x={gap + i * (w + gap)} y={base} width={w} height={v * span} rx={Math.min(w / 3, 4)} fill={colour(i)} fillOpacity={op * 0.3} />}
        {cfg.viz_peaks && <rect x={gap + i * (w + gap)} y={base - Math.min(1, v + 0.08) * span - 4} width={w} height={3} rx={1.5} fill={colour(i)} fillOpacity={op} />}
      </g>
    ))
  }
  return (
    <div style={{ background: cfg.background, borderRadius: 10, padding: '40px 0 0', marginTop: 12, overflow: 'hidden' }} aria-label="Preview of the music bars">
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ display: 'block' }}>
        {body}
      </svg>
    </div>
  )
}
