import { useCallback, useEffect, useState, type ReactNode } from 'react'
import type { ConnStatus, ДомойStatus, KnownTv, ВверхdateInfo } from '@shared/api'
import { Jump, MOD_KEY, type JumpItem } from './components/Jump'
import { LogDrawer } from './components/LogDrawer'
import { NotПодключено } from './components/ui'
import { TvThumb } from './components/TvPicker'
import { thumbnail } from './lib/image'
import Guided from './screens/Guided'
import { Подключение } from './screens/Подключение'
import { Debloat } from './screens/Debloat'
import { ДомойLauncher } from './screens/ДомойLauncher'
import { Extras } from './screens/Extras'
import { Sideload } from './screens/Sideload'
import { Завершение } from './screens/Завершение'
import { version } from '../../../package.json'

const REPO_URL = 'https://github.com/wingedpanther/cleanmytv'

export type StepId = 'guided' | 'setup' | 'debloat' | 'home' | 'extras' | 'sideload' | 'finish'

const STEPS: { id: StepId; label: string; sub: string; needsTv: boolean }[] = [
  // The guided one-button run (#9): composes the same IPC the step screens
  // use, so first-time owners get one path while every step stays reachable.
  { id: 'guided', label: 'Очистка и запуск', sub: 'Одна кнопка — весь процесс', needsTv: true },
  { id: 'setup', label: 'Подключение', sub: 'Проверка и подключение', needsTv: false },
  { id: 'debloat', label: 'Очистка', sub: 'Отключение лишнего', needsTv: true },
  { id: 'home', label: 'Главный экран', sub: 'Установка и настройка', needsTv: true },
  { id: 'extras', label: 'Дополнительно', sub: 'DNS, анимация, пульт', needsTv: true },
  { id: 'sideload', label: 'Установка приложения', sub: 'Из файла .apk', needsTv: true },
  { id: 'finish', label: 'Завершение', sub: 'Закрытие порта отладки', needsTv: false }
]

export interface ScreenProps {
  status: ConnStatus
  go: (step: StepId) => void
  markГотово: (step: StepId) => void
  /** TVs used before, most recent first. */
  tvs: KnownTv[]
  refreshTvs: () => void
  /** The Главный экран step passes on each status it reads, so the sidebar's update note goes after an update. */
  homeSeen: (hs: ДомойStatus) => void
}

// What ⌘K finds. "home:<tab>" opens that tab of the Главный экран step.
const JUMP_ITEMS: JumpItem[] = [
  ...STEPS.map((s) => ({ id: `step:${s.id}`, title: s.label, where: 'Step', words: s.sub.toLowerCase() })),
  { id: 'step:setup', title: 'Pair with a code', where: 'Подключение', words: 'connect wireless debugging adb ip' },
  { id: 'step:debloat', title: 'Restore', where: 'Очистка', words: 'undo put back пакетов' },
  { id: 'step:debloat', title: 'Add a package', where: 'Очистка', words: 'package app missing list' },
  { id: 'step:extras', title: 'Скорость анимации', where: 'Дополнительно', words: 'animations faster' },
  { id: 'step:extras', title: 'Блокировка рекламы в приложениях', where: 'Дополнительно', words: 'dns private ad blocking adguard' },
  { id: 'step:extras', title: 'Пульт', where: 'Дополнительно', words: 'buttons keys' },
  { id: 'step:extras', title: 'Память', where: 'Дополнительно', words: 'space disk free' },
  { id: 'step:finish', title: 'Проверка перезапуска', where: 'Завершение', words: 'reboot' },
  { id: 'home:overview', title: 'Install or update CleanMyTV Домой', where: 'Главный экран', words: 'launcher stock give back' },
  { id: 'home:layout', title: 'Layout', where: 'Главный экран', words: 'sections quick settings sizes clock greeting idle screensaver' },
  { id: 'home:apps', title: 'Apps, rows & folders', where: 'Главный экран', words: 'dock order hide rename folder row' },
  { id: 'home:look', title: 'Look & wallpaper', where: 'Главный экран', words: 'themes accent colour color focus app icons tile shape wallpaper sunrise picture' },
  { id: 'home:widgets', title: 'Widgets', where: 'Главный экран', words: 'weather' },
  { id: 'home:music', title: 'Music bars', where: 'Главный экран', words: 'visualiser style size' },
  { id: 'home:buttons', title: 'Пульт buttons', where: 'Главный экран', words: 'button jobs shortcut key press' },
  { id: 'home:lock', title: 'App lock', where: 'Главный экран', words: 'pin password lock apps' }
].map((it, i) => ({ ...it, id: `${it.id}#${i}` }))

