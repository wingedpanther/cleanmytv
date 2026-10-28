import { useEffect, useState, type ReactNode } from 'react'
import type { ApplyResult, CheckItem, DownloadProgress, HomeStatus, InstallRequest, LauncherStatus, Prefs, ToolId } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, StatusChip, Toggle, errorText } from '../components/ui'

export function Launcher({ go, markDone }: ScreenProps): ReactNode {
  const [st, setSt] = useState<LauncherStatus | null>(null)
  const [apk, setApk] = useState<'bundled' | 'local'>('bundled')
  const [stock, setStock] = useState<'auto' | 'none'>('auto')
  const [inputKey, setInputKey] = useState(false)
  const [homeKey, setHomeKey] = useState(false)
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [askReplace, setAskReplace] = useState(false)
  const [askStock, setAskStock] = useState(false)
  const [advanced, setAdvanced] = useState(false)
  const [prefs, setPrefs] = useState<Prefs>({ preview: false })
  const [home, setHome] = useState<HomeStatus | null>(null)

  const refresh = (): void => {
    window.cytv.launcherStatus().then((s) => {
      setSt(s)
      if (!s.bundledApk && s.localApk) setApk('local')
    })
    window.cytv.homeStatus().then(setHome, () => setHome(null))
  }
  useEffect(refresh, [])
  useEffect(() => {
    window.cytv.prefs().then(setPrefs)
  }, [])

  async function togglePreview(on: boolean): Promise<void> {
    setPrefs(await window.cytv.setPrefs({ preview: on }))
  }

  async function run(label: string, fn: () => Promise<ApplyResult>): Promise<void> {
    setBusy(label)
    setResult(null)
    try {
      const r = await fn()
      setResult(r)
      if (r.ok) markDone('launcher')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  // Google TV builds use launcherx; its stock remote's Input button needs the service once launcherx is off.
  useEffect(() => {
    if (st?.stockLauncher === 'com.google.android.apps.tv.launcherx') setInputKey(true)
  }, [st?.stockLauncher])

  async function install(replaceIncompatible = false): Promise<void> {
    setAskReplace(false)
    setBusy('Installing the launcher. Keep the TV on.')
    setResult(null)
    const req: InstallRequest = { apk, stockLauncher: stock, inputKeyService: inputKey, homeKeyService: homeKey, replaceIncompatible }
    try {
      const r = await window.cytv.install(req)
      setResult(r)
      if (r.incompatible) setAskReplace(true)
      if (r.ok) markDone('launcher')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  async function useStock(): Promise<void> {
    setAskStock(false)
    setBusy('Giving Home back to the stock launcher')
    try {
      setResult(await window.cytv.useStockLauncher())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      refresh()
    }
  }

  const canInstall = st && (apk === 'bundled' ? st.bundledApk : st.localApk)
  // Either of our launchers owns Home, so the stock one is off and "give it back" applies.
  const oursIsHome = !!st?.isHome || !!home?.isHome

  return (
    <>
      <h1>Launcher</h1>
      <p className="lede">
        The new Home screen is a 390 KB app: your apps in a dock and a grid, a clock, and no ads. The app installs it, starts it,
        and only then switches off the stock launcher. If Home doesn't land on the new one, it puts the old one back.
      </p>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {!st ? (
        <Busy text="Asking the TV what owns Home" />
      ) : (
        <>
          <div className="card">
            <div className="card-head">
              <h2>On this TV now</h2>
              <StatusChip status={st.isHome ? 'ok' : st.installed ? 'warn' : 'optional'} text={st.isHome ? 'Is Home' : st.installed ? 'Installed, not Home' : 'Not installed'} />
            </div>
            <div className="facts">
              <div>
                <span>Home screen</span>
                <b className="mono">{st.homeComponent ?? 'nothing answers'}</b>
              </div>
              <div>
                <span>Stock launcher</span>
                <b className="mono">{st.stockLauncher ?? 'none found'}</b>
              </div>
              <div>
                <span>CleanYourTV launcher</span>
                <b>{st.installed ? `version ${st.versionName ?? '?'}` : 'not installed'}</b>
              </div>
              <div>
                <span>In this app</span>
                <b>{st.bundledApk ? `version ${st.bundledVersion ?? '?'}` : 'no bundled copy'}</b>
              </div>
            </div>
          </div>

          <div className="card white">
            <h2>{st.installed ? 'Reinstall or update' : 'Install'}</h2>

            {st.localApk && (
              <div className="seg" style={{ marginBottom: 14 }}>
                <button className={apk === 'bundled' ? 'on' : ''} onClick={() => setApk('bundled')} disabled={!st.bundledApk}>
                  Bundled copy
                </button>
                <button className={apk === 'local' ? 'on' : ''} onClick={() => setApk('local')}>
                  Built on this computer
                </button>
              </div>
            )}

            <Toggle
              label="Switch off the stock launcher"
              hint={
                st.stockLauncher
                  ? `Switches off ${st.stockLauncher} once the new launcher is running. Without this, the TV may ask which Home to use.`
                  : 'The app asks the TV which launcher owns Home and only switches off one it recognises.'
              }
              checked={stock === 'auto'}
              onChange={(v) => setStock(v ? 'auto' : 'none')}
            />
            <Toggle
              label="Input button works from any app"
              hint="Needed on Google TV once the stock launcher is off. Also powers custom remote buttons (Customise → Advanced)."
              checked={inputKey}
              onChange={setInputKey}
            />
            <Toggle
              label="Fix the Home button"
              hint="For remotes whose Home button opens All apps, such as Xiaomi sticks. Also routes the Settings button."
              checked={homeKey}
              onChange={setHomeKey}
            />
            {(inputKey || homeKey) && (
              <p className="small muted" style={{ marginTop: 6 }}>
                These are accessibility services. Android switches them off on every reinstall, so the app turns them on again each
                time.
              </p>
            )}

            <div className="row" style={{ marginTop: 16 }}>
              <button className="btn primary" disabled={!canInstall || !!busy} onClick={() => install()}>
                {st.installed ? 'Reinstall launcher' : 'Install launcher'}
              </button>
              {st.installed && !st.isHome && home?.isHome && (
                <button className="btn" disabled={!!busy} onClick={() => run('Switching Home to CleanYourTV', () => window.cytv.useCleanYourTv())}>
                  Use CleanYourTV
                </button>
              )}
              {oursIsHome && (
                <button className="btn" disabled={!!busy} onClick={() => setAskStock(true)}>
                  Give Home back to the stock launcher
                </button>
              )}
              <span className="spacer" />
              {st.isHome && (
                <button className="btn ghost" onClick={() => go('customise')}>
                  Next: customise
                </button>
              )}
            </div>
            {!st.bundledApk && !st.localApk && (
              <div className="notice warn" style={{ marginTop: 14, marginBottom: 0 }}>
                This copy of the app has no launcher bundled. Build it from source below.
              </div>
            )}
          </div>

          {prefs.preview && <PreviewCard home={home} busy={!!busy} run={run} goCustomise={() => go('customise')} />}

          <div>
            <button className="btn small ghost" onClick={() => setAdvanced(!advanced)} aria-expanded={advanced}>
              {advanced ? '▾' : '▸'} Advanced: build from source
            </button>
            {advanced && <BuildFromSource onBuilt={refresh} />}
          </div>

          <div className="card" style={{ marginTop: 16 }}>
            <Toggle
              label="Show preview features"
              hint="Adds CleanMyTV Home, our own launcher, still early. You can switch back to CleanYourTV or the stock launcher here at any time."
              checked={prefs.preview}
              onChange={togglePreview}
            />
          </div>
        </>
      )}

      {askReplace && (
        <Confirm title="Replace the launcher on the TV?" confirmLabel="Replace it" onConfirm={() => install(true)} onCancel={() => setAskReplace(false)}>
          <p>The launcher on the TV was built on another computer, so Android won't update it in place.</p>
          <p>
            The app can uninstall it and install this one. The tile order and App library choices you made on the TV are lost.
            Settings you pushed from this app are kept.
          </p>
        </Confirm>
      )}
      {askStock && (
        <Confirm title="Use the stock launcher again?" confirmLabel="Switch back" onConfirm={useStock} onCancel={() => setAskStock(false)}>
          <p>
            The app switches {st?.stockLauncher ?? 'the stock launcher'} back on and makes it Home. The launchers this app installed
            stay installed, so you can switch again later.
          </p>
        </Confirm>
      )}
    </>
  )
}

function PreviewCard(props: { home: HomeStatus | null; busy: boolean; run: (label: string, fn: () => Promise<ApplyResult>) => Promise<void>; goCustomise: () => void }): ReactNode {
  const { home, busy, run } = props
  if (!home) return null
  const outdated = home.installed && home.bundledVersion && home.versionName !== home.bundledVersion
  return (
    <div className="card white">
      <div className="card-head">
        <h2>CleanMyTV Home · Preview</h2>
        <StatusChip status={home.isHome ? 'ok' : home.installed ? 'warn' : 'optional'} text={home.isHome ? 'Is Home' : home.installed ? 'Installed, not Home' : 'Not installed'} />
      </div>
      <p className="muted">
        Our own launcher, built for this app: changes from Customise show on the TV straight away, settings survive a reboot, and
        music gets equaliser bars along the bottom. It's early: one row of apps for now. If anything breaks, switch back with the
        buttons here.
      </p>
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
          <button className="btn" disabled={busy} onClick={() => run('Switching Home to CleanMyTV Home', () => window.cytv.useHome())}>
            Use CleanMyTV Home
          </button>
        )}
        <span className="spacer" />
        {home.isHome && (
          <button className="btn ghost" onClick={props.goCustomise}>
            Next: customise
          </button>
        )}
      </div>
    </div>
  )
}

const BUILD_TOOLS: ToolId[] = ['jdk', 'sdk', 'source']

function BuildFromSource({ onBuilt }: { onBuilt: () => void }): ReactNode {
  const [checks, setChecks] = useState<CheckItem[] | null>(null)
  const [progress, setProgress] = useState<Partial<Record<ToolId, DownloadProgress>>>({})
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)

  const refresh = (): void => {
    window.cytv.checks().then((c) => setChecks(c.filter((x) => BUILD_TOOLS.includes(x.id as ToolId))))
  }
  useEffect(() => {
    refresh()
    return window.cytv.onProgress((p) => setProgress((prev) => ({ ...prev, [p.tool]: p })))
  }, [])

  async function getAll(): Promise<void> {
    for (const c of checks ?? []) {
      if (c.status === 'ok' || !c.download) continue
      setBusy(`Downloading ${c.label}`)
      const r = await window.cytv.download(c.download).catch((e) => ({ ok: false, summary: errorText(e), warnings: [] }))
      if (!r.ok) {
        setResult(r)
        break
      }
    }
    setBusy(null)
    refresh()
  }

  async function build(): Promise<void> {
    setBusy('Building the launcher. The first build downloads Gradle and takes a few minutes.')
    setResult(null)
    try {
      setResult(await window.cytv.buildLocal())
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      onBuilt()
    }
  }

  const ready = checks?.every((c) => c.status === 'ok')
  return (
    <div className="card" style={{ marginTop: 8 }}>
      <p className="muted">
        Build the launcher on this computer from the cleanyourtv source. The app downloads Java 17, the Android SDK and the
        source into its own folder, about 400 MB. Nothing is installed system-wide.
      </p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {!checks ? (
        <Busy text="Checking" />
      ) : (
        <ul className="checklist" style={{ marginBottom: 14 }}>
          {checks.map((c) => {
            const p = c.download ? progress[c.download] : undefined
            return (
              <li key={c.id} className="check">
                <div className="check-title">
                  <StatusChip status={c.status === 'optional' ? 'missing' : c.status} text={c.status === 'ok' ? 'Ready' : 'Not yet'} />
                  <span>{c.label}</span>
                </div>
                <div className="check-detail">
                  {c.detail}
                  {p && p.phase !== 'done' && busy && (
                    <div className="progress">
                      <div style={{ width: `${p.total ? (p.received / p.total) * 100 : 0}%` }} />
                    </div>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {busy && <Busy text={busy} />}
      <div className="row" style={{ marginTop: 10 }}>
        {!ready && (
          <button className="btn" onClick={getAll} disabled={!!busy || !checks}>
            Download what's missing
          </button>
        )}
        <button className="btn" onClick={build} disabled={!ready || !!busy}>
          Build launcher
        </button>
      </div>
    </div>
  )
}
