// The guided "Очистка и запуск" run (#9): one button that walks the happy path —
// pick the best-matching list, disable its suggested пакетов, install
// CleanMyTV Домой, and land on the Главный экран step — while keeping every step
// inspectable and skippable. It composes the same IPC calls the individual
// screens use; no new main-process behaviour.
import { useEffect, useRef, useState } from 'react'
import type { ApplyResult, DebloatPlan } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, StatusChip, Stepper, errorText } from '../components/ui'
import { defaultВыкл. } from '../lib/ticks'

type Phase =
  | { t: 'plan' }                       // picking the list, building the plan
  | { t: 'ready'; plan: DebloatPlan; disable: string[] }  // showing what will happen
  | { t: 'run'; step: string }          // executing
  | { t: 'done'; result: ApplyResult; customised: boolean }
  | { t: 'failed'; message: string }

const STAGES = ['Выбор списка', 'Очистка', 'Install Домой', 'Последний этап']

export default function Guided({ go, markГотово }: ScreenProps): React.ReactElement {
  const [phase, setPhase] = useState<Phase>({ t: 'plan' })
  // Which of STAGES is running; past the end means all done.
  const [at, setAt] = useState(0)
  const [skipped, setSkipped] = useState(false)
  const alive = useRef(true)
  useEffect(() => () => { alive.current = false }, [])

  useEffect(() => { if (phase.t === 'plan') void prepare() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function prepare(): Promise<void> {
    try {
      const lists = await window.cytv.lists()
      const best = lists.reduce((a, b) => (b.score > a.score ? b : a))
      if (best.score <= 0) {
        setPhase({ t: 'failed', message: 'No list matches this TV closely. Use Очистка to pick one by hand — the guided run needs a matching list to be safe.' })
        return
      }
      const plan = await window.cytv.plan(best.id)
      // What Очистка would switch off with its defaults: features people use stay on (#57).
      const suggested = defaultВыкл.(plan.entries)
      setPhase({ t: 'ready', plan, disable: suggested })
      setAt(1)
    } catch (e) {
      setPhase({ t: 'failed', message: errorText(e) })
    }
  }

  async function run(skipDebloat: boolean): Promise<void> {
    const p = phase
    if (p.t !== 'ready') return
    setPhase({ t: 'run', step: 'Подготовка' })
    setSkipped(skipDebloat)
    const notes: string[] = []
    try {
      if (!skipDebloat && p.disable.length) {
        setPhase({ t: 'run', step: `Disabling ${p.disable.length} пакетов from "${p.plan.title}"` })
        // No safety overrides: the stock launcher and setupwraith stay on until CleanMyTV Домой is Домой,
        // and the Wi-Fi check runs (#33).
        const r = await window.cytv.applyDebloat({ listId: p.plan.listId, disable: p.disable, enable: [] })
        if (!r.ok) throw new Error(r.summary)
        notes.push(`Очищено: ${r.summary.replace(/\.$/, '')}`)
        if (alive.current) markГотово('debloat')
      } else {
        notes.push(skipDebloat ? 'Очистка skipped' : 'Больше нечего отключать')
      }

      setAt(2)
      const before = await window.cytv.homeStatus()
      if (before.unsupported) {
        notes.push(before.unsupported)
        setAt(STAGES.length)
        setPhase({ t: 'done', result: { ok: true, summary: notes.join(' · '), warnings: [] }, customised: false })
        return
      }
      const outdated = before.installed && before.bundledVersion && before.versionName !== before.bundledVersion
      if (!before.isДомой || outdated) {
        setPhase({ t: 'run', step: 'Установка CleanMyTV Домой' })
        const r = await window.cytv.installДомой()
        if (!r.ok) throw new Error(r.summary + (r.stockChoices ? ' Open the Главный экран step and pick it there.' : ''))
        notes.push('CleanMyTV Домой установлено и назначено главным экраном')
        if (alive.current) markГотово('home')
      } else {
        notes.push('CleanMyTV Домой уже назначено главным экраном — без изменений')
      }

      setAt(3)
      if (!skipDebloat) {
        // What the first pass kept on because CleanMyTV Домой wasn't Домой yet.
        const plan = await window.cytv.plan(p.plan.listId)
        const left = plan.entries.filter((e) => e.state === 'enabled' && p.disable.includes(e.pkg)).map((e) => e.pkg)
        if (left.length) {
          setPhase({ t: 'run', step: `Switching off ${left.join(', ')}` })
          const r = await window.cytv.applyDebloat({ listId: p.plan.listId, disable: left, enable: [] })
          notes.push(r.warnings.length ? `Kept on: ${r.warnings.join(' ')}` : `Then switched off ${left.length} more`)
        }
      }
      notes.push('Next: the restart check on Завершение')

      setAt(STAGES.length)
      setPhase({ t: 'done', result: { ok: true, summary: notes.join(' · '), warnings: [] }, customised: true })
    } catch (e) {
      notes.push('Остановлено: ' + errorText(e))
      setPhase({ t: 'failed', message: notes.join(' · ') })
    }
  }

  return (
    <>
      <h1>Очистка и запуск</h1>
      <p className="muted">
        The one-button path: the best-matching list for your TV, its suggested пакетов off, CleanMyTV
        Домой installed and set as Домой — then you land on the Главный экран step to arrange it. Nothing is
        uninstalled and every step stays open in the sidebar if you want to go slower.
      </p>

      <Stepper steps={STAGES} at={at} failed={phase.t === 'failed'} skipped={skipped ? [1, 3] : []} />

      {phase.t === 'plan' && <Busy text="Чтение данных ТВ и подбор подходящего списка" />}

      {phase.t === 'ready' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="card">
            <h2 style={{ margin: 0 }}>Готово к запуску</h2>
            <p style={{ margin: '8px 0' }}>
              Список: <b>{phase.plan.title}</b> — наиболее подходящий для вашего ТВ.
            </p>
            <p style={{ margin: '8px 0' }}>
              {phase.disable.length > 0 ? (
                <>
                  Будут отключены <b>{phase.disable.length} пакетов</b> (the list's suggested set — the same
                  defaults Очистка pre-ticks), then install CleanMyTV Домой and hand Домой to it.
                </>
              ) : (
                <>Nothing on this list is left to switch off with Очистка's defaults, so this installs CleanMyTV Домой and hands Домой to it.</>
              )}
            </p>
            <p className="muted small" style={{ margin: 0 }}>
              Restore puts every package back exactly as it is now; Домой can go back to the stock
              launcher from the Главный экран step at any time.
            </p>
          </div>
          <div className="row">
            <button className="btn primary" onClick={() => void run(false)}>Очистка и запуск</button>
            <button className="btn" onClick={() => void run(true)}>Главный экран only (skip clean up)</button>
            <button className="btn ghost" onClick={() => go('debloat')}>Let me pick by hand instead</button>
          </div>
        </div>
      )}

      {phase.t === 'run' && <Busy text={phase.step} />}

      {phase.t === 'done' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="card">
            <StatusChip status="ok" text="Готово" />
            <p style={{ margin: '8px 0 0' }}>{phase.result.summary}</p>
          </div>
          <div className="row">
            <button className="btn primary" onClick={() => go('home')}>Настроить главный экран</button>
            <button className="btn ghost" onClick={() => go('finish')}>Завершение (restart check, then close the debug port)</button>
          </div>
        </div>
      )}

      {phase.t === 'failed' && (
        <div className="stack" style={{ gap: 16 }}>
          <div className="notice error">{phase.message}</div>
          <div className="row">
            <button className="btn" onClick={() => { setPhase({ t: 'plan' }); setAt(0); setSkipped(false); void prepare() }}>Повторить</button>
            <button className="btn ghost" onClick={() => go('debloat')}>Перейти к пошаговой настройке</button>
          </div>
        </div>
      )}
    </>
  )
}