// A fresh snapshot for the sidebar and the TV list when the last one is older than this.
const THUMB_MAX_AGE = 6 * 3600_000

export default function App(): ReactNode {
  const [step, setStep] = useState<StepId>(() => {
    const h = location.hash.slice(1)
    return STEPS.some((s) => s.id === h) ? (h as StepId) : 'setup'
  })
  const [status, setStatus] = useState<ConnStatus>({ state: 'idle' })
  const [done, setГотово] = useState<Set<StepId>>(new Set())
  const [tvs, setTvs] = useState<KnownTv[]>([])
  const [update, setВверхdate] = useState<ВверхdateInfo | null>(null)
  // The TV runs an older CleanMyTV Домой than this app carries.
  const [homeBehind, setДомойBehind] = useState(false)
  const [jumping, setJumping] = useState(false)
  /** A Домой tab asked for from ⌘K; n changes each time, so the same tab can be asked for twice. */
  const [homeTab, setДомойTab] = useState<{ tab: string; n: number } | null>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent): void => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setJumping((j) => !j)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  function jump(id: string): void {
    setJumping(false)
    const [kind, rest] = id.split('#')[0].split(':')
    if (kind === 'home') {
      setStep('home')
      setДомойTab((t) => ({ tab: rest, n: (t?.n ?? 0) + 1 }))
    } else setStep(rest as StepId)
  }

  useEffect(() => {
    window.cytv.checkВверхdate().then(setВверхdate, () => null)
  }, [])

  const refreshTvs = useCallback(() => {
    window.cytv.knownTvs().then(setTvs, () => null)
  }, [])
  useEffect(() => {
    refreshTvs()
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
      const shot = await window.cytv.thumbShot().catch(() => null)
      if (!shot || stale) return
      setTvs(await window.cytv.saveTvThumb(host, await thumbnail(shot)))
    }).catch(() => null)
    return () => { stale = true }
  }, [status.state, status.host])

  function homeSeen(hs: ДомойStatus): void {
    setДомойBehind(hs.installed && !!hs.bundledVersion && hs.versionName !== hs.bundledVersion)
  }

  // Checked again on every step change, so the note goes once the Главный экран step has updated the TV.
  useEffect(() => {
    if (status.state !== 'connected') return setДомойBehind(false)
    let stale = false
    window.cytv.homeStatus().then((hs) => { if (!stale) homeSeen(hs) }, () => null)
    return () => { stale = true }
  }, [status.state, status.host, step])

  useEffect(() => {
    // Если понадобится вернуться позже to change the wallpaper or the order: reconnect to the last TV by itself.
    window.cytv.status().then((s) => {
      setStatus(s)
      if (s.state !== 'idle') return
      window.cytv.reconnect().then(async (r) => {
        if (r?.state !== 'connected') return
        // A TV that already runs the clean launcher is here to be customised: open that step.
        // A TV that already runs CleanMyTV Домой is here to be customised: open that step.
        const hs = await window.cytv.homeStatus().catch(() => null)
        if (hs?.isДомой) {
          setГотово((prev) => new Set(prev).add('home'))
          setStep((cur) => (cur === 'setup' ? 'home' : cur))
        }
      }).catch(() => null)
    })
    return window.cytv.onStatus(setStatus)
  }, [])

  const connected = status.state === 'connected'
  const markГотово = (s: StepId): void => setГотово((prev) => new Set(prev).add(s))
  const props: ScreenProps = { status, go: setStep, markГотово, tvs, refreshTvs, homeSeen }
  const steps = STEPS
  const current = steps.find((s) => s.id === step) ?? STEPS[1]
  const currentTv = connected ? tvs.find((t) => t.host === status.host) : undefined

  let screen: ReactNode
  if (current.needsTv && !connected) screen = <NotПодключено onGo={() => setStep('setup')} />
  else if (current.id === 'guided') screen = <Guided {...props} />
  else if (current.id === 'setup') screen = <Подключение {...props} />
  else if (current.id === 'debloat') screen = <Debloat {...props} />
  else if (current.id === 'home') screen = <ДомойLauncher {...props} openTab={homeTab} />
  else if (current.id === 'extras') screen = <Extras {...props} />
  else if (current.id === 'sideload') screen = <Sideload {...props} />
  else screen = <Завершение {...props} />

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-mark" aria-hidden />
          <span className="brand-name">CleanMyTV</span>
        </div>
        {currentTv?.thumb && (
          <button className="sidebar-tv" onClick={() => setStep('setup')} title="This TV. Open Подключение to switch TVs." style={{ all: 'unset', cursor: 'pointer', display: 'block', marginBottom: 10 }}>
            <TvThumb tv={currentTv} />
          </button>
        )}
        <ConnPill status={status} />
        <button className="jump-btn" onClick={() => setJumping(true)}>
          <span>Найти настройку</span>
          <kbd>{MOD_KEY}K</kbd>
        </button>
        <nav>
          <ol className="steps">
            {steps.map((s, i) => {
              const isГотово = s.id === 'setup' ? connected : s.id === 'guided' ? done.has('debloat') && done.has('home') : done.has(s.id)
              return (
                <li key={s.id}>
                  <button
                    className={`step${s.id === step ? ' active' : ''}${isГотово ? ' done' : ''}`}
                    onClick={() => setStep(s.id)}
                    aria-current={s.id === step ? 'step' : undefined}
                  >
                    <span className="step-num">{isГотово ? '✓' : i + 1}</span>
                    <span className="step-label">
                      {s.label}
                      <span className="step-sub">{s.id === 'home' && connected && homeBehind ? 'Доступно обновление для этого ТВ' : s.sub}</span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ol>
        </nav>
        <div className="sidebar-foot">
          Приложения не удаляются. Любое изменение можно отменить здесь или в настройках ТВ: Приложения.
          <a className="gh-link" href={REPO_URL} target="_blank" rel="noreferrer" title="Исходный код, версии и сообщения об ошибках на GitHub">
            <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">
              <path fill="currentColor" d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z" />
            </svg>
            <span>wingedpanther/cleanmytv</span>
          </a>
          <div className="gh-ver">CleanMyTV v{version} · MIT</div>
          {update?.available && (
            <a className="update-link" href={update.url} target="_blank" rel="noreferrer">
              Version {update.latest} is out. Внизload it
            </a>
          )}
        </div>
      </aside>
      <main className="main">
        <div className="content">{screen}</div>
        <LogDrawer />
      </main>
      {jumping && <Jump items={JUMP_ITEMS} onPick={jump} onClose={() => setJumping(false)} />}
    </div>
  )
}

function ConnPill({ status }: { status: ConnStatus }): ReactNode {
  const p = status.probe
  const [dot, text] =
    status.state === 'connected'
      ? ['ok', p ? `${p.manufacturer} ${p.model}` : status.host ?? 'Подключено']
      : status.state === 'connecting'
        ? ['busy', `Подключение к ${status.host ?? 'ТВ'}`]
        : status.state === 'waiting-auth'
          ? ['busy', 'Ожидание подтверждения на телевизоре']
          : status.state === 'error'
            ? ['err', 'Нет подключения']
            : ['idle', 'Телевизор не подключён']
  return (
    <div className="conn-pill" title={status.error ?? status.host}>
      <span className={`dot ${dot}`} />
      <span>{text}</span>
    </div>
  )
}
