import { useCallback, useEffect, useState, type ReactNode } from 'react'
import type { ConnStatus, KnownTv } from '@shared/api'
import { LogDrawer } from './components/LogDrawer'
import { NotConnected } from './components/ui'
import { TvThumb } from './components/TvPicker'
import { thumbnail } from './lib/image'
import Guided from './screens/Guided'
import { Setup } from './screens/Setup'
import { Debloat } from './screens/Debloat'
import { Launcher } from './screens/Launcher'
import { Customise } from './screens/Customise'
import { HomeLauncher } from './screens/HomeLauncher'
import { Extras } from './screens/Extras'
import { Finish } from './screens/Finish'
import { version } from '../../../package.json'

const REPO_URL = 'https://github.com/wingedpanther/cleanmytv'

export type StepId = 'guided' | 'setup' | 'debloat' | 'launcher' | 'customise' | 'home' | 'extras' | 'finish'

const STEPS: { id: StepId; label: string; sub: string; needsTv: boolean }[] = [
  // The guided one-button run (#9): composes the same IPC the step screens
  // use, so first-time owners get one path while every step stays reachable.
  { id: 'guided', label: 'Clean & launch', sub: 'One button, every step', needsTv: true },
  { id: 'setup', label: 'Setup', sub: 'Check and connect', needsTv: false },
  { id: 'debloat', label: 'Clean up', sub: 'Turn off the bloat', needsTv: true },
  { id: 'launcher', label: 'Launcher', sub: 'Install the new Home', needsTv: true },
  { id: 'customise', label: 'Customise', sub: 'Order, wallpaper, layout', needsTv: true },
  // Only with preview features on (Launcher screen).
  { id: 'home', label: 'Home launcher', sub: 'CleanMyTV Home · preview', needsTv: true },
  { id: 'extras', label: 'TV extras', sub: 'Screensaver, DNS, remote', needsTv: true },
  { id: 'finish', label: 'Finish', sub: 'Close the debug port', needsTv: false }
]

export interface ScreenProps {
  status: ConnStatus
  go: (step: StepId) => void
  markDone: (step: StepId) => void
  /** TVs used before, most recent first. */
  tvs: KnownTv[]
  refreshTvs: () => void
}

/** Screens that change the preview setting fire this so the sidebar follows. */
export const PREFS_EVENT = 'cytv-prefs'

// A fresh snapshot for the sidebar and the TV list when the last one is older than this.
const THUMB_MAX_AGE = 6 * 3600_000

