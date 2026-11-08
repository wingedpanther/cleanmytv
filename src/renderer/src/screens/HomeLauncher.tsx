import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, ButtonAction, HomeButton, HomeConfig, HomeGroup, HomeTheme, ThemeНазадup, HomeМакет, HomeSection, HomeStatus, KeyPress, QuickEntry, QuickId, TvApp } from '@shared/api'
import type { ScreenProps } from '../App'
import { AppOrder } from '../components/AppOrder'
import { HomeПредпросмотр, QuickMark, quickApp, quickLabel } from '../components/HomeПредпросмотр'
import { ActionButton, Busy, Confirm, PinBoxes, ResultNotice, Slider, StatusChip, Toggle, errorText, usePhase } from '../components/ui'
import { prepareОбои, thumbnail } from '../lib/image'
import { fetchBingDaily } from '../lib/backgrounds'
import { findPlaces, type Place } from '../lib/places'
import { drawОбои, paintОбои } from '../lib/themes'
import { BUILT_IN_THEMES, THEME_EXT, THEME_PICTURES, parseTheme, withИзображение, type BuiltInTheme, type ThemeArt, type ThemePaint } from '@shared/themes'

type Tab = 'overview' | 'apps' | 'layout' | 'look' | 'widgets' | 'music' | 'buttons' | 'lock'

const TABS: [Tab, string][] = [
  ['overview', 'Обзор'],
  ['layout', 'Макет'],
  ['apps', 'Приложения, ряды и папки'],
  ['look', 'Оформление и обои'],
  ['widgets', 'Виджеты'],
  ['music', 'Музыкальные полосы'],
  ['buttons', 'Кнопки пульта'],
  ['lock', 'Блокировка приложений']
]

const ACCENTS = ['#FF3000', '#2EC4B6', '#FFB23C', '#7C5CFF', '#3DDC84', '#FFFFFF']

/** The TV has an older CleanMyTV Home than this app carries. */
const outdated = (h: HomeStatus): boolean => h.installed && !!h.bundledVersion && h.versionName !== h.bundledVersion

type SetFn = (change: Partial<HomeConfig>, wait?: number) => void

/**
 * Everything for CleanMyTV Home. Each change is sent to the TV as soon as it's made (sliders and typing
 * wait until they settle), and the launcher redraws at once: there's no Apply button.
 */
