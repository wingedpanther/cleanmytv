import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, ButtonAction, HomeButton, HomeConfig, HomeGroup, HomeTheme, ThemeBackup, HomeLayout, HomeSection, HomeStatus, KeyPress, LookSlot, QuickEntry, QuickId, TvApp } from '@shared/api'
import type { ScreenProps } from '../App'
import { AppOrder } from '../components/AppOrder'
import { ScreenTimeTab } from './ScreenTimeTab'
import { HomePreview, QUICK_LABEL, QUICK_NAME, QuickMark, quickApp, quickLabel } from '../components/HomePreview'
import { ActionButton, Busy, Confirm, PinBoxes, ResultNotice, Slider, StatusChip, Toggle, errorText, usePhase } from '../components/ui'
import { prepareWallpaper, thumbnail } from '../lib/image'
import { fetchBingDaily } from '../lib/backgrounds'
import { findPlaces, type Place } from '../lib/places'
import { drawWallpaper, paintWallpaper } from '../lib/themes'
import { t, tx } from '../lib/i18n'
import { BUILT_IN_THEMES, THEME_EXT, THEME_PICTURES, parseTheme, withPicture, type BuiltInTheme, type ThemeArt, type ThemePaint } from '@shared/themes'

type Tab = 'overview' | 'apps' | 'layout' | 'look' | 'widgets' | 'music' | 'buttons' | 'lock' | 'screentime'

