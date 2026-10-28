import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, LauncherConfig, ThemeApplyRequest, ThemePreset, TvApp, TvTheme } from '@shared/api'
import type { ScreenProps } from '../App'
import { AppOrder } from '../components/AppOrder'
import { TvPreview } from '../components/TvPreview'
import { Busy, Confirm, Modal, ResultNotice, Slider, Toggle, errorText } from '../components/ui'
import { BUILTIN, orderedApps, val } from '../lib/apps'
import { dataUrlToImage, prepareWallpaper, uniqueNames, type PreparedImage } from '../lib/image'
import { fetchBingDaily, tmdbBackdropPaths, fetchTmdbBackdrop } from '../lib/backgrounds'
import { HomeCustomise } from './HomeCustomise'

type Tab = 'apps' | 'layout' | 'backdrop' | 'greeting' | 'motion' | 'advanced'
type WallChange = { kind: 'keep' } | { kind: 'remove' } | { kind: 'set'; image: PreparedImage }
type RotationChange = { kind: 'keep' } | { kind: 'set'; images: PreparedImage[] }

const TABS: [Tab, string][] = [
  ['apps', 'Apps & order'],
  ['layout', 'Layout'],
  ['backdrop', 'Backdrop'],
  ['greeting', 'Greeting'],
  ['motion', 'Motion'],
  ['advanced', 'Advanced']
]

/** CleanMyTV Home gets its own page; CleanYourTV (or no launcher yet) gets the full editor. */
export function Customise(props: ScreenProps): ReactNode {
  const [which, setWhich] = useState<'checking' | 'home' | 'classic'>('checking')
  useEffect(() => {
    window.cytv.homeStatus().then((h) => setWhich(h.isHome ? 'home' : 'classic'), () => setWhich('classic'))
  }, [])
  if (which === 'checking')
    return (
      <div className="content">
        <h1>Customise</h1>
        <Busy text="Asking the TV which launcher is Home" />
      </div>
    )
  if (which === 'home') return <HomeCustomise onDone={() => props.markDone('customise')} />
  return <ClassicCustomise {...props} />
}

