import { useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, SystemState } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, errorText } from '../components/ui'

const ADGUARD = 'dns.adguard-dns.com'

const SPEEDS: [number, string][] = [
  [0, 'Off'],
  [0.5, 'Fast (0.5×)'],
  [1, 'Normal (1×)']
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

  if (!sys) return <Busy text="Reading the TV's settings" />

  const dnsOn = sys.privateDnsMode === 'hostname' && !!sys.privateDnsHost
  const scale = Number(sys.animationScale === 'null' ? 1 : sys.animationScale)

  return (
    <>
      <h1>TV extras</h1>
      <p className="lede">Settings outside the launcher that the TV's own menus hide or don't offer.</p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      <div className="card">
          <h2>Animation speed</h2>
          <p className="muted small">How fast menus and apps open, across the whole TV. Off feels snappiest on slow TVs.</p>
          <div className="seg">
            {SPEEDS.map(([v, label]) => (
              <button key={v} className={scale === v ? 'on' : ''} disabled={!!busy} onClick={() => run('Setting animation speed', () => window.cytv.setAnimationScale(v))}>
                {label}
              </button>
            ))}
          </div>
      </div>

      <div className="card">
        <div className="card-head">
          <h2>Block ads inside apps</h2>
          <span className={`chip ${dnsOn ? 'ok' : 'optional'}`}>{dnsOn ? `On · ${sys.privateDnsHost}` : 'Off'}</span>
        </div>
        <p className="muted">
          Points the TV's Private DNS at an ad-blocking resolver, so ad and tracker domains stop resolving in every app. It
          survives reboots. The trade-off: that resolver sees every DNS query the TV makes. It does not remove YouTube ads.
        </p>
        {sys.sdk < 28 ? (
          <div className="notice warn">Private DNS needs Android 9 or newer. This TV runs API {sys.sdk}.</div>
        ) : (
          <>
            <div className="row">
              <button className="btn" disabled={!!busy} onClick={() => run('Turning on AdGuard DNS', () => window.cytv.setPrivateDns(ADGUARD))}>
                Use AdGuard DNS
              </button>
              <input type="text" value={dnsHost} onChange={(e) => setDnsHost(e.target.value)} placeholder="or another resolver, e.g. dns.nextdns.io" style={{ width: 280 }} />
              <button
                className="btn"
                disabled={!!busy || !/^[a-z0-9.-]+\.[a-z]{2,}$/i.test(dnsHost.trim())}
                onClick={() => run('Setting Private DNS', () => window.cytv.setPrivateDns(dnsHost.trim()))}
              >
                Use this one
              </button>
              {dnsOn && (
                <button className="btn ghost" disabled={!!busy} onClick={() => run('Turning Private DNS off', () => window.cytv.setPrivateDns(null))}>
                  Turn off
                </button>
              )}
            </div>
            <p className="small muted" style={{ marginTop: 10 }}>
              If an app stops playing or the TV says “No internet”, the resolver is down. Turn this off.
            </p>
          </>
        )}
      </div>

      <div>
        <div className="card">
          <h2>Remote</h2>
          <p className="muted small">Press buttons on the TV from here, handy when the remote is in the other room.</p>
          <div className="row" style={{ gap: 24, alignItems: 'flex-start' }}>
            <div className="remote" role="group" aria-label="Remote control">
              <span />
              <Key code={19} label="▲" name="Up" />
              <span />
              <Key code={21} label="◀" name="Left" />
              <Key code={23} label="OK" name="OK" round />
              <Key code={22} label="▶" name="Right" />
              <span />
              <Key code={20} label="▼" name="Down" />
              <span />
            </div>
            <div className="stack" style={{ gap: 6 }}>
              <Key code={3} label="Home" wide />
              <Key code={4} label="Back" wide />
              <div className="row" style={{ gap: 6 }}>
                <Key code={25} label="Vol −" wide />
                <Key code={24} label="Vol +" wide />
              </div>
            </div>
          </div>
          <div style={{ marginTop: 14 }}>
            <button className="btn small ghost" onClick={() => setAskReboot(true)}>
              Restart the TV
            </button>
          </div>
        </div>
      </div>

      {askReboot && (
        <Confirm
          title="Restart the TV?"
          confirmLabel="Restart"
          onCancel={() => setAskReboot(false)}
          onConfirm={() => {
            setAskReboot(false)
            window.cytv.reboot().then(
              () => setResult({ ok: true, summary: 'The TV is restarting. Connect again from Setup once it is back.', warnings: [] }),
              (e) => setResult({ ok: false, summary: errorText(e), warnings: [] })
            )
          }}
        >
          <p>The connection drops while it restarts. On some TVs, port 5555 stays closed after a restart until you pair again.</p>
        </Confirm>
      )}
    </>
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