export function HomeLauncher({ markDone, refreshTvs, status, homeSeen, openTab }: ScreenProps & { openTab?: { tab: string; n: number } | null }): ReactNode {
  const [home, setHome] = useState<HomeStatus | null>(null)
  const [cfg, setCfg] = useState<HomeConfig | null>(null)
  const [apps, setПриложения] = useState<TvApp[] | null>(null)
  const [tab, setTab] = useState<Tab>('overview')
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [saving, setSaving] = useState(false)
  const [askStock, setAskStock] = useState(false)
  const [stockPick, setStockPick] = useState<StockPick | null>(null)
  const [themeUsed, setThemeUsed] = useState<string | null>(null)
  const [themeНазадup, setThemeНазадup] = useState<ThemeНазадup | null>(null)
  /** A theme waiting for the owner to accept that their wallpaper can't be saved first. */
  const [riskyTheme, setRiskyTheme] = useState<(() => void) | null>(null)
  const pending = useRef<Partial<HomeConfig>>({})
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  async function load(): Promise<void> {
    try {
      const h = await window.cytv.homeStatus()
      setHome(h)
      homeSeen(h)
      setCfg(null)
      // An older launcher on the TV doesn't know the newer settings; the page waits for Update.
      if (h.installed && !outdated(h)) {
        // The launcher's own list: the apps it shows, with the names the TV uses.
        const [c, a] = await Promise.all([window.cytv.readHomeConfig(), window.cytv.homeПриложения()])
        setCfg(c)
        setПриложения(a)
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
    // A change of the owner's own: Undo no longer means the theme.
    setThemeUsed(null)
    setCfg((prev) => (prev ? { ...prev, ...change } : prev))
    pending.current = { ...pending.current, ...change }
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(flush, wait)
  }

  /** `retry` runs the same step again with the stock launcher the owner picks, when the app can't tell (#39). */
  async function run(label: string, fn: () => Promise<ApplyResult>, retry?: (stock: string) => Promise<ApplyResult>): Promise<void> {
    setBusy(label)
    setResult(null)
    setStockPick(null)
    try {
      const r = await fn()
      setResult(r)
      if (r.stockChoices && retry) setStockPick({ label, choices: r.stockChoices, retry })
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
      setCfg(await window.cytv.setHomeОбои(jpeg))
      setThemeUsed(null)
      markDone('home')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  /** Draws every built-in theme's pictures and puts them on the TV, for its own theme carousel. */
  async function themesToTv(): Promise<void> {
    const total = BUILT_IN_THEMES.length * THEME_PICTURES
    setResult(null)
    try {
      setBusy('Загрузка тем на телевизор')
      await window.cytv.themePackBegin()
      let i = 0
      for (const t of BUILT_IN_THEMES) {
        for (let n = 0; n < THEME_PICTURES; n++) {
          setBusy(`Загрузка тем на телевизор: picture ${++i} of ${total}`)
          const jpeg = paintОбои(withИзображение(t.paint, n), `${t.id}-${n}.jpg`).base64
          await window.cytv.themePackPut({ id: t.id, name: t.name, look: t.look, picture: n, jpeg })
        }
      }
      setCfg(await window.cytv.themePackEnd(BUILT_IN_THEMES.map((t) => t.id)))
      setResult({ ok: true, summary: `${BUILT_IN_THEMES.length} themes are on the TV, ${THEME_PICTURES} pictures each. Open them with the Темы icon (Макет → top bar icons) or a remote button.`, warnings: [] })
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  const readThemeНазадup = (): void => {
    window.cytv.themeНазадup().then(setThemeНазадup, () => setThemeНазадup(null))
  }

  /** `used`: the theme now on the TV; null once the old look is back. */
  async function themeStep(label: string, fn: () => Promise<HomeConfig>, used: string | null): Promise<void> {
    // A slider still settling would land on top of the theme.
    if (timer.current) clearTimeout(timer.current)
    await flush()
    setBusy(label)
    setResult(null)
    try {
      setCfg(await fn())
      setThemeUsed(used)
      markDone('home')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      readThemeНазадup()
    }
  }

  // Before the first theme, the look gets saved; say so first if the wallpaper can't be part of it.
  const guarded = (go: () => void): void => {
    if (themeНазадup?.savedAt === null && themeНазадup.wallpaperAtRisk) setRiskyTheme(() => go)
    else go()
  }

  const themes: ThemeActions = {
    used: themeUsed,
    builtIn: (id, picture = 0) => {
      const t = BUILT_IN_THEMES.find((b) => b.id === id)
      if (t) guarded(() => themeStep(`Putting ${t.name} on the TV`, async () =>
        window.cytv.applyHomeTheme({ name: t.name, look: t.look, wallpaper: paintОбои(withИзображение(t.paint, picture), `${t.id}.jpg`).base64, from: `${t.id}:${picture}` }), t.name))
    },
    current: cfg?.wallpaper_from ?? '',
    another: () => {
      const [id, n] = (cfg?.wallpaper_from ?? '').split(':')
      const t = BUILT_IN_THEMES.find((b) => b.id === id)
      if (!t) return
      const next = ((Number(n) || 0) + 1) % THEME_PICTURES
      themeStep(`Drawing another picture for ${t.name}`, async () =>
        window.cytv.setHomeОбои(paintОбои(withИзображение(t.paint, next), `${t.id}.jpg`).base64).then(() =>
          window.cytv.setHomeConfig({ wallpaper_from: `${t.id}:${next}` })), t.name)
    },
    onTv: cfg?.themes_on_tv ?? 0,
    toTv: () => themesToTv(),
    custom: (t) => guarded(() => themeStep(`Putting ${t.name} on the TV`, () => window.cytv.applyHomeTheme(t), t.name)),
    file: (text) => {
      let t: HomeTheme
      try {
        t = parseTheme(text)
      } catch (e) {
        setResult({ ok: false, summary: errorText(e), warnings: [] })
        return
      }
      guarded(() => themeStep(`Putting ${t.name} on the TV`, () => window.cytv.applyHomeTheme(t), t.name))
    },
    backup: themeНазадup,
    restore: () => themeStep('Восстановление сохранённого оформления', () => window.cytv.restoreThemeНазадup(), null),
    forget: () => window.cytv.forgetThemeНазадup().then(readThemeНазадup),
    save: async (name) => {
      try {
        const r = await window.cytv.saveHomeTheme(name)
        if (r) setResult(r)
      } catch (e) {
        setResult({ ok: false, summary: errorText(e), warnings: [] })
      }
    }
  }

  const ready = !!home?.installed && !outdated(home) && !!cfg
  // ⌘K asked for a tab: it opens once the launcher's settings are in.
  useEffect(() => {
    if (openTab && (ready || openTab.tab === 'overview') && TABS.some(([id]) => id === openTab.tab)) setTab(openTab.tab as Tab)
  }, [openTab, ready])
  useEffect(() => {
    if (ready) readThemeНазадup()
  }, [ready])
  // Why the other tabs are greyed out (#38).
  const locked = !home?.installed
    ? 'Install CleanMyTV Home on the Обзор tab first.'
    : outdated(home)
      ? 'Press Обновить и применить on the Обзор tab first.'
      : "Waiting for CleanMyTV Home's settings. If this stays, reconnect from Setup."

  return (
    <>
      <h1>Главный экран</h1>
      <p className="lede">
        CleanMyTV Home is the TV's new home screen: no ads, your apps, your layout. Install it once, then change anything here;
        the TV shows it straight away and keeps it after a reboot. The stock launcher is one click away on the Обзор tab.
      </p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {stockPick && !busy && (
        <StockPicker
          pick={stockPick}
          onPick={(stock) => run(stockPick.label, () => stockPick.retry(stock), stockPick.retry)}
          onCancel={() => setStockPick(null)}
        />
      )}
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {!home ? (
        <Busy text="Запрос состояния CleanMyTV Home у телевизора" />
      ) : (
        <>
          <div className="tabs" role="tablist">
            {TABS.map(([id, label]) => (
              <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? 'on' : ''} disabled={id !== 'overview' && !ready} title={id !== 'overview' && !ready ? locked : undefined} onClick={() => setTab(id)}>
                {label}
              </button>
            ))}
          </div>

          {outdated(home) && (
            <div className="notice warn">
              The TV has CleanMyTV Home {home.versionName}; this app carries {home.bundledVersion}. Press Обновить и применить below to change
              its layout, apps, look and remote buttons from here. The other tabs open after that.
            </div>
          )}
          {tab === 'overview' && (
            <Обзор home={home} busy={!!busy} busyText={busy} run={run} askStock={() => setAskStock(true)} host={status.host} onShot={refreshTvs} />
          )}
          {ready && tab === 'apps' && apps && <ПриложенияTab apps={apps} cfg={cfg} set={set} />}
          {ready && tab === 'layout' && apps && <МакетTab apps={apps} cfg={cfg} set={set} />}
          {ready && tab === 'look' && (
            <>
              <LookTab cfg={cfg} set={set} busy={!!busy} wallpaper={wallpaper} themes={themes} />
              <Слайд-шоуCard cfg={cfg} set={set} setCfg={setCfg} onResult={setResult} />
              <КаминCard cfg={cfg} set={set} setCfg={setCfg} onResult={setResult} />
            </>
          )}
          {ready && tab === 'widgets' && <ВиджетыTab cfg={cfg} set={set} />}
          {ready && tab === 'music' && <MusicTab cfg={cfg} set={set} />}
          {ready && tab === 'lock' && apps && <LockTab cfg={cfg} set={set} apps={apps} setCfg={setCfg} onError={(e) => setResult({ ok: false, summary: errorText(e), warnings: [] })} />}
          {ready && tab === 'buttons' && apps && <ButtonsTab cfg={cfg} set={set} apps={apps} setCfg={setCfg} brand={status.probe?.manufacturer} onError={(e) => setResult({ ok: false, summary: errorText(e), warnings: [] })} />}
          {ready && tab !== 'overview' && <p className="small muted">{saving ? 'Отправка to the TV…' : 'Сохранено на телевизоре.'}</p>}
        </>
      )}

      {riskyTheme && (
        <Confirm
          title="Ваши обои могут't be saved"
          confirmLabel="Всё равно использовать тему"
          onCancel={() => setRiskyTheme(null)}
          onConfirm={() => {
            const go = riskyTheme
            setRiskyTheme(null)
            go()
          }}
        >
          <p>
            Before the first theme, the app saves your look so you can put it back. Your wallpaper was set by an older CleanMyTV,
            which kept no copy of it, so it can't be part of that: putting your look back keeps the theme's wallpaper.
          </p>
          <p>Чтобы сохранить изображение, сначала снова установите его ниже, в разделе «Обои».</p>
        </Confirm>
      )}

      {askStock && (
        <Confirm
          title="Вернуть штатный интерфейс?"
          confirmLabel="Переключиться"
          onConfirm={() => {
            setAskStock(false)
            run('Возврат штатного главного экрана', () => window.cytv.useStockLauncher())
          }}
          onCancel={() => setAskStock(false)}
        >
          <p>Приложение снова включает штатный интерфейс телевизора и назначает его главным экраном. CleanMyTV Home остаётся установленным — при желании можно переключиться обратно.</p>
        </Confirm>
      )}
    </>
  )
}

interface StockPick {
  label: string
  choices: string[]
  retry: (stock: string) => Promise<ApplyResult>
}

function StockPicker({ pick, onPick, onCancel }: { pick: StockPick; onPick: (stock: string) => void; onCancel: () => void }): ReactNode {
  const [chosen, setChosen] = useState(pick.choices.length === 1 ? pick.choices[0] : '')
  return (
    <div className="card white">
      <h2>Какой интерфейс установлен на телевизоре по умолчанию?</h2>
      {pick.choices.length === 0 ? (
        <p className="muted">
          Nothing on the TV can be Home apart from CleanMyTV Home, so there is no launcher to fall back on. Leave it as it is and
          report your TV model on GitHub.
        </p>
      ) : (
        <>
          <p className="small muted">
            These apps can be Home on this TV. Pick the one the TV came with. It stays on, and Undo and the restart check give Home
            back to it.
          </p>
          <div className="place-list" role="radiogroup">
            {pick.choices.map((c) => (
              <label key={c} className="row" style={{ gap: 8 }}>
                <input type="radio" name="stock" checked={chosen === c} onChange={() => setChosen(c)} />
                <span className="mono">{c}</span>
              </label>
            ))}
          </div>
        </>
      )}
      <div className="row" style={{ marginTop: 16 }}>
        <button className="btn primary" disabled={!chosen} onClick={() => onPick(chosen)}>
          Use this one and carry on
        </button>
        <button className="btn ghost" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  )
}

function Обзор(props: { home: HomeStatus; busy: boolean; run: (label: string, fn: () => Promise<ApplyResult>, retry?: (stock: string) => Promise<ApplyResult>) => Promise<void>; askStock: () => void; host?: string; onShot: () => void; busyText?: string | null }): ReactNode {
  const { home, busy, run } = props
  const [shot, setShot] = useState<string | null>(null)
  const [shooting, setShooting] = useState(false)
  const [shotError, setShotError] = useState<string | null>(null)

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
          <h2>На этом телевизоре</h2>
          <StatusChip status={home.isHome ? 'ok' : home.installed ? 'warn' : 'optional'} text={home.isHome ? 'Главный экран' : home.installed ? 'Установлено, но не назначено главным экраном' : 'Не установлено'} />
        </div>
        <div className="facts">
          <div>
            <span>На телевизоре</span>
            <b>{home.installed ? `version ${home.versionName ?? '?'}` : 'not installed'}</b>
          </div>
          <div>
            <span>В приложении</span>
            <b>{home.bundled ? `version ${home.bundledVersion ?? '?'}` : 'no bundled copy'}</b>
          </div>
        </div>
        {home.unsupported && (
          <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
            {home.unsupported}
          </div>
        )}
        <div className="row" style={{ marginTop: 16 }}>
          {(!home.installed || outdated(home)) && (
            <ActionButton
              primary
              phase={props.busyText?.startsWith('Установка') ? 'busy' : 'idle'}
              label={home.installed ? 'Обновить и применить' : 'Установить и применить'}
              busyLabel={home.installed ? 'Обновление' : 'Установка'}
              disabled={busy || !home.bundled || !!home.unsupported}
              onClick={() => run('Установка CleanMyTV Home. Keep the TV on.', () => window.cytv.installHome(), (s) => window.cytv.installHome(s))}
            />
          )}
          {home.installed && !home.isHome && (
            <button className="btn primary" disabled={busy} onClick={() => run('Назначение CleanMyTV Home главным экраном', () => window.cytv.useHome(), (s) => window.cytv.useHome(s))}>
              Use CleanMyTV Home
            </button>
          )}
          {home.isHome && (
            <button className="btn" disabled={busy} onClick={props.askStock}>
              Give Home back to the stock launcher
            </button>
          )}
        </div>
        {home.oldLauncher && (
          <div className="notice" style={{ marginTop: 16, marginBottom: 0 }}>
            <p style={{ margin: '0 0 10px' }}>
              The CleanYourTV launcher from earlier versions of this app is still on the TV. CleanMyTV Home replaces it.
              {!home.isHome && ' Сначала установите CleanMyTV Home или переключитесь на него.'}
            </p>
            <button className="btn small" disabled={busy || !home.isHome} onClick={() => run('Удаление лаунчера CleanYourTV', () => window.cytv.removeOldLauncher())}>
              Удалить the CleanYourTV launcher
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head">
          <h2>Изображение на телевизоре</h2>
          <button className="btn small" onClick={snapshot} disabled={shooting}>
            {shooting ? 'Создание снимка…' : 'Сделать снимок'}
          </button>
        </div>
        {shotError && <div className="notice warn">{shotError}</div>}
        {shot ? (
          <img src={shot} alt="Экран телевизора" style={{ width: '100%', borderRadius: 10, border: '1px solid var(--line)' }} />
        ) : (
          <p className="muted small">Текущий снимок экрана телевизора. Он также станет миниатюрой этого ТВ на боковой панели.</p>
        )}
      </div>
    </>
  )
}

function ПриложенияTab({ apps, cfg, set }: { apps: TvApp[]; cfg: HomeConfig; set: SetFn }): ReactNode {
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
        <HomeПредпросмотр apps={apps} cfg={cfg} />
      </div>
      <div className="card">
        <h2>Панель избранного и порядок</h2>
        <Slider label="Приложения на панели избранного" hint="Первые приложения отображаются в виде ряда крупных плиток. 0 — отключить панель." value={cfg.dock_size} min={0} max={8} onChange={(v) => set({ dock_size: v }, 300)} />
        <Toggle label="Мобильные приложения в папке «Другие приложения»" hint="Приложения made for phones, with no TV picture, move out of the dock and the grid into one folder. Приложения you put in rows or folders stay where they are." checked={cfg.other_apps} onChange={(v) => set({ other_apps: v })} />
        <div className="row" style={{ margin: '6px 0 10px' }}>
          <span className="muted small">Перетаскивайте элементы или используйте стрелки. Выделенные плитки находятся на панели избранного. Приложения в папке отображаются только внутри неё.</span>
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
        title="Ряды"
        hint="Именованные ряды приложений, например «Просмотр» или «Музыка». Приложение может одновременно находиться в ряду и общей сетке."
        groups={cfg.rows}
        apps={ordered}
        max={8}
        onChange={(rows) => set({ rows }, 500)}
      />
      <Groups
        kind="folders"
        title="Папки"
        hint="Папка отображается одной плиткой; кнопка OK на пульте открывает её. Приложения из папки не показываются на панели избранного и в сетке."
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
  const fresh = props.kind === 'rows' ? 'Новый ряд' : 'Новая папка'
  return (
    <div className="card">
      <div className="card-head">
        <h2>{props.title}</h2>
        <button className="btn small" disabled={groups.length >= props.max} onClick={() => onChange([...groups, { name: `${fresh} ${groups.length + 1}`, apps: [] }])}>
          {props.kind === 'rows' ? 'Добавить ряд' : 'Добавить папку'}
        </button>
      </div>
      <p className="muted small">{props.hint}</p>
      {groups.map((g, i) => (
        <div key={i} className="group-card">
          <div className="row">
            <input type="text" maxLength={30} value={g.name} aria-label={`${props.kind === 'rows' ? 'Ряд' : 'Папка'} name`} onChange={(e) => update(i, { ...g, name: e.target.value })} style={{ flex: 1 }} />
            <button className="btn small ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label="Переместить вверх">
              ↑
            </button>
            <button className="btn small ghost" disabled={i === groups.length - 1} onClick={() => move(i, 1)} aria-label="Переместить вниз">
              ↓
            </button>
            <button className="btn small ghost" onClick={() => onChange(groups.filter((_, j) => j !== i))}>
              Удалить
            </button>
          </div>
          <div className="chips">
            {g.apps.length === 0 && <span className="muted small">Пока приложений нет. Раздел будет скрыт на телевизоре, пока вы не добавите приложения.</span>}
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
            aria-label="Добавить приложение"
            onChange={(e) => e.target.value && update(i, { ...g, apps: [...g.apps, e.target.value] })}
          >
            <option value="">Добавить приложение…</option>
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

const LAYOUTS: { id: HomeМакет; label: string; hint: string; mini: [number, number, number, number, boolean][] }[] = [
  { id: 'shelf', label: 'Полка', hint: 'Верхняя панель, свободное пространство, приложения ниже.', mini: [[6, 6, 30, 6, true], [64, 6, 30, 6, true], [6, 40, 88, 24, false], [6, 72, 88, 18, true]] },
  { id: 'split', label: 'Разделённый', hint: 'Часы and quick settings on the left, apps on the right.', mini: [[6, 10, 26, 18, true], [6, 62, 20, 24, true], [38, 8, 56, 26, false], [38, 42, 56, 18, true]] },
  { id: 'rail', label: 'Боковая панель', hint: 'Значки слева, которые раскрываются вместе с названиями.', mini: [[3, 6, 6, 84, true], [70, 6, 24, 8, true], [16, 24, 78, 28, false], [16, 60, 78, 18, true]] }
]

const SECTION_LABEL: Record<HomeSection, string> = { nowplaying: 'Сейчас играет', dock: 'Dock', rows: 'Ряды', folders: 'Папки', grid: 'Все приложения' }
const ALL_SECTIONS: HomeSection[] = ['nowplaying', 'dock', 'rows', 'folders', 'grid']
const ALL_QUICK: QuickId[] = ['inputs', 'wifi', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music', 'panel', 'themes', 'fireplace', 'settings']
/** As the launcher's HomeConfig.MAX_QUICK_APPS. */
const MAX_QUICK_APPS = 6
const IDLE_CHOICES: [number, string][] = [[0, 'Off'], [1, '1 min'], [2, '2 min'], [5, '5 min'], [10, '10 min'], [15, '15 min'], [30, '30 min']]

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
            <button className="btn small ghost" disabled={i <= 0} onClick={() => move(t, -1)} aria-label="Раньше">
              ↑
            </button>
            <button className="btn small ghost" disabled={i < 0 || i === on.length - 1} onClick={() => move(t, 1)} aria-label="Позже">
              ↓
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function МакетTab({ apps, cfg, set }: { apps: TvApp[]; cfg: HomeConfig; set: SetFn }): ReactNode {
  return (
    <>
      <div className="home-preview-sticky">
        <HomeПредпросмотр apps={apps} cfg={cfg} />
      </div>
      <div className="card">
        <h2>Макет</h2>
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
            <Slider label="Свободное место над приложениями" hint="Область обоев между верхней панелью и первым рядом." value={cfg.top_space} min={0} max={40} unit="%" onChange={(v) => set({ top_space: v }, 250)} />
          </div>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Разделы</h2>
          <p className="muted small">Сверху вниз. Снимите отметку, чтобы скрыть раздел.</p>
          <OrderList all={ALL_SECTIONS} on={cfg.sections} label={(s) => SECTION_LABEL[s]} onChange={(sections) => set({ sections })} />
        </div>
        <div className="card">
          <h2>Быстрые настройки</h2>
          <p className="muted small">
            {cfg.layout === 'rail' ? 'Icons in the rail.' : cfg.layout === 'split' ? 'At the bottom of the left side.' : 'Under the clock and date.'} The TV hides any it can't open, such as Inputs on a box
            without HDMI inputs, or Search when the search app is switched off.
          </p>
          <OrderList<QuickEntry>
            all={[...ALL_QUICK, ...cfg.quick.filter((q) => quickApp(q))]}
            on={cfg.quick}
            label={(q) => {
              const pkg = quickApp(q)
              const missing = pkg ? !apps.some((a) => a.pkg === pkg) : cfg.quick_available && !cfg.quick_available.includes(q as QuickId)
              return (
                <>
                  <span className="quick-mark"><QuickMark q={q} apps={apps} /></span> {quickLabel(q, apps)}
                  {missing && <span className="muted small">не установлено на этом ТВ</span>}
                </>
              )
            }}
            onChange={(quick) => set({ quick })}
          />
          <QuickAppAdd apps={apps} quick={cfg.quick} onAdd={(pkg) => set({ quick: [...cfg.quick, `app:${pkg}`] })} />
        </div>
      </div>

      <div className="card">
        <h2>Размеры</h2>
        <Slider label="Плитки панели избранного" value={cfg.dock_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ dock_scale: v }, 250)} />
        <Slider label="Плитки рядов и папок" value={cfg.tile_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ tile_scale: v }, 250)} />
        <Slider label="Приложений в одном ряду общего списка" value={cfg.columns} min={3} max={8} onChange={(v) => set({ columns: v }, 250)} />
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Названия приложений</p>
        <div className="seg">
          {([['always', 'Всегда', { show_names: true, name_focus: false }], ['focus', 'Выделениеed app only', { show_names: true, name_focus: true }], ['off', 'Off', { show_names: false, name_focus: false }]] as const).map(([id, label, v]) => (
            <button key={id} className={(!cfg.show_names ? 'off' : cfg.name_focus ? 'focus' : 'always') === id ? 'on' : ''} onClick={() => set(v)}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {!cfg.show_names ? 'Не показывать подписи под плитками. У приложений без ТВ-баннера название всё равно отображается на плитке.'
            : cfg.name_focus ? 'Название отображается только у выбранного приложения. Остальные скрыты.' : 'Под каждой плиткой отображается название.'}
        </p>
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>Часы</h2>
          <div className="seg">
            {([['time_date', 'Время и дата'], ['time', 'Только время'], ['off', 'Off']] as const).map(([id, label]) => (
              <button key={id} className={cfg.clock === id ? 'on' : ''} onClick={() => set({ clock: id })}>
                {label}
              </button>
            ))}
          </div>
        </div>
        <div className="card">
          <h2>Приветствие</h2>
          <label className="field">
            <span>Ваше имя</span>
            <span className="hint">Показывает приветствие «Добрый вечер, имя». Оставьте поле пустым, чтобы отключить его.</span>
            <input type="text" maxLength={40} value={cfg.greeting} placeholder="например, Саша" onChange={(e) => set({ greeting: e.target.value }, 700)} />
          </label>
        </div>
      </div>

      <div className="card">
        <h2>Экран ожидания</h2>
        <p className="muted small">
          When nobody has pressed a button for this long, Home fades out the apps and shows just the wallpaper{cfg.clock === 'off' ? '' : ' and a large clock'}. Any button brings everything back.
        </p>
        <div className="seg">
          {IDLE_CHOICES.map(([m, label]) => (
            <button key={m} className={cfg.idle_minutes === m ? 'on' : ''} onClick={() => set({ idle_minutes: m })}>
              {label}
            </button>
          ))}
        </div>
      </div>
    </>
  )
}

/** Adds an app as a round icon next to the quick settings, up to MAX_QUICK_APPS. */
function QuickAppAdd({ apps, quick, onAdd }: { apps: TvApp[]; quick: QuickEntry[]; onAdd: (pkg: string) => void }): ReactNode {
  const used = quick.filter((q) => quickApp(q)).length
  const free = apps.filter((a) => !quick.includes(`app:${a.pkg}`)).sort((a, b) => a.label.localeCompare(b.label))
  return (
    <div className="row" style={{ marginTop: 10 }}>
      <select value="" aria-label="Добавить приложение icon" disabled={used >= MAX_QUICK_APPS || !free.length} onChange={(e) => e.target.value && onAdd(e.target.value)}>
        <option value="">{used >= MAX_QUICK_APPS ? `${MAX_QUICK_APPS} приложение the most` : 'Добавить приложение as an icon…'}</option>
        {free.map((a) => (
          <option key={a.pkg} value={a.pkg}>
            {a.label}
          </option>
        ))}
      </select>
      <span className="muted small">Для приложений, которые вы часто открываете, например VPN. Снимите отметку, чтобы убрать приложение.</span>
    </div>
  )
}

function Цвет({ label, hint, value, onChange }: { label: string; hint?: string; value: string; onChange: (c: string, wait?: number) => void }): ReactNode {
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

const FOCUS_SWATCH: Record<HomeConfig['focus'], (accent: string) => string> = {
  lift: (a) => `0 4px 8px rgba(0, 0, 0, 0.55), 0 0 12px 1px ${a}59, inset 0 0 0 1px rgba(255, 255, 255, 0.28)`,
  glow: (a) => `0 0 12px 2px ${a}`,
  outline: (a) => `0 0 0 2px ${a}`,
  none: () => 'none'
}

interface ThemeActions {
  used: string | null
  builtIn: (id: string, picture?: number) => void
  /** The theme picture on the TV now ("<id>:<n>"), or "". */
  current: string
  another: () => void
  /** Темы already on the TV for its carousel. */
  onTv: number
  toTv: () => void
  /** A theme made in the editor. */
  custom: (t: HomeTheme) => void
  file: (text: string) => void
  backup: ThemeНазадup | null
  restore: () => void
  forget: () => void
  save: (name: string) => Promise<void>
}

function ThemeGrid({ themes, actions, busy }: { themes: BuiltInTheme[]; actions: ThemeActions; busy: boolean }): ReactNode {
  // Each card shows a small copy of the wallpaper the theme draws.
  const art = useMemo(() => new Map(themes.map((t) => [t.id, `url(${drawОбои(t.paint, 320, 180).toDataURL('image/jpeg', 0.85)})`])), [themes])
  return (
    <div className="themes">
      {themes.map((t) => (
        <button key={t.id} className={`theme${actions.used === t.name ? ' on' : ''}`} disabled={busy} onClick={() => actions.builtIn(t.id)}>
          <span className="theme-art" style={{ backgroundImage: art.get(t.id), backgroundSize: 'cover' }}>
            <span className="theme-tile" style={{ boxShadow: FOCUS_SWATCH[t.look.focus ?? 'lift'](t.look.accent ?? '#FFFFFF') }} />
            <span className="theme-tile" />
            <span className="theme-tile" />
          </span>
          <span className="theme-name">{t.name}</span>
        </button>
      ))}
    </div>
  )
}

function ТемыCard({ themes, busy }: { themes: ThemeActions; busy: boolean }): ReactNode {
  const file = useRef<HTMLInputElement>(null)
  const [name, setName] = useState('My theme')
  const [editing, setEditing] = useState(false)
  return (
    <div className="card">
      <h2>Темы</h2>
      <p className="muted">
        A theme sets the wallpaper, colours, tile sizes and music bars in one go. Your apps, rows and layout stay as they are.
      </p>
      {themes.backup?.savedAt ? (
        <div className="notice row" style={{ justifyContent: 'space-between' }}>
          <span>
            Your look from {new Date(themes.backup.savedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })} is saved
            {themes.backup.withОбои ? '' : ', without its wallpaper'}.
          </span>
          <span className="row" style={{ gap: 8 }}>
            <button className="btn small" disabled={busy} onClick={themes.restore}>
              Put it back
            </button>
            <button className="btn small ghost" disabled={busy} onClick={themes.forget}>
              Сбросить
            </button>
          </span>
        </div>
      ) : (
        <p className="small muted">Перед применением первой темы текущие настройки оформления сохраняются — их всегда можно восстановить.</p>
      )}
      <ThemeGrid themes={BUILT_IN_THEMES} actions={themes} busy={busy} />
      {editing ? <ThemeEditor themes={themes} busy={busy} onЗакрыть={() => setEditing(false)} /> : null}
      <div className="row" style={{ marginTop: 12 }}>
        <button className="btn ghost" disabled={busy || !BUILT_IN_THEMES.some((t) => themes.current.startsWith(`${t.id}:`))} onClick={themes.another}>
          Another picture
        </button>
        <button className="btn ghost" disabled={busy} onClick={themes.toTv}>
          {themes.onTv ? 'Повторно загрузить темы на телевизор' : 'Загрузить темы на телевизор'}
        </button>
        {!editing && (
          <button className="btn ghost" disabled={busy} onClick={() => setEditing(true)}>
            Make your own…
          </button>
        )}
      </div>
      <p className="small muted" style={{ marginTop: 8 }}>
        Each theme has {THEME_PICTURES} pictures. With the themes on the TV, you can switch them with the remote: add the Темы icon to the top bar
        on the Макет tab, or give a remote button the Темы job. {themes.onTv ? `${themes.onTv} themes are on the TV now.` : 'Это займёт одну-две минуты.'}
      </p>
      <input
        ref={file}
        type="file"
        accept={`.${THEME_EXT}`}
        hidden
        onChange={(e) => {
          const f = e.target.files?.[0]
          e.target.value = ''
          if (f) f.text().then(themes.file)
        }}
      />
      <div className="row" style={{ marginTop: 16 }}>
        <input type="text" value={name} maxLength={40} onChange={(e) => setName(e.target.value)} aria-label="Название темы" style={{ width: 180 }} />
        <button className="btn" disabled={busy || !name.trim()} onClick={() => themes.save(name)}>
          Save this look…
        </button>
        <button className="btn ghost" disabled={busy} onClick={() => file.current?.click()}>
          Load a theme file…
        </button>
      </div>
      <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
        Theme files end in .{THEME_EXT}. Share one and anyone can load it on their TV.
      </p>
    </div>
  )
}

function LookTab(props: { cfg: HomeConfig; set: SetFn; busy: boolean; wallpaper: (label: string, get: () => Promise<string | null>) => Promise<void>; themes: ThemeActions }): ReactNode {
  const { cfg, set, busy, wallpaper, themes } = props
  const file = useRef<HTMLInputElement>(null)
  return (
    <>
      <ТемыCard themes={themes} busy={busy} />
      <div className="card">
        <h2>Акцентный цвет</h2>
        <p className="muted">Используется для подсветки выбранной плитки и музыкальных полос.</p>
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
          <input type="color" value={cfg.accent} onChange={(e) => set({ accent: e.target.value.toUpperCase() }, 400)} aria-label="Выберите цвет" />
        </div>
      </div>

      <div className="card">
        <h2>Выделение</h2>
        <p className="muted">Как выделяется плитка, на которую наведён курсор пульта.</p>
        <div className="seg">
          {([['lift', 'Подъём'], ['glow', 'Свечение'], ['outline', 'Контур'], ['none', 'Нет']] as const).map(([id, label]) => (
            <button key={id} className={cfg.focus === id ? 'on' : ''} onClick={() => set({ focus: id })}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {cfg.focus === 'lift' ? 'Плитка приподнимается с мягкой тенью и лёгким свечением акцентного цвета; по ней проходит блик.'
            : cfg.focus === 'glow' ? 'Свечение акцентным цветом и белая рамка.' : cfg.focus === 'outline' ? 'Контур акцентного цвета.' : 'Меняется только цвет плитки.'}
        </p>
        {cfg.focus === 'lift' && <Toggle label="Наклон в сторону движения" hint="Плитка слегка наклоняется в сторону нажатия, а затем возвращается." checked={cfg.tilt} onChange={(v) => set({ tilt: v })} />}
        <Toggle label="Закрепить выбранную плитку" hint="Ряд перемещается под ней, начиная на одну позицию левее." checked={cfg.pin_focus} onChange={(v) => set({ pin_focus: v })} />
        <Toggle label="Цвет фона от приложения's colour" hint="Оттенок выбранного приложения's colour washes in from the top." checked={cfg.follow_colour} onChange={(v) => set({ follow_colour: v })} />
      </div>

      <div className="card">
        <h2>Значки приложений</h2>
        <p className="muted">Баннеры — собственные изображения приложений для ТВ. Другие стили оформляют все логотипы одинаково, чтобы плитки в рядах сочетались.</p>
        <div className="seg">
          {([['banner', 'Баннеры'], ['colour', 'Цвет'], ['mono', 'Монохромный'], ['duo', 'Два цвета']] as const).map(([id, label]) => (
            <button key={id} className={cfg.icon_style === id ? 'on' : ''} onClick={() => set({ icon_style: id })}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {cfg.icon_style === 'banner' ? 'Стандартное изображение приложения для ТВ.'
            : cfg.icon_style === 'colour' ? 'Белый логотип на фирменном фоне.'
            : cfg.icon_style === 'mono' ? 'Все логотипы одного акцентного цвета на одинаковых полупрозрачных плитках.'
            : "Every logo in two shades of your accent, on your theme's colours."}
          {cfg.icon_style !== 'banner' && ' Если логотип приложения недоступен, отображается первая буква его названия.'}
        </p>
        {cfg.icon_style !== 'banner' && (
          <>
            <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Форма плиток</p>
            <div className="seg">
              {([['wide', 'Широкая'], ['square', 'Квадратная'], ['squircle', 'Скруглённый квадрат'], ['round', 'Круглая']] as const).map(([id, label]) => (
                <button key={id} className={cfg.tile_shape === id ? 'on' : ''} onClick={() => set({ tile_shape: id })}>
                  {label}
                </button>
              ))}
            </div>
          </>
        )}
      </div>

      <div className="card">
        <h2>Обои</h2>
        <p className="muted">{cfg.has_wallpaper ? 'Обои установлены.' : 'Обои не заданы: отображается цвет фона.'}</p>
        <input
          ref={file}
          type="file"
          accept="image/*"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = ''
            if (f) wallpaper('Подготовка и отправка изображения', async () => (await prepareОбои(f, 'wallpaper.jpg')).base64)
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
            <button className="btn ghost" disabled={busy} onClick={() => wallpaper('Удаление обоев', async () => null)}>
              Удалить
            </button>
          )}
        </div>
        <Slider label="Затемнять обои" hint="Названия и часы остаются читаемыми на светлых изображениях." value={cfg.wallpaper_dim} min={0} max={80} unit="%" onChange={(v) => set({ wallpaper_dim: v }, 300)} />
        <Toggle label="Эффект рассвета при смене" hint="Новые обои сначала затемнены, затем сверху появляется свет. Отключите, если телевизор's animations are off." checked={cfg.intro} onChange={(v) => set({ intro: v })} />
        <Цвет label="Цвет фона" hint="Фон под всеми элементами, когда's no wallpaper. Keep it dark: text is white." value={cfg.background} onChange={(c, w) => set({ background: c }, w)} />
      </div>
    </>
  )
}

function ВиджетыTab({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  const playing = cfg.sections.includes('nowplaying')
  const [query, setQuery] = useState('')
  const [places, setPlaces] = useState<Place[] | null>(null)
  const [searching, setПоиск] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function search(): Promise<void> {
    setПоиск(true)
    setError(null)
    try {
      setPlaces(await findPlaces(query))
    } catch (e) {
      setError(errorText(e))
    } finally {
      setПоиск(false)
    }
  }

  function pick(p: Place): void {
    set({ weather_place: p.name, weather_lat: p.lat, weather_lon: p.lon })
    setPlaces(null)
    setQuery('')
  }

  return (
    <>
      <div className="card">
        <Toggle
          label="Сейчас играет"
          hint="A card with what any music or video app is playing, with play, pause and next. It shows only while something is loaded. The TV lets CleanMyTV Home see which app is playing; it reads no notifications."
          checked={playing}
          onChange={(v) => set({ sections: v ? ['nowplaying', ...cfg.sections.filter((s) => s !== 'nowplaying')] : cfg.sections.filter((s) => s !== 'nowplaying') })}
        />
        {playing && <p className="small muted" style={{ margin: '8px 0 0' }}>Move it up or down in Макет → Разделы.</p>}
      </div>

      <div className="card">
        <h2>Погода</h2>
        <p className="muted">
          The temperature, today's high and low, and the sky next to the clock. It comes from Open-Meteo: free, no account. The
          TV asks for it every 30 minutes.
        </p>
        {cfg.weather_place ? (
          <div className="row" style={{ marginBottom: 14 }}>
            <span>
              Showing the weather for <b>{cfg.weather_place}</b>
            </span>
            <button className="btn small ghost" onClick={() => set({ weather_place: '' })}>
              Turn off
            </button>
          </div>
        ) : null}
        <form
          className="row"
          onSubmit={(e) => {
            e.preventDefault()
            void search()
          }}
        >
          <input type="text" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Город" style={{ width: 240 }} />
          <button className="btn" type="submit" disabled={searching || query.trim().length < 2}>
            {cfg.weather_place ? 'Изменить место' : 'Найти'}
          </button>
          {searching && <Busy text="Поиск" />}
        </form>
        {error && <div className="notice error" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
        {places && (
          <ul className="place-list">
            {places.length === 0 && <li className="small muted">Место не найдено. Попробуйте указать ближайший город.</li>}
            {places.map((p) => (
              <li key={`${p.lat},${p.lon}`}>
                <button className="btn small ghost" onClick={() => pick(p)}>
                  <b>{p.name}</b>
                  {p.detail && <span className="muted">&nbsp;· {p.detail}</span>}
                </button>
              </li>
            ))}
          </ul>
        )}
        <div className="seg" style={{ marginTop: 14 }}>
          {([['c', '°C'], ['f', '°F']] as const).map(([id, label]) => (
            <button key={id} className={cfg.weather_unit === id ? 'on' : ''} onClick={() => set({ weather_unit: id })}>
              {label}
            </button>
          ))}
        </div>
      </div>
    </>
  )
}

function MusicTab({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  return (
    <>
      <div className="card">
        <Toggle
          label="Музыкальные полосы"
          hint="Полосы внизу главного экрана реагируют на музыку или видео, воспроизводимые на телевизоре. Пульт's clicks don't set them off. Add the Музыкальные полосы icon in Макет → Быстрые настройки to turn them on and off from the TV."
          checked={cfg.visualiser}
          onChange={(v) => set({ visualiser: v })}
        />
        <Количество полосПредпросмотр cfg={cfg} />
      </div>
      {cfg.visualiser && (
        <>
          <div className="card">
            <h2>Стиль</h2>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['bars', 'Количество полос'], ['mirror', 'Зеркальный'], ['wave', 'Волна']] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_style === id ? 'on' : ''} onClick={() => set({ viz_style: id })}>
                  {label}
                </button>
              ))}
            </div>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['accent', 'Акцентный цвет'], ['rainbow', 'Радуга'], ['white', 'Белый']] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_colour === id ? 'on' : ''} onClick={() => set({ viz_colour: id })}>
                  {label}
                </button>
              ))}
            </div>
            {cfg.viz_style !== 'wave' && <Toggle label="Маркеры пиков" hint="Небольшая отметка над каждой полосой, которая медленно опускается." checked={cfg.viz_peaks} onChange={(v) => set({ viz_peaks: v })} />}
          </div>
          <div className="card">
            <h2>Размер и внешний вид</h2>
            <Slider label="Количество полос" value={cfg.viz_bars} min={8} max={64} step={4} onChange={(v) => set({ viz_bars: v }, 300)} />
            <Slider label="Высота" hint="Какую часть экрана они могут занимать." value={cfg.viz_height} min={4} max={40} unit="%" onChange={(v) => set({ viz_height: v }, 300)} />
            <Slider label="Непрозрачность" value={cfg.viz_opacity} min={20} max={100} unit="%" onChange={(v) => set({ viz_opacity: v }, 300)} />
            <Slider label="Чувствительность" hint="При высоком значении даже тихая музыка вызывает заметное движение." value={cfg.viz_sensitivity} min={25} max={300} step={5} unit="%" onChange={(v) => set({ viz_sensitivity: v }, 300)} />
          </div>
        </>
      )}
    </>
  )
}

const ACTIONS: [ButtonAction, string][] = [
  ['settings', 'Открыть настройки'],
  ['inputs', 'Открыть входы'],
  ['home', 'На главный экран'],
  ['back', 'Назад'],
  ['app', 'Открыть приложение…'],
  ['wifi', 'Открыть Wi‑Fi'],
  ['bluetooth', 'Открыть Bluetooth и пульты'],
  ['display', 'Open Изображение & sound'],
  ['apps', 'Открыть список приложений'],
  ['sleep', 'Запустить заставку'],
  ['search', 'Открыть поиск'],
  ['music', 'Музыкальные полосы on or off'],
  ['panel', 'Open Быстрые настройки'],
  ['themes', 'Open Темы'],
  ['fireplace', 'Показать камин'],
  ['next_wallpaper', "Следующее изображение темы"],
  ['none', 'Ничего не делать']
]

/** KEYCODE_PROG_RED -> "Красная кнопка". */
const keyName = (name: string): string => {
  const s = name.replace(/^KEYCODE_/, '').toLowerCase().replace(/_/g, ' ')
  return s.charAt(0).toUpperCase() + s.slice(1)
}

/**
 * Xiaomi's Mi (PatchWall) button arrives as KEY_WWW, scancode 150, which Android hands to apps as
 * KEYCODE_EXPLORER. Once PatchWall is off nothing answers it (#38; upstream redmi-mitv-mooq3 notes).
 */
const MI_BUTTON = 64

function ButtonsTab(props: { cfg: HomeConfig; set: SetFn; apps: TvApp[]; setCfg: (c: HomeConfig) => void; brand?: string; onError: (e: unknown) => void }): ReactNode {
  const { cfg, set, apps } = props
  const [switching, setSwitching] = useState(false)
  const [listening, setСлушатьing] = useState(false)
  const [heard, setHeard] = useState<KeyPress[]>([])
  const [names, setNames] = useState<Record<number, string>>({})

  async function toggle(on: boolean): Promise<void> {
    setSwitching(true)
    try {
      props.setCfg(await window.cytv.setRemoteButtons(on))
    } catch (e) {
      props.onError(e)
    } finally {
      setSwitching(false)
    }
  }

  // Слушать for 30 seconds: show every button the TV receives from now on.
  useEffect(() => {
    if (!listening) return
    let stop = false
    let since = Number.MAX_SAFE_INTEGER
    const end = Date.now() + 30_000
    void (async () => {
      const first = await window.cytv.homeKeys().catch(() => [])
      since = first.length ? first[first.length - 1].seq : 0
      setHeard([])
      while (!stop && Date.now() < end) {
        await new Promise((r) => setTimeout(r, 600))
        const keys = await window.cytv.homeKeys().catch(() => [])
        const fresh = keys.filter((k) => k.seq > since)
        if (fresh.length) {
          since = fresh[fresh.length - 1].seq
          setHeard((prev) => [...prev, ...fresh].slice(-12))
          setNames((prev) => ({ ...prev, ...Object.fromEntries(fresh.map((k) => [k.code, k.name])) }))
        }
      }
      if (!stop) setСлушатьing(false)
    })()
    return () => { stop = true }
  }, [listening])

  const mapped = new Set(cfg.buttons.map((b) => b.code))
  const update = (buttons: HomeButton[]): void => set({ buttons })
  const label = (code: number): string => (names[code] ? `${keyName(names[code])} · ${code}` : `Button ${code}`)
  const xiaomi = /xiaomi|redmi/i.test(props.brand ?? '')
  const miDone = cfg.buttons_on && cfg.buttons.some((b) => b.code === MI_BUTTON && b.action === 'home')

  async function miButtonHome(): Promise<void> {
    setSwitching(true)
    try {
      // In order: the button first, then the service, so neither answer overwrites the other.
      props.setCfg(await window.cytv.setHomeConfig({ buttons: [...cfg.buttons.filter((b) => b.code !== MI_BUTTON), { code: MI_BUTTON, action: 'home' }] }))
      if (!cfg.buttons_on) props.setCfg(await window.cytv.setRemoteButtons(true))
    } catch (e) {
      props.onError(e)
    } finally {
      setSwitching(false)
    }
  }

  return (
    <>
      {xiaomi && !miDone && (
        <div className="notice">
          <p style={{ margin: '0 0 10px' }}>
            The Mi button on Xiaomi remotes opens PatchWall, so it does nothing once PatchWall is off. CleanMyTV Home can make it go
            Home instead. If it still does nothing after this, press Слушать below and press the Mi button to see what it sends.
          </p>
          <button className="btn small" disabled={switching} onClick={miButtonHome}>
            Make the Mi button go Home
          </button>
        </div>
      )}
      <div className="card">
        <Toggle
          label="Кнопки пульта"
          hint="Lets CleanMyTV Home give buttons a new job, in every app. Only the buttons you add below are touched; every other button works as before. It uses an Android accessibility service, switched on and off from here."
          checked={cfg.buttons_on}
          disabled={switching}
          onChange={toggle}
        />
      </div>

      <div className="card">
        <div className="card-head">
          <h2>Найти кнопку</h2>
          <button className="btn small" onClick={() => setСлушатьing(!listening)}>
            {listening ? 'Остановить прослушивание' : 'Слушать'}
          </button>
        </div>
        <p className="muted small">
          Press Слушать, then press buttons on the remote. Each one the TV receives shows here with its code.
          {!cfg.buttons_on && ' With Кнопки пульта off, only presses on the home screen show up.'} A button that never shows up
          (often Power, volume or the mic) is handled before Android sees it, so it can't get a new job on this TV.
        </p>
        {listening && heard.length === 0 && <Busy text="Слушатьing. Press a button on the remote" />}
        {heard.length > 0 && (
          <ul className="order-list">
            {heard.map((k) => (
              <li key={k.seq}>
                <span className="grow">
                  <b>{keyName(k.name)}</b>
                  <span className="muted small">code {k.code}</span>
                </span>
                <button className="btn small" disabled={mapped.has(k.code)} onClick={() => update([...cfg.buttons, { code: k.code, action: 'settings' }])}>
                  {mapped.has(k.code) ? 'Добавлено' : 'Назначить действие'}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>Действия кнопок</h2>
        {cfg.buttons.length === 0 ? (
          <p className="muted small">Нет yet. Найти кнопку above and give it a job.</p>
        ) : (
          <ul className="order-list">
            {cfg.buttons.map((b, i) => (
              <li key={b.code}>
                <span className="grow">
                  <b>{label(b.code)}</b>
                </span>
                <select
                  value={b.action}
                  aria-label={`Job for ${label(b.code)}`}
                  onChange={(e) => {
                    const action = e.target.value as ButtonAction
                    update(cfg.buttons.map((x, j) => (j === i ? { code: x.code, action, ...(action === 'app' ? { app: apps[0]?.pkg } : {}) } : x)))
                  }}
                >
                  {ACTIONS.map(([id, text]) => (
                    <option key={id} value={id}>
                      {text}
                    </option>
                  ))}
                </select>
                {b.action === 'app' && (
                  <select value={b.app ?? ''} aria-label="Открываемое приложение" onChange={(e) => update(cfg.buttons.map((x, j) => (j === i ? { ...x, app: e.target.value } : x)))}>
                    {apps.map((a) => (
                      <option key={a.pkg} value={a.pkg}>
                        {a.label}
                      </option>
                    ))}
                  </select>
                )}
                <button className="btn small ghost" onClick={() => update(cfg.buttons.filter((_, j) => j !== i))}>
                  Удалить
                </button>
              </li>
            ))}
          </ul>
        )}
        {cfg.buttons.length > 0 && !cfg.buttons_on && <p className="small muted" style={{ marginTop: 8 }}>Turn on Кнопки пульта above for these to work.</p>}
      </div>
    </>
  )
}

/** A rough picture of the chosen bars, moving to a made-up beat. */
function Количество полосПредпросмотр({ cfg }: { cfg: HomeConfig }): ReactNode {
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
    body = <polyline points={pts.join(' ')} fill="none" stroke={colour(0)} strokeНепрозрачность={op} strokeWidth={3} strokeLinejoin="round" />
  } else {
    const mirror = cfg.viz_style === 'mirror'
    const base = mirror ? H / 2 : H
    const span = mirror ? H / 2 : H
    body = levels.map((v, i) => (
      <g key={i}>
        <rect x={gap + i * (w + gap)} y={base - v * span} width={w} height={v * span} rx={Math.min(w / 3, 4)} fill={colour(i)} fillНепрозрачность={op} />
        {mirror && <rect x={gap + i * (w + gap)} y={base} width={w} height={v * span} rx={Math.min(w / 3, 4)} fill={colour(i)} fillНепрозрачность={op * 0.3} />}
        {cfg.viz_peaks && <rect x={gap + i * (w + gap)} y={base - Math.min(1, v + 0.08) * span - 4} width={w} height={3} rx={1.5} fill={colour(i)} fillНепрозрачность={op} />}
      </g>
    ))
  }
  return (
    <div style={{ background: cfg.background, borderRadius: 10, padding: '40px 0 0', marginTop: 12, overflow: 'hidden' }} aria-label="Предпросмотр музыкальных полос">
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" style={{ display: 'block' }}>
        {body}
      </svg>
    </div>
  )
}

/** Typed twice, digits only, one box per digit. */
function PinForm({ label, onSet }: { label: string; onSet: (pin: string) => Promise<void> }): ReactNode {
  const [a, setA] = useState('')
  const [b, setB] = useState('')
  const [busy, setBusy] = useState(false)
  const note = a.length > 0 && a.length < 4 ? 'Не менее 4 цифр.' : b.length >= a.length && b && a !== b ? "The two PIN-кодs don't match." : ''
  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="pin-row">
        <span>PIN-код</span>
        <PinBoxes value={a} onChange={setA} label="PIN-код" />
      </div>
      <div className="pin-row">
        <span>Повторите</span>
        <PinBoxes value={b} onChange={setB} label="PIN-код again" />
      </div>
      <div className="row">
        <ActionButton
          phase={busy ? 'busy' : 'idle'}
          label={label}
          busyLabel="Отправка"
          disabled={a.length < 4 || a !== b}
          onClick={() => {
            setBusy(true)
            onSet(a).finally(() => {
              setBusy(false)
              setA('')
              setB('')
            })
          }}
        />
        <span className="small muted">{note || "4 to 6 digits. Type it on the TV with the remote's number keys or the on-screen pad."}</span>
      </div>
    </div>
  )
}

function LockTab(props: { cfg: HomeConfig; set: SetFn; apps: TvApp[]; setCfg: (c: HomeConfig) => void; onError: (e: unknown) => void }): ReactNode {
  const { cfg, set, apps, setCfg, onError } = props
  const [q, setQ] = useState('')
  const [changing, setChanging] = useState(false)
  const [forgetting, setForgetting] = useState(false)
  const locked = new Set(cfg.lock_apps)
  const shown = apps.filter((a) => !q || a.label.toLowerCase().includes(q.toLowerCase()) || a.pkg.includes(q.toLowerCase()))
  const call = (p: Promise<HomeConfig>): Promise<void> => p.then(setCfg, onError)
  if (!cfg.lock_pin_set) {
    return (
      <div className="card">
        <h2>Задать PIN-код-код</h2>
        <p className="muted">
          Заблокированные приложения ask for this PIN-код on the TV before they open, however they're opened: from Home, by voice, or from Settings.
          The TV keeps it scrambled, and this app can't read it back.
        </p>
        <PinForm label="Задать PIN-код-код" onSet={(pin) => call(window.cytv.setLockPin(pin))} />
      </div>
    )
  }
  const toggle = (pkg: string): void => set({ lock_apps: locked.has(pkg) ? cfg.lock_apps.filter((p) => p !== pkg) : [...cfg.lock_apps, pkg] })
  return (
    <>
      <div className="card">
        <h2>Блокировка приложений</h2>
        <Toggle
          label="Блокировка приложений on"
          hint={cfg.lock_on ? `${cfg.lock_apps.length} ${cfg.lock_apps.length === 1 ? 'app is' : 'приложения'} locked${cfg.lock_system ? ', plus Settings, the Play Store and Быстрые настройки' : ''}.` : 'Your PIN-код and the list stay while it\'s off.'}
          checked={cfg.lock_on}
          onChange={(v) => call(window.cytv.setAppLock(v))}
        />
        <p className="small muted" style={{ marginBottom: 0 }}>
          A family lock: it stops a quick tap, not someone with this app or a factory reset. Anyone with ADB access to the TV can turn it off.
        </p>
      </div>

      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2 style={{ margin: 0 }}>Заблокированные приложения</h2>
          <span className="row" style={{ gap: 8 }}>
            <button className="btn small ghost" onClick={() => set({ lock_apps: [...new Set([...cfg.lock_apps, ...shown.map((a) => a.pkg)])] })}>Заблокировать все показанные</button>
            <button className="btn small ghost" disabled={!cfg.lock_apps.length} onClick={() => set({ lock_apps: [] })}>Разблокировать все</button>
          </span>
        </div>
        <input type="search" placeholder="Найти приложение" value={q} onChange={(e) => setQ(e.target.value)} style={{ margin: '12px 0', width: '100%' }} aria-label="Найти приложение" />
        <div className="list-picker" style={{ maxВысота: 360 }}>
          {!q && (
            <label className="row" style={{ gap: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={cfg.lock_system} onChange={(e) => set({ lock_system: e.target.checked })} />
              <span>Settings, Play Store and Быстрые настройки</span>
              <span className="small muted">а также удаление приложений — чтобы блокировку нельзя было обойти через настройки</span>
            </label>
          )}
          {shown.map((a) => (
            <label key={a.pkg} className="row" style={{ gap: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={locked.has(a.pkg)} onChange={() => toggle(a.pkg)} />
              <span>{a.label}</span>
              <span className="small muted mono">{a.pkg}</span>
            </label>
          ))}
          {!shown.length && <p className="small muted">Приложения не найдены.</p>}
        </div>
      </div>

      <div className="card">
        <h2>Параметры</h2>
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Разблокировка действует</p>
        <div className="seg">
          {([['sleep', 'До перехода ТВ в спящий режим'], ['15', '15 минут'], ['always', 'До запуска другого приложения']] as const).map(([id, label]) => (
            <button key={id} className={cfg.lock_for === id ? 'on' : ''} onClick={() => set({ lock_for: id })}>{label}</button>
          ))}
        </div>
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Заблокированные приложения on Home</p>
        <div className="seg">
          {([['badge', 'Показывать их с замком'], ['hide', 'Скрыть их']] as const).map(([id, label]) => (
            <button key={id} className={cfg.lock_show === id ? 'on' : ''} onClick={() => set({ lock_show: id })}>{label}</button>
          ))}
        </div>
      </div>

      <div className="card">
        <h2>PIN-код</h2>
        {changing ? (
          <PinForm label="Изменить PIN-код-код" onSet={(pin) => call(window.cytv.setLockPin(pin)).then(() => setChanging(false))} />
        ) : (
          <div className="row">
            <button className="btn ghost" onClick={() => setChanging(true)}>Изменить PIN-код-код</button>
            <button className="btn ghost" onClick={() => setForgetting(true)}>Forget the PIN-код and turn the lock off</button>
          </div>
        )}
        <p className="small muted" style={{ marginBottom: 0 }}>Забыли PIN-код? Измените его здесь: этот компьютер всегда может задать новый.</p>
      </div>
      {forgetting && (
        <Confirm title="Forget the PIN-код?" confirmLabel="Сбросить" onConfirm={() => { setForgetting(false); call(window.cytv.clearAppLock()) }} onCancel={() => setForgetting(false)}>
          <p>Блокировка отключится, и все приложения будут доступны. Список заблокированных приложений сохранится.</p>
        </Confirm>
      )}
    </>
  )
}

/** The fireplace: a looping video with its crackle, sent to the TV once, then used as a screensaver, full screen or behind Home. */
function КаминCard({ cfg, set, setCfg, onResult }: { cfg: HomeConfig; set: SetFn; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void }): ReactNode {
  const [phase, setPhase] = usePhase()
  const on = !!cfg.fire_on_tv
  const go = async (action: 'put' | 'show' | 'saver' | 'remove'): Promise<void> => {
    if (action === 'put') setPhase('busy')
    try {
      const r = await window.cytv.fireplace(action)
      if (action === 'put' || action === 'remove') setCfg(await window.cytv.readHomeConfig())
      if (action === 'put') setPhase('done')
      onResult(r)
    } catch (e) {
      if (action === 'put') setPhase('failed')
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  return (
    <div className="card">
      <h2>Камин</h2>
      <p className="muted">
        A real log fire with its crackle, looping without a seam. It's about 11 MB, downloaded once and sent to the TV. Video and
        sound from Pixabay.
      </p>
      <div className="row" style={{ marginBottom: 12 }}>
        <ActionButton
          phase={phase}
          primary={!on}
          label={on ? 'Send it again' : 'Установить камин на телевизор'}
          busyLabel="Отправка"
          doneLabel="На телевизоре"
          onClick={() => void go('put')}
        />
        {on && <button className="btn" onClick={() => void go('show')}>Показать сейчас</button>}
        {on && <button className="btn ghost" onClick={() => void go('saver')}>Установить как заставку</button>}
      </div>
      {on && (
        <>
          <Toggle label="На фоне главного экрана" hint="Отображается вместо обоев. Изображение останавливается, когда открыто приложение или телевизор's animations are off." checked={cfg.fire_wall} onChange={(v) => set({ fire_wall: v })} />
          <Toggle label="Полноэкранный эффект потрескивания и заставка" checked={cfg.fire_sound} onChange={(v) => set({ fire_sound: v })} />
          <Toggle label="Показывать эффект потрескивания и на главном экране" hint="По умолчанию выключено, чтобы не отвлекать при просмотре." checked={cfg.fire_wall_sound} onChange={(v) => set({ fire_wall_sound: v })} />
          <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Выключить через</p>
          <div className="seg" style={{ marginBottom: 6 }}>
            {([[0, 'Никогда'], [30, '30 min'], [60, '1 час'], [120, '2 часа']] as const).map(([m, label]) => (
              <button key={m} className={cfg.fire_timer === m ? 'on' : ''} onClick={() => set({ fire_timer: m })}>{label}</button>
            ))}
          </div>
          <p className="small muted" style={{ marginTop: 0 }}>
            Full screen only: the fire fades out, then the TV sleeps. Press OK on the remote to change it while the fire is on.
          </p>
          {(cfg.fire_sound || cfg.fire_wall_sound) && <Slider label="Громкость" value={cfg.fire_volume} min={0} max={100} step={5} unit="%" onChange={(v) => set({ fire_volume: v }, 250)} />}
          <p style={{ marginTop: 10 }}>
            <button className="btn ghost" onClick={() => void go('remove')}>Удалить с телевизора</button>
          </p>
        </>
      )}
    </div>
  )
}

const SLIDE_MAX = 12
const SLIDE_TIMES: [number, string][] = [[0, 'Off'], [5, '5 min'], [15, '15 min'], [30, '30 min'], [60, '1 час']]

/** Слайд-шоу (#56): a new wallpaper every few minutes, from pictures picked here or the current theme's own. */
function Слайд-шоуCard({ cfg, set, setCfg, onResult }: { cfg: HomeConfig; set: SetFn; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void }): ReactNode {
  const [phase, setPhase] = usePhase()
  const file = useRef<HTMLInputElement>(null)
  const mine = cfg.slides_on_tv ?? 0
  const send = async (files: File[]): Promise<void> => {
    setPhase('busy')
    try {
      const jpegs: string[] = []
      for (const f of files.slice(0, SLIDE_MAX)) jpegs.push((await prepareОбои(f, f.name)).base64)
      setCfg(await window.cytv.setSlides(jpegs))
      setPhase('done')
      if (files.length > SLIDE_MAX) onResult({ ok: true, summary: `The slideshow takes ${SLIDE_MAX} pictures, so the first ${SLIDE_MAX} went to the TV.`, warnings: [] })
    } catch (e) {
      setPhase('failed')
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  return (
    <div className="card">
      <h2>Слайд-шоу</h2>
      <p className="muted">Автоматически меняет обои каждые несколько минут, пока открыт главный экран. Для каждого изображения используется эффект «Рассвет».</p>
      <p style={{ margin: '6px 0 8px', fontWeight: 600 }}>Изображения</p>
      <div className="seg" style={{ marginBottom: 12 }}>
        <button className={cfg.slide_from === 'mine' ? 'on' : ''} onClick={() => set({ slide_from: 'mine' })}>My pictures{mine ? ` (${mine})` : ''}</button>
        <button className={cfg.slide_from === 'theme' ? 'on' : ''} onClick={() => set({ slide_from: 'theme' })}>Изображения темы</button>
      </div>
      {cfg.slide_from === 'mine' && (
        <div className="row" style={{ marginBottom: 12 }}>
          <input
            ref={file}
            type="file"
            accept="image/*"
            multiple
            hidden
            onChange={(e) => {
              const fs = [...(e.target.files ?? [])]
              e.target.value = ''
              if (fs.length) void send(fs)
            }}
          />
          <ActionButton phase={phase} label={mine ? 'Choose new pictures…' : 'Choose pictures…'} busyLabel="Отправка" doneLabel="На телевизоре" onClick={() => file.current?.click()} />
          {mine > 0 && <button className="btn ghost" onClick={() => void send([])}>Удалить изображения</button>}
          <span className="small muted">Up to {SLIDE_MAX}. They replace the ones on the TV.</span>
        </div>
      )}
      {cfg.slide_from === 'theme' && (
        <p className="small muted">Steps through the three pictures of the theme you picked on the TV. Загрузить темы на телевизор first (Темы, above).</p>
      )}
      <p style={{ margin: '6px 0 8px', fontWeight: 600 }}>Интервал смены</p>
      <div className="seg">
        {SLIDE_TIMES.map(([m, label]) => (
          <button key={m} className={cfg.slide_minutes === m ? 'on' : ''} disabled={m > 0 && cfg.slide_from === 'mine' && mine === 0} onClick={() => set({ slide_minutes: m })}>{label}</button>
        ))}
      </div>
    </div>
  )
}

const ART_KINDS: [ThemeArt['kind'], string][] = [
  ['aurora', 'Aurora'], ['ridges', 'Ridges'], ['dunes', 'Dunes'], ['flow', 'Flow'], ['stars', 'Stars'],
  ['contours', 'Contours'], ['bokeh', 'Bokeh'], ['horizon', 'Horizon'], ['rings', 'Rings']
]

/** A colour mixed toward black by [k] (0 to 1), as #RRGGBB. */
function darken(hex: string, k: number): string {
  const n = parseInt(hex.slice(1), 16)
  const c = [n >> 16, (n >> 8) & 255, n & 255].map((v) => Math.round(v * (1 - k)))
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase()
}

/** Theme editor: start from a built-in theme, change its colours and art, see it, and put it on the TV. */
function ThemeEditor({ themes, busy, onЗакрыть }: { themes: ThemeActions; busy: boolean; onЗакрыть: () => void }): ReactNode {
  const startFrom = (id: string): { accent: string; second: string; background: string; kind: ThemeArt['kind']; seed: number; base: BuiltInTheme } => {
    const t = BUILT_IN_THEMES.find((b) => b.id === id) ?? BUILT_IN_THEMES[0]
    return {
      base: t,
      accent: (t.look.accent as string) ?? '#4CAF50',
      second: t.paint.art?.colours[1] ?? t.paint.glows[1]?.colour ?? t.paint.glows[0]?.colour ?? '#888888',
      background: t.paint.base[0],
      kind: t.paint.art?.kind ?? 'aurora',
      seed: t.paint.art?.seed ?? 1
    }
  }
  const [s, setS] = useState(() => startFrom(BUILT_IN_THEMES.find((t) => themes.current.startsWith(`${t.id}:`))?.id ?? BUILT_IN_THEMES[0].id))
  const [name, setName] = useState('My theme')
  const canvas = useRef<HTMLCanvasElement>(null)
  const paint: ThemePaint = useMemo(() => ({
    base: [s.background, darken(s.background, 0.45)],
    glows: [
      { colour: s.accent, x: 0.12, y: 0.12, r: 0.45, a: 0.16 },
      { colour: s.second, x: 0.9, y: 0.92, r: 0.4, a: 0.11 }
    ],
    art: { kind: s.kind, colours: [s.accent, s.second], seed: s.seed }
  }), [s])
  useEffect(() => {
    const c = canvas.current
    if (!c) return
    const drawn = drawОбои(paint, 384, 216)
    c.getContext('2d')?.drawImage(drawn, 0, 0)
  }, [paint])
  const use = (): void => themes.custom({
    name: name.trim() || 'My theme',
    look: { ...s.base.look, accent: s.accent, background: darken(s.background, 0.45) },
    wallpaper: paintОбои(paint, 'theme.jpg').base64
  })
  return (
    <div className="theme-editor">
      <div className="row" style={{ alignItems: 'flex-start', gap: 20 }}>
        <canvas ref={canvas} width={384} height={216} className="theme-editor-preview" style={{ borderColor: s.accent }} aria-label="Предпросмотр" />
        <div style={{ flex: 1, minWidth: 240 }}>
          <label className="field">
            <span>Начать с</span>
            <select value={s.base.id} onChange={(e) => setS(startFrom(e.target.value))}>
              {BUILT_IN_THEMES.map((t) => <option key={t.id} value={t.id}>{t.name}</option>)}
            </select>
          </label>
          <Цвет label="Акцент" value={s.accent} onChange={(c) => setS({ ...s, accent: c })} />
          <Цвет label="Второй цвет" hint="Дополнительный цвет изображения." value={s.second} onChange={(c) => setS({ ...s, second: c })} />
          <Цвет label="Фон" hint="Оставить тёмным: главный экран's text is white." value={s.background} onChange={(c) => setS({ ...s, background: c })} />
        </div>
      </div>
      <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>Изображение</p>
      <div className="seg" style={{ flexWrap: 'wrap' }}>
        {ART_KINDS.map(([k, label]) => (
          <button key={k} className={s.kind === k ? 'on' : ''} onClick={() => setS({ ...s, kind: k })}>{label}</button>
        ))}
      </div>
      <div className="row" style={{ marginTop: 14 }}>
        <button className="btn ghost" onClick={() => setS({ ...s, seed: s.seed + 1 })}>Новый узор</button>
        <input type="text" value={name} maxLength={40} onChange={(e) => setName(e.target.value)} aria-label="Название темы" style={{ width: 180 }} />
        <button className="btn primary" disabled={busy} onClick={use}>Применить на телевизоре</button>
        <button className="btn ghost" onClick={onЗакрыть}>Закрыть</button>
      </div>
      <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
        Then Save this look… below keeps it as a .{THEME_EXT} file to share.
      </p>
    </div>
  )
}