function ClassicCustomise({ markDone }: ScreenProps): ReactNode {
  const [apps, setApps] = useState<TvApp[] | null>(null)
  const [loaded, setLoaded] = useState<TvTheme | null>(null)
  const [cfg, setCfg] = useState<LauncherConfig>({})
  const [wall, setWall] = useState<WallChange>({ kind: 'keep' })
  const [rotation, setRotation] = useState<RotationChange>({ kind: 'keep' })
  const [presets, setPresets] = useState<ThemePreset[]>([])
  const [tab, setTab] = useState<Tab>('apps')
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)
  const [shot, setShot] = useState<string | null>(null)

  async function load(): Promise<void> {
    setLoadError(null)
    try {
      const [a, t] = await Promise.all([window.cytv.apps(), window.cytv.readTheme()])
      setApps(a)
      setLoaded(t)
      setCfg(structuredClone(t.config))
      setWall({ kind: 'keep' })
      setRotation({ kind: 'keep' })
    } catch (e) {
      setLoadError(errorText(e))
    }
  }

  useEffect(() => {
    load()
    window.cytv.presets().then(setPresets, () => setPresets([]))
  }, [])

  const set = <K extends keyof LauncherConfig>(key: K, value: LauncherConfig[K] | undefined): void =>
    setCfg((prev) => {
      const next = { ...prev }
      if (value === undefined) delete next[key]
      else next[key] = value
      return next
    })

  const dirty =
    !!loaded && (JSON.stringify(sortKeys(cfg)) !== JSON.stringify(sortKeys(loaded.config)) || wall.kind !== 'keep' || rotation.kind !== 'keep')

  const wallpaperUrl = wall.kind === 'set' ? wall.image.dataUrl : wall.kind === 'remove' ? null : (loaded?.wallpaper ?? null)
  const firstRotation = rotation.kind === 'set' ? rotation.images[0]?.dataUrl : undefined
  const previewWall = wallpaperUrl ?? (val(cfg, 'wallpaper_rotation_minutes') > 0 ? (firstRotation ?? null) : null)

  async function apply(): Promise<void> {
    setBusy('Pushing the theme and restarting Home on the TV')
    setResult(null)
    const req: ThemeApplyRequest = {
      config: cfg,
      wallpaper: wall.kind === 'set' ? { kind: 'set', image: { name: 'wallpaper.jpg', base64: wall.image.base64 } } : wall,
      wallpapers: rotation.kind === 'set' ? { kind: 'set', images: rotation.images.map(({ name, base64 }) => ({ name, base64 })) } : rotation
    }
    try {
      const r = await window.cytv.applyTheme(req)
      setResult(r)
      if (r.ok) {
        markDone('customise')
        await load()
      }
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  async function reset(): Promise<void> {
    setConfirmReset(false)
    setBusy('Removing the theme from the TV')
    try {
      setResult(await window.cytv.resetTheme())
      await load()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  async function screenshot(): Promise<void> {
    setBusy('Taking a screenshot on the TV')
    try {
      setShot(await window.cytv.screenshot())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  if (loadError)
    return (
      <div className="content">
        <h1>Customise</h1>
        <div className="notice error">{loadError}</div>
        <button className="btn" onClick={load}>
          Try again
        </button>
      </div>
    )
  if (!apps || !loaded)
    return (
      <div className="content">
        <h1>Customise</h1>
        <Busy text="Reading the apps and the current theme from the TV" />
      </div>
    )

  return (
    <>
      <div className="content with-footer">
        <h1>Customise</h1>
        <p className="lede">
          Changes show in the preview straight away and reach the TV when you press Apply. No rebuild: the launcher reads them
          the next time Home starts. A setting you never touch keeps the launcher's built-in value.
        </p>
        <ResultNotice result={result} onDismiss={() => setResult(null)} />

        <div className="custom">
          <div>
            <div className="tabs" role="tablist">
              {TABS.map(([id, label]) => (
                <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'on' : ''} onClick={() => setTab(id)}>
                  {label}
                </button>
              ))}
            </div>
            {tab === 'apps' && <AppsTab apps={apps} cfg={cfg} set={set} />}
            {tab === 'layout' && <LayoutTab cfg={cfg} set={set} />}
            {tab === 'backdrop' && (
              <BackdropTab
                cfg={cfg}
                set={set}
                setCfg={setCfg}
                wallpaperUrl={wallpaperUrl}
                wall={wall}
                setWall={setWall}
                rotation={rotation}
                setRotation={setRotation}
                loadedRotation={loaded.wallpapers}
                presets={presets}
              />
            )}
            {tab === 'greeting' && <GreetingTab cfg={cfg} set={set} />}
            {tab === 'motion' && <MotionTab cfg={cfg} set={set} />}
            {tab === 'advanced' && <AdvancedTab apps={apps} cfg={cfg} set={set} />}
          </div>
          <div className="custom-preview">
            <TvPreview apps={apps} cfg={cfg} wallpaper={previewWall} />
            <p className="preview-caption">
              A sketch of Home with your settings. Tiles show initials because the app can't read app artwork from the TV. Click a
              dock tile to focus it. For the real thing, take a screenshot.
            </p>
          </div>
        </div>
      </div>

      <div className="sticky-foot">
        <button className="btn primary" onClick={apply} disabled={!dirty || !!busy}>
          Apply to TV
        </button>
        <button className="btn" onClick={load} disabled={!dirty || !!busy}>
          Undo changes
        </button>
        <button className="btn ghost" onClick={() => setConfirmReset(true)} disabled={!!busy || (!loaded.present && !dirty)}>
          Reset to built-in look
        </button>
        <button className="btn ghost" onClick={screenshot} disabled={!!busy}>
          Screenshot from TV
        </button>
        <span className="spacer" />
        {busy ? (
          <Busy text={busy} />
        ) : dirty ? (
          <span className="small">
            <span className="dirty-dot" />
            Not on the TV yet. Applying also clears column and motion choices made on the TV.
          </span>
        ) : (
          <span className="small muted">{loaded.present ? 'The TV has these settings.' : 'The TV uses the built-in look.'}</span>
        )}
      </div>

      {confirmReset && (
        <Confirm title="Reset to the built-in look?" confirmLabel="Reset" onConfirm={reset} onCancel={() => setConfirmReset(false)}>
          <p>The theme file and wallpapers come off the TV, and Home restarts with the launcher's built-in dock and colours.</p>
          <p className="muted small">Apps added or removed through the App library on the TV stay as they are.</p>
        </Confirm>
      )}
      {shot && (
        <Modal wide onClose={() => setShot(null)}>
          <h2>The TV right now</h2>
          <img className="shot" src={shot} alt="Screenshot of the TV" />
          <p className="muted small" style={{ marginTop: 10 }}>
            A black picture usually means Live TV, an HDMI input or a streaming app was on screen. Press Home and try again.
          </p>
          <div className="modal-actions">
            <button className="btn" onClick={screenshot}>
              Take another
            </button>
            <button className="btn primary" onClick={() => setShot(null)}>
              Close
            </button>
          </div>
        </Modal>
      )}
    </>
  )
}

type SetFn = <K extends keyof LauncherConfig>(key: K, value: LauncherConfig[K] | undefined) => void

function AppsTab({ apps, cfg, set }: { apps: TvApp[]; cfg: LauncherConfig; set: SetFn }): ReactNode {
  const ordered = orderedApps(apps, cfg)
  const hidden = new Set(val(cfg, 'hidden_apps'))
  const columns = val(cfg, 'columns')
  // Non-TV (phone) apps never reach the Home grid — the launcher only lists
  // them in the App library. Surfacing them by name here answers "where did
  // my sideloaded app go" in one glance instead of a bare count (#3).
  const phoneAppList = apps.filter((a) => !a.tv).sort((a, b) => a.label.localeCompare(b.label))
  const phoneApps = phoneAppList.length
  const missingDock = (cfg.dock_apps ?? []).filter((p) => !apps.some((a) => a.pkg === p))

  return (
    <div>
      <p className="muted">
        Drag tiles into the order you want, or use the arrows. The first {columns} visible apps make the dock. The rest follow
        in the grid in this order.
      </p>
      <div className="dock-legend">
        <span>
          <span className="swatch" />
          Dock
        </span>
        <span>
          <span className="swatch" style={{ background: 'var(--white)', borderColor: 'var(--line)' }} />
          Grid
        </span>
        <span className="spacer" />
        <button className="btn small ghost" onClick={() => set('dock_apps', undefined)} disabled={!cfg.dock_apps}>
          Reset order
        </button>
        <button className="btn small ghost" onClick={() => set('hidden_apps', undefined)} disabled={!cfg.hidden_apps}>
          Show all
        </button>
      </div>
      <AppOrder
        apps={ordered}
        hidden={hidden}
        columns={columns}
        onOrder={(pkgs) => set('dock_apps', pkgs)}
        onHidden={(pkg, hide) => {
          const next = new Set(hidden)
          if (hide) next.add(pkg)
          else next.delete(pkg)
          set('hidden_apps', [...next])
        }}
      />
      <div className="notice" style={{ marginTop: 16 }}>
        If you reordered tiles on the TV itself, Apply replaces that order with this one.
        {missingDock.length > 0 && ` ${missingDock.length} apps in the saved order aren't installed and are left out.`}
      </div>

      {phoneApps > 0 && (
        <div style={{ marginTop: 16 }}>
          <h3>Phone apps on this TV ({phoneApps})</h3>
          <p className="muted small">
            These have no TV interface, so the launcher keeps them off the Home grid — they only appear in the App library
            (Layout tab). Nothing to fix here; this list is just so you know they are still installed and reachable.
          </p>
          <div className="thumbs" style={{ flexWrap: 'wrap', gap: 6 }}>
            {phoneAppList.map((a) => (
              <span key={a.pkg} className="chip optional" style={{ textTransform: 'none' }}>
                {a.label}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

function LayoutTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  return (
    <div className="stack" style={{ gap: 18 }}>
      <Slider
        label="Apps per row"
        hint="The dock has this many slots too. Tiles shrink to fit from 5 up."
        value={val(cfg, 'columns')}
        min={4}
        max={8}
        onChange={(v) => set('columns', v)}
      />
      <Slider
        label="Grid rows under the dock"
        hint="0 parks the dock at the bottom of the screen."
        value={val(cfg, 'rows_below_dock')}
        min={0}
        max={3}
        onChange={(v) => set('rows_below_dock', v)}
      />
      <div>
        <Toggle label="Dock as its own row" hint="A lighter band with no names under the tiles. Off makes one plain grid." checked={val(cfg, 'dock_row')} onChange={(v) => set('dock_row', v)} />
        <Toggle label="App name above the dock" hint="The focused app's name in large type. Off keeps only the glow and leaves room for bigger tiles." checked={val(cfg, 'dock_title')} onChange={(v) => set('dock_title', v)} />
        <Toggle label="Fill empty dock slots" hint="With fewer dock apps than slots, the first grid apps move up. Off leaves the slots empty." checked={val(cfg, 'fill_dock')} onChange={(v) => set('fill_dock', v)} />
        <Toggle label="App library" hint="A pill after the last row lists every installed app, so you can add or remove Home shortcuts with the remote." checked={val(cfg, 'app_library')} onChange={(v) => set('app_library', v)} />
        <Toggle label="Reorder button in the top bar" hint="Reordering is also in the Settings button's long-press menu." checked={val(cfg, 'reorder_button')} onChange={(v) => set('reorder_button', v)} />
        <Toggle label="CPU, memory and storage under the clock" hint="Refreshes only while Home is on screen." checked={val(cfg, 'show_stats')} onChange={(v) => set('show_stats', v)} />
        <Toggle label="Default-home tile" hint="A tile that opens Android's home picker, on TVs that have one." checked={val(cfg, 'show_home_settings_shortcut')} onChange={(v) => set('show_home_settings_shortcut', v)} />
      </div>
    </div>
  )
}

function BackdropTab(props: {
  cfg: LauncherConfig
  set: SetFn
  setCfg: (fn: (c: LauncherConfig) => LauncherConfig) => void
  wallpaperUrl: string | null
  wall: WallChange
  setWall: (w: WallChange) => void
  rotation: RotationChange
  setRotation: (r: RotationChange) => void
  loadedRotation: string[]
  presets: ThemePreset[]
}): ReactNode {
  const { cfg, set } = props
  const single = useRef<HTMLInputElement>(null)
  const many = useRef<HTMLInputElement>(null)
  const [working, setWorking] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const rotationMinutes = val(cfg, 'wallpaper_rotation_minutes')
  const [tmdbKey, setTmdbKey] = useState('')
  const [tmdbList, setTmdbList] = useState('')

  /** Appends prepared images to the rotation set (issue #5 sources funnel in here). */
  function addToRotation(images: PreparedImage[]): void {
    const out = props.rotation.kind === 'set' ? [...props.rotation.images, ...images] : images
    props.setRotation({ kind: 'set', images: uniqueNames(out).slice(0, 100) })
    set('wallpaper', true)
    if (!rotationMinutes) set('wallpaper_rotation_minutes', 30)
  }

  async function addBing(): Promise<void> {
    setErr(null)
    setWorking('Fetching the Bing picture of the day')
    try {
      addToRotation([await fetchBingDaily()])
    } catch (e) {
      setErr(`The Bing picture could not be fetched: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function addTmdb(): Promise<void> {
    const key = tmdbKey.trim()
    const list = Number(tmdbList.trim())
    setErr(null)
    if (!key || !Number.isInteger(list) || list <= 0) {
      setErr('Enter your TMDB API key and a list number (digits only, from themoviedb.org/list/…).')
      return
    }
    setWorking('Fetching movie backdrops from TMDB')
    try {
      const paths = (await tmdbBackdropPaths(list, key)).slice(0, 20)
      if (!paths.length) {
        setErr('That TMDB list has no backdrops to use.')
        return
      }
      const images: PreparedImage[] = []
      for (let i = 0; i < paths.length; i++) {
        setWorking(`Fetching backdrop ${i + 1} of ${paths.length}`)
        images.push(await fetchTmdbBackdrop(paths[i]))
      }
      addToRotation(images)
    } catch (e) {
      setErr(`TMDB could not be used: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function pickSingle(files: FileList | null): Promise<void> {
    if (!files?.[0]) return
    setErr(null)
    setWorking('Sizing the picture for the TV')
    try {
      const img = await prepareWallpaper(files[0], 'wallpaper.jpg')
      props.setWall({ kind: 'set', image: img })
      set('wallpaper', true)
    } catch (e) {
      setErr(`That picture could not be read: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  async function pickMany(files: FileList | null): Promise<void> {
    if (!files?.length) return
    setErr(null)
    const list = [...files].slice(0, 100)
    const out: PreparedImage[] = props.rotation.kind === 'set' ? [...props.rotation.images] : []
    try {
      for (let i = 0; i < list.length; i++) {
        setWorking(`Sizing picture ${i + 1} of ${list.length}`)
        out.push(await prepareWallpaper(list[i]))
      }
      props.setRotation({ kind: 'set', images: uniqueNames(out).slice(0, 100) })
      set('wallpaper', true)
      if (!rotationMinutes) set('wallpaper_rotation_minutes', 30)
    } catch (e) {
      setErr(`One of the pictures could not be read: ${errorText(e)}`)
    } finally {
      setWorking(null)
    }
  }

  function applyPreset(p: ThemePreset): void {
    // Presets set the look; the app order and hidden apps you chose stay.
    const { dock_apps: _d, hidden_apps: _h, ...look } = p.config
    props.setCfg((c) => ({ ...c, ...look }))
    if (p.wallpaper) props.setWall({ kind: 'set', image: dataUrlToImage(p.wallpaper, 'wallpaper.jpg') })
  }

  const rotationNames = props.rotation.kind === 'set' ? props.rotation.images.map((i) => i.name) : props.loadedRotation

  return (
    <div className="stack" style={{ gap: 20 }}>
      {props.presets.length > 0 && (
        <div>
          <h3>Start from a theme</h3>
          <div className="presets">
            {props.presets.map((p) => (
              <button key={p.id} className="preset" onClick={() => applyPreset(p)}>
                <div
                  className="preset-thumb"
                  style={{ background: p.wallpaper ? `center / cover url(${p.wallpaper})` : p.config.background_color ?? '#000' }}
                />
                <b>{p.name}</b>
                <span>{p.description}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field">
          Background colour
          <span className="hint">Behind everything. Keep it dark: the clock and names are white.</span>
          <div className="row">
            <input type="color" value={normHex(val(cfg, 'background_color'))} onChange={(e) => set('background_color', e.target.value.toUpperCase())} />
            <input type="text" value={val(cfg, 'background_color')} onChange={(e) => /^#[0-9a-fA-F]{6}$/.test(e.target.value) && set('background_color', e.target.value)} style={{ width: 110 }} />
          </div>
        </label>
      </div>

      <div>
        <h3>Wallpaper</h3>
        <p className="muted small">
          Any picture works. The app crops it to 1920×1080 and shrinks it under 300 KB, because a 1 GB TV decodes it on every
          Home start.
        </p>
        <Toggle label="Show a wallpaper" checked={val(cfg, 'wallpaper')} onChange={(v) => set('wallpaper', v)} />
        <div className="row" style={{ marginTop: 8 }}>
          {props.wallpaperUrl && (
            <div className="thumb">
              <img src={props.wallpaperUrl} alt="Current wallpaper" />
              <span>
                {props.wall.kind === 'set' ? `New, ${Math.round(props.wall.image.bytes / 1024)} KB` : 'On the TV'}
              </span>
            </div>
          )}
          <input ref={single} type="file" accept="image/*" hidden onChange={(e) => pickSingle(e.target.files).then(() => (e.target.value = ''))} />
          <button className="btn small" onClick={() => single.current?.click()} disabled={!!working}>
            {props.wallpaperUrl ? 'Choose another picture' : 'Choose a picture'}
          </button>
          {props.wallpaperUrl && (
            <button className="btn small ghost" onClick={() => props.setWall({ kind: 'remove' })}>
              Remove
            </button>
          )}
          {props.wall.kind !== 'keep' && (
            <button className="btn small ghost" onClick={() => props.setWall({ kind: 'keep' })}>
              Keep the TV's picture
            </button>
          )}
        </div>
        {working && <div style={{ marginTop: 8 }}><Busy text={working} /></div>}
        {err && <div className="notice error" style={{ marginTop: 8 }}>{err}</div>}
      </div>

      <Slider label="Darken the wallpaper" hint="A black layer over the picture, so text stays readable." value={val(cfg, 'wallpaper_dim')} min={0} max={90} unit="%" onChange={(v) => set('wallpaper_dim', v)} />

      <div>
        <h3>Rotating wallpapers</h3>
        <p className="muted small">
          Add several pictures and the launcher picks a new one at random every few minutes, only while Home is on screen. They
          replace the set already on the TV.
        </p>
        {rotationNames.length > 0 && (
          <div className="thumbs" style={{ marginBottom: 10 }}>
            {props.rotation.kind === 'set'
              ? props.rotation.images.map((img, i) => (
                  <div key={img.name} className="thumb">
                    <img src={img.dataUrl} alt={img.name} />
                    <span>{img.name}</span>
                    <button
                      className="btn small"
                      style={{ padding: '0 6px' }}
                      aria-label={`Remove ${img.name}`}
                      onClick={() => props.rotation.kind === 'set' && props.setRotation({ kind: 'set', images: props.rotation.images.filter((_, j) => j !== i) })}
                    >
                      ✕
                    </button>
                  </div>
                ))
              : rotationNames.map((n) => (
                  <span key={n} className="chip optional" style={{ textTransform: 'none' }}>
                    {n}
                  </span>
                ))}
          </div>
        )}
        <div className="row">
          <input ref={many} type="file" accept="image/*" multiple hidden onChange={(e) => pickMany(e.target.files).then(() => (e.target.value = ''))} />
          <button className="btn small" onClick={() => many.current?.click()} disabled={!!working}>
            Add pictures
          </button>
          {rotationNames.length > 0 && (
            <button className="btn small ghost" onClick={() => props.setRotation({ kind: 'set', images: [] })}>
              Remove all
            </button>
          )}
        </div>
        <label className="field" style={{ marginTop: 12, maxWidth: 260 }}>
          Change picture every
          <select value={rotationMinutes} onChange={(e) => set('wallpaper_rotation_minutes', Number(e.target.value))}>
            {[...new Set([0, 5, 15, 30, 60, 120, 360, 720, 1440, rotationMinutes])]
              .sort((a, b) => a - b)
              .map((m) => (
                <option key={m} value={m}>
                  {m === 0 ? 'Never (single wallpaper)' : m < 60 ? `${m} minutes` : m === 60 ? '1 hour' : `${m / 60} hours`}
                </option>
              ))}
          </select>
        </label>

        <div style={{ marginTop: 16 }}>
          <h4 style={{ marginBottom: 4 }}>Get pictures</h4>
          <p className="muted small">
            Fetched on this computer, resized for the TV and added to the rotation above. Nothing is fetched on the TV and
            no keys are sent to it.
          </p>
          <div className="row" style={{ marginTop: 8, flexWrap: 'wrap' }}>
            <button className="btn small" onClick={() => addBing()} disabled={!!working}>
              Bing picture of the day
            </button>
            <button className="btn small" onClick={() => addTmdb()} disabled={!!working}>
              TMDB backdrops
            </button>
          </div>
          <div className="row" style={{ marginTop: 8, flexWrap: 'wrap' }}>
            <input
              type="password"
              value={tmdbKey}
              onChange={(e) => setTmdbKey(e.target.value)}
              placeholder="TMDB API key (v3)"
              style={{ width: 220 }}
              autoComplete="off"
            />
            <input
              type="text"
              value={tmdbList}
              onChange={(e) => setTmdbList(e.target.value.replace(/[^0-9]/g, ''))}
              placeholder="List number, e.g. 28"
              style={{ width: 150 }}
              inputMode="numeric"
            />
          </div>
          <p className="muted small" style={{ marginTop: 6 }}>
            The key stays in this window and is never saved or sent to the TV. Create a free key at themoviedb.org; the
            list number is the digits at the end of a themoviedb.org/list/… address.
          </p>
        </div>
      </div>

      <Slider
        label="Time-of-day sky"
        hint="A dark gradient that follows the clock: night blues, a soft blue day, a warm evening. Around 60 reads well over a wallpaper. 0 turns it off."
        value={val(cfg, 'sky_intensity')}
        min={0}
        max={100}
        onChange={(v) => set('sky_intensity', v)}
      />
    </div>
  )
}

function GreetingTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  const festival = cfg.festival ?? ''
  return (
    <div className="stack" style={{ gap: 18 }}>
      <label className="field">
        Name after the greeting
        <span className="hint">“Mum” shows GOOD MORNING, MUM. Leave empty for the greeting alone.</span>
        <input type="text" value={val(cfg, 'greeting_name')} onChange={(e) => set('greeting_name', e.target.value || undefined)} placeholder="Mum" />
      </label>
      <Toggle label="Solar Hijri date" hint="Shows the Shamsi date used in Iran and Afghanistan above the usual date." checked={val(cfg, 'shamsi_date')} onChange={(v) => set('shamsi_date', v)} />

      <div className="card" style={{ marginBottom: 0 }}>
        <h3>Festive skin</h3>
        <p className="muted small">
          The network pill becomes a diya, Inputs a temple bell, Settings a marigold, and the first Home of the day gets a petal
          shower. It switches itself off after the last day.
        </p>
        <div className="row" style={{ alignItems: 'flex-end' }}>
          <label className="field">
            Festival
            <select
              value={festival}
              onChange={(e) => {
                const v = e.target.value
                if (!v) {
                  set('festival', undefined)
                  set('festival_until', undefined)
                  set('greeting', undefined)
                  set('accent_color', undefined)
                } else set('festival', v)
              }}
            >
              <option value="">None</option>
              <option value="diwali">Diwali</option>
              <option value="dussehra">Dussehra</option>
            </select>
          </label>
          {festival && (
            <>
              <label className="field">
                Last day
                <input type="date" value={cfg.festival_until ?? ''} onChange={(e) => set('festival_until', e.target.value || undefined)} />
              </label>
              <label className="field">
                Glow colour
                <input type="color" value={normHex(cfg.accent_color ?? '#FFB23C')} onChange={(e) => set('accent_color', e.target.value.toUpperCase())} />
              </label>
            </>
          )}
        </div>
        {festival && (
          <label className="field" style={{ marginTop: 12 }}>
            Greeting while it's on
            <span className="hint">Leave empty for the built-in one.</span>
            <input type="text" value={cfg.greeting ?? ''} onChange={(e) => set('greeting', e.target.value || undefined)} placeholder="Happy Diwali" />
          </label>
        )}
      </div>
    </div>
  )
}

function MotionTab({ cfg, set }: { cfg: LauncherConfig; set: SetFn }): ReactNode {
  return (
    <div>
      <Toggle label="Animations" hint="Focus, glass highlight, title and tile entrance motion." checked={val(cfg, 'animations')} onChange={(v) => set('animations', v)} />
      <Toggle label="Advanced effects" hint="Tile lift, icon zoom, smooth glow colour and app opening effects. Uses more memory." checked={val(cfg, 'advanced_effects')} onChange={(v) => set('advanced_effects', v)} />
      <Toggle
        label="Reduced motion"
        hint="For TVs whose graphics can't keep up: a stiff highlight, no overshoot, short fades. A MediaTek MT5895 on Android 10 is the known case."
        checked={val(cfg, 'reduced_motion')}
        onChange={(v) => set('reduced_motion', v)}
      />
      <p className="muted small" style={{ marginTop: 10 }}>
        System-wide animation speed is in TV extras.
      </p>
    </div>
  )
}

function AdvancedTab({ apps, cfg, set }: { apps: TvApp[]; cfg: LauncherConfig; set: SetFn }): ReactNode {
  const sorted = useMemo(() => [...apps].sort((a, b) => a.label.localeCompare(b.label)), [apps])
  const keys = cfg.app_keys ?? []
  const rows = keys.map((k) => {
    const i = k.indexOf(':')
    return { code: k.slice(0, i), pkg: k.slice(i + 1) }
  })
  const writeKeys = (r: { code: string; pkg: string }[]): void =>
    set('app_keys', r.length ? r.map((x) => `${x.code}:${x.pkg}`) : undefined)

  return (
    <div className="stack" style={{ gap: 18 }}>
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ flex: 1 }}>
          Live TV app
          <span className="hint">Offered at the end of the Inputs menu.</span>
          <AppSelect apps={sorted} value={val(cfg, 'live_tv_package')} onChange={(v) => set('live_tv_package', v)} allowCustom />
        </label>
        <label className="field" style={{ flex: 1 }}>
          VPN app
          <span className="hint">The network pill opens it, and a “VPN off” badge shows while no VPN is up.</span>
          <AppSelect apps={sorted} value={val(cfg, 'vpn_app')} onChange={(v) => set('vpn_app', v)} />
        </label>
      </div>

      <div>
        <h3>Remote buttons that open an app</h3>
        <p className="muted small">
          For buttons only the stock launcher answered, like the YouTube button on Google TV (190). Needs the Input or Home
          button service from the Launcher step.
        </p>
        {rows.map((r, i) => (
          <div key={i} className="kv-row">
            <input
              type="text"
              value={r.code}
              placeholder="190"
              aria-label="Keycode"
              onChange={(e) => writeKeys(rows.map((x, j) => (j === i ? { ...x, code: e.target.value.trim() } : x)))}
            />
            <AppSelect apps={sorted} value={r.pkg} onChange={(v) => writeKeys(rows.map((x, j) => (j === i ? { ...x, pkg: v } : x)))} />
            <button className="icon-btn" aria-label="Remove" onClick={() => writeKeys(rows.filter((_, j) => j !== i))}>
              ✕
            </button>
          </div>
        ))}
        <div className="row">
          <button className="btn small" onClick={() => writeKeys([...rows, { code: '190', pkg: 'com.google.android.youtube.tv' }])}>
            Add a button
          </button>
          <span className="small muted">Keycode as a number, or a name like KEYCODE_BUTTON_3.</span>
        </div>
      </div>

      <NumberList
        label="Buttons that open Settings"
        hint="Keycodes for the Home button service. TCL Google TV gear button: 83."
        value={cfg.settings_key_keycodes}
        onChange={(v) => set('settings_key_keycodes', v)}
      />
      <Toggle
        label="Settings button opens a quick panel"
        hint="A small panel with TV settings, Display, Network, Apps and Launcher settings, over whatever is on screen."
        checked={val(cfg, 'settings_key_panel')}
        onChange={(v) => set('settings_key_panel', v)}
      />
      <NumberList
        label="More buttons that open the input switcher"
        hint="Keycodes for the Input button service. KEYCODE_TV always does. Xiaomi PatchWall button: 192."
        value={cfg.input_key_keycodes}
        onChange={(v) => set('input_key_keycodes', v)}
      />
      <Toggle
        label="Module cards"
        hint="Apps that offer their own tile ask you once each. Off: the launcher never looks or asks."
        checked={val(cfg, 'module_cards')}
        onChange={(v) => set('module_cards', v)}
      />
    </div>
  )
}

function AppSelect(props: { apps: TvApp[]; value: string; onChange: (v: string) => void; allowCustom?: boolean }): ReactNode {
  const known = props.apps.some((a) => a.pkg === props.value)
  return (
    <select value={props.value} onChange={(e) => props.onChange(e.target.value)}>
      <option value="">None</option>
      {!known && props.value && <option value={props.value}>{props.value} (not installed)</option>}
      {props.apps.map((a) => (
        <option key={a.pkg} value={a.pkg}>
          {a.label}
        </option>
      ))}
    </select>
  )
}

function NumberList(props: { label: string; hint: string; value?: number[]; onChange: (v: number[] | undefined) => void }): ReactNode {
  const [text, setText] = useState((props.value ?? []).join(', '))
  useEffect(() => setText((props.value ?? []).join(', ')), [props.value?.join(',')])
  return (
    <label className="field">
      {props.label}
      <span className="hint">{props.hint}</span>
      <input
        type="text"
        value={text}
        placeholder="83, 190"
        onChange={(e) => setText(e.target.value)}
        onBlur={() => {
          const nums = text.split(/[\s,]+/).filter(Boolean).map(Number).filter((n) => Number.isInteger(n) && n > 0)
          props.onChange(nums.length ? nums : undefined)
          setText(nums.join(', '))
        }}
      />
    </label>
  )
}

function normHex(c: string): string {
  return /^#[0-9a-fA-F]{6}$/.test(c) ? c.toLowerCase() : BUILTIN.background_color
}

function sortKeys(o: LauncherConfig): Record<string, unknown> {
  return Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined).sort(([a], [b]) => a.localeCompare(b)))
}
