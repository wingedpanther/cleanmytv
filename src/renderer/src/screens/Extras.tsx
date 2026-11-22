import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AppSize, AppSizes, ApplyResult, BackupPreview, SystemState, TvHealth } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, Skeleton, errorText } from '../components/ui'
import { t } from '../lib/i18n'

const ADGUARD = 'dns.adguard-dns.com'

const SPEEDS: [number, string][] = [
  [0, t('Off')],
  [0.5, t('Fast (0.5×)')],
  [1, t('Normal (1×)')]
]

// match_content_frame_rate; unset means 1, Android's default.
const FRAME_RATES: [number, string][] = [
  [0, t('Never')],
  [1, t('Only if smooth')],
  [2, t('Always')]
]

export function Extras({ markDone }: ScreenProps): ReactNode {
  const [sys, setSys] = useState<SystemState | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [dnsHost, setDnsHost] = useState('')
  const [askReboot, setAskReboot] = useState(false)

  const refresh = (): void => {
    window.cytv.systemState().then((s) => {
      setSys(s)
      if (s.privateDnsMode === 'hostname' && s.privateDnsHost && s.privateDnsHost !== ADGUARD) setDnsHost(s.privateDnsHost)
    })
  }
  useEffect(refresh, [])

  async function run(label: string, fn: () => Promise<ApplyResult>): Promise<void> {
    setBusy(label)
    try {
      const r = await fn()
      setResult(r)
      if (r.ok) markDone('extras')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  if (!sys) return <Busy text={t("Reading the TV's settings")} />

  const dnsOn = sys.privateDnsMode === 'hostname' && !!sys.privateDnsHost
  const scale = Number(sys.animationScale === 'null' ? 1 : sys.animationScale)
  const frameRate = sys.matchFrameRate === 'null' ? 1 : Number(sys.matchFrameRate)

  return (
    <>
      <h1>{t('TV extras')}</h1>
      <p className="lede">{t("Settings outside the launcher that the TV's own menus hide or don't offer.")}</p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {sys.storage && <Storage storage={sys.storage} busy={!!busy} run={run} />}

      <Health onRestart={() => setAskReboot(true)} />

      <div className="card">
          <h2>{t('Animation speed')}</h2>
          <p className="muted small">{t('How fast menus and apps open, across the whole TV. Off feels snappiest on slow TVs.')}</p>
          <div className="seg">
            {SPEEDS.map(([v, label]) => (
              <button key={v} className={scale === v ? 'on' : ''} disabled={!!busy} onClick={() => run(t('Setting animation speed'), () => window.cytv.setAnimationScale(v))}>
                {label}
              </button>
            ))}
          </div>
      </div>

      <div className="card">
        <h2>{t('Google screensaver')}</h2>
        <p className="muted small">
          {t('Shows Google Photos, art and the weather while the TV rests. If it stopped after a clean-up, this turns its two apps back on and sets it as the screensaver again. Clean up leaves them alone after that.')}
        </p>
        <button className="btn" disabled={!!busy} onClick={() => run(t('Bringing back the screensaver'), () => window.cytv.fixScreensaver())}>
          {t('Bring it back')}
        </button>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>{t('Block ads inside apps')}</h2>
          <span className={`chip ${dnsOn ? 'ok' : 'optional'}`}>{dnsOn ? t('On · {host}', { host: sys.privateDnsHost ?? '' }) : t('Off')}</span>
        </div>
        <p className="muted">
          {t("Points the TV's Private DNS at an ad-blocking resolver, so ad and tracker domains stop resolving in every app. It survives reboots. The trade-off: that resolver sees every DNS query the TV makes. It does not remove YouTube ads.")}
        </p>
        {sys.sdk < 28 ? (
          <div className="notice warn">{t('Private DNS needs Android 9 or newer. This TV runs API {sdk}.', { sdk: sys.sdk })}</div>
        ) : (
          <>
            <div className="row">
              <button className="btn" disabled={!!busy} onClick={() => run(t('Turning on AdGuard DNS'), () => window.cytv.setPrivateDns(ADGUARD))}>
                {t('Use AdGuard DNS')}
              </button>
              <input type="text" value={dnsHost} onChange={(e) => setDnsHost(e.target.value)} placeholder={t('or another resolver, e.g. dns.nextdns.io')} style={{ width: 280 }} />
              <button
                className="btn"
                disabled={!!busy || !/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(dnsHost.trim())}
                onClick={() => run(t('Setting Private DNS'), () => window.cytv.setPrivateDns(dnsHost.trim()))}
              >
                {t('Use this one')}
              </button>
              {dnsOn && (
                <button className="btn ghost" disabled={!!busy} onClick={() => run(t('Turning Private DNS off'), () => window.cytv.setPrivateDns(null))}>
                  {t('Turn off')}
                </button>
              )}
            </div>
            <p className="small muted" style={{ marginTop: 10 }}>
              {t('If an app stops playing or the TV says “No internet”, the resolver is down. Turn this off.')}
            </p>
          </>
        )}
      </div>

      {sys.sdk >= 31 && sys.matchFrameRate !== '' && (
        <div className="card">
          <h2>{t('Match frame rate')}</h2>
          <p className="muted small">{t("The TV switches to the video's frame rate, so films at 24 fps don't judder.")}</p>
          <div className="seg">
            {FRAME_RATES.map(([v, label]) => (
              <button
                key={v}
                className={frameRate === v ? 'on' : ''}
                disabled={!!busy}
                title={v === 2 ? t('The screen may go black for a moment when a video starts.') : undefined}
                onClick={() => run(t('Setting the frame rate'), () => window.cytv.setMatchFrameRate(v))}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}

      <Backup busy={!!busy} run={run} onResult={setResult} />

      <div>
        <div className="card">
          <h2>{t('Remote')}</h2>
          <p className="muted small">{t('Press buttons on the TV from here, handy when the remote is in the other room.')}</p>
          <div className="row" style={{ gap: 24, alignItems: 'flex-start' }}>
            <div className="remote" role="group" aria-label={t('Remote control')}>
              <span />
              <Key code={19} label="▲" name={t('Up')} />
              <span />
              <Key code={21} label="◀" name={t('Left')} />
              <Key code={23} label="OK" name="OK" round />
              <Key code={22} label="▶" name={t('Right')} />
              <span />
              <Key code={20} label="▼" name={t('Down')} />
              <span />
            </div>
            <div className="stack" style={{ gap: 6 }}>
              <Key code={3} label={t('Home')} wide />
              <Key code={4} label={t('Back')} wide />
              <div className="row" style={{ gap: 6 }}>
                <Key code={25} label={t('Vol −')} wide />
                <Key code={24} label={t('Vol +')} wide />
              </div>
            </div>
          </div>
          <div style={{ marginTop: 14 }}>
            <button className="btn small ghost" onClick={() => setAskReboot(true)}>
              {t('Restart the TV')}
            </button>
          </div>
        </div>
      </div>

      {askReboot && (
        <Confirm
          title={t('Restart the TV?')}
          confirmLabel={t('Restart')}
          onCancel={() => setAskReboot(false)}
          onConfirm={() => {
            setAskReboot(false)
            window.cytv.reboot().then(
              () => setResult({ ok: true, summary: t('The TV is restarting. Connect again from Setup once it is back.'), warnings: [] }),
              (e) => setResult({ ok: false, summary: errorText(e), warnings: [] })
            )
          }}
        >
          <p>{t('The connection drops while it restarts. On some TVs, port 5555 stays closed after a restart until you pair again.')}</p>
        </Confirm>
      )}
    </>
  )
}

// Little memory left for the app in front: TVs start closing apps in the background and feel slow.
const LOW_MEM = 0.15
// TVs that only ever sleep can run for weeks; a restart clears what has built up.
const LONG_UP_DAYS = 7
// Chips on TVs throttle somewhere above this.
const HOT_C = 80

/** Memory, time since the last restart and temperature, read when the screen opens. */
function Health({ onRestart }: { onRestart: () => void }): ReactNode {
  const [h, setH] = useState<TvHealth | null>(null)
  const [error, setError] = useState<string | null>(null)
  const read = (): void => {
    setError(null)
    window.cytv.health().then(setH, (e) => setError(errorText(e)))
  }
  useEffect(read, [])

  if (error) return <div className="card"><h2>{t('Health')}</h2><div className="notice warn" style={{ marginBottom: 0 }}>{error}</div></div>
  if (!h) return <div className="card"><h2>{t('Health')}</h2><Skeleton rows={3} label={t("Reading the TV's health")} /></div>

  const lowMem = h.memTotalMb > 0 && h.memFreeMb / h.memTotalMb < LOW_MEM
  const days = Math.floor(h.uptimeS / 86400)
  const longUp = days >= LONG_UP_DAYS
  const hot = h.tempC !== null && h.tempC >= HOT_C
  const up = days >= 1 ? t('{n} {n|day|days}', { n: days }) : t('{n} {n|hour|hours}', { n: Math.max(1, Math.floor(h.uptimeS / 3600)) })
  const tips = [
    lowMem && t('Little memory is free, so apps may reload when you switch back to them.'),
    longUp && t("The TV hasn't restarted in {time}. A restart clears what has built up and often makes an older TV quicker.", { time: up }),
    hot && t('The chip is hot. Give the TV or box some air around its vents.')
  ].filter((x): x is string => !!x)
  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('Health')}</h2>
        <span className={`chip ${tips.length ? 'warn' : 'ok'}`}>{tips.length ? t('Worth a look') : t('All good')}</span>
      </div>
      <div className="stat-row">
        <div className="stat">
          <b>{t('{n} MB', { n: h.memFreeMb })}</b>
          <span>{t('free of {total} memory', { total: t('{n} MB', { n: h.memTotalMb }) })}</span>
        </div>
        <div className="stat">
          <b>{up}</b>
          <span>{t('since the last restart')}</span>
        </div>
        {h.tempC !== null && (
          <div className="stat">
            <b>{t('{n} °C', { n: h.tempC })}</b>
            <span>{t('hottest chip sensor')}</span>
          </div>
        )}
        {h.chip && (
          <div className="stat">
            <b>{h.chip}</b>
            <span>{t('{n} {n|core|cores}', { n: h.cores })}</span>
          </div>
        )}
      </div>
      {tips.map((tip) => (
        <p key={tip} className="muted small" style={{ marginTop: 8 }}>{tip}</p>
      ))}
      <div className="row" style={{ marginTop: 12 }}>
        {longUp && <button className="btn" onClick={onRestart}>{t('Restart the TV')}</button>}
        <button className="btn small ghost" onClick={read}>{t('Read again')}</button>
      </div>
    </div>
  )
}

// Below this, installs and app updates start failing on most TVs (#48).
const LOW_MB = 500

/** Bytes as MB, or GB from 1 GB up. */
function size(bytes: number): string {
  const mb = bytes / 1024 / 1024
  if (mb >= 1024) return t('{n} GB', { n: (mb / 1024).toFixed(1) })
  return t('{n} MB', { n: mb >= 10 ? Math.round(mb) : mb.toFixed(1) })
}

const total = (a: AppSize): number => a.app + (a.data ?? 0) + (a.cache ?? 0)
// The rest wait behind Show all: the top of the list is where the space is.
const TOP = 12

type Run = (label: string, fn: () => Promise<ApplyResult>) => Promise<void>

function Storage({ storage, busy, run }: { storage: NonNullable<SystemState['storage']>; busy: boolean; run: Run }): ReactNode {
  const [sizes, setSizes] = useState<AppSizes | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [all, setAll] = useState(false)
  /** Names as CleanMyTV Home shows them, when the TV has it. */
  const [labels, setLabels] = useState<Map<string, string>>(new Map())
  const [removing, setRemoving] = useState<AppSize | null>(null)

  async function load(): Promise<void> {
    setLoading(true)
    setError(null)
    try {
      setSizes(await window.cytv.appSizes())
      if (!labels.size) {
        const apps = await window.cytv.homeApps().catch(() => [])
        setLabels(new Map(apps.map((a) => [a.pkg, a.label])))
      }
    } catch (e) {
      setError(errorText(e))
    } finally {
      setLoading(false)
    }
  }

  /** Runs a job that changes the space, then measures again if the list is open. */
  async function change(label: string, fn: () => Promise<ApplyResult>): Promise<void> {
    await run(label, fn)
    if (sizes) await load()
  }

  const free = Math.round(storage.freeKb / 1024)
  const total = Math.round(storage.totalKb / 1024)
  const used = Math.min(100, Math.max(0, 100 - (storage.freeKb / storage.totalKb) * 100))
  const gb = (mb: number): string => (mb >= 1024 ? t('{n} GB', { n: (mb / 1024).toFixed(1) }) : t('{n} MB', { n: mb }))
  const low = free < LOW_MB
  return (
    <div className="card">
      <div className="card-head">
        <h2>{t('Storage')}</h2>
        <span className={`chip ${low ? 'warn' : 'ok'}`}>{t('{size} free', { size: gb(free) })}</span>
      </div>
      <div className={`progress${low ? ' low' : ''}`} role="meter" aria-valuenow={Math.round(used)} aria-valuemin={0} aria-valuemax={100} aria-label={t('Storage used')}>
        <div style={{ width: `${used}%` }} />
      </div>
      <p className="muted small" style={{ marginTop: 8 }}>
        {t('{used} of {total} used.', { used: gb(total - free), total: gb(total) })}{' '}
        {low ? t('Low enough that installs and app updates can fail.') : t('Apps keep caches here that they rebuild when needed.')}
      </p>
      <div className="row" style={{ marginTop: 12 }}>
        <button className="btn" disabled={busy} onClick={() => void change(t('Clearing app caches'), () => window.cytv.clearCaches())}>
          {t('Clear app caches')}
        </button>
        <span className="muted small">{t('Logins and settings stay. Apps may open a little slower the first time.')}</span>
      </div>

      {!sizes && !loading && (
        <button className="btn small ghost" style={{ marginTop: 12 }} disabled={busy} onClick={() => void load()}>
          {t('See what takes the space')}
        </button>
      )}
      {loading && !sizes && <div style={{ marginTop: 12 }}><Skeleton rows={5} label={t('Measuring the apps')} /></div>}
      {error && <div className="notice warn" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
      {sizes && <AppList sizes={sizes} labels={labels} all={all} busy={busy || loading} onAll={() => setAll(true)} onRemove={setRemoving} />}

      {removing && (
        <Confirm
          title={t('Remove the update?')}
          confirmLabel={t('Remove update')}
          onCancel={() => setRemoving(null)}
          onConfirm={() => {
            const pkg = removing.pkg
            setRemoving(null)
            void change(t('Removing the update'), () => window.cytv.removeUpdate(pkg))
          }}
        >
          <p>{t('{app} goes back to the version the TV came with, and stays switched off.', { app: labels.get(removing.pkg) ?? removing.pkg })}</p>
          <p className="muted small">{t("Its data goes too. If you switch it on again, the Play Store can update it again.")}</p>
        </Confirm>
      )}
    </div>
  )
}

function AppList(props: { sizes: AppSizes; labels: Map<string, string>; all: boolean; busy: boolean; onAll: () => void; onRemove: (a: AppSize) => void }): ReactNode {
  const { sizes, labels } = props
  const shown = useMemo(() => (props.all ? sizes.apps : sizes.apps.slice(0, TOP)), [sizes, props.all])
  if (!sizes.apps.length) return <p className="muted small" style={{ marginTop: 12 }}>{t("The TV didn't say how much space its apps take.")}</p>
  return (
    <div style={{ marginTop: 14 }}>
      {sizes.source === 'code' && (
        <p className="muted small">{t("This TV hasn't measured its apps' data yet, so these are the apps' own sizes.")}</p>
      )}
      <div className="table-wrap">
        <table className="pkgs">
          <thead>
            <tr>
              <th>{t('App')}</th>
              <th style={{ width: 110 }}>{t('Size')}</th>
              <th style={{ width: 150 }} />
            </tr>
          </thead>
          <tbody>
            {shown.map((a) => (
              <tr key={a.pkg}>
                <td>
                  {labels.get(a.pkg) ? <div>{labels.get(a.pkg)}</div> : null}
                  <div className={labels.get(a.pkg) ? 'pkg-name small muted' : 'pkg-name'}>{a.pkg}</div>
                </td>
                <td title={a.data === undefined ? undefined : t('App {app}, data {data}, cache {cache}', { app: size(a.app), data: size(a.data), cache: size(a.cache ?? 0) })}>
                  {size(total(a))}
                  {a.state === 'disabled' && <div><span className="chip">{t('Off')}</span></div>}
                </td>
                <td>
                  {a.state === 'disabled' && a.updated && (
                    <button className="btn small ghost" disabled={props.busy} onClick={() => props.onRemove(a)}>
                      {t('Remove update')}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!props.all && sizes.apps.length > TOP && (
        <button className="btn small ghost" style={{ marginTop: 8 }} onClick={props.onAll}>
          {t('Show all {n}', { n: sizes.apps.length })}
        </button>
      )}
    </div>
  )
}

/** Saves the clean-up and CleanMyTV Home's settings to a file, and puts a file's back after a look at what it does. */
function Backup({ busy, run, onResult }: { busy: boolean; run: Run; onResult: (r: ApplyResult) => void }): ReactNode {
  const [preview, setPreview] = useState<BackupPreview | null>(null)

  async function save(): Promise<void> {
    try {
      const path = await window.cytv.saveBackup()
      if (path) onResult({ ok: true, summary: t('Saved the backup to {path}.', { path }), warnings: [] })
    } catch (e) {
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }

  async function open(): Promise<void> {
    try {
      setPreview(await window.cytv.openBackup())
    } catch (e) {
      onResult({ ok: false, summary: errorText(e), warnings: [] })
    }
  }

  const when = (iso: string): string => {
    const d = new Date(iso)
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
  }

  return (
    <div className="card">
      <h2>{t('Back up this TV')}</h2>
      <p className="muted small">
        {t("One file with what Clean up switched off and CleanMyTV Home's settings and app order.")}{' '}
        {t('Restore puts them back after a reset, or on another TV. The PIN and wallpaper stay out.')}
      </p>
      <div className="row">
        <button className="btn" disabled={busy} onClick={() => void save()}>
          {t('Save a backup')}
        </button>
        <button className="btn ghost" disabled={busy} onClick={() => void open()}>
          {t('Restore a backup')}
        </button>
      </div>

      {preview && (
        <Confirm
          title={t('Restore this backup?')}
          confirmLabel={t('Restore')}
          onCancel={() => setPreview(null)}
          onConfirm={() => {
            setPreview(null)
            void run(t('Restoring the backup'), () => window.cytv.restoreBackup())
          }}
        >
          <p>{t('From {tv}, saved {date}.', { tv: preview.tv || preview.file, date: when(preview.savedAt) })}</p>
          {preview.otherModel && <div className="notice warn">{t('This backup comes from another TV model. Check the apps it switches off.')}</div>}
          <ul>
            <li>
              {preview.switchOff.length
                ? t('Switches off {n} {n|app|apps} that {n|is|are} on now.', { n: preview.switchOff.length })
                : t('Switches nothing off: everything in it is off already.')}
            </li>
            {preview.missing.length > 0 && <li>{t("Skips {n} {n|app|apps} this TV doesn't have.", { n: preview.missing.length })}</li>}
            {preview.home === 'apply' && <li>{t("Puts CleanMyTV Home's settings and app order back.")}</li>}
            {preview.home === 'not-installed' && <li>{t("Leaves out CleanMyTV Home's settings, as this TV doesn't have it.")}</li>}
          </ul>
          {preview.switchOff.length > 0 && <p className="small mono muted">{preview.switchOff.join(', ')}</p>}
        </Confirm>
      )}
    </div>
  )
}

function Key({ code, label, name, round, wide }: { code: number; label: string; name?: string; round?: boolean; wide?: boolean }): ReactNode {
  return (
    <button
      className={`btn${round ? ' ok-key' : ''}${wide ? ' small' : ''}`}
      aria-label={name ?? label}
      style={wide ? { minWidth: 72, justifyContent: 'center' } : undefined}
      onClick={() => window.cytv.key(code)}
    >
      {label}
    </button>
  )
}