export default function App(): ReactNode {
  const [step, setStep] = useState<StepId>(() => {
    const h = location.hash.slice(1)
    return STEPS.some((s) => s.id === h) ? (h as StepId) : 'setup'
  })
  const [status, setStatus] = useState<ConnStatus>({ state: 'idle' })
  const [done, setDone] = useState<Set<StepId>>(new Set())
  const [preview, setPreview] = useState(false)
  const [tvs, setTvs] = useState<KnownTv[]>([])

  const refreshTvs = useCallback(() => {
    window.cytv.knownTvs().then(setTvs, () => null)
  }, [])
  useEffect(() => {
    refreshTvs()
    const load = (): void => void window.cytv.prefs().then((p) => setPreview(p.preview))
    load()
    window.addEventListener(PREFS_EVENT, load)
    return () => window.removeEventListener(PREFS_EVENT, load)
  }, [refreshTvs])

  // On connecting: refresh the TV list and, if its snapshot is missing or old, take a new one quietly.
  useEffect(() => {
    if (status.state !== 'connected' || !status.host) return
    const host = status.host
    let stale = false
    window.cytv.knownTvs().then(async (list) => {
      setTvs(list)
      const tv = list.find((t) => t.host === host)
      if (!tv || (tv.thumb && Date.now() - (tv.thumbAt ?? 0) < THUMB_MAX_AGE)) return
      const shot = await window.cytv.screenshot().catch(() => null)
      if (!shot || stale) return
      setTvs(await window.cytv.saveTvThumb(host, await thumbnail(shot)))
    }).catch(() => null)
    return () => { stale = true }
  }, [status.state, status.host])

  useEffect(() => {
    // Coming back later to change the wallpaper or the order: reconnect to the last TV by itself.
    window.cytv.status().then((s) => {
      setStatus(s)
      if (s.state !== 'idle') return
      window.cytv.reconnect().then(async (r) => {
        if (r?.state !== 'connected') return
        // A TV that already runs the clean launcher is here to be customised: open that step.
        const [ls, hs] = await Promise.all([window.cytv.launcherStatus(), window.cytv.homeStatus().catch(() => null)])
        if (hs?.isHome) {
          // Our launcher is Home: its page is the one to open, so make sure it's in the menu.
          await window.cytv.setPrefs({ preview: true })
          setPreview(true)
          setDone((prev) => new Set(prev).add('launcher'))
          setStep((cur) => (cur === 'setup' ? 'home' : cur))
        } else if (ls.isHome) {
          setDone((prev) => new Set(prev).add('launcher'))
          setStep((cur) => (cur === 'setup' ? 'customise' : cur))
        }
      }).catch(() => null)
    })
    return window.cytv.onStatus(setStatus)
  }, [])

  const connected = status.state === 'connected'
  const markDone = (s: StepId): void => setDone((prev) => new Set(prev).add(s))
  const props: ScreenProps = { status, go: setStep, markDone, tvs, refreshTvs }
  const steps = STEPS.filter((s) => s.id !== 'home' || preview)
  const current = steps.find((s) => s.id === step) ?? STEPS[1]
  const currentTv = connected ? tvs.find((t) => t.host === status.host) : undefined

  let screen: ReactNode
  if (current.needsTv && !connected) screen = <NotConnected onGo={() => setStep('setup')} />
  else if (current.id === 'guided') screen = <Guided {...props} />
  else if (current.id === 'setup') screen = <Setup {...props} />
  else if (current.id === 'debloat') screen = <Debloat {...props} />
  else if (current.id === 'launcher') screen = <Launcher {...props} />
  else if (current.id === 'customise') screen = <Customise {...props} />
  else if (current.id === 'home') screen = <HomeLauncher {...props} />
  else if (current.id === 'extras') screen = <Extras {...props} />
  else screen = <Finish {...props} />

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden />
          <span className="brand-name">CleanMyTV</span>
        </div>
        {currentTv?.thumb && (
          <button className="sidebar-tv" onClick={() => setStep('setup')} title="This TV. Open Setup to switch TVs." style={{ all: 'unset', cursor: 'pointer', display: 'block', marginBottom: 10 }}>
            <TvThumb tv={currentTv} />
          </button>
        )}
        <ConnPill status={status} />
        <nav>
          <ol className="steps">
            {steps.map((s, i) => {
              const isDone = s.id === 'setup' ? connected : s.id === 'guided' ? done.has('debloat') && done.has('launcher') : done.has(s.id)
              return (
                <li key={s.id}>
                  <button
                    className={`step${s.id === step ? ' active' : ''}${isDone ? ' done' : ''}`}
                    onClick={() => setStep(s.id)}
                    aria-current={s.id === step ? 'step' : undefined}
                  >
                    <span className="step-num">{isDone ? '✓' : i + 1}</span>
                    <span className="step-label">
                      {s.label}
                      <span className="step-sub">{s.sub}</span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ol>
        </nav>
        <div className="sidebar-foot">
          Nothing is uninstalled. Every change can be undone from this app or the TV's Settings → Apps.
          <a className="gh-link" href={REPO_URL} target="_blank" rel="noreferrer" title="Source, releases and issues on GitHub">
            <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
              <path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
            </svg>
            <span>wingedpanther/cleanmytv</span>
          </a>
          <div className="gh-ver">CleanMyTV v{version} · MIT</div>
        </div>
      </aside>
      <main className="main">
        {/* Customise renders its own scroll area and sticky footer. */}
        {current.id === 'customise' && connected ? screen : <div className="content">{screen}</div>}
        <LogDrawer />
      </main>
    </div>
  )
}

function ConnPill({ status }: { status: ConnStatus }): ReactNode {
  const p = status.probe
  const [dot, text] =
    status.state === 'connected'
      ? ['ok', p ? `${p.manufacturer} ${p.model}` : status.host ?? 'Connected']
      : status.state === 'connecting'
        ? ['busy', `Connecting to ${status.host ?? 'TV'}`]
        : status.state === 'waiting-auth'
          ? ['busy', 'Waiting for Allow on the TV']
          : status.state === 'error'
            ? ['err', 'Not connected']
            : ['idle', 'No TV connected']
  return (
    <div className="conn-pill" title={status.error ?? status.host}>
      <span className={`dot ${dot}`} />
      <span>{text}</span>
    </div>
  )
}
