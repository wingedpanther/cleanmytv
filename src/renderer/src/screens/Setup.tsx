import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, CheckItem, DownloadProgress, FoundTv, ScanHit, ToolId } from '@shared/api'
import type { ScreenProps } from '../App'
import { TvPicker, TvThumb } from '../components/TvPicker'
import { Busy, ResultNotice, StatusChip, errorText } from '../components/ui'
import { t, tx } from '../lib/i18n'

const IP_RE = /^\d{1,3}(\.\d{1,3}){3}(:\d{2,5})?$/
const DISCOVER_EVERY_MS = 2000
// The first answers take a moment; asking again soon means the list doesn't wait a full round.
const DISCOVER_FIRST_MS = 500

function foundStatus(tv: FoundTv): { text: string; dot: string } {
  if (tv.ready) return { text: t('ready to connect'), dot: 'ok' }
  if (tv.pairPort) return { text: t('ready to pair'), dot: 'ok' }
  if (tv.connectPort) return { text: t('open the pairing screen'), dot: 'busy' }
  return { text: t('debugging is off'), dot: 'idle' }
}

export function Setup({ status, go, tvs, refreshTvs }: ScreenProps): ReactNode {
  const [checks, setChecks] = useState<CheckItem[] | null>(null)
  const [progress, setProgress] = useState<Partial<Record<ToolId, DownloadProgress>>>({})
  const [downloading, setDownloading] = useState<Set<ToolId>>(new Set())
  const [downloadResult, setDownloadResult] = useState<ApplyResult | null>(null)

  const [hits, setHits] = useState<ScanHit[] | null>(null)
  const [scanning, setScanning] = useState(false)
  const [found, setFound] = useState<FoundTv[]>([])
  const [host, setHost] = useState('')
  // Once the user has typed or picked an address, a TV showing up doesn't change it.
  const [hostChosen, setHostChosen] = useState(false)
  const [connectError, setConnectError] = useState<string | null>(null)
  // The connect failed in a way Pair with a code can get past.
  const [offerPairing, setOfferPairing] = useState(false)
  const [wantPair, setWantPair] = useState(false)
  const [pairing, setPairing] = useState(false)

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
    setOfferPairing(false)
    setHostChosen(true)
    try {
      const s = await window.cytv.connect(h.trim())
      if (s.state === 'error' && !s.needsPairing) {
        setConnectError(s.error ?? t('Could not connect.'))
        setOfferPairing(!!s.offerPairing)
      }
    } catch (e) {
      setConnectError(errorText(e))
    }
  }

  const busy = status.state === 'connecting' || status.state === 'waiting-auth'
  const connected = status.state === 'connected'
  const required = checks?.filter((c) => c.id !== 'platform-tools') ?? []
  const optional = checks?.filter((c) => c.id === 'platform-tools') ?? []

  // Listen for TVs while nothing is connected. This only fills in the form: the app never logs in to a
  // TV it found by itself.
  // Not while pairing either: some TVs serve one connection at a time, and adb needs it.
  const looking = !connected && !busy && !pairing
  useEffect(() => {
    if (!looking) return
    let stopped = false
    let first = true
    let timer: ReturnType<typeof setTimeout>
    const poll = async (): Promise<void> => {
      try {
        const tvs = await window.cytv.discover()
        if (!stopped) setFound(tvs)
      } catch {
        // The scan and the address field still work.
      }
      if (!stopped) timer = setTimeout(poll, first ? DISCOVER_FIRST_MS : DISCOVER_EVERY_MS)
      first = false
    }
    poll()
    return () => {
      stopped = true
      clearTimeout(timer)
      window.cytv.stopDiscovery()
    }
  }, [looking])

  useEffect(() => {
    if (hostChosen) {
      // The TV the user picked before opening its pairing screen.
      const chosen = found.find((t) => t.host === host)
      if (chosen?.pairPort && !chosen.ready) setWantPair(true)
      return
    }
    const open = found.filter((t) => t.pairPort && !t.ready)
    const ready = found.filter((t) => t.ready)
    // Two pairing screens at once: leave the choice to the user, and don't close the form they're in.
    const target = open.length === 1 ? open[0].host : host
    if (open.length === 1) setHost(target)
    else if (ready.length === 1) setHost((cur) => cur || ready[0].host)
    setWantPair(open.some((t) => t.host === target))
  }, [found, host, hostChosen])

  function pick(tv: FoundTv): void {
    setHost(tv.host)
    setHostChosen(true)
    setWantPair(!tv.ready && Boolean(tv.pairPort || tv.connectPort))
  }

  const pairHost = wantPair ? host : status.host ?? host
  // A pairing error belongs to the address it came from, not to one picked since.
  const showPair = wantPair || (status.needsPairing && (!hostChosen || host === status.host))

  return (
    <>
      <h1>{t('Set up')}</h1>
      <p className="lede">
        {t("This app talks to the TV over your Wi-Fi. You don't need Python, Java or Android tools to clean the TV and install the home screen. The one optional download is only for pairing some Google TVs.")}
      </p>

      {status.state === 'waiting-auth' && <WaitingForAllow />}

      {!connected && status.state !== 'waiting-auth' && (
        <TvPicker
          tvs={tvs}
          status={status}
          onChange={refreshTvs}
          title={tvs.length > 1 ? t('Which TV?') : t('Your TV')}
          hint={tvs.length > 1 ? t("You've used this app with more than one TV, so it waits for you to pick.") : undefined}
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
                  <StatusChip status="ok" text={t('Connected')} />
                  <button className="btn small ghost" onClick={() => window.cytv.disconnect()}>
                    {t('Disconnect')}
                  </button>
                </div>
              </div>
              <div className="facts">
                <div>
                  <span>{t('Address')}</span>
                  <b>{status.host}</b>
                </div>
                <div>
                  <span>Android</span>
                  <b>
                    {status.probe.release} (API {status.probe.sdk})
                  </b>
                </div>
                <div>
                  <span>{t('Device')}</span>
                  <b>{status.probe.device}</b>
                </div>
                <div>
                  <span>{t('Build')}</span>
                  <b className="mono">{status.probe.build}</b>
                </div>
              </div>
            </div>
          </div>
          {status.probe.adbSecure === '0' && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              {tx("This TV reports {prop}. Any device on your network can reach its debug port without the Allow prompt. Close it when you're done: the Finish step walks you through it.", { prop: <code>ro.adb.secure=0</code> })}
            </div>
          )}
          {status.probe.sdk > 0 && status.probe.sdk < 28 && (
            <div className="notice warn" style={{ marginTop: 16, marginBottom: 0 }}>
              {t('This TV reports Android {release} (API {sdk}). CleanMyTV Home needs Android 9 (API 28) or newer, so the Home screen step will refuse to install here. Cleanup stays available and reversible (nothing is uninstalled).', { release: status.probe.release, sdk: status.probe.sdk })}
            </div>
          )}
          <div className="row" style={{ marginTop: 16 }}>
            <button className="btn primary" onClick={() => go('debloat')}>
              {t('Next: clean up the TV')}
            </button>
          </div>
        </div>
      ) : null}

      {connected && status.probe ? (
        <TvPicker tvs={tvs} status={status} onChange={refreshTvs} title={t('Your other TVs')} hint={t('Connecting to one disconnects this TV.')} />
      ) : (
        <div className="grid-2">
          <div className="card">
            <h2>{t('1. On the TV')}</h2>
            <ol className="guide">
              <li>
                <span>
                  {tx('Open {path}. On some Google TVs it is {other}.', {
                    path: <span className="path">{t('Settings')}<i>→</i>{t('Device Preferences')}<i>→</i>{t('About')}</span>,
                    other: <span className="path">{t('Settings')}<i>→</i>{t('System')}<i>→</i>{t('About')}</span>
                  })}
                </span>
              </li>
              <li>
                <span>
                  {tx('Scroll to {build} (or {osBuild}) and press OK on it seven times. The TV says you are now a developer.', {
                    build: <b>{t('Build')}</b>,
                    osBuild: <b>{t('Android TV OS build')}</b>
                  })}
                </span>
              </li>
              <li>
                <span>
                  {tx('Go back one screen and open {dev}.', { dev: <b>{t('Developer options')}</b> })}
                </span>
              </li>
              <li>
                <span>
                  {tx('Turn on {usb}. If you also see {network} or {adb}, turn that on too.', {
                    usb: <b>{t('USB debugging')}</b>,
                    network: <b>{t('Network debugging')}</b>,
                    adb: <b>{t('ADB debugging')}</b>
                  })}
                </span>
              </li>
              <li>
                <span>{t('Keep the TV on and on the same Wi-Fi as this computer.')}</span>
              </li>
            </ol>
          </div>

          <div className="card">
            <h2>{t('2. Find your TV')}</h2>
            <p className="muted small">{t('TVs on your Wi-Fi show up here by themselves. Pick yours to fill in its address.')}</p>
            {status.moved && status.error && (
              <div className="notice warn" style={{ marginBottom: 12 }}>
                {status.error} {t('Only connect to an address you recognise as your TV.')}
              </div>
            )}
            {found.length > 0 ? (
              <ul className="host-list">
                {found.map((tv) => {
                  const s = foundStatus(tv)
                  return (
                    <li key={tv.host}>
                      <button
                        className={`host-item${host === tv.host ? ' selected' : ''}`}
                        onClick={() => pick(tv)}
                        onDoubleClick={() => tv.ready && connect(tv.host)}
                      >
                        <span className={`dot ${s.dot}`} />
                        <span style={{ flex: 1 }}>
                          {tv.name || t('Android device')} <span className="muted small">{tv.host}</span>
                        </span>
                        <span className="muted small">{s.text}</span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            ) : (
              looking && <Busy text={t('Looking for TVs')} />
            )}
            <p className="muted small" style={{ marginTop: 12 }}>
              {t("Not listed? Older TVs don't announce themselves: the scan checks your network for port 5555. It takes a few seconds.")}
            </p>
            <div className="row" style={{ marginBottom: 12 }}>
              <button className="btn" onClick={scan} disabled={scanning || busy}>
                {scanning ? t('Scanning…') : hits ? t('Scan again') : t('Scan the network')}
              </button>
              {scanning && <span className="spinner" />}
            </div>
            {hits && hits.length === 0 && (
              <div className="notice warn">
                {t('No TV answered. Check that USB debugging is on and the TV is on this Wi-Fi, or type its address below (TV Settings → Network → your network).')}
              </div>
            )}
            {hits && hits.length > 0 && (
              <ul className="host-list">
                {hits.map((h) => (
                  <li key={h.host}>
                    <button
                      className={`host-item${host === h.host ? ' selected' : ''}`}
                      onClick={() => {
                        setHost(h.host)
                        setHostChosen(true)
                        setWantPair(false)
                      }}
                      onDoubleClick={() => connect(h.host)}
                    >
                      <span className="dot ok" />
                      <span style={{ flex: 1 }}>{h.host}</span>
                      <span className="muted small">{t('port 5555 open')}</span>
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
                {t('TV address')}
                <input type="text" value={host} onChange={(e) => { setHost(e.target.value); setHostChosen(true) }} placeholder="192.168.1.50" />
              </label>
              <div className="row">
                <button className="btn primary" type="submit" disabled={busy || !IP_RE.test(host.trim())}>
                  {status.state === 'connecting' ? t('Connecting…') : t('Connect')}
                </button>
                {status.state === 'connecting' && <Busy text={t('Opening the connection')} />}
              </div>
            </form>
            {connectError && (
              <div className="notice error" style={{ marginTop: 14, marginBottom: 0, whiteSpace: 'pre-line' }}>
                {connectError}
                {connectError.includes('/Applications/CleanMyTV.app/') && (
                  <div style={{ marginTop: 10 }}>
                    <button className="btn small" onClick={() => void navigator.clipboard.writeText('/Applications/CleanMyTV.app/Contents/MacOS/CleanMyTV')}>
                      {t('Copy the Terminal command')}
                    </button>
                  </div>
                )}
                {offerPairing && !wantPair && (
                  <div style={{ marginTop: 10 }}>
                    <button className="btn small" onClick={() => { setHostChosen(true); setWantPair(true) }}>
                      {t('Pair with a code')}
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {showPair && !connected && (
        // Keyed by the TV, not the address: typing in the address field mustn't wipe the code.
        <PairForm
          key={found.find((t) => t.host === pairHost)?.host ?? 'typed'}
          host={pairHost}
          tv={found.find((t) => t.host === pairHost)}
          onBusy={setPairing}
          onPaired={() => connect(pairHost)}
        />
      )}

      <div className="card white" style={{ marginTop: 8 }}>
        <div className="card-head">
          <h2>{t("What's ready")}</h2>
          <button className="btn small ghost" onClick={refreshChecks}>
            {t('Check again')}
          </button>
        </div>
        <ResultNotice result={downloadResult} onDismiss={() => setDownloadResult(null)} />
        {!checks ? (
          <Busy text={t('Checking this computer')} />
        ) : (
          <>
            <Checklist items={required} progress={progress} downloading={downloading} onDownload={download} />
            {optional.length > 0 && (
              <>
                <h3 style={{ marginTop: 22 }}>{t('Optional')}</h3>
                <p className="muted small">
                  {t("Only needed to pair a TV that asks for a code. Each one downloads into this app's own folder.")}
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
                  {active ? t('Downloading…') : t('Download')}
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
      ? t('{got} of {total}', { got: mb(p.received), total: p.total ? mb(p.total) : '?' })
      : p.phase === 'extract'
        ? t('Unpacking')
        : p.phase === 'install'
          ? p.message ?? t('Installing')
          : p.phase === 'error'
            ? p.message ?? t('Failed')
            : t('Done')
  return (
    <div>
      <div className={`progress${p.phase === 'download' && p.total ? '' : ' indeterminate'}`}>
        <div style={p.phase === 'download' && p.total ? { width: `${pct}%` } : undefined} />
      </div>
      <span className="small muted">{label}</span>
    </div>
  )
}

const mb = (n: number): string => t('{n} MB', { n: (n / 1e6).toFixed(n > 1e8 ? 0 : 1) })

function WaitingForAllow(): ReactNode {
  return (
    <div className="waiting" role="status">
      <div className="allow-mock" aria-hidden>
        <b>{t('Allow USB debugging?')}</b>
        <div className="small">{t('RSA key fingerprint…')}</div>
        <div className="small">☑ {t('Always allow from this computer')}</div>
        <div className="am-btn">{t('Cancel')}</div>
        <div className="am-btn focus">{t('Allow')}</div>
      </div>
      <div>
        <div className="big">{t('Look at the TV and choose Allow.')}</div>
        <p className="muted" style={{ marginTop: 6 }}>
          {tx("Tick {always} first, so it doesn't ask again. If nothing shows, press Home on the remote: the prompt can hide behind a running app.", {
            always: <b>{t('Always allow from this computer')}</b>
          })}
        </p>
        <Busy text={t('Waiting for the TV')} />
      </div>
    </div>
  )
}

function PairForm({ host, tv, onBusy, onPaired }: { host: string; tv?: FoundTv; onBusy: (busy: boolean) => void; onPaired: () => Promise<void> }): ReactNode {
  // Null until typed in: the ports follow what the TV advertises, unless the user has overridden them.
  const [pairTyped, setPairTyped] = useState<string | null>(null)
  const [connectTyped, setConnectTyped] = useState<string | null>(null)
  // The last connect port seen: one slow check shouldn't empty the field while the user types the code.
  const lastConnect = useRef<number | undefined>(undefined)
  if (tv?.connectPort) lastConnect.current = tv.connectPort
  const pairPort = pairTyped ?? (tv?.pairPort ? String(tv.pairPort) : '')
  const connectPort = connectTyped ?? (lastConnect.current ? String(lastConnect.current) : '')
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const codeInput = useRef<HTMLInputElement>(null)
  const card = useRef<HTMLDivElement>(null)

  // It opens by itself, below the fold on a small window.
  useEffect(() => {
    card.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [])

  // The pairing screen just opened: the code is all that's left. Not while the user is typing elsewhere.
  useEffect(() => {
    if (tv?.pairPort && document.activeElement?.tagName !== 'INPUT') codeInput.current?.focus()
  }, [tv?.pairPort])
  const [result, setResult] = useState<ApplyResult | null>(null)

  async function pair(): Promise<void> {
    setBusy(true)
    onBusy(true)
    setResult(null)
    try {
      const r = await window.cytv.pair(host, Number(pairPort), code.trim(), Number(connectPort))
      setResult(r)
      if (r.ok) await onPaired()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(false)
      onBusy(false)
    }
  }

  const valid = /^\d{4,5}$/.test(pairPort) && /^\d{6}$/.test(code.trim()) && /^\d{4,5}$/.test(connectPort)
  return (
    <div className="card white" ref={card}>
      <h2>{tv?.name ? t('Pair with a code: {name}', { name: tv.name }) : t('Pair with a code')}</h2>
      <p className="muted">
        {t("Many Google TVs and Chromecasts only offer Wireless debugging, and keep port 5555 closed until you pair once. This uses Google's adb, which the app downloads if it isn't here yet.")}
      </p>
      <ol className="guide" style={{ marginBottom: 14 }}>
        <li>
          <span>
            {tx('On the TV: {path}, turn it on, and note the {ip} on that screen. That is the connect port.', {
              path: <span className="path">{t('Developer options')}<i>→</i>{t('Wireless debugging')}</span>,
              ip: <b>{t('IP address & port')}</b>
            })}
          </span>
        </li>
        <li>
          <span>
            {tx('Choose {pair}. Note the 6-digit code and the port shown under it. That is the pair port.', {
              pair: <b>{t('Pair device with pairing code')}</b>
            })}
          </span>
        </li>
      </ol>
      {tv?.pairPort && connectPort ? (
        <div className="notice ok">{t('Found the pairing screen on {host}. Type the 6-digit code it shows.', { host })}</div>
      ) : (
        tv && !tv.pairPort && <p className="muted small">{t('Waiting for the pairing screen on {host}. The ports fill in when you open it.', { host })}</p>
      )}
      <ResultNotice result={result} />
      <div className="row" style={{ alignItems: 'flex-end' }}>
        <label className="field" style={{ width: 120 }}>
          {t('Pair port')}
          <input type="text" inputMode="numeric" value={pairPort} onChange={(e) => setPairTyped(e.target.value)} />
        </label>
        <label className="field" style={{ width: 130 }}>
          {t('Code')}
          <input ref={codeInput} type="text" inputMode="numeric" value={code} onChange={(e) => setCode(e.target.value)} />
        </label>
        <label className="field" style={{ width: 130 }}>
          {t('Connect port')}
          <input type="text" inputMode="numeric" value={connectPort} onChange={(e) => setConnectTyped(e.target.value)} />
        </label>
        <button className="btn primary" onClick={pair} disabled={!valid || busy}>
          {busy ? t('Pairing…') : t('Pair and connect')}
        </button>
      </div>
      {/* The example numbers that used to sit in these fields read like real ones, and got sent as such. */}
      <p className="small muted" style={{ marginTop: 8 }}>
        {t('Pair port and code: from the pairing window. Connect port: the port on the main Wireless debugging screen, after the IP address.')}
      </p>
    </div>
  )
}
