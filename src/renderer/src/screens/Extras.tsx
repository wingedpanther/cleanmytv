import { useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, SystemState } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, errorText } from '../components/ui'
import { t } from '../lib/i18n'

const ADGUARD = 'dns.adguard-dns.com'

const SPEEDS: [number, string][] = [
  [0, t('Off')],
  [0.5, t('Fast (0.5×)')],
  [1, t('Normal (1×)')]
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

      {sys.storage && <Storage storage={sys.storage} busy={!!busy} onClear={() => run(t('Clearing app caches'), () => window.cytv.clearCaches())} />}

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

// Below this, installs and app updates start failing on most TVs (#48).
const LOW_MB = 500

function Storage({ storage, busy, onClear }: { storage: NonNullable<SystemState['storage']>; busy: boolean; onClear: () => void }): ReactNode {
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
        <button className="btn" disabled={busy} onClick={onClear}>
          {t('Clear app caches')}
        </button>
        <span className="muted small">{t('Logins and settings stay. Apps may open a little slower the first time.')}</span>
      </div>
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