const TABS: [Tab, string][] = [
  ['overview', t('Overview')],
  ['layout', t('Layout')],
  ['apps', t('Apps, rows & folders')],
  ['look', t('Look & wallpaper')],
  ['widgets', t('Widgets')],
  ['music', t('Music bars')],
  ['buttons', t('Remote buttons')],
  ['lock', t('App lock')],
  ['screentime', t('Screen time')]
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
  const [apps, setApps] = useState<TvApp[] | null>(null)
  const [tab, setTab] = useState<Tab>('overview')
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [saving, setSaving] = useState(false)
  const [askStock, setAskStock] = useState(false)
  const [stockPick, setStockPick] = useState<StockPick | null>(null)
  const [themeUsed, setThemeUsed] = useState<string | null>(null)
  const [themeBackup, setThemeBackup] = useState<ThemeBackup | null>(null)
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
  // Input tiles join the TV's app list once the launcher has the setting.
  const tiles = cfg?.input_tiles
  useEffect(() => {
    if (tiles === undefined) return
    const id = setTimeout(() => window.cytv.homeApps().then(setApps).catch(() => undefined), 1000)
    return () => clearTimeout(id)
  }, [tiles])

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
      setCfg(await window.cytv.setHomeWallpaper(jpeg))
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
      setBusy(t('Putting the themes on the TV'))
      await window.cytv.themePackBegin()
      let i = 0
      for (const theme of BUILT_IN_THEMES) {
        for (let n = 0; n < THEME_PICTURES; n++) {
          setBusy(t('Putting the themes on the TV: picture {i} of {total}', { i: ++i, total }))
          const jpeg = paintWallpaper(withPicture(theme.paint, n), `${theme.id}-${n}.jpg`).base64
          await window.cytv.themePackPut({ id: theme.id, name: theme.name, look: theme.look, picture: n, jpeg })
        }
      }
      setCfg(await window.cytv.themePackEnd(BUILT_IN_THEMES.map((b) => b.id)))
      setResult({
        ok: true,
        summary: t('{n} themes are on the TV, {pictures} pictures each. Open them with the Themes icon (Layout → top bar icons) or a remote button.', { n: BUILT_IN_THEMES.length, pictures: THEME_PICTURES }),
        warnings: []
      })
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  /** #145: the built-in themes come off; ones saved on the TV stay, and so does the look on it now. */
  async function themesOffTv(): Promise<void> {
    setResult(null)
    try {
      setBusy(t('Taking the themes off the TV'))
      const next = await window.cytv.themePackRemove()
      setCfg(next)
      const left = next.themes_on_tv ?? 0
      setResult({
        ok: true,
        summary: left
          ? t('The built-in themes are off the TV. The {n} you saved on the TV stay.', { n: left })
          : t('The themes are off the TV. Your look stays as it is.'),
        warnings: []
      })
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
    }
  }

  const readThemeBackup = (): void => {
    window.cytv.themeBackup().then(setThemeBackup, () => setThemeBackup(null))
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
      readThemeBackup()
    }
  }

  // Before the first theme, the look gets saved; say so first if the wallpaper can't be part of it.
  const guarded = (go: () => void): void => {
    if (themeBackup?.savedAt === null && themeBackup.wallpaperAtRisk) setRiskyTheme(() => go)
    else go()
  }

  const themes: ThemeActions = {
    used: themeUsed,
    builtIn: (id, picture = 0) => {
      const theme = BUILT_IN_THEMES.find((b) => b.id === id)
      if (theme) guarded(() => themeStep(t('Putting {name} on the TV', { name: theme.name }), async () =>
        window.cytv.applyHomeTheme({ name: theme.name, look: theme.look, wallpaper: paintWallpaper(withPicture(theme.paint, picture), `${theme.id}.jpg`).base64, from: `${theme.id}:${picture}` }), theme.name))
    },
    current: cfg?.wallpaper_from ?? '',
    another: () => {
      const [id, n] = (cfg?.wallpaper_from ?? '').split(':')
      const theme = BUILT_IN_THEMES.find((b) => b.id === id)
      if (!theme) return
      const next = ((Number(n) || 0) + 1) % THEME_PICTURES
      themeStep(t('Drawing another picture for {name}', { name: theme.name }), async () =>
        window.cytv.setHomeWallpaper(paintWallpaper(withPicture(theme.paint, next), `${theme.id}.jpg`).base64).then(() =>
          window.cytv.setHomeConfig({ wallpaper_from: `${theme.id}:${next}` })), theme.name)
    },
    onTv: cfg?.themes_on_tv ?? 0,
    toTv: () => themesToTv(),
    offTv: () => themesOffTv(),
    custom: (theme) => guarded(() => themeStep(t('Putting {name} on the TV', { name: theme.name }), () => window.cytv.applyHomeTheme(theme), theme.name)),
    file: (text) => {
      let theme: HomeTheme
      try {
        theme = parseTheme(text)
      } catch (e) {
        setResult({ ok: false, summary: errorText(e), warnings: [] })
        return
      }
      guarded(() => themeStep(t('Putting {name} on the TV', { name: theme.name }), () => window.cytv.applyHomeTheme(theme), theme.name))
    },
    backup: themeBackup,
    restore: () => themeStep(t('Putting your saved look back'), () => window.cytv.restoreThemeBackup(), null),
    forget: () => window.cytv.forgetThemeBackup().then(readThemeBackup),
    saveOnTv: (name) =>
      themeStep(t('Saving {name} on the TV', { name: name.trim() }), async () => {
        const c = await window.cytv.saveThemeOnTv(name)
        setResult({ ok: true, summary: t('{name} is a theme on the TV now. Pick it under Themes in Customize on the TV, or with the Themes icon.', { name: name.trim() }), warnings: [] })
        return c
      }, name.trim()),
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
    if (ready) readThemeBackup()
  }, [ready])
  // Why the other tabs are greyed out (#38).
  const locked = !home?.installed
    ? t('Install CleanMyTV Home on the Overview tab first.')
    : outdated(home)
      ? t('Press Update and use on the Overview tab first.')
      : t("Waiting for CleanMyTV Home's settings. If this stays, reconnect from Setup.")

  return (
    <>
      <h1>{t('Home screen')}</h1>
      <p className="lede">
        {t("CleanMyTV Home is the TV's new home screen: no ads, your apps, your layout. Install it once, then change anything here; the TV shows it straight away and keeps it after a reboot. The stock launcher is one click away on the Overview tab.")}
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
        <Busy text={t('Asking the TV about CleanMyTV Home')} />
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
              {t('The TV has CleanMyTV Home {tv}; this app carries {app}. Press Update and use below to change its layout, apps, look and remote buttons from here. The other tabs open after that.', { tv: home.versionName ?? '?', app: home.bundledVersion ?? '?' })}
            </div>
          )}
          {tab === 'overview' && (
            <Overview home={home} busy={!!busy} busyText={busy} run={run} askStock={() => setAskStock(true)} host={status.host} onShot={refreshTvs} />
          )}
          {ready && tab === 'apps' && apps && <AppsTab apps={apps} cfg={cfg} set={set} />}
          {ready && tab === 'layout' && apps && <LayoutTab apps={apps} cfg={cfg} set={set} sdk={status.probe?.sdk} />}
          {ready && tab === 'look' && (
            <>
              <LookTab cfg={cfg} set={set} busy={!!busy} wallpaper={wallpaper} themes={themes} />
              <DayNightCard cfg={cfg} set={set} setCfg={setCfg} onResult={setResult} />
              <SlideshowCard cfg={cfg} set={set} setCfg={setCfg} onResult={setResult} />
              <FireplaceCard cfg={cfg} set={set} setCfg={setCfg} onResult={setResult} sdk={status.probe?.sdk} />
            </>
          )}
          {ready && tab === 'widgets' && (
            <>
              <WidgetsTab cfg={cfg} set={set} />
              <NoteCard cfg={cfg} setCfg={setCfg} onResult={setResult} />
              {apps && <GuestCard cfg={cfg} set={set} apps={apps} />}
            </>
          )}
          {ready && tab === 'music' && <MusicTab cfg={cfg} set={set} />}
          {ready && tab === 'lock' && apps && <LockTab cfg={cfg} set={set} apps={apps} setCfg={setCfg} onError={(e) => setResult({ ok: false, summary: errorText(e), warnings: [] })} />}
          {ready && tab === 'screentime' && apps && <ScreenTimeTab cfg={cfg} set={set} apps={apps} />}
          {ready && tab === 'buttons' && apps && <ButtonsTab cfg={cfg} set={set} apps={apps} setCfg={setCfg} brand={status.probe?.manufacturer} onError={(e) => setResult({ ok: false, summary: errorText(e), warnings: [] })} />}
          {ready && tab !== 'overview' && <p className="small muted">{saving ? t('Sending to the TV…') : t('Saved on the TV.')}</p>}
        </>
      )}

      {riskyTheme && (
        <Confirm
          title={t("Your wallpaper can't be saved")}
          confirmLabel={t('Use the theme anyway')}
          onCancel={() => setRiskyTheme(null)}
          onConfirm={() => {
            const go = riskyTheme
            setRiskyTheme(null)
            go()
          }}
        >
          <p>
            {t("Before the first theme, the app saves your look so you can put it back. Your wallpaper was set by an older CleanMyTV, which kept no copy of it, so it can't be part of that: putting your look back keeps the theme's wallpaper.")}
          </p>
          <p>{t('To keep it, set the same picture again first, under Wallpaper below.')}</p>
        </Confirm>
      )}

      {askStock && (
        <Confirm
          title={t('Use the stock launcher again?')}
          confirmLabel={t('Switch back')}
          onConfirm={() => {
            setAskStock(false)
            run(t('Giving Home back to the stock launcher'), () => window.cytv.useStockLauncher())
          }}
          onCancel={() => setAskStock(false)}
        >
          <p>{t("The app switches the TV's own launcher back on and makes it Home. CleanMyTV Home stays installed, so you can switch again.")}</p>
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
      <h2>{t("Which one is the TV's own launcher?")}</h2>
      {pick.choices.length === 0 ? (
        <p className="muted">
          {t('Nothing on the TV can be Home apart from CleanMyTV Home, so there is no launcher to fall back on. Leave it as it is and report your TV model on GitHub.')}
        </p>
      ) : (
        <>
          <p className="small muted">
            {t('These apps can be Home on this TV. Pick the one the TV came with. It stays on, and Undo and the restart check give Home back to it.')}
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
          {t('Use this one and carry on')}
        </button>
        <button className="btn ghost" onClick={onCancel}>
          {t('Cancel')}
        </button>
      </div>
    </div>
  )
}

function Overview(props: { home: HomeStatus; busy: boolean; run: (label: string, fn: () => Promise<ApplyResult>, retry?: (stock: string) => Promise<ApplyResult>) => Promise<void>; askStock: () => void; host?: string; onShot: () => void; busyText?: string | null }): ReactNode {
  const { home, busy, run } = props
  // The busy text tells this button it is the one running.
  const installing = t('Installing CleanMyTV Home. Keep the TV on.')
  const [shot, setShot] = useState<string | null>(null)
  const [shooting, setShooting] = useState(false)
  const [shotError, setShotError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  // Other launchers on the TV (#82): null until the owner asks.
  const [others, setOthers] = useState<string[] | null>(null)
  const [othersError, setOthersError] = useState<string | null>(null)

  async function findOthers(): Promise<void> {
    setOthersError(null)
    try {
      setOthers(await window.cytv.otherLaunchers())
    } catch (e) {
      setOthersError(errorText(e))
    }
  }

  async function snapshot(): Promise<void> {
    setShooting(true)
    setShotError(null)
    try {
      const s = await window.cytv.screenshot()
      setShot(s)
      setSaved(null)
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
          <h2>{t('On this TV')}</h2>
          <StatusChip status={home.isHome ? 'ok' : home.installed ? 'warn' : 'optional'} text={home.isHome ? t('Is Home') : home.installed ? t('Installed, not Home') : t('Not installed')} />
        </div>
        <div className="facts">
          <div>
            <span>{t('On the TV')}</span>
            <b>{home.installed ? t('version {v}', { v: home.versionName ?? '?' }) : t('not installed')}</b>
          </div>
          <div>
            <span>{t('In this app')}</span>
            <b>{home.bundled ? t('version {v}', { v: home.bundledVersion ?? '?' }) : t('no bundled copy')}</b>
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
              phase={props.busyText === installing ? 'busy' : 'idle'}
              label={home.installed ? t('Update and use') : t('Install and use')}
              busyLabel={home.installed ? t('Updating') : t('Installing')}
              disabled={busy || !home.bundled || !!home.unsupported}
              onClick={() => run(installing, () => window.cytv.installHome(), (s) => window.cytv.installHome(s))}
            />
          )}
          {home.installed && !home.isHome && (
            <button className="btn primary" disabled={busy} onClick={() => run(t('Switching Home to CleanMyTV Home'), () => window.cytv.useHome(), (s) => window.cytv.useHome(s))}>
              {t('Use CleanMyTV Home')}
            </button>
          )}
          {home.isHome && (
            <button className="btn" disabled={busy} onClick={props.askStock}>
              {t('Give Home back to the stock launcher')}
            </button>
          )}
          {home.isHome && home.googleLauncher && (
            <button className="btn ghost" disabled={busy} onClick={() => run(home.googleKept ? t("Switching Google TV's launcher off") : t("Keeping Google TV's launcher on"), () => window.cytv.keepGoogleLauncher(!home.googleKept))}>
              {home.googleKept ? t("Switch Google TV's launcher off again") : t("Keep Google TV's launcher on")}
            </button>
          )}
          {home.installed && others === null && (
            <button className="btn ghost" disabled={busy} onClick={() => void findOthers()}>
              {t('Use another launcher…')}
            </button>
          )}
        </div>
        {home.isHome && home.fireTv && (
          <p className="muted small" style={{ marginTop: 16, marginBottom: 0 }}>
            {t("Fire TV always opens Amazon's home screen first. CleanMyTV Home comes back in front a moment after you press Home, up to 5 seconds on some models.")}
          </p>
        )}
        {home.isHome && home.googleLauncher && (
          <p className="muted small" style={{ marginTop: 16, marginBottom: 0 }}>
            {home.googleKept
              ? t("Google TV's launcher is kept on, so apps that need it work. After a Home press, CleanMyTV Home comes back in front a moment later.")
              : t("If Apple TV or Netflix stops playing, or Google Photos albums won't load for the screensaver, keep Google TV's launcher on. Those apps need it. CleanMyTV Home then comes back in front a moment after you press Home.")}
          </p>
        )}
        {othersError && <div className="notice error" style={{ marginTop: 16, marginBottom: 0 }}>{othersError}</div>}
        {others !== null && (
          <div className="notice" style={{ marginTop: 16, marginBottom: 0 }}>
            {others.length === 0 ? (
              <p style={{ margin: 0 }}>{t('No other launcher is installed. Put one on with Install an app, then come back here.')}</p>
            ) : (
              <>
                <p style={{ margin: '0 0 10px' }}>
                  {t('These can be Home instead. Remote buttons, the app lock and everything in these tabs need CleanMyTV Home, so they stop until you switch back.')}
                </p>
                <div className="row">
                  {others.map((pkg) => (
                    <button key={pkg} className="btn small" disabled={busy} onClick={() => run(t('Making {pkg} Home', { pkg }), () => window.cytv.useOtherLauncher(pkg))}>
                      {t('Use {pkg}', { pkg })}
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
        {home.oldLauncher && (
          <div className="notice" style={{ marginTop: 16, marginBottom: 0 }}>
            <p style={{ margin: '0 0 10px' }}>
              {t('The CleanYourTV launcher from earlier versions of this app is still on the TV. CleanMyTV Home replaces it.')}
              {!home.isHome && ` ${t('Install or switch to CleanMyTV Home first.')}`}
            </p>
            <button className="btn small" disabled={busy || !home.isHome} onClick={() => run(t('Removing the CleanYourTV launcher'), () => window.cytv.removeOldLauncher())}>
              {t('Remove the CleanYourTV launcher')}
            </button>
          </div>
        )}
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t('What the TV shows')}</h2>
          <button className="btn small" onClick={snapshot} disabled={shooting}>
            {shooting ? t('Taking a snapshot…') : t('Take a snapshot')}
          </button>
        </div>
        {shotError && <div className="notice warn">{shotError}</div>}
        {shot ? (
          <>
            <img src={shot} alt={t('The TV screen')} style={{ width: '100%', borderRadius: 10, border: '1px solid var(--line)' }} />
            <div className="row" style={{ marginTop: 10 }}>
              <button className="btn small" onClick={() => void window.cytv.saveScreenshot().then((p) => p && setSaved(p)).catch((e) => setShotError(errorText(e)))}>
                {t('Save…')}
              </button>
              {saved && <span className="muted small">{t('Saved as {name}', { name: saved.split(/[\\/]/).pop() ?? saved })}</span>}
            </div>
          </>
        ) : (
          <p className="muted small">{t("A picture of the TV screen right now. It also becomes this TV's thumbnail in the sidebar.")}</p>
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
        <h2>{t('Dock and order')}</h2>
        <Slider label={t('Apps in the dock')} hint={t('The first apps sit in a row of large tiles. 0 for no dock.')} value={cfg.dock_size} min={0} max={8} onChange={(v) => set({ dock_size: v }, 300)} />
        <Toggle label={t('Inputs as tiles')} hint={t('Each HDMI port becomes a tile with the name the Inputs menu shows, like PS5. Put it in the dock or a row, and one press switches to it.')} checked={cfg.input_tiles} onChange={(v) => set({ input_tiles: v })} />
        <Toggle label={t('Phone apps in an Other apps folder')} hint={t('Apps made for phones, with no TV picture, move out of the dock and the grid into one folder. Apps you put in rows or folders stay where they are.')} checked={cfg.other_apps} onChange={(v) => set({ other_apps: v })} />
        <NewAppsTo cfg={cfg} set={set} />
        <div className="row" style={{ margin: '6px 0 10px' }}>
          <span className="muted small">{t('Drag to reorder, or use the arrows. Highlighted tiles are the dock. Apps in a folder show only in the folder.')}</span>
          <span className="spacer" />
          <button className="btn small ghost" onClick={() => set({ order: [] })}>
            {t('Reset order')}
          </button>
          <button className="btn small ghost" onClick={() => set({ hidden: [] })} disabled={!cfg.hidden.length}>
            {t('Show all')}
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
        title={t('Rows')}
        hint={t('Named rows of apps, like Watch or Music. An app can be in a row and the grid at the same time.')}
        groups={cfg.rows}
        apps={ordered}
        max={8}
        onChange={(rows) => set({ rows }, 500)}
      />
      <Groups
        kind="folders"
        title={t('Folders')}
        hint={t('A folder shows as one tile; OK on the remote opens it. Apps in a folder leave the dock and grid.')}
        groups={cfg.folders}
        apps={ordered}
        max={12}
        onChange={(folders) => set({ folders }, 500)}
      />
    </>
  )
}

/** Where newly installed apps go. The TV keeps the choice on its row when that's renamed, and goes back to All apps when it's removed. */
function NewAppsTo({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  const gone = cfg.new_apps_to !== '' && cfg.new_apps_to !== 'dock' && !cfg.rows.some((g) => g.name === cfg.new_apps_to)
  return (
    <div style={{ margin: '4px 0 12px' }}>
      <div className="row" style={{ gap: 16, flexWrap: 'wrap' }}>
        <label className="field">
          <span>{t('New apps')}</span>
          <select value={cfg.new_apps_to} onChange={(e) => set({ new_apps_to: e.target.value })}>
            <option value="">{t('All apps only')}</option>
            <option value="dock">{t('The dock, for a day')}</option>
            {cfg.rows.map((g) => (
              <option key={g.name} value={g.name}>
                {t('Row: {name}', { name: g.name })}
              </option>
            ))}
            {gone && <option value={cfg.new_apps_to}>{t("A row that's gone")}</option>}
          </select>
        </label>
      </div>
      <p className="muted small">{t('Apps installed from now on land there by themselves and show New on the TV for a day. Updates stay where they are.')}</p>
    </div>
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
  const fresh = (n: number): string => (props.kind === 'rows' ? t('New row {n}', { n }) : t('New folder {n}', { n }))
  return (
    <div className="card">
      <div className="card-head">
        <h2>{props.title}</h2>
        <button className="btn small" disabled={groups.length >= props.max} onClick={() => onChange([...groups, { name: fresh(groups.length + 1), apps: [] }])}>
          {props.kind === 'rows' ? t('Add a row') : t('Add a folder')}
        </button>
      </div>
      <p className="muted small">{props.hint}</p>
      {groups.map((g, i) => (
        <div key={i} className="group-card">
          <div className="row">
            <input type="text" maxLength={30} value={g.name} aria-label={props.kind === 'rows' ? t('Row name') : t('Folder name')} onChange={(e) => update(i, { ...g, name: e.target.value })} style={{ flex: 1 }} />
            <button className="btn small ghost" disabled={i === 0} onClick={() => move(i, -1)} aria-label={t('Move up')}>
              ↑
            </button>
            <button className="btn small ghost" disabled={i === groups.length - 1} onClick={() => move(i, 1)} aria-label={t('Move down')}>
              ↓
            </button>
            <button className="btn small ghost" onClick={() => onChange(groups.filter((_, j) => j !== i))}>
              {t('Remove')}
            </button>
          </div>
          <div className="chips">
            {g.apps.length === 0 && <span className="muted small">{t('No apps yet: it stays hidden on the TV until you add some.')}</span>}
            {g.apps.map((p) => (
              <span key={p} className="chip-app">
                {label(p)}
                <button aria-label={t('Take {app} out', { app: label(p) })} onClick={() => update(i, { ...g, apps: g.apps.filter((x) => x !== p) })}>
                  ×
                </button>
              </span>
            ))}
          </div>
          <select
            value=""
            aria-label={t('Add an app')}
            onChange={(e) => e.target.value && update(i, { ...g, apps: [...g.apps, e.target.value] })}
          >
            <option value="">{t('Add an app…')}</option>
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
  { id: 'shelf', label: t('Shelf'), hint: t('Top bar, open space, apps lower down.'), mini: [[6, 6, 30, 6, true], [64, 6, 30, 6, true], [6, 40, 88, 24, false], [6, 72, 88, 18, true]] },
  { id: 'split', label: t('Split'), hint: t('Clock and quick settings on the left, apps on the right.'), mini: [[6, 10, 26, 18, true], [6, 62, 20, 24, true], [38, 8, 56, 26, false], [38, 42, 56, 18, true]] },
  { id: 'rail', label: t('Rail'), hint: t('Icons down the left edge that open with labels.'), mini: [[3, 6, 6, 84, true], [70, 6, 24, 8, true], [16, 24, 78, 28, false], [16, 60, 78, 18, true]] },
  { id: 'glass', label: t('Glass'), hint: t('What the focused dock app has to continue, in large type, and the dock in a glass tray. Amber unless you pick an accent.'), mini: [[6, 6, 30, 6, true], [6, 18, 50, 14, true], [6, 36, 26, 4, false], [20, 76, 60, 16, true]] },
  { id: 'teletext', label: 'Teletext', hint: t('Numbered pages of text on black, like an old broadcast text service. A number key opens a line. Its own colours and type: wallpaper, accent and font don\'t apply.'), mini: [[6, 6, 88, 12, true], [6, 24, 46, 50, false], [58, 24, 36, 34, false], [6, 84, 88, 8, true]] },
  { id: 'bau', label: 'Bau', hint: t('A big bold clock and three shapes on the left, the top bar icons listed under them. Apps on the right.'), mini: [[6, 8, 22, 20, false], [6, 32, 14, 6, true], [6, 66, 22, 24, true], [34, 8, 60, 26, false], [34, 42, 60, 18, true]] }
]

const SECTION_LABEL: Record<HomeSection, string> = { nowplaying: t('Now playing'), recent: t('Recently used'), screentime: t('Screen time'), dock: t('Dock'), watchnext: t('Continue watching'), rows: t('Rows'), folders: t('Folders'), grid: t('All apps') }
const ALL_SECTIONS: HomeSection[] = ['nowplaying', 'recent', 'screentime', 'dock', 'watchnext', 'rows', 'folders', 'grid']
const ALL_QUICK: QuickId[] = ['inputs', 'wifi', 'bluetooth', 'display', 'apps', 'sleep', 'search', 'music', 'panel', 'themes', 'fireplace', 'memory', 'timer', 'screentime', 'customize', 'settings']
/** As the launcher's HomeConfig.MAX_QUICK_APPS. */
const MAX_QUICK_APPS = 6
const IDLE_CHOICES: [number, string][] = [[0, t('Off')], ...[1, 2, 5, 10, 15, 30].map((n): [number, string] => [n, t('{n} min', { n })])]

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
      {items.map((item) => {
        const i = on.indexOf(item)
        return (
          <li key={item} className={i < 0 ? 'off' : ''}>
            <label className="grow">
              <input type="checkbox" checked={i >= 0} onChange={(e) => onChange(e.target.checked ? [...on, item] : on.filter((x) => x !== item))} />
              {props.label(item)}
            </label>
            <button className="btn small ghost" disabled={i <= 0} onClick={() => move(item, -1)} aria-label={t('Earlier')}>
              ↑
            </button>
            <button className="btn small ghost" disabled={i < 0 || i === on.length - 1} onClick={() => move(item, 1)} aria-label={t('Later')}>
              ↓
            </button>
          </li>
        )
      })}
    </ul>
  )
}

function LayoutTab({ apps, cfg, set, sdk }: { apps: TvApp[]; cfg: HomeConfig; set: SetFn; sdk?: number }): ReactNode {
  return (
    <>
      <div className="home-preview-sticky">
        <HomePreview apps={apps} cfg={cfg} />
      </div>
      <div className="card">
        <h2>{t('Layout')}</h2>
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
        {cfg.layout === 'glass' && (
          <div style={{ marginTop: 14 }}>
            <p style={{ margin: '0 0 8px', fontWeight: 600 }}>{t('Backdrop')}</p>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['wallpaper', t('My wallpaper')], ['slate', t('Glass slate')]] as const).map(([id, label]) => (
                <button key={id} className={cfg.glass_backdrop === id ? 'on' : ''} onClick={() => set({ glass_backdrop: id })}>{label}</button>
              ))}
            </div>
            <Toggle label={t('Banners in the Glass dock')} hint={t('Off, the dock and All apps show app names on glass, which turn white when focused.')} checked={cfg.glass_banners} onChange={(v) => set({ glass_banners: v })} />
          </div>
        )}
        {cfg.layout === 'shelf' && (
          <div style={{ marginTop: 14 }}>
            <Slider label={t('Space above the apps')} hint={t('Open wallpaper between the top bar and the first row.')} value={cfg.top_space} min={0} max={80} unit="%" onChange={(v) => set({ top_space: v }, 250)} />
          </div>
        )}
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>{t('Sections')}</h2>
          <p className="muted small">{t('Top to bottom. Untick one to hide it.')}</p>
          <OrderList all={ALL_SECTIONS} on={cfg.sections} label={(s) => SECTION_LABEL[s]} onChange={(sections) => set({ sections })} />
          <label className="field" style={{ marginTop: 14 }}>
            <span>{t('All apps title')}</span>
            <span className="hint">{t('Your own words for the heading above the grid. Empty keeps "All apps".')}</span>
            <input type="text" maxLength={24} value={cfg.names.all_apps ?? ''} placeholder="All apps" onChange={(e) => set({ names: { ...cfg.names, all_apps: e.target.value } }, 700)} />
          </label>
          {cfg.sections.includes('recent') && (
            <p className="small muted" style={{ marginTop: 10 }}>
              {t('Recently used shows the last 8 apps the TV opened, newest first, whichever way they were opened.')}
            </p>
          )}
          {cfg.sections.includes('screentime') && (
            <p className="small muted" style={{ marginTop: 10 }}>
              {t("Screen time shows today's total, the last 7 days and today's top apps. Press it on the TV for the dashboard. The numbers stay on the TV.")}
            </p>
          )}
          {cfg.sections.includes('watchnext') && (
            <p className="small muted" style={{ marginTop: 10 }}>
              {t("Continue watching shows the films and episodes you're part way through. Apps that fill Google TV's row fill this one too. It hides when there's nothing to show.")}
            </p>
          )}
        </div>
        <div className="card">
          <h2>{t('Quick settings')}</h2>
          <p className="muted small">
            {cfg.layout === 'rail' ? t('Icons in the rail.') : cfg.layout === 'split' ? t('At the bottom of the left side.') : t('Under the clock and date.')}{' '}
            {t("The TV hides any it can't open, such as Inputs on a box without HDMI inputs, or Search when the search app is switched off.")}
          </p>
          <OrderList<QuickEntry>
            all={[...ALL_QUICK, ...cfg.quick.filter((q) => quickApp(q))]}
            on={cfg.quick}
            label={(q) => {
              const pkg = quickApp(q)
              const missing = pkg ? !apps.some((a) => a.pkg === pkg) : cfg.quick_available && !cfg.quick_available.includes(q as QuickId)
              return (
                <>
                  <span className="quick-mark"><QuickMark q={q} apps={apps} /></span> {pkg ? quickLabel(q, apps) : QUICK_NAME[q as QuickId]}
                  {missing && <span className="muted small">{t('not on this TV')}</span>}
                </>
              )
            }}
            onChange={(quick) => set({ quick })}
          />
          <QuickAppAdd apps={apps} quick={cfg.quick} onAdd={(pkg) => set({ quick: [...cfg.quick, `app:${pkg}`] })} />
          {cfg.quick.some((q) => !quickApp(q)) && (
            <details style={{ marginTop: 14 }}>
              <summary className="small muted" style={{ cursor: 'pointer' }}>
                {t('Rename the buttons')}
              </summary>
              <p className="muted small">{t('In your own language, for example. Leave a box empty to keep the English name.')}</p>
              {cfg.quick.filter((q) => !quickApp(q)).map((q) => (
                <label key={q} className="field">
                  <span>{QUICK_NAME[q as QuickId]}</span>
                  <input type="text" maxLength={24} value={cfg.names[q as QuickId] ?? ''} placeholder={QUICK_LABEL[q as QuickId]} onChange={(e) => set({ names: { ...cfg.names, [q]: e.target.value } }, 700)} />
                </label>
              ))}
            </details>
          )}
          {cfg.quick.includes('inputs') && (
            <Toggle label={t('Live TV in Inputs')} hint={t("The TV's tuner, at the top of the Inputs menu. Switch it off if you don't watch TV channels.")} checked={cfg.live_tv} onChange={(v) => set({ live_tv: v })} />
          )}
        </div>
      </div>

      <div className="card">
        <Toggle
          label={t('Back to the input at power on')}
          hint={t('If the TV was on an HDMI input or TV channels when it went to sleep, it goes back there when you turn it on. Otherwise it starts on Home as usual. This uses the same accessibility service as Remote buttons.')}
          checked={cfg.power_on_input}
          onChange={(v) => set({ power_on_input: v })}
        />
        {cfg.power_on_input && (
          <div className="row" style={{ marginTop: 10, gap: 16, flexWrap: 'wrap' }}>
            {/* A box with no HDMI, or a launcher too old to list inputs, keeps going back to the input it slept on. */}
            {!!cfg.tv_inputs?.length && (
              <label className="field">
                <span>{t('Open at power on')}</span>
                <select value={cfg.power_on_target} onChange={(e) => set({ power_on_target: e.target.value })}>
                  <option value="">{t('The last input')}</option>
                  {cfg.tv_inputs.map((i) => (
                    <option key={i.id} value={i.id}>
                      {i.name}
                    </option>
                  ))}
                  {cfg.power_on_target && !cfg.tv_inputs.some((i) => i.id === cfg.power_on_target) && <option value={cfg.power_on_target}>{t('An input the TV no longer has')}</option>}
                </select>
              </label>
            )}
            <label className="field">
              <span>{t('Wait after waking')}</span>
              <select value={cfg.power_on_wait} onChange={(e) => set({ power_on_wait: Number(e.target.value) })}>
                {[0, 3, 5, 10].map((n) => (
                  <option key={n} value={n}>
                    {t('{n} s', { n })}
                  </option>
                ))}
              </select>
            </label>
          </div>
        )}
      </div>

      <div className="card">
        <Toggle
          label={t('Bedtime')}
          hint={t('The TV turns off at this time on the days you pick. A minute before, a note says so, and any button on the remote keeps it on for the night. This uses the same accessibility service as Remote buttons.')}
          checked={cfg.bedtime_on}
          onChange={(v) => set({ bedtime_on: v })}
        />
        {cfg.bedtime_on && (
          <div className="row" style={{ marginTop: 10, gap: 16, flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <label className="field">
              <span>{t('Turn off at')}</span>
              <input type="time" value={hhmm(cfg.bedtime_at)} onChange={(e) => { const m = minutesOf(e.target.value); if (m !== null) set({ bedtime_at: m }, 600) }} />
            </label>
            <div className="chips" role="group" aria-label={t('Days')}>
              {WEEKDAYS().map(([d, name]) => (
                <label key={d} className="chip-app" style={{ paddingRight: 10 }}>
                  <input
                    type="checkbox"
                    checked={cfg.bedtime_days.includes(d)}
                    onChange={(e) => set({ bedtime_days: e.target.checked ? [...cfg.bedtime_days, d].sort() : cfg.bedtime_days.filter((x) => x !== d) })}
                  />
                  {name}
                </label>
              ))}
            </div>
          </div>
        )}
        {cfg.bedtime_on && cfg.bedtime_days.length === 0 && <p className="small muted" style={{ marginTop: 8 }}>{t('Pick at least one day.')}</p>}
        {cfg.bedtime_on && <ScreensaverNote sdk={sdk} />}
      </div>

      <div className="card">
        <Toggle
          label={t('Keep my keyboard')}
          hint={t('Some TVs switch to "Use the keyboard on your mobile device" after a restart or standby. Home puts your keyboard back straight away. Turn it off if you type from the Google TV app on your phone right after turning the TV on.')}
          checked={cfg.keyboard_guard}
          onChange={(v) => set({ keyboard_guard: v })}
        />
      </div>

      <div className="card">
        <Toggle
          label={t('Edit on the TV (experimental)')}
          hint={t('Adds a Customize icon to the top bar, which opens an editor for the layout, look, widgets, apps, music bars and fireplace from the remote. With App lock on, it asks for the PIN. Turn it off to take the icon away again.')}
          checked={cfg.edit_on_tv}
          onChange={(v) => {
            const rest = cfg.quick.filter((q) => q !== 'customize')
            const at = rest.indexOf('settings')
            set({ edit_on_tv: v, quick: v ? (at < 0 ? [...rest, 'customize'] : [...rest.slice(0, at), 'customize', ...rest.slice(at)]) : rest })
          }}
        />
      </div>

      <div className="card">
        <h2>{t('Sizes')}</h2>
        <Slider label={t('Dock tiles')} value={cfg.dock_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ dock_scale: v }, 250)} />
        <Slider label={t('Row and folder tiles')} value={cfg.tile_scale} min={60} max={150} step={5} unit="%" onChange={(v) => set({ tile_scale: v }, 250)} />
        <Slider label={t('Apps per row in All apps')} value={cfg.columns} min={3} max={8} onChange={(v) => set({ columns: v }, 250)} />
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('App names')}</p>
        <div className="seg">
          {([['always', t('Always'), { show_names: true, name_focus: false }], ['focus', t('Focused app only'), { show_names: true, name_focus: true }], ['off', t('Off'), { show_names: false, name_focus: false }]] as const).map(([id, label, v]) => (
            <button key={id} className={(!cfg.show_names ? 'off' : cfg.name_focus ? 'focus' : 'always') === id ? 'on' : ''} onClick={() => set(v)}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {!cfg.show_names ? t('No names under tiles. Apps without a TV banner still show their name on the tile.')
            : cfg.name_focus ? t('Only the app you move onto shows its name. The others stay hidden.') : t('Every tile shows its name underneath.')}
        </p>
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>{t('Clock')}</h2>
          <div className="seg">
            {([['time_date', t('Time and date')], ['time', t('Time only')], ['off', t('Off')]] as const).map(([id, label]) => (
              <button key={id} className={cfg.clock === id ? 'on' : ''} onClick={() => set({ clock: id })}>
                {label}
              </button>
            ))}
          </div>
        </div>
        <div className="card">
          <h2>{t('Greeting')}</h2>
          <label className="field">
            <span>{t('Your name')}</span>
            <span className="hint">{t('Shows "Good evening, name". Leave it empty for no greeting.')}</span>
            <input type="text" maxLength={40} value={cfg.greeting} placeholder={t('e.g. Sam')} onChange={(e) => set({ greeting: e.target.value }, 700)} />
          </label>
        </div>
      </div>

      <div className="card">
        <h2>{t('Idle screen')}</h2>
        <p className="muted small">
          {cfg.clock === 'off'
            ? t('When nobody has pressed a button for this long, Home fades out the apps and shows just the wallpaper. Any button brings everything back.')
            : t('When nobody has pressed a button for this long, Home fades out the apps and shows just the wallpaper and a large clock. Any button brings everything back.')}
        </p>
        <div className="seg">
          {IDLE_CHOICES.map(([m, label]) => (
            <button key={m} className={cfg.idle_minutes === m ? 'on' : ''} onClick={() => set({ idle_minutes: m })}>
              {label}
            </button>
          ))}
        </div>
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('What it shows')}</p>
        <div className="seg">
          {([['corner', t('Clock in the corner')], ['center', t('Big clock')], ['photos', t('Photos')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.idle_style === id ? 'on' : ''} onClick={() => set({ idle_style: id })}>{label}</button>
          ))}
        </div>
        {cfg.idle_style === 'photos' && <p className="small muted">{t("The slideshow's photos, or the themes' pictures when there are none, a new one every 20 seconds.")}</p>}
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
      <select value="" aria-label={t('Add an app icon')} disabled={used >= MAX_QUICK_APPS || !free.length} onChange={(e) => e.target.value && onAdd(e.target.value)}>
        <option value="">{used >= MAX_QUICK_APPS ? t('{n} apps is the most', { n: MAX_QUICK_APPS }) : t('Add an app as an icon…')}</option>
        {free.map((a) => (
          <option key={a.pkg} value={a.pkg}>
            {a.label}
          </option>
        ))}
      </select>
      <span className="muted small">{t('For apps you open a lot, like a VPN. Untick one to remove it.')}</span>
    </div>
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
  /** Themes already on the TV for its carousel. */
  onTv: number
  toTv: () => void
  /** Takes the built-in themes off the TV again. */
  offTv: () => void
  /** A theme made in the editor. */
  custom: (t: HomeTheme) => void
  file: (text: string) => void
  backup: ThemeBackup | null
  restore: () => void
  forget: () => void
  save: (name: string) => Promise<void>
  /** The TV's look becomes a theme on the TV, next to the built-in ones. */
  saveOnTv: (name: string) => void
}

function ThemeGrid({ themes, actions, busy }: { themes: BuiltInTheme[]; actions: ThemeActions; busy: boolean }): ReactNode {
  // Each card shows a small copy of the wallpaper the theme draws.
  const art = useMemo(() => new Map(themes.map((t) => [t.id, `url(${drawWallpaper(t.paint, 320, 180).toDataURL('image/jpeg', 0.85)})`])), [themes])
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

function ThemesCard({ themes, busy, cfg, set }: { themes: ThemeActions; busy: boolean; cfg: HomeConfig; set: SetFn }): ReactNode {
  const file = useRef<HTMLInputElement>(null)
  const [name, setName] = useState(t('My theme'))
  const [editing, setEditing] = useState(false)
  return (
    <div className="card">
      <h2>{t('Themes')}</h2>
      <p className="muted">
        {t('A theme sets the wallpaper, colours, tile sizes and music bars in one go. Your apps, rows and layout stay as they are.')}
      </p>
      {themes.backup?.savedAt ? (
        <div className="notice row" style={{ justifyContent: 'space-between' }}>
          <span>
            {themes.backup.withWallpaper
              ? t('Your look from {date} is saved.', { date: new Date(themes.backup.savedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) })
              : t('Your look from {date} is saved, without its wallpaper.', { date: new Date(themes.backup.savedAt).toLocaleDateString(undefined, { day: 'numeric', month: 'short' }) })}
          </span>
          <span className="row" style={{ gap: 8 }}>
            <button className="btn small" disabled={busy} onClick={themes.restore}>
              {t('Put it back')}
            </button>
            <button className="btn small ghost" disabled={busy} onClick={themes.forget}>
              {t('Forget it')}
            </button>
          </span>
        </div>
      ) : (
        <p className="small muted">{t('Your current look is saved before the first theme, so you can always put it back.')}</p>
      )}
      <ThemeGrid themes={BUILT_IN_THEMES} actions={themes} busy={busy} />
      {editing ? <ThemeEditor themes={themes} busy={busy} onClose={() => setEditing(false)} /> : null}
      <div className="row" style={{ marginTop: 12 }}>
        <button className="btn ghost" disabled={busy || !BUILT_IN_THEMES.some((b) => themes.current.startsWith(`${b.id}:`))} onClick={themes.another}>
          {t('Another picture')}
        </button>
        <button className="btn ghost" disabled={busy} onClick={themes.toTv}>
          {themes.onTv ? t('Put the themes on the TV again') : t('Put the themes on the TV')}
        </button>
        {themes.onTv > 0 && (
          <button className="btn ghost" disabled={busy} onClick={themes.offTv}>
            {t('Take them off the TV')}
          </button>
        )}
        {!editing && (
          <button className="btn ghost" disabled={busy} onClick={() => setEditing(true)}>
            {t('Make your own…')}
          </button>
        )}
      </div>
      <p className="small muted" style={{ marginTop: 8 }}>
        {t('Each theme has {n} pictures. With the themes on the TV, you can switch them with the remote: add the Themes icon to the top bar on the Layout tab, or give a remote button the Themes job.', { n: THEME_PICTURES })}{' '}
        {themes.onTv ? t('{n} themes are on the TV now.', { n: themes.onTv }) : t('It takes a minute or two.')}
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
        <input type="text" value={name} maxLength={40} onChange={(e) => setName(e.target.value)} aria-label={t('Theme name')} style={{ width: 180 }} />
        <button className="btn" disabled={busy || !name.trim()} onClick={() => themes.save(name)}>
          {t('Save this look…')}
        </button>
        <button className="btn ghost" disabled={busy || !name.trim() || !cfg.has_wallpaper} title={cfg.has_wallpaper ? undefined : t('Put a wallpaper on first; a theme needs a picture.')} onClick={() => themes.saveOnTv(name)}>
          {t('Save to the TV')}
        </button>
        <button className="btn ghost" disabled={busy} onClick={() => file.current?.click()}>
          {t('Load a theme file…')}
        </button>
      </div>
      <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
        {t("Theme files end in .{ext}. Share one and anyone can load it on their TV. Save to the TV adds this look and wallpaper to the TV's themes, or updates the one with the same name, and leaves the others alone.", { ext: THEME_EXT })}
      </p>
      <Toggle
        label={t('Each theme keeps its changes')}
        hint={t('Change the look on the TV while a theme is on, and that theme keeps it: colours, sizes, glass, shading, spacing and music bars. Rows, folders, App lock and buttons stay the same for every theme. Works with day and night too.')}
        checked={cfg.theme_memory}
        onChange={(v) => set({ theme_memory: v })}
      />
    </div>
  )
}

function LookTab(props: { cfg: HomeConfig; set: SetFn; busy: boolean; wallpaper: (label: string, get: () => Promise<string | null>) => Promise<void>; themes: ThemeActions }): ReactNode {
  const { cfg, set, busy, wallpaper, themes } = props
  const file = useRef<HTMLInputElement>(null)
  return (
    <>
      <ThemesCard themes={themes} busy={busy} cfg={cfg} set={set} />
      <div className="card">
        <h2>{t('Accent colour')}</h2>
        <p className="muted">{t('For the focus glow or outline and the music bars.')}</p>
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
          <input type="color" value={cfg.accent} onChange={(e) => set({ accent: e.target.value.toUpperCase() }, 400)} aria-label={t('Pick any colour')} />
        </div>
      </div>

      <div className="card">
        <h2>{t('Focus')}</h2>
        <p className="muted">{t("How the tile under the remote's cursor stands out.")}</p>
        <div className="seg">
          {([['lift', t('Lift')], ['glow', t('Glow')], ['outline', t('Outline')], ['none', t('None')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.focus === id ? 'on' : ''} onClick={() => set({ focus: id })}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {cfg.focus === 'lift' ? t('The tile floats on a soft shadow with a faint halo in your accent, and a light sweeps across it once.')
            : cfg.focus === 'glow' ? t('A glow in your accent and a white ring.') : cfg.focus === 'outline' ? t('A line in your accent.') : t('Only the tile colour changes.')}
        </p>
        {cfg.focus === 'lift' && <Toggle label={t('Lean toward the move')} hint={t('The tile tilts a little the way you pressed, then settles.')} checked={cfg.tilt} onChange={(v) => set({ tilt: v })} />}
        <Toggle label={t('Keep the focused tile in place')} hint={t('The row slides under it, one step in from the left.')} checked={cfg.pin_focus} onChange={(v) => set({ pin_focus: v })} />
        <Toggle label={t('Start on the app I usually open')} hint={t('Home opens with the focus on the app you open most at this time of day, if it sits near the start of a row. The order stays.')} checked={cfg.focus_usual} onChange={(v) => set({ focus_usual: v })} />
        <Toggle label={t("Background takes the app's colour")} hint={t("A tint of the focused app's colour washes in from the top.")} checked={cfg.follow_colour} onChange={(v) => set({ follow_colour: v })} />
      </div>

      <div className="card">
        <h2>{t('Font')}</h2>
        <p className="muted">{cfg.layout === 'teletext' ? t('Teletext keeps its own monospace type. The font here is for the other layouts.') : cfg.layout === 'glass' ? t('Glass keeps the TV\'s usual sans. The font here is for the other layouts.') : t('For names, headings and menus on the TV. The clock keeps its own.')}</p>
        <div className="seg" style={{ marginBottom: 18 }}>
          {([['default', t('Usual')], ['condensed', t('Condensed')], ['serif', t('Serif')], ['casual', t('Casual')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.font === id ? 'on' : ''} onClick={() => set({ font: id })} style={{ fontFamily: FONT_CSS[id] }}>{label}</button>
          ))}
        </div>
        <h2>{t('App icons')}</h2>
        <p className="muted">{t("Banners are each app's own TV picture. The other styles redraw every app's logo the same way, so the rows match.")}</p>
        <div className="seg">
          {([['banner', t('Banners')], ['colour', t('Colour')], ['mono', t('Mono')], ['duo', t('Duotone')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.icon_style === id ? 'on' : ''} onClick={() => set({ icon_style: id })}>
              {label}
            </button>
          ))}
        </div>
        <p className="small muted">
          {cfg.icon_style === 'banner' ? t('What every TV app ships with.')
            : cfg.icon_style === 'colour' ? t('The logo in white on its brand colour.')
            : cfg.icon_style === 'mono' ? t('Every logo in one colour from your accent, on the same glass tile.')
            : t("Every logo in two shades of your accent, on your theme's colours.")}
          {cfg.icon_style !== 'banner' && ` ${t('An app without a usable logo shows its first letter.')}`}
        </p>
        {/* Shown under Banners too, so people find it: a banner is the app's own wide picture and keeps its shape. */}
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Tile shape')}</p>
        <div className="seg">
          {([['wide', t('Wide')], ['square', t('Square')], ['squircle', t('Squircle')], ['round', t('Round')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.icon_style !== 'banner' && cfg.tile_shape === id ? 'on' : ''} disabled={cfg.icon_style === 'banner'} onClick={() => set({ tile_shape: id })}>
              {label}
            </button>
          ))}
        </div>
        {cfg.icon_style === 'banner' && <p className="small muted">{t('Pick Colour, Mono or Duotone to change the shape. Banners stay wide.')}</p>}
        {cfg.icon_style === 'mono' && (
          <Slider label={t('Glass')} hint={t('How much the tile behind each logo shows. Lower lets more of the wallpaper through.')} value={cfg.tile_glass} min={0} max={60} unit="%" onChange={(v) => set({ tile_glass: v }, 250)} />
        )}
      </div>

      <div className="card">
        <h2>{t('Wallpaper')}</h2>
        <p className="muted">{cfg.has_wallpaper ? t('A wallpaper is set.') : t('No wallpaper: the background colour shows.')}</p>
        <input
          ref={file}
          type="file"
          accept="image/*,.heic,.heif"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = ''
            if (f) wallpaper(t('Preparing and sending the picture'), async () => (await prepareWallpaper(f, 'wallpaper.jpg')).base64)
          }}
        />
        <div className="row" style={{ marginBottom: 12 }}>
          <button className="btn" disabled={busy} onClick={() => file.current?.click()}>
            {t('Choose a picture…')}
          </button>
          <button className="btn" disabled={busy} onClick={() => wallpaper(t("Fetching today's Bing picture"), async () => (await fetchBingDaily()).base64)}>
            {t('Bing picture of the day')}
          </button>
          {cfg.has_wallpaper && (
            <button className="btn ghost" disabled={busy} onClick={() => wallpaper(t('Removing the wallpaper'), async () => null)}>
              {t('Remove')}
            </button>
          )}
        </div>
        <Slider label={t('Darken the wallpaper')} hint={t('Keeps names and the clock readable on bright pictures.')} value={cfg.wallpaper_dim} min={0} max={80} unit="%" onChange={(v) => set({ wallpaper_dim: v }, 300)} />
        <Toggle label={t('Bing every day')} hint={t("Bing's new picture becomes the wallpaper each day, while Home is on screen. Picking another wallpaper doesn't stop it; this switch does.")} checked={cfg.bing_daily} onChange={(v) => set({ bing_daily: v })} />
        <Toggle label={t('Show where it was taken')} hint={t("A small line in the bottom corner of Home says where Bing's picture was taken. For pictures the TV fetches: Bing every day, or the button in the TV's own editor.")} checked={cfg.bing_caption} onChange={(v) => set({ bing_caption: v })} />
        <Toggle label={t('Snow')} hint={t("Gentle snow over the wallpaper, behind the apps. It holds still while an app is open or the TV's animations are off. The Christmas theme turns it on.")} checked={cfg.snow} onChange={(v) => set({ snow: v })} />
        <Shading cfg={cfg} set={set} />
        <Toggle label={t('Sunrise when it changes')} hint={t("A new wallpaper starts dim and the light comes in from the top. Off when the TV's animations are off.")} checked={cfg.intro} onChange={(v) => set({ intro: v })} />
        <Colour label={t('Background colour')} hint={t("Behind everything when there's no wallpaper. Keep it dark: text is white.")} value={cfg.background} onChange={(c, w) => set({ background: c }, w)} />
      </div>
    </>
  )
}

/** The shades over the wallpaper, top and bottom, and the app colour wash: how strong and how far each reaches. */
function Shading({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  const changed = SHADING.some((k) => cfg[k] !== SHADING_DEFAULTS[k])
  return (
    <>
      <p style={{ margin: '18px 0 4px', fontWeight: 600 }}>{t('Shading')}</p>
      <p className="small muted" style={{ marginTop: 0 }}>{t('Shades in your background colour keep the top bar and the app names readable. Reach is how far down or up each one goes.')}</p>
      {cfg.layout === 'shelf' ? (
        <>
          <Slider label={t('Top shade')} value={cfg.shade_top} min={0} max={100} unit="%" onChange={(v) => set({ shade_top: v }, 300)} />
          <Slider label={t('Top shade reach')} value={cfg.shade_top_reach} min={0} max={100} unit="%" onChange={(v) => set({ shade_top_reach: v }, 300)} />
        </>
      ) : (
        <p className="small muted">{t('The top shade is part of the Shelf layout.')}</p>
      )}
      <Slider label={t('Bottom shade')} value={cfg.shade_bottom} min={0} max={100} unit="%" onChange={(v) => set({ shade_bottom: v }, 300)} />
      <Slider label={t('Bottom shade reach')} value={cfg.shade_bottom_reach} min={0} max={100} unit="%" onChange={(v) => set({ shade_bottom_reach: v }, 300)} />
      {cfg.follow_colour ? (
        <>
          <Slider label={t('App colour')} hint={t("The focused app's colour, washing in from the top.")} value={cfg.wash} min={0} max={100} unit="%" onChange={(v) => set({ wash: v }, 300)} />
          <Slider label={t('App colour reach')} value={cfg.wash_reach} min={10} max={100} unit="%" onChange={(v) => set({ wash_reach: v }, 300)} />
        </>
      ) : (
        <p className="small muted">{t("Turn on Background takes the app's colour, under Focus, to set its strength and reach here.")}</p>
      )}
      {changed && (
        <button className="btn ghost" style={{ marginTop: 4 }} onClick={() => set(Object.fromEntries(SHADING.map((k) => [k, SHADING_DEFAULTS[k]])))}>
          {t('Back to the usual shading')}
        </button>
      )}
    </>
  )
}

const SHADING = ['shade_top', 'shade_top_reach', 'shade_bottom', 'shade_bottom_reach', 'wash', 'wash_reach'] as const
const SHADING_DEFAULTS: Pick<HomeConfig, (typeof SHADING)[number]> = {
  shade_top: 60, shade_top_reach: 40, shade_bottom: 80, shade_bottom_reach: 65, wash: 50, wash_reach: 100
}

function WidgetsTab({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  const playing = cfg.sections.includes('nowplaying')
  const [query, setQuery] = useState('')
  const [places, setPlaces] = useState<Place[] | null>(null)
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function search(): Promise<void> {
    setSearching(true)
    setError(null)
    try {
      setPlaces(await findPlaces(query))
    } catch (e) {
      setError(errorText(e))
    } finally {
      setSearching(false)
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
          label={t('Now playing')}
          hint={t('A card with what any music or video app is playing, with play, pause and next. It shows only while something is loaded. The TV lets CleanMyTV Home see which app is playing; it reads no notifications.')}
          checked={playing}
          onChange={(v) => set({ sections: v ? ['nowplaying', ...cfg.sections.filter((s) => s !== 'nowplaying')] : cfg.sections.filter((s) => s !== 'nowplaying') })}
        />
        {playing && <p className="small muted" style={{ margin: '8px 0 0' }}>{t('Move it up or down in Layout → Sections.')}</p>}
      </div>

      <div className="card">
        <h2>{t('Weather')}</h2>
        <p className="muted">
          {t("The temperature, today's high and low, and the sky next to the clock. It comes from Open-Meteo, or from MET Norway when the TV can't reach Open-Meteo. Both are free, with no account. The TV asks every 30 minutes.")}
        </p>
        {cfg.weather_place ? (
          <div className="row" style={{ marginBottom: 14 }}>
            <span>{tx('Showing the weather for {place}', { place: <b>{cfg.weather_place}</b> })}</span>
            <button className="btn small ghost" onClick={() => set({ weather_place: '' })}>
              {t('Turn off')}
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
          <input type="text" value={query} onChange={(e) => setQuery(e.target.value)} placeholder={t('Town or city')} style={{ width: 240 }} />
          <button className="btn" type="submit" disabled={searching || query.trim().length < 2}>
            {cfg.weather_place ? t('Change place') : t('Find')}
          </button>
          {searching && <Busy text={t('Searching')} />}
        </form>
        {error && <div className="notice error" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
        {places && (
          <ul className="place-list">
            {places.length === 0 && <li className="small muted">{t('No place by that name. Try the nearest town.')}</li>}
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
        <div style={{ marginTop: 14 }}>
          <Toggle
            label={t('Hourly forecast')}
            hint={t('The next 4 hours under the weather: the time, the sky and the temperature.')}
            checked={cfg.weather_hours}
            onChange={(v) => set({ weather_hours: v })}
          />
          <Toggle
            label={t('Daily forecast')}
            hint={t('The next 5 days under the weather: the day, the sky, the high and the low.')}
            checked={cfg.weather_days}
            onChange={(v) => set({ weather_days: v })}
          />
        </div>
      </div>

      <div className="card">
        <Toggle
          label={t('Free memory')}
          hint={t('How much memory the TV has free, under the clock, like 1.4 of 2 GB free. Add Clean memory in Layout → Quick settings to close apps left running in the background from the TV.')}
          checked={cfg.memory}
          onChange={(v) => set({ memory: v })}
        />
      </div>
    </>
  )
}

function MusicTab({ cfg, set }: { cfg: HomeConfig; set: SetFn }): ReactNode {
  return (
    <>
      <div className="card">
        <Toggle
          label={t('Music bars')}
          hint={t("Bars along the bottom of the home screen that move with music or video playing on the TV. The remote's clicks don't set them off. Add the Music bars icon in Layout → Quick settings to turn them on and off from the TV.")}
          checked={cfg.visualiser}
          onChange={(v) => set({ visualiser: v })}
        />
        <BarsPreview cfg={cfg} />
      </div>
      {cfg.visualiser && (
        <>
          <div className="card">
            <h2>{t('Style')}</h2>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['bars', t('Bars')], ['mirror', t('Mirrored')], ['wave', t('Wave')]] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_style === id ? 'on' : ''} onClick={() => set({ viz_style: id })}>
                  {label}
                </button>
              ))}
            </div>
            <div className="seg" style={{ marginBottom: 12 }}>
              {([['accent', t('Accent colour')], ['rainbow', t('Rainbow')], ['white', t('White')]] as const).map(([id, label]) => (
                <button key={id} className={cfg.viz_colour === id ? 'on' : ''} onClick={() => set({ viz_colour: id })}>
                  {label}
                </button>
              ))}
            </div>
            {cfg.viz_style !== 'wave' && <Toggle label={t('Peak dots')} hint={t('A small mark above each bar that falls slowly.')} checked={cfg.viz_peaks} onChange={(v) => set({ viz_peaks: v })} />}
          </div>
          <div className="card">
            <h2>{t('Size and feel')}</h2>
            <Slider label={t('Bars')} value={cfg.viz_bars} min={8} max={64} step={4} onChange={(v) => set({ viz_bars: v }, 300)} />
            <Slider label={t('Height')} hint={t('How much of the screen they can reach.')} value={cfg.viz_height} min={4} max={40} unit="%" onChange={(v) => set({ viz_height: v }, 300)} />
            <Slider label={t('Opacity')} value={cfg.viz_opacity} min={20} max={100} unit="%" onChange={(v) => set({ viz_opacity: v }, 300)} />
            <Slider label={t('Sensitivity')} hint={t('Higher makes quiet music move more.')} value={cfg.viz_sensitivity} min={25} max={300} step={5} unit="%" onChange={(v) => set({ viz_sensitivity: v }, 300)} />
          </div>
        </>
      )}
    </>
  )
}

const ACTIONS: [ButtonAction, string][] = [
  ['settings', t('Open Settings')],
  ['inputs', t('Open Inputs')],
  ['home', t('Go Home')],
  ['launcher', t('Open CleanMyTV Home')],
  ['back', t('Back')],
  ['app', t('Open an app…')],
  ['wifi', t('Open Wi-Fi')],
  ['bluetooth', t('Open Bluetooth & remotes')],
  ['display', t('Open Picture & sound')],
  ['apps', t('Open the app list')],
  ['sleep', t('Start the screensaver')],
  ['search', t('Open Search')],
  ['music', t('Music bars on or off')],
  ['panel', t('Open Quick settings')],
  ['themes', t('Open Themes')],
  ['fireplace', t('Show the fireplace')],
  ['screentime', t('Open Screen time')],
  ['memory', t('Clean memory')],
  ['next_wallpaper', t('Next picture of the theme')],
  ['brightness', t('Brightness: 10, 25, 50, 75, 100%')],
  ['timer', t('Sleep timer: 15, 30, 60, 90, 120 min, off')],
  ['none', t('Do nothing')]
]

/** KEYCODE_PROG_RED -> "Prog red". Android's own key names, left in English. */
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
  const [listening, setListening] = useState(false)
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

  // Listen for 30 seconds: show every button the TV receives from now on.
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
      if (!stop) setListening(false)
    })()
    return () => { stop = true }
  }, [listening])

  const mapped = new Set(cfg.buttons.map((b) => b.code))
  const update = (buttons: HomeButton[]): void => set({ buttons })
  const label = (code: number): string => (names[code] ? `${keyName(names[code])} · ${code}` : t('Button {code}', { code }))
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
            {t('The Mi button on Xiaomi remotes opens PatchWall, so it does nothing once PatchWall is off. CleanMyTV Home can make it go Home instead. If it still does nothing after this, press Listen below and press the Mi button to see what it sends.')}
          </p>
          <button className="btn small" disabled={switching} onClick={miButtonHome}>
            {t('Make the Mi button go Home')}
          </button>
        </div>
      )}
      <div className="card">
        <Toggle
          label={t('Remote buttons')}
          hint={t('Lets CleanMyTV Home give buttons a new job, in every app. Only the buttons you add below are touched; every other button works as before. It uses an Android accessibility service, switched on and off from here.')}
          checked={cfg.buttons_on}
          disabled={switching}
          onChange={toggle}
        />
      </div>

      <div className="card">
        <Toggle
          label={t('Number keys open the dock')}
          hint={t('On Home, 1 opens the first app in the dock, 2 the second, and so on. For remotes with number keys.')}
          checked={cfg.number_keys}
          onChange={(v) => set({ number_keys: v })}
        />
        <Toggle
          label={t('Press Home twice for the last app')}
          hint={t('Two quick presses of Home go back to the app you were in. Neither needs Remote buttons on.')}
          checked={cfg.home_twice}
          onChange={(v) => set({ home_twice: v })}
        />
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t('Find a button')}</h2>
          <button className="btn small" onClick={() => setListening(!listening)}>
            {listening ? t('Stop listening') : t('Listen')}
          </button>
        </div>
        <p className="muted small">
          {t('Press Listen, then press buttons on the remote. Each one the TV receives shows here with its code.')}
          {!cfg.buttons_on && ` ${t('With Remote buttons off, only presses on the home screen show up.')}`}{' '}
          {t("A button that never shows up (often Power, volume or the mic) is handled before Android sees it, so it can't get a new job on this TV.")}
        </p>
        {listening && heard.length === 0 && <Busy text={t('Listening. Press a button on the remote')} />}
        {heard.length > 0 && (
          <ul className="order-list">
            {heard.map((k) => (
              <li key={k.seq}>
                <span className="grow">
                  <b>{keyName(k.name)}</b>
                  <span className="muted small">{t('code {code}', { code: k.code })}</span>
                </span>
                <button className="btn small" disabled={mapped.has(k.code)} onClick={() => update([...cfg.buttons, { code: k.code, action: 'settings' }])}>
                  {mapped.has(k.code) ? t('Added') : t('Give it a job')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>{t('Button jobs')}</h2>
        {cfg.buttons.length === 0 ? (
          <p className="muted small">{t('None yet. Find a button above and give it a job.')}</p>
        ) : (
          <ul className="order-list button-list">
            {cfg.buttons.map((b, i) => (
              <li key={b.code}>
                <span className="grow">
                  <b>{label(b.code)}</b>
                </span>
                <select
                  value={b.action}
                  aria-label={t('Job for {button}', { button: label(b.code) })}
                  onChange={(e) => {
                    const action = e.target.value as ButtonAction
                    update(cfg.buttons.map((x, j) => (j === i ? { ...x, action, app: action === 'app' ? (x.app ?? apps[0]?.pkg) : undefined } : x)))
                  }}
                >
                  {ACTIONS.map(([id, text]) => (
                    <option key={id} value={id}>
                      {text}
                    </option>
                  ))}
                </select>
                {b.action === 'app' && (
                  <select value={b.app ?? ''} aria-label={t('App to open')} onChange={(e) => update(cfg.buttons.map((x, j) => (j === i ? { ...x, app: e.target.value } : x)))}>
                    {apps.map((a) => (
                      <option key={a.pkg} value={a.pkg}>
                        {a.label}
                      </option>
                    ))}
                  </select>
                )}
                <select
                  value={b.hold ?? 'none'}
                  aria-label={t('Job for holding {button} down', { button: label(b.code) })}
                  title={t('Held down')}
                  onChange={(e) => {
                    const hold = e.target.value as ButtonAction
                    update(cfg.buttons.map((x, j) => (j === i ? { ...x, hold: hold === 'none' ? undefined : hold, hold_app: hold === 'app' ? (x.hold_app ?? apps[0]?.pkg) : undefined } : x)))
                  }}
                >
                  {ACTIONS.map(([id, text]) => (
                    <option key={id} value={id}>
                      {id === 'none' ? t('Held down: nothing extra') : t('Held down: {job}', { job: text })}
                    </option>
                  ))}
                </select>
                {b.hold === 'app' && (
                  <select value={b.hold_app ?? ''} aria-label={t('App to open when held')} onChange={(e) => update(cfg.buttons.map((x, j) => (j === i ? { ...x, hold_app: e.target.value } : x)))}>
                    {apps.map((a) => (
                      <option key={a.pkg} value={a.pkg}>
                        {a.label}
                      </option>
                    ))}
                  </select>
                )}
                <button className="btn small ghost" onClick={() => update(cfg.buttons.filter((_, j) => j !== i))}>
                  {t('Remove')}
                </button>
              </li>
            ))}
          </ul>
        )}
        {cfg.buttons.length > 0 && !cfg.buttons_on && <p className="small muted" style={{ marginTop: 8 }}>{t('Turn on Remote buttons above for these to work.')}</p>}
      </div>
    </>
  )
}

/** A rough picture of the chosen bars, moving to a made-up beat. */
function BarsPreview({ cfg }: { cfg: HomeConfig }): ReactNode {
  const [time, setTime] = useState(0)
  useEffect(() => {
    let id = 0
    const tick = (now: number): void => {
      setTime(now / 1000)
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
    const beat = 0.55 + 0.45 * Math.abs(Math.sin(time * 2.2))
    return Math.min(1, (0.25 + 0.6 * Math.abs(Math.sin(i * 1.7 + time * 3.1)) * (1 - x * 0.35)) * beat)
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
    <div style={{ background: cfg.background, borderRadius: 10, padding: '40px 0 0', marginTop: 12, overflow: 'hidden' }} aria-label={t('Preview of the music bars')}>
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
  const note = a.length > 0 && a.length < 4 ? t('At least 4 digits.') : b.length >= a.length && b && a !== b ? t("The two PINs don't match.") : ''
  return (
    <div className="stack" style={{ gap: 12 }}>
      <div className="pin-row">
        <span>{t('PIN')}</span>
        <PinBoxes value={a} onChange={setA} label={t('PIN')} />
      </div>
      <div className="pin-row">
        <span>{t('Again')}</span>
        <PinBoxes value={b} onChange={setB} label={t('PIN again')} />
      </div>
      <div className="row">
        <ActionButton
          phase={busy ? 'busy' : 'idle'}
          label={label}
          busyLabel={t('Sending')}
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
        <span className="small muted">{note || t("4 to 6 digits. Type it on the TV with the remote's number keys or the on-screen pad.")}</span>
      </div>
    </div>
  )
}

/** As the launcher's homeFont: what each choice looks like here, from fonts every computer has. */
const FONT_CSS: Record<HomeConfig['font'], string> = {
  default: 'inherit', condensed: '"Arial Narrow", "Roboto Condensed", sans-serif-condensed, sans-serif', serif: 'Georgia, serif', casual: '"Comic Sans MS", "Comic Neue", cursive'
}

const LIMIT_CHOICES = [15, 30, 45, 60, 90, 120, 180, 240]
const minutes = (m: number): string => (m < 60 ? t('{n} min', { n: m }) : m % 60 === 0 ? t('{n} h', { n: m / 60 }) : t('{h} h {m} min', { h: Math.floor(m / 60), m: m % 60 }))

/** Daily limits: an app gets so many minutes a day, then it needs the PIN for the rest of the day. */
function DailyLimits({ cfg, set, apps }: { cfg: HomeConfig; set: SetFn; apps: TvApp[] }): ReactNode {
  const [pkg, setPkg] = useState('')
  const [mins, setMins] = useState(60)
  const label = (p: string): string => apps.find((a) => a.pkg === p)?.label ?? p
  const entries = Object.entries(cfg.limits)
  return (
    <div className="card">
      <h2>{t('Daily limits')}</h2>
      <p className="muted small">
        {t('After its minutes for the day, an app needs the PIN, which gives it the rest of the day. Home counts the time itself, and the count starts again at midnight.')}
      </p>
      {entries.map(([p, m]) => (
        <div key={p} className="row" style={{ justifyContent: 'space-between', marginBottom: 6 }}>
          <span>{t('{app}: {time} a day', { app: label(p), time: minutes(m) })}</span>
          <button className="btn small ghost" onClick={() => set({ limits: Object.fromEntries(entries.filter(([k]) => k !== p)) })}>{t('Remove')}</button>
        </div>
      ))}
      <div className="row" style={{ marginTop: 10 }}>
        <select value={pkg} onChange={(e) => setPkg(e.target.value)} aria-label={t('App')}>
          <option value="">{t('Choose an app…')}</option>
          {apps.filter((a) => !(a.pkg in cfg.limits)).map((a) => <option key={a.pkg} value={a.pkg}>{a.label}</option>)}
        </select>
        <select value={mins} onChange={(e) => setMins(Number(e.target.value))} aria-label={t('Minutes a day')}>
          {LIMIT_CHOICES.map((m) => <option key={m} value={m}>{minutes(m)}</option>)}
        </select>
        <button className="btn small" disabled={!pkg} onClick={() => { set({ limits: { ...cfg.limits, [pkg]: mins } }); setPkg('') }}>{t('Add')}</button>
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
        <h2>{t('Set a PIN')}</h2>
        <p className="muted">
          {t("Locked apps ask for this PIN on the TV before they open, however they're opened: from Home, by voice, or from Settings. The TV keeps it scrambled, and this app can't read it back.")}
        </p>
        <PinForm label={t('Set PIN')} onSet={(pin) => call(window.cytv.setLockPin(pin))} />
      </div>
    )
  }
  const toggle = (pkg: string): void => set({ lock_apps: locked.has(pkg) ? cfg.lock_apps.filter((p) => p !== pkg) : [...cfg.lock_apps, pkg] })
  return (
    <>
      <div className="card">
        <h2>{t('App lock')}</h2>
        <Toggle
          label={t('App lock on')}
          hint={
            !cfg.lock_on ? t("Your PIN and the list stay while it's off.")
              : cfg.lock_system ? t('{n} {n|app is|apps are} locked, plus Settings, the Play Store and Quick settings.', { n: cfg.lock_apps.length })
              : t('{n} {n|app is|apps are} locked.', { n: cfg.lock_apps.length })
          }
          checked={cfg.lock_on}
          onChange={(v) => call(window.cytv.setAppLock(v))}
        />
        <p className="small muted" style={{ marginBottom: 0 }}>
          {t('A family lock: it stops a quick tap, not someone with this app or a factory reset. Anyone with ADB access to the TV can turn it off.')}
        </p>
      </div>

      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between' }}>
          <h2 style={{ margin: 0 }}>{t('Locked apps')}</h2>
          <span className="row" style={{ gap: 8 }}>
            <button className="btn small ghost" onClick={() => set({ lock_apps: [...new Set([...cfg.lock_apps, ...shown.map((a) => a.pkg)])] })}>{t('Lock all shown')}</button>
            <button className="btn small ghost" disabled={!cfg.lock_apps.length} onClick={() => set({ lock_apps: [] })}>{t('Unlock all')}</button>
          </span>
        </div>
        <input type="search" placeholder={t('Find an app')} value={q} onChange={(e) => setQ(e.target.value)} style={{ margin: '12px 0', width: '100%' }} aria-label={t('Find an app')} />
        <div className="list-picker" style={{ maxHeight: 360 }}>
          {!q && (
            <>
            <label className="row" style={{ gap: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={cfg.lock_system} onChange={(e) => set({ lock_system: e.target.checked })} />
              <span>{t('Settings, Play Store and Quick settings')}</span>
              <span className="small muted">{t("also the uninstaller, so the lock can't be switched off or uninstalled around")}</span>
            </label>
            <label className="row" style={{ gap: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={cfg.lock_editor} onChange={(e) => set({ lock_editor: e.target.checked })} />
              <span>{t('Customize Home')}</span>
              <span className="small muted">{t('the editor on the TV; its App lock page always asks')}</span>
            </label>
            </>
          )}
          {shown.map((a) => (
            <label key={a.pkg} className="row" style={{ gap: 10, cursor: 'pointer' }}>
              <input type="checkbox" checked={locked.has(a.pkg)} onChange={() => toggle(a.pkg)} />
              <span>{a.label}</span>
              <span className="small muted mono">{a.pkg}</span>
            </label>
          ))}
          {!shown.length && <p className="small muted">{t('No app matches.')}</p>}
        </div>
      </div>

      <div className="card">
        <h2>{t('Options')}</h2>
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('An unlock lasts')}</p>
        <div className="seg">
          {([['sleep', t('Until the TV sleeps')], ['15', t('15 minutes')], ['always', t('Until another app opens')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.lock_for === id ? 'on' : ''} onClick={() => set({ lock_for: id })}>{label}</button>
          ))}
        </div>
        <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Locked apps on Home')}</p>
        <div className="seg">
          {([['badge', t('Show them with a lock')], ['hide', t('Hide them')]] as const).map(([id, label]) => (
            <button key={id} className={cfg.lock_show === id ? 'on' : ''} onClick={() => set({ lock_show: id })}>{label}</button>
          ))}
        </div>
      </div>

      <DailyLimits cfg={cfg} set={set} apps={apps} />

      <div className="card">
        <h2>{t('PIN')}</h2>
        {changing ? (
          <PinForm label={t('Change PIN')} onSet={(pin) => call(window.cytv.setLockPin(pin)).then(() => setChanging(false))} />
        ) : (
          <div className="row">
            <button className="btn ghost" onClick={() => setChanging(true)}>{t('Change PIN')}</button>
            <button className="btn ghost" onClick={() => setForgetting(true)}>{t('Forget the PIN and turn the lock off')}</button>
          </div>
        )}
        <p className="small muted" style={{ marginBottom: 0 }}>{t('Forgot it? Change it here: this computer can always set a new one.')}</p>
      </div>
      {forgetting && (
        <Confirm title={t('Forget the PIN?')} confirmLabel={t('Forget it')} onConfirm={() => { setForgetting(false); call(window.cytv.clearAppLock()) }} onCancel={() => setForgetting(false)}>
          <p>{t('The lock turns off and every app opens freely. Your list of locked apps stays for next time.')}</p>
        </Confirm>
      )}
    </>
  )
}

/** Android 8 has no way for an app to turn the TV off, so Home starts the screensaver there instead. */
function ScreensaverNote({ sdk }: { sdk?: number }): ReactNode {
  return sdk !== undefined && sdk < 28 ? <p className="small muted" style={{ marginTop: 8 }}>{t('On Android 8 Home starts the screensaver instead of turning the TV off.')}</p> : null
}

/** The fireplace: a looping video with its crackle, sent to the TV once, then used as a screensaver, full screen or behind Home. */
function FireplaceCard({ cfg, set, setCfg, onResult, sdk }: { cfg: HomeConfig; set: SetFn; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void; sdk?: number }): ReactNode {
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
      <h2>{t('Fireplace')}</h2>
      <p className="muted">
        {t("A real log fire with its crackle, looping without a seam. It's about 11 MB, downloaded once and sent to the TV. Video and sound from Pixabay.")}
      </p>
      <div className="row" style={{ marginBottom: 12 }}>
        <ActionButton
          phase={phase}
          primary={!on}
          label={on ? t('Send it again') : t('Put the fireplace on the TV')}
          busyLabel={t('Sending')}
          doneLabel={t('On the TV')}
          onClick={() => void go('put')}
        />
        {on && <button className="btn" onClick={() => void go('show')}>{t('Show it now')}</button>}
        {on && <button className="btn ghost" onClick={() => void go('saver')}>{t('Make it the screensaver')}</button>}
      </div>
      {on && (
        <>
          <Toggle label={t('Behind Home')} hint={t("Shows instead of the picture. It holds still while an app is open or the TV's animations are off.")} checked={cfg.fire_wall} onChange={(v) => set({ fire_wall: v })} />
          <Toggle label={t('Crackle full screen and as the screensaver')} checked={cfg.fire_sound} onChange={(v) => set({ fire_sound: v })} />
          <Toggle label={t('Crackle behind Home too')} hint={t('Off by default, so browsing stays quiet.')} checked={cfg.fire_wall_sound} onChange={(v) => set({ fire_wall_sound: v })} />
          <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Turn off after')}</p>
          <div className="seg" style={{ marginBottom: 6 }}>
            {([[0, t('Never')], [30, t('{n} min', { n: 30 })], [60, t('1 hour')], [120, t('2 hours')]] as const).map(([m, label]) => (
              <button key={m} className={cfg.fire_timer === m ? 'on' : ''} onClick={() => set({ fire_timer: m })}>{label}</button>
            ))}
          </div>
          <p className="small muted" style={{ marginTop: 0 }}>
            {t('Full screen only: the fire fades out, then the TV sleeps. Press OK on the remote to change it while the fire is on.')}
          </p>
          {cfg.fire_timer > 0 && <ScreensaverNote sdk={sdk} />}
          {(cfg.fire_sound || cfg.fire_wall_sound) && <Slider label={t('Volume')} value={cfg.fire_volume} min={0} max={100} step={5} unit="%" onChange={(v) => set({ fire_volume: v }, 250)} />}
          <p style={{ marginTop: 10 }}>
            <button className="btn ghost" onClick={() => void go('remove')}>{t('Take it off the TV')}</button>
          </p>
        </>
      )}
    </div>
  )
}

const SLIDE_MAX = 12
const SLIDE_TIMES: [number, string][] = [[0, t('Off')], [5, t('{n} min', { n: 5 })], [15, t('{n} min', { n: 15 })], [30, t('{n} min', { n: 30 })], [60, t('1 hour')]]

/** Slideshow (#56): a new wallpaper every few minutes, from pictures picked here or the current theme's own. */
/** "07:00" for minutes past midnight, and back, for the time fields. */
/** Bedtime's days, 1 Monday to 7 Sunday, as the launcher counts them. */
const WEEKDAYS = (): [number, string][] => [[1, t('Mon')], [2, t('Tue')], [3, t('Wed')], [4, t('Thu')], [5, t('Fri')], [6, t('Sat')], [7, t('Sun')]]
const hhmm = (m: number): string => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`
const minutesOf = (v: string): number | null => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(v)
  return m ? Number(m[1]) * 60 + Number(m[2]) : null
}

/** The save buttons for each look: first time, then again. */
const SAVE_LOOK: Record<LookSlot, [string, string]> = {
  day: [t('Save this as the day look'), t('Save again as the day look')],
  night: [t('Save this as the night look'), t('Save again as the night look')]
}

function savedText(saved: LookSlot[]): string {
  if (saved.length === 0) return t('No looks saved yet.')
  if (saved.length === 1) return saved[0] === 'day' ? t('Saved: the day look.') : t('Saved: the night look.')
  return saved[0] === 'day' ? t('Saved: the day and the night look.') : t('Saved: the night and the day look.')
}

function DayNightCard({ cfg, set, setCfg, onResult }: { cfg: HomeConfig; set: SetFn; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void }): ReactNode {
  const [saving, setSaving] = useState<LookSlot | null>(null)
  const saved = cfg.looks_saved ?? []
  const both = saved.includes('day') && saved.includes('night')
  const save = async (slot: LookSlot): Promise<void> => {
    setSaving(slot)
    try {
      setCfg(await window.cytv.saveHomeLook(slot))
    } catch (e) {
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setSaving(null)
    }
  }
  return (
    <div className="card">
      <h2>{t('Day and night')}</h2>
      <p className="muted">
        {t('Two looks that swap at set times, each with its own wallpaper, colours and icons. Set Home up the way you want it in the day and save it, then do the same for the night. Any theme works, shared ones too.')}
      </p>
      {cfg.looks_saved === undefined ? (
        <p className="small muted">{t('Update CleanMyTV Home on the Overview tab to use this.')}</p>
      ) : (
        <>
          <div className="row" style={{ marginBottom: 8 }}>
            {(['day', 'night'] as const).map((slot) => (
              <button key={slot} className="btn" disabled={saving !== null} onClick={() => void save(slot)}>
                {saving === slot ? t('Saving…') : SAVE_LOOK[slot][saved.includes(slot) ? 1 : 0]}
              </button>
            ))}
          </div>
          <p className="small muted">{savedText(saved)}</p>
          <div className="row" style={{ margin: '12px 0' }}>
            <label>
              {t('Day from')}{' '}
              <input type="time" value={hhmm(cfg.day_at)} onChange={(e) => { const m = minutesOf(e.target.value); if (m !== null) set({ day_at: m }, 600) }} />
            </label>
            <label>
              {t('Night from')}{' '}
              <input type="time" value={hhmm(cfg.night_at)} onChange={(e) => { const m = minutesOf(e.target.value); if (m !== null) set({ night_at: m }, 600) }} />
            </label>
          </div>
          <Toggle
            label={t('Switch at these times')}
            hint={both ? t('Home puts the right look on within a minute, then again at each time. A theme you pick by hand stays until the next switch.') : t('Save both looks first.')}
            checked={cfg.day_night}
            disabled={!both && !cfg.day_night}
            onChange={(v) => set({ day_night: v })}
          />
        </>
      )}
    </div>
  )
}

const WIFI_SECURITY = (): [HomeConfig['wifi_security'], string][] => [['WPA', t('WPA, WPA2 or WPA3')], ['WEP', 'WEP'], ['nopass', t('No password')]]

/** Guest Wi-Fi: a QR code on the TV that phones join from, and guest mode, which shows only the apps picked here. */
function GuestCard({ cfg, set, apps }: { cfg: HomeConfig; set: SetFn; apps: TvApp[] }): ReactNode {
  const [show, setShow] = useState(false)
  const [q, setQ] = useState('')
  const picked = new Set(cfg.guest_apps)
  const shown = apps.filter((a) => !q || a.label.toLowerCase().includes(q.toLowerCase()) || a.pkg.includes(q.toLowerCase()))
  const toggle = (pkg: string): void => set({ guest_apps: picked.has(pkg) ? cfg.guest_apps.filter((p) => p !== pkg) : [...cfg.guest_apps, pkg] })
  return (
    <div className="card">
      <h2>{t('Guests')}</h2>
      <p className="muted">{t('The Guest Wi-Fi icon in the top bar shows a QR code that phones scan to join your Wi-Fi, with the name and password written out. Guest mode, from the same icon, shows only the apps you pick here until the TV goes to sleep.')}</p>
      <Toggle label={t('Guest Wi-Fi icon in the top bar')} checked={cfg.quick.includes('guest')} onChange={(v) => set({ quick: v ? [...cfg.quick, 'guest'] : cfg.quick.filter((id) => id !== 'guest') })} />
      <label className="field">
        <span>{t('Wi-Fi name')}</span>
        <input type="text" maxLength={32} value={cfg.wifi_name} onChange={(e) => set({ wifi_name: e.target.value }, 700)} />
      </label>
      <div className="seg" style={{ margin: '4px 0 12px' }}>
        {WIFI_SECURITY().map(([id, label]) => (
          <button key={id} className={cfg.wifi_security === id ? 'on' : ''} onClick={() => set({ wifi_security: id })}>{label}</button>
        ))}
      </div>
      {cfg.wifi_security !== 'nopass' && (
        <label className="field">
          <span>{t('Password')}</span>
          <span className="hint">{t('It stays on the TV and this computer, and shows on the TV only when someone opens Guest Wi-Fi.')}</span>
          <span className="row">
            <input type={show ? 'text' : 'password'} maxLength={63} value={cfg.wifi_password} style={{ flex: 1 }} onChange={(e) => set({ wifi_password: e.target.value }, 700)} />
            <button className="btn small ghost" onClick={() => setShow(!show)}>{show ? t('Hide') : t('Show')}</button>
          </span>
        </label>
      )}
      <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Apps in guest mode')}</p>
      <p className="small muted">{cfg.guest_apps.length ? t('{n} {n|app|apps} picked. They keep their places on Home.', { n: cfg.guest_apps.length }) : t('Pick at least one app to turn guest mode on from the TV.')}</p>
      <input type="search" placeholder={t('Find an app')} value={q} onChange={(e) => setQ(e.target.value)} style={{ margin: '8px 0 12px', width: '100%' }} aria-label={t('Find an app')} />
      <div className="list-picker" style={{ maxHeight: 280 }}>
        {shown.map((a) => (
          <label key={a.pkg} className="row" style={{ gap: 10, cursor: 'pointer' }}>
            <input type="checkbox" checked={picked.has(a.pkg)} onChange={() => toggle(a.pkg)} />
            <span>{a.label}</span>
            <span className="small muted mono">{a.pkg}</span>
          </label>
        ))}
        {!shown.length && <p className="small muted">{t('No app matches.')}</p>}
      </div>
    </div>
  )
}

/** A line on Home until someone presses OK on it. The phone page can leave one too. */
function NoteCard({ cfg, setCfg, onResult }: { cfg: HomeConfig; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void }): ReactNode {
  const [text, setText] = useState('')
  const [phase, setPhase] = usePhase()
  const on = cfg.note_on_tv ?? ''
  const send = async (line: string): Promise<void> => {
    setPhase('busy')
    try {
      setCfg(await window.cytv.setTvNote(line))
      setPhase('done')
      if (line) setText('')
    } catch (e) {
      setPhase('failed')
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  return (
    <div className="card">
      <h2>{t('Note on the TV')}</h2>
      <p className="muted">{t('A line that stays on Home until someone presses OK on it. You can also leave one from your phone: on the TV, Customize Home, Themes and wallpaper, From your phone.')}</p>
      {cfg.note_on_tv === undefined ? (
        <p className="small muted">{t('Update CleanMyTV Home on the Overview tab to use this.')}</p>
      ) : (
        <>
          {on && (
            <div className="row" style={{ marginBottom: 12 }}>
              <span>{t('On the TV now: “{text}”', { text: on })}</span>
              <button className="btn ghost" onClick={() => void send('')}>{t('Take it down')}</button>
            </div>
          )}
          <div className="row">
            <input type="text" value={text} maxLength={200} placeholder={t("Dinner's in the oven")} style={{ flex: 1, minWidth: 240 }} onChange={(e) => setText(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && text.trim()) void send(text) }} />
            <ActionButton phase={phase} label={on ? t('Replace it') : t('Put it on the TV')} busyLabel={t('Sending')} doneLabel={t('On the TV')} disabled={!text.trim()} onClick={() => void send(text)} />
          </div>
        </>
      )}
    </div>
  )
}

function SlideshowCard({ cfg, set, setCfg, onResult }: { cfg: HomeConfig; set: SetFn; setCfg: (c: HomeConfig) => void; onResult: (r: ApplyResult) => void }): ReactNode {
  const [phase, setPhase] = usePhase()
  const file = useRef<HTMLInputElement>(null)
  const mine = cfg.slides_on_tv ?? 0
  const saver = async (): Promise<void> => {
    try {
      onResult(await window.cytv.slidesSaver())
    } catch (e) {
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  const send = async (files: File[]): Promise<void> => {
    setPhase('busy')
    try {
      const jpegs: string[] = []
      for (const f of files.slice(0, SLIDE_MAX)) jpegs.push((await prepareWallpaper(f, f.name)).base64)
      setCfg(await window.cytv.setSlides(jpegs))
      setPhase('done')
      if (files.length > SLIDE_MAX) onResult({ ok: true, summary: t('The slideshow takes {n} pictures, so the first {n} went to the TV.', { n: SLIDE_MAX }), warnings: [] })
    } catch (e) {
      setPhase('failed')
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }
  return (
    <div className="card">
      <h2>{t('Slideshow')}</h2>
      <p className="muted">{t('A new wallpaper every few minutes while Home is on screen, each with the Sunrise.')}</p>
      <p style={{ margin: '6px 0 8px', fontWeight: 600 }}>{t('Pictures')}</p>
      <div className="seg" style={{ marginBottom: 12 }}>
        <button className={cfg.slide_from === 'mine' ? 'on' : ''} onClick={() => set({ slide_from: 'mine' })}>{mine ? t('My pictures ({n})', { n: mine }) : t('My pictures')}</button>
        <button className={cfg.slide_from === 'theme' ? 'on' : ''} onClick={() => set({ slide_from: 'theme' })}>{t("The theme's pictures")}</button>
      </div>
      {cfg.slide_from === 'mine' && (
        <div className="row" style={{ marginBottom: 12 }}>
          <input
            ref={file}
            type="file"
            accept="image/*,.heic,.heif"
            multiple
            hidden
            onChange={(e) => {
              const fs = [...(e.target.files ?? [])]
              e.target.value = ''
              if (fs.length) void send(fs)
            }}
          />
          <ActionButton phase={phase} label={mine ? t('Choose new pictures…') : t('Choose pictures…')} busyLabel={t('Sending')} doneLabel={t('On the TV')} onClick={() => file.current?.click()} />
          {mine > 0 && <button className="btn ghost" onClick={() => void send([])}>{t('Remove them')}</button>}
          <span className="small muted">{t('Up to {n}. They replace the ones on the TV.', { n: SLIDE_MAX })}</span>
        </div>
      )}
      {cfg.slide_from === 'theme' && (
        <p className="small muted">{t('Steps through the three pictures of the theme you picked on the TV. Put the themes on the TV first (Themes, above).')}</p>
      )}
      <p style={{ margin: '6px 0 8px', fontWeight: 600 }}>{t('Change every')}</p>
      <div className="seg">
        {SLIDE_TIMES.map(([m, label]) => (
          <button key={m} className={cfg.slide_minutes === m ? 'on' : ''} disabled={m > 0 && cfg.slide_from === 'mine' && mine === 0} onClick={() => set({ slide_minutes: m })}>{label}</button>
        ))}
      </div>
      <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Screensaver')}</p>
      <div className="row">
        <button className="btn ghost" onClick={() => void saver()}>{t('Make it the screensaver')}</button>
        <span className="small muted">{t('The photos with the clock and what is playing, over any app, after the TV\'s screensaver time.')}</span>
      </div>
    </div>
  )
}

const ART_KINDS: [ThemeArt['kind'], string][] = [
  ['aurora', t('Aurora')], ['ridges', t('Ridges')], ['dunes', t('Dunes')], ['flow', t('Flow')], ['stars', t('Stars')],
  ['contours', t('Contours')], ['bokeh', t('Bokeh')], ['horizon', t('Horizon')], ['rings', t('Rings')], ['winter', t('Winter night')]
]

/** A colour mixed toward black by [k] (0 to 1), as #RRGGBB. */
function darken(hex: string, k: number): string {
  const n = parseInt(hex.slice(1), 16)
  const c = [n >> 16, (n >> 8) & 255, n & 255].map((v) => Math.round(v * (1 - k)))
  return '#' + c.map((v) => v.toString(16).padStart(2, '0')).join('').toUpperCase()
}

/** Theme editor: start from a built-in theme, change its colours and art, see it, and put it on the TV. */
function ThemeEditor({ themes, busy, onClose }: { themes: ThemeActions; busy: boolean; onClose: () => void }): ReactNode {
  const startFrom = (id: string): { accent: string; second: string; background: string; kind: ThemeArt['kind']; seed: number; base: BuiltInTheme } => {
    const theme = BUILT_IN_THEMES.find((b) => b.id === id) ?? BUILT_IN_THEMES[0]
    return {
      base: theme,
      accent: (theme.look.accent as string) ?? '#4CAF50',
      second: theme.paint.art?.colours[1] ?? theme.paint.glows[1]?.colour ?? theme.paint.glows[0]?.colour ?? '#888888',
      background: theme.paint.base[0],
      kind: theme.paint.art?.kind ?? 'aurora',
      seed: theme.paint.art?.seed ?? 1
    }
  }
  const [s, setS] = useState(() => startFrom(BUILT_IN_THEMES.find((b) => themes.current.startsWith(`${b.id}:`))?.id ?? BUILT_IN_THEMES[0].id))
  const [name, setName] = useState(t('My theme'))
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
    const drawn = drawWallpaper(paint, 384, 216)
    c.getContext('2d')?.drawImage(drawn, 0, 0)
  }, [paint])
  const use = (): void => themes.custom({
    name: name.trim() || t('My theme'),
    look: { ...s.base.look, accent: s.accent, background: darken(s.background, 0.45) },
    wallpaper: paintWallpaper(paint, 'theme.jpg').base64
  })
  return (
    <div className="theme-editor">
      <div className="row" style={{ alignItems: 'flex-start', gap: 20 }}>
        <canvas ref={canvas} width={384} height={216} className="theme-editor-preview" style={{ borderColor: s.accent }} aria-label={t('Preview')} />
        <div style={{ flex: 1, minWidth: 240 }}>
          <label className="field">
            <span>{t('Start from')}</span>
            <select value={s.base.id} onChange={(e) => setS(startFrom(e.target.value))}>
              {BUILT_IN_THEMES.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </label>
          <Colour label={t('Accent')} value={s.accent} onChange={(c) => setS({ ...s, accent: c })} />
          <Colour label={t('Second colour')} hint={t('The other light in the picture.')} value={s.second} onChange={(c) => setS({ ...s, second: c })} />
          <Colour label={t('Background')} hint={t("Keep it dark: Home's text is white.")} value={s.background} onChange={(c) => setS({ ...s, background: c })} />
        </div>
      </div>
      <p style={{ margin: '14px 0 8px', fontWeight: 600 }}>{t('Picture')}</p>
      <div className="seg" style={{ flexWrap: 'wrap' }}>
        {ART_KINDS.map(([k, label]) => (
          <button key={k} className={s.kind === k ? 'on' : ''} onClick={() => setS({ ...s, kind: k })}>{label}</button>
        ))}
      </div>
      <div className="row" style={{ marginTop: 14 }}>
        <button className="btn ghost" onClick={() => setS({ ...s, seed: s.seed + 1 })}>{t('New pattern')}</button>
        <input type="text" value={name} maxLength={40} onChange={(e) => setName(e.target.value)} aria-label={t('Theme name')} style={{ width: 180 }} />
        <button className="btn primary" disabled={busy} onClick={use}>{t('Use on the TV')}</button>
        <button className="btn ghost" onClick={onClose}>{t('Close')}</button>
      </div>
      <p className="small muted" style={{ marginTop: 8, marginBottom: 0 }}>
        {t('Then Save this look… below keeps it as a .{ext} file to share.', { ext: THEME_EXT })}
      </p>
    </div>
  )
}
