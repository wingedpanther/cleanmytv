import type React from 'react'
import { useEffect, useRef, useState, type ClipboardEvent, type ReactNode } from 'react'
import type { ApplyResult, CheckStatus } from '@shared/api'

export function Toggle(props: {
  label: string
  hint?: string
  checked: boolean
  onChange: (v: boolean) => void
  disabled?: boolean
}): ReactNode {
  return (
    <label className="toggle" style={props.disabled ? { opacity: 0.5, cursor: 'not-allowed' } : undefined}>
      <input
        type="checkbox"
        checked={props.checked}
        disabled={props.disabled}
        onChange={(e) => props.onChange(e.target.checked)}
      />
      <span className="toggle-switch" />
      <span className="toggle-text">
        <b>{props.label}</b>
        {props.hint && <span>{props.hint}</span>}
      </span>
    </label>
  )
}

const chipText: Record<CheckStatus, string> = {
  ok: 'Ready',
  missing: 'Needed',
  warn: 'Check',
  optional: 'Optional',
  checking: 'Checking'
}

export function StatusChip({ status, text }: { status: CheckStatus; text?: string }): ReactNode {
  return <span className={`chip ${status}`}>{text ?? chipText[status]}</span>
}

export function Modal(props: { children: ReactNode; onClose: () => void; wide?: boolean }): ReactNode {
  useEffect(() => {
    const onKey = (e: KeyboardEvent): void => {
      if (e.key === 'Escape') props.onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [props.onClose])
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && props.onClose()}>
      <div className={`modal${props.wide ? ' wide' : ''}`} role="dialog" aria-modal="true">
        {props.children}
      </div>
    </div>
  )
}

export function Confirm(props: {
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
  onCancel: () => void
}): ReactNode {
  return (
    <Modal onClose={props.onCancel}>
      <h2>{props.title}</h2>
      <div>{props.children}</div>
      <div className="modal-actions">
        <button className="btn ghost" onClick={props.onCancel}>
          Cancel
        </button>
        <button className="btn primary" onClick={props.onConfirm} autoFocus>
          {props.confirmLabel}
        </button>
      </div>
    </Modal>
  )
}

