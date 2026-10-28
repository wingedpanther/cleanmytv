import { useCallback, useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, CheckItem, DownloadProgress, ScanHit, ToolId } from '@shared/api'
import type { ScreenProps } from '../App'
import { TvPicker, TvThumb } from '../components/TvPicker'
import { Busy, ResultNotice, StatusChip, errorText } from '../components/ui'

const IP_RE = /^\d{1,3}(\.\d{1,3}){3}(:\d{2,5})?$/

export function Setup({ status, go, tvs, refreshTvs }: ScreenProps): ReactNode {
  const [checks, setChecks] = useState<CheckItem[] | null>(null)
  const [progress, setProgress] = useState<Partial<Record<ToolId, DownloadProgress>>>({})
  const [downloading, setDownloading] = useState<Set<ToolId>>(new Set())
  const [downloadResult, setDownloadResult] = useState<ApplyResult | null>(null)

  const [hits, setHits] = useState<ScanHit[] | null>(null)
  const [scanning, setScanning] = useState(false)
  const [host, setHost] = useState('')
  const [connectError, setConnectError] = useState<string | null>(null)

  const refreshChecks = useCallback(() => {
    window.cytv.checks().then(setChecks, () => setChecks([]))
  }, [])

  useEffect(() => {
    refreshChecks()
    window.cytv.lastHost().then((h) => h && setHost((cur) => cur || h))
    return window.cytv.onProgress((p) => setProgress((prev) => ({ ...prev, [p.tool]: p })))
  }, [refreshChecks])

  // The checklist depends on the connection.
  useEffect(() => {
    if (status.state === 'connected' || status.state === 'error' || status.state === 'idle') refreshChecks()
  }, [status.state, refreshChecks])

  async function download(tool: ToolId): Promise<void> {
    setDownloading((s) => new Set(s).add(tool))
    try {
      setDownloadResult(await window.cytv.download(tool))
    } catch (e) {
      setDownloadResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setDownloading((s) => {
        const n = new Set(s)
        n.delete(tool)
        return n
      })
      refreshChecks()
    }
  }

  async function scan(): Promise<void> {
    setScanning(true)
    try {
      const found = await window.cytv.scan()
      setHits(found)
      if (found.length === 1 && !host) setHost(found[0].host)
    } catch (e) {
      setConnectError(errorText(e))
    } finally {
      setScanning(false)
    }
  }

  // The saved TV wasn't at its address: look for it, and let the user pick (the app never logs in to
  // scanned devices by itself).
  useEffect(() => {
    if (status.moved && hits === null && !scanning) scan()
  }, [status.moved])

  async function connect(h = host): Promise<void> {
    setConnectError(null)
    try {
      const s = await window.cytv.connect(h.trim())
      if (s.state === 'error' && !s.needsPairing) setConnectError(s.error ?? 'Could not connect.')
    } catch (e) {
      setConnectError(errorText(e))
    }
  }

  const busy = status.state === 'connecting' || status.state === 'waiting-auth'
  const connected = status.state === 'connected'
  const required = checks?.filter((c) => c.id !== 'platform-tools') ?? []
  const optional = checks?.filter((c) => c.id === 'platform-tools') ?? []

  return (
    <>
      <h1>Set up</h1>
      <p className="lede">
        This app talks to the TV over your Wi-Fi. You don't need Python, Java or Android tools to clean the TV and install
        the home screen. The one optional download is only for pairing some Google TVs.
      </p>

      {status.state === 'waiting-auth' && <WaitingForAllow />}

      {!connected && status.state !== 'waiting-auth' && (
        <TvPicker
          tvs={tvs}
          status={status}
          onChange={refreshTvs}
          title={tvs.length > 1 ? 'Which TV?' : 'Your TV'}
          hint={tvs.length > 1 ? "You've used this app with more than one TV, so it waits for you to pick." : undefined}
        />
      )}

      {connected && status.probe ? (
        <div className="card white">
          <div className="tv-card">
            {tvs.find((t) => t.host === status.host)?.thumb ? <TvThumb tv={tvs.find((t) => t.host === status.host)} /> : <div className="tv-glyph" aria-hidden />}
            <div>
              <div className="row between">
                <h2 style={{ margin: 0 }}>
                  {status.probe.manufacturer} {status.probe.model}
                </h2>
                <div className="row">
                  <StatusChip status="ok" text="Connected" />
                  <button className="btn small ghost" onClick={() => window.cytv.disconnect()}>
                    Disconnect
                  </button>
                </div>
              </div>
              <div className="facts">
                <div>
                  <span>Address</span>
                  <b>{status.host}</b>
                </div>
                <div>
                  <span>Android</span>
                  <b>
                    {status.probe.release} (API {status.probe.sdk})
                  </b>
                </div>
                <div>
                  <span>Device</span>
                  <b>{status.probe.device}</b>
                </div>
                <div>
                  <span>Build</span>
                  <b className="mono">{status.probe.build}</b>
                </div>
              </div>
            </div>
          </div>
          {status.probe.adbSecure === '0' && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              This TV reports <code>ro.adb.secure=0</code>. Any device on your network can reach its debug port without the
              Allow prompt. Close it when you're done: the Finish step walks you through it.
            </div>
          )}
          {status.probe.sdk > 0 && status.probe.sdk < 28 && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              This TV reports Android {status.probe.release} (API {status.probe.sdk}). CleanMyTV Home needs Android 9
              (API 28) or newer, so the Home screen step will refuse to install here. Cleanup stays available and
              reversible (nothing is uninstalled).
            </div>
          )}
          <div className="row" style={{ marginTop: 16 }}>
            <button className="btn primary" onClick={() => go('debloat')}>
              Next: clean up the TV
            </button>
          </div>
        </div>
      ) : null}

      {connected && status.probe ? (
        <TvPicker tvs={tvs} status={status} onChange={refreshTvs} title="Your other TVs" hint="Connecting to one disconnects this TV." />
      ) : (
        <div className="grid-2">
          <div className="card">
            <h2>1. On the TV</h2>
            <ol className="guide">
              <li>
                <span>
                  Open <span className="path">Settings<i>→</i>Device Preferences<i>→</i>About</span>. On some Google TVs it is{' '}
                  <span className="path">Settings<i>→</i>System<i>→</i>About</span>.
                </span>
              </li>
              <li>
                <span>
                  Scroll to <b>Build</b> (or <b>Android TV OS build</b>) and press OK on it seven times. The TV says you are now a
                  developer.
                </span>
              </li>
              <li>
                <span>
                  Go back one screen and open <b>Developer options</b>.
                </span>
              </li>
              <li>
                <span>
                  Turn on <b>USB debugging</b>. If you also see <b>Network debugging</b> or <b>ADB debugging</b>, turn that on
                  too.
                </span>
              </li>
              <li>
                <span>Keep the TV on and on the same Wi-Fi as this computer.</span>
              </li>
            </ol>
          </div>

          <div className="card">
            <h2>2. Find your TV</h2>
            <p className="muted small">The scan looks for TVs with port 5555 open on your network. It takes a few seconds.</p>
            {status.moved && status.error && (
              <div className="notice warn" style={{ marginBottom: 12 }}>
                {status.error} Only connect to an address you recognise as your TV.
              </div>
            )}
            <div className="row" style={{ marginBottom: 12 }}>
              <button className="btn" onClick={scan} disabled={scanning || busy}>
                {scanning ? 'Scanning…' : hits ? 'Scan again' : 'Scan the network'}
              </button>
              {scanning && <span className="spinner" />}
            </div>
            {hits && hits.length === 0 && (
              <div className="notice warn">
                No TV answered. Check that USB debugging is on and the TV is on this Wi-Fi, or type its address below (TV
                Settings → Network → your network).
              </div>
            )}
            {hits && hits.length > 0 && (
              <ul className="host-list">
                {hits.map((h) => (
                  <li key={h.host}>
                    <button
                      className={`host-item${host === h.host ? ' selected' : ''}`}
                      onClick={() => setHost(h.host)}
                      onDoubleClick={() => connect(h.host)}
                    >
                      <span className="dot ok" />
                      <span style={{ flex: 1 }}>{h.host}</span>
                      <span className="muted small">port 5555 open</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <form
              className="stack"
              onSubmit={(e) => {
                e.preventDefault()
                connect()
              }}
            >
              <label className="field">
                TV address
                <input type="text" value={host} onChange={(e) => setHost(e.target.value)} placeholder="192.168.1.50" />
              </label>
              <div className="row">
                <button className="btn primary" type="submit" disabled={busy || !IP_RE.test(host.trim())}>
                  {status.state === 'connecting' ? 'Connecting…' : 'Connect'}
                </button>
                {status.state === 'connecting' && <Busy text="Opening the connection" />}
              </div>
            </form>
            {connectError && (
              <div className="notice error" style={{ marginTop: 14, marginBottom: 0 }}>
                {connectError}
              </div>
            )}
          </div>
        </div>
      )}

      {status.needsPairing && !connected && <PairForm host={status.host ?? host} onPaired={() => connect(status.host ?? host)} />}

      <div className="card white" style={{ marginTop: 8 }}>
        <div className="card-head">
          <h2>What's ready</h2>
          <button className="btn small ghost" onClick={refreshChecks}>
            Check again
          </button>
        </div>
        <ResultNotice result={downloadResult} onDismiss={() => setDownloadResult(null)} />
        {!checks ? (
          <Busy text="Checking this computer" />
        ) : (
          <>
            <Checklist items={required} progress={progress} downloading={downloading} onDownload={download} />
            {optional.length > 0 && (
              <>
                <h3 style={{ marginTop: 22 }}>Optional</h3>
                <p className="muted small">
                  Only needed to pair a TV that asks for a code.
                  Each one downloads into this app's own folder.
                </p>
                <Checklist items={optional} progress={progress} downloading={downloading} onDownload={download} />
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}

function Checklist(props: {
  items: CheckItem[]
  progress: Partial<Record<ToolId, DownloadProgress>>
  downloading: Set<ToolId>
  onDownload: (t: ToolId) => void
}): ReactNode {
  return (
    <ul className="checklist">
      {props.items.map((c) => {
        const p = c.download ? props.progress[c.download] : undefined
        const active = c.download ? props.downloading.has(c.download) : false
        return (
          <li key={c.id} className="check">
            <div className="check-title">
              <StatusChip status={c.status} />
              <span>{c.label}</span>
            </div>
            <div className="check-detail">
              {c.detail}
              {active && p && <DownloadBar p={p} />}
              {active && !p && <div className="progress indeterminate"><div /></div>}
            </div>
            <div className="check-actions">
              {c.download && (
                <button className="btn small" onClick={() => props.onDownload(c.download!)} disabled={active}>
                  {active ? 'Downloading…' : 'Download'}
                </button>
              )}
            </div>
          </li>
        )
      })}
    </ul>
  )
}

function DownloadBar({ p }: { p: DownloadProgress }): ReactNode {
  const pct = p.total > 0 ? Math.min(100, (p.received / p.total) * 100) : 0
  const label =
    p.phase === 'download'
      ? `${mb(p.received)} of ${p.total ? mb(p.total) : '?'}`
      : p.phase === 'extract'
        ? 'Unpacking'
        : p.phase === 'install'
          ? p.message ?? 'Installing'
          : p.phase === 'error'
            ? p.message ?? 'Failed'
            : 'Done'
  return (
    <div>
      <div className={`progress${p.phase === 'download' && p.total ? '' : ' indeterminate'}`}>
        <div style={p.phase === 'download' && p.total ? { width: `${pct}%` } : undefined} />
      </div>
      <span className="small muted">{label}</span>
    </div>
  )
}

const mb = (n: number): string => `${(n / 1e6).toFixed(n > 1e8 ? 0 : 1)} MB`

function WaitingForAllow(): ReactNode {
  return (
    <div className="waiting" role="status">
      <div className="allow-mock" aria-hidden>
        <b>Allow USB debugging?</b>
        <div className="small">RSA key fingerprint…</div>
        <div className="small">☑ Always allow from this computer</div>
        <div className="am-btn">Cancel</div>
        <div className="am-btn focus">Allow</div>
      </div>
      <div>
        <div className="big">Look at the TV and choose Allow.</div>
        <p className="muted" style={{ marginTop: 6 }}>
          Tick <b>Always allow from this computer</b> first, so it doesn't ask again. If nothing shows, press Home on the remote:
          the prompt can hide behind a running app.
        </p>
        <Busy text="Waiting for the TV" />
      </div>
    </div>
  )
}

function PairForm({ host, onPaired }: { host: string; onPaired: () => void }): ReactNode {
  const [pairPort, setPairPort] = useState('')
  const [code, setCode] = useState('')
  const [connectPort, setConnectPort] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<ApplyResult | null>(null)

  async function pair(): Promise<void> {
    setBusy(true)
    setResult(null)
    try {
      const r = await window.cytv.pair(host, Number(pairPort), code.trim(), Number(connectPort))
      setResult(r)
      if (r.ok) onPaired()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(false)
    }
  }

  const valid = /^\d{4,5}$/.test(pairPort) && /^\d{6}$/.test(code.trim()) && /^\d{4,5}$/.test(connectPort)
  return (
    <div className="card white">
      <h2>This TV needs a pairing code</h2>
      <p className="muted">
        Some Google TVs (Sony, some Philips) keep port 5555 closed until you pair once. This uses Google's adb, which the app
        downloads if it isn't here yet.
      </p>
      <ol className="guide" style={{ marginBottom: 14 }}>
        <li>
          <span>
            On the TV: <span className="path">Developer options<i>→</i>Wireless debugging</span>, turn it on, and note the{' '}
            <b>IP address &amp; port</b> on that screen. That is the connect port.
          </span>
        </li>
        <li>
          <span>
            Choose <b>Pair device with pairing code</b>. Note the 6-digit code and the port shown under it. That is the pair
            port.
          </span>
        </li>
      </ol>
      <ResultNotice result={result} />
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ width: 120 }}>
          Pair port
          <input type="text" inputMode="numeric" value={pairPort} onChange={(e) => setPairPort(e.target.value)} placeholder="37145" />
        </label>
        <label className="field" style={{ width: 130 }}>
          Code
          <input type="text" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} placeholder="482913" />
        </label>
        <label className="field" style={{ width: 130 }}>
          Connect port
          <input type="text" inputMode="numeric" value={connectPort} onChange={(e) => setConnectPort(e.target.value)} placeholder="41827" />
        </label>
        <button className="btn primary" onClick={pair} disabled={!valid || busy}>
          {busy ? 'Pairing…' : 'Pair and connect'}
        </button>
      </div>
    </div>
  )
}
