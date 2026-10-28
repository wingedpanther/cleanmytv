import { useEffect, useState, type ReactNode } from 'react'
import type { ScreenProps } from '../App'
import { Busy } from '../components/ui'

const STEPS = [
  'On the TV, open Developer options and turn off USB debugging.',
  'Turn off Network debugging, ADB debugging or Wireless debugging if your TV has it.',
  'Optional: choose Revoke USB debugging authorisations, so no computer is trusted any more. Skip this to avoid the Allow prompt next time.',
  'Restart the TV.'
]

export function Finish({ status, markDone }: ScreenProps): ReactNode {
  const [host, setHost] = useState(status.host ?? '')
  const [ticks, setTicks] = useState<boolean[]>(STEPS.map(() => false))
  const [checking, setChecking] = useState(false)
  const [res, setRes] = useState<{ closed: boolean; message: string } | null>(null)

  useEffect(() => {
    if (!host) window.cytv.lastHost().then((h) => h && setHost(h))
  }, [])

  async function check(): Promise<void> {
    setChecking(true)
    setRes(null)
    try {
      const r = await window.cytv.checkAdbClosed(host.trim())
      setRes(r)
      if (r.closed) markDone('finish')
    } catch (e) {
      setRes({ closed: false, message: e instanceof Error ? e.message : String(e) })
    } finally {
      setChecking(false)
    }
  }

  return (
    <>
      <h1>Finish and lock</h1>
      <p className="lede">
        Network debugging is a plain-text shell on port 5555. On some TVs any device on your Wi-Fi can use it without the Allow
        prompt. Close it when you're done. The home screen and the clean-up keep working without it. Turn it on again
        any time you want to change something from this app.
      </p>

      <div className="card">
        <h2>On the TV</h2>
        <ul className="checklist">
          {STEPS.map((s, i) => (
            <li key={i} className="check" style={{ gridTemplateColumns: 'auto 1fr' }}>
              <label className="row" style={{ cursor: 'pointer', gridColumn: '1 / 3' }}>
                <input type="checkbox" checked={ticks[i]} onChange={(e) => setTicks(ticks.map((t, j) => (j === i ? e.target.checked : t)))} />
                <span style={{ textDecoration: ticks[i] ? 'line-through' : undefined, opacity: ticks[i] ? 0.6 : 1 }}>{s}</span>
              </label>
            </li>
          ))}
        </ul>
        {status.state === 'connected' && (
          <p className="small muted" style={{ marginTop: 10 }}>
            The app is still connected. Turning debugging off ends the connection, which is expected.
          </p>
        )}
      </div>

      <div className="card white">
        <h2>Coming back later</h2>
        <p className="muted" style={{ marginBottom: 8 }}>
          To change the wallpaper, the app order or anything else on the home screen:
        </p>
        <ol className="small" style={{ margin: '0 0 8px', paddingLeft: 20 }}>
          <li>On the TV, turn USB debugging on again (and Network or ADB debugging if your TV has it).</li>
          <li>Open this app. It reconnects to the TV by itself, finds it again if its address changed, and opens the Home screen step.</li>
          <li>If you revoked the authorisations, choose Allow on the TV once more.</li>
          <li>Apply your changes, then turn debugging off again.</li>
        </ol>
        <p className="small muted" style={{ margin: 0 }}>
          The app reads what is on the TV before you edit, so you always start from the current wallpaper and order. Some Google TVs
          that needed a pairing code close port 5555 when they restart; the app shows the pairing form again if so.
        </p>
      </div>

      <div className="card white">
        <h2>Check from this computer</h2>
        <p className="muted">Once the TV has restarted, check that port 5555 no longer answers.</p>
        <div className="row">
          <input type="text" value={host} onChange={(e) => setHost(e.target.value)} placeholder="192.168.1.50" style={{ width: 200 }} />
          <button className="btn primary" onClick={check} disabled={checking || !/^\d{1,3}(\.\d{1,3}){3}$/.test(host.trim())}>
            Check port 5555 is closed
          </button>
          {checking && <Busy text="Knocking on port 5555" />}
        </div>
        {res && (
          <div className={`notice ${res.closed ? 'ok' : 'warn'}`} style={{ marginTop: 16, marginBottom: 0 }}>
            <b>{res.closed ? 'Closed.' : 'Still open.'}</b> {res.message}
          </div>
        )}
      </div>
    </>
  )
}