export function ResultNotice({ result, onDismiss }: { result: ApplyResult | null; onDismiss?: () => void }): ReactNode {
  if (!result) return null
  const kind = !result.ok ? 'error' : result.warnings.length ? 'warn' : 'ok'
  return (
    <div className={`notice ${kind}`} role="status">
      <div className="row between">
        <b>{result.summary}</b>
        {onDismiss && (
          <button className="icon-btn" onClick={onDismiss} aria-label="Dismiss">
            ✕
          </button>
        )}
      </div>
      {result.warnings.length > 0 && (
        <ul>
          {result.warnings.map((w, i) => (
            <li key={i}>{w}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export function Busy({ text }: { text: string }): ReactNode {
  return (
    <div className="row muted">
      <span className="spinner" />
      <span>{text}</span>
    </div>
  )
}

export function Slider(props: {
  label: string
  hint?: string
  value: number
  min: number
  max: number
  step?: number
  unit?: string
  onChange: (v: number) => void
}): ReactNode {
  return (
    <label className="field">
      <span>{props.label}</span>
      {props.hint && <span className="hint">{props.hint}</span>}
      <div className="slider-field">
        <input
          type="range"
          min={props.min}
          max={props.max}
          step={props.step ?? 1}
          value={props.value}
          onChange={(e) => props.onChange(Number(e.target.value))}
        />
        <output>
          {props.value}
          {props.unit ?? ''}
        </output>
      </div>
    </label>
  )
}

/** Runs an async action with a busy flag and an error turned into an ApplyResult. */
export async function attempt<T>(fn: () => Promise<T>): Promise<T | ApplyResult> {
  try {
    return await fn()
  } catch (e) {
    return { ok: false, summary: errorText(e), warnings: [] }
  }
}

export function errorText(e: unknown): string {
  const msg = e instanceof Error ? e.message : String(e)
  // Electron prefixes errors thrown in the main process.
  return msg.replace(/^Error invoking remote method '[^']+': (Error: )?/, '')
}

export function NotConnected({ onGo }: { onGo: () => void }): ReactNode {
  return (
    <div className="empty">
      <div className="tv-glyph" />
      <h2>Connect to your TV first</h2>
      <p className="muted">This step reads and changes things on the TV, so it needs the connection from Setup.</p>
      <button className="btn primary" onClick={onGo}>
        Go to Setup
      </button>
    </div>
  )
}

// The pieces below follow components from EasyUI (MIT, Suraj Maurya, github.com/Surajmaurya1/easyui),
// rewritten for this stylesheet with CSS transitions in place of Framer Motion.

export type Phase = 'idle' | 'busy' | 'done' | 'failed'

/** A phase that falls back to idle a moment after done or failed. */
export function usePhase(): [Phase, (p: Phase) => void] {
  const [phase, setPhase] = useState<Phase>('idle')
  useEffect(() => {
    if (phase !== 'done' && phase !== 'failed') return
    const t = setTimeout(() => setPhase('idle'), 2400)
    return () => clearTimeout(t)
  }, [phase])
  return [phase, setPhase]
}

/**
 * A button whose label changes in place while its job runs. All four labels sit in one grid cell,
 * so the button keeps the width of the longest and nothing beside it moves.
 */
export function ActionButton(props: {
  phase: Phase
  label: string
  busyLabel: string
  doneLabel?: string
  failedLabel?: string
  primary?: boolean
  disabled?: boolean
  onClick: () => void
}): ReactNode {
  const faces: [Phase, ReactNode][] = [
    ['idle', props.label],
    ['busy', <><span className="spinner" />{props.busyLabel}</>],
    ['done', <><Tick />{props.doneLabel ?? 'Done'}</>],
    ['failed', <><span className="morph-bang">!</span>{props.failedLabel ?? "Didn't work"}</>]
  ]
  return (
    <button
      className={`btn morph${props.primary ? ' primary' : ''}`}
      data-phase={props.phase}
      disabled={props.disabled || props.phase === 'busy'}
      aria-busy={props.phase === 'busy'}
      onClick={props.onClick}
    >
      {faces.map(([id, face]) => (
        <span key={id} className={`morph-face${id === props.phase ? ' on' : ''}`} aria-hidden={id !== props.phase}>
          {face}
        </span>
      ))}
    </button>
  )
}

export function Tick(): ReactNode {
  return (
    <svg className="tick" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <path d="M3.5 8.5l3 3 6-7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

/**
 * A toast with an Undo button and a bar that runs down. Hovering stops the clock. After Undo it says
 * so for a moment, then goes.
 */
export function UndoToast(props: {
  text: string
  undoneText: string
  seconds?: number
  onUndo: () => Promise<void>
  onClose: () => void
}): ReactNode {
  const total = (props.seconds ?? 10) * 1000
  const [left, setLeft] = useState(total)
  const [paused, setPaused] = useState(false)
  const [state, setState] = useState<'open' | 'undoing' | 'undone' | 'leaving'>('open')
  const close = useRef(props.onClose)
  close.current = props.onClose

  useEffect(() => {
    if (paused || state !== 'open') return
    let last = performance.now()
    let frame = requestAnimationFrame(function tick(now) {
      setLeft((l) => Math.max(0, l - (now - last)))
      last = now
      frame = requestAnimationFrame(tick)
    })
    return () => cancelAnimationFrame(frame)
  }, [paused, state])

  useEffect(() => {
    if (left === 0 && state === 'open') setState('leaving')
  }, [left, state])
  useEffect(() => {
    if (state === 'undone') {
      const t = setTimeout(() => setState('leaving'), 1600)
      return () => clearTimeout(t)
    }
    if (state === 'leaving') {
      const t = setTimeout(() => close.current(), 180)
      return () => clearTimeout(t)
    }
  }, [state])

  async function undo(): Promise<void> {
    setState('undoing')
    await props.onUndo().catch(() => null)
    setState('undone')
  }

  return (
    <div className="toast-dock">
      <div
        className={`toast${state === 'leaving' ? ' leaving' : ''}`}
        role="status"
        aria-live="polite"
        onMouseEnter={() => setPaused(true)}
        onMouseLeave={() => setPaused(false)}
      >
        <span className="toast-icon">{state === 'undone' ? <Tick /> : state === 'undoing' ? <span className="spinner" /> : <Tick />}</span>
        <span className="toast-text">{state === 'undone' ? props.undoneText : state === 'undoing' ? 'Putting it back' : props.text}</span>
        {state === 'open' && (
          <button className="btn small" onClick={undo}>
            ↶ Undo
          </button>
        )}
        <button className="icon-btn" onClick={() => setState('leaving')} aria-label="Close">
          ✕
        </button>
        {state === 'open' && <span className="toast-clock" style={{ transform: `scaleX(${left / total})` }} />}
      </div>
    </div>
  )
}

/** One box per digit. Typing moves on, Backspace moves back, and a pasted PIN fills every box. */
export function PinBoxes(props: { value: string; onChange: (v: string) => void; length?: number; label: string; autoFocus?: boolean }): ReactNode {
  const length = props.length ?? 6
  const boxes = useRef<(HTMLInputElement | null)[]>([])
  const still = useRef(typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches)

  useEffect(() => {
    if (props.autoFocus) boxes.current[0]?.focus()
  }, [props.autoFocus])

  function pop(i: number): void {
    if (!still.current) boxes.current[i]?.animate([{ transform: 'scale(1)' }, { transform: 'scale(1.14)' }, { transform: 'scale(1)' }], { duration: 220, easing: 'ease-out' })
  }

  function type(i: number, raw: string): void {
    const d = raw.replace(/\D/g, '').slice(-1)
    if (!d) return
    // Digits fill from the left: typing in a later box lands in the first empty one.
    const at = Math.min(i, props.value.length)
    props.onChange((props.value.slice(0, at) + d + props.value.slice(at + 1)).slice(0, length))
    pop(at)
    boxes.current[Math.min(at + 1, length - 1)]?.focus()
  }

  function key(i: number, e: React.KeyboardEvent<HTMLInputElement>): void {
    if (e.key === 'Backspace') {
      e.preventDefault()
      const at = props.value[i] ? i : i - 1
      if (at < 0) return
      props.onChange(props.value.slice(0, at) + props.value.slice(at + 1))
      boxes.current[at]?.focus()
    } else if (e.key === 'ArrowLeft' && i > 0) {
      e.preventDefault()
      boxes.current[i - 1]?.focus()
    } else if (e.key === 'ArrowRight' && i < length - 1) {
      e.preventDefault()
      boxes.current[i + 1]?.focus()
    }
  }

  function paste(e: ClipboardEvent): void {
    const digits = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!digits) return
    e.preventDefault()
    props.onChange(digits)
    boxes.current[Math.min(digits.length, length - 1)]?.focus()
  }

  return (
    <div className="pin-boxes" role="group" aria-label={props.label}>
      {Array.from({ length }, (_, i) => (
        <input
          key={i}
          ref={(el) => {
            boxes.current[i] = el
          }}
          type="password"
          inputMode="numeric"
          autoComplete="new-password"
          maxLength={2}
          value={props.value[i] ?? ''}
          className={props.value[i] ? 'filled' : ''}
          aria-label={`${props.label}, digit ${i + 1} of ${length}`}
          onChange={(e) => type(i, e.target.value)}
          onKeyDown={(e) => key(i, e)}
          onPaste={paste}
          onFocus={(e) => e.target.select()}
        />
      ))}
    </div>
  )
}

/** Numbered steps joined by a line that fills as each one finishes. */
export function Stepper(props: { steps: string[]; at: number; failed?: boolean; skipped?: number[] }): ReactNode {
  return (
    <ol className="stepper" aria-label="Progress">
      {props.steps.map((label, i) => {
        const skipped = props.skipped?.includes(i)
        const state = i < props.at ? (skipped ? 'skipped' : 'complete') : i === props.at ? (props.failed ? 'failed' : 'current') : 'upcoming'
        return (
          <li key={label} className={`stepper-step ${state}`} aria-current={state === 'current' ? 'step' : undefined}>
            {i > 0 && (
              <span className="stepper-line">
                <span style={{ transform: `scaleX(${i <= props.at ? 1 : 0})` }} />
              </span>
            )}
            <span className="stepper-dot">{state === 'complete' ? <Tick /> : state === 'failed' ? '!' : state === 'skipped' ? '–' : i + 1}</span>
            <span className="stepper-label">
              {label}
              {skipped && <span className="sr-only"> (skipped)</span>}
              {state === 'complete' && <span className="sr-only"> (done)</span>}
            </span>
          </li>
        )
      })}
    </ol>
  )
}

/** Grey rows with a light sweep, in place of content that's still on its way from the TV. */
export function Skeleton({ rows = 6, label }: { rows?: number; label: string }): ReactNode {
  const widths = ['72%', '58%', '80%', '64%', '76%', '52%']
  return (
    <div className="skeleton" role="status" aria-busy="true" aria-label={label}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton-row">
          <span className="skeleton-bar" style={{ width: 18 }} />
          <span className="skeleton-bar" style={{ width: widths[i % widths.length] }} />
          <span className="skeleton-bar" style={{ width: 90, marginLeft: 'auto' }} />
        </div>
      ))}
      <span className="sr-only">{label}</span>
    </div>
  )
}
