import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { LogLine } from '@shared/api'

const MAX_LINES = 500

const line = (l: LogLine): string => `${new Date(l.time).toISOString()} [${l.task}] ${l.level === 'cmd' ? '$ ' : ''}${l.text}`
/** IPv4 addresses, which say nothing a bug report needs. */
const noAddresses = (s: string): string => s.replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, 'x.x.x.x')

export function LogDrawer(): ReactNode {
  const [lines, setLines] = useState<LogLine[]>([])
  const [open, setOpen] = useState(false)
  const body = useRef<HTMLDivElement>(null)
  /** Record a problem: idle, recording, saving, or what happened last. */
  const [rec, setRec] = useState<'idle' | 'on' | 'saving'>('idle')
  const [recNote, setRecNote] = useState('')
  const record = async (): Promise<void> => {
    setRecNote('')
    try {
      if (rec === 'idle') {
        await window.cytv.recordStart()
        setRec('on')
        return
      }
      setRec('saving')
      const path = await window.cytv.recordStop()
      setRecNote(path ? `Saved to ${path}. Look through it, then attach it to your GitHub issue.` : 'Not saved.')
      setRec('idle')
    } catch (e) {
      setRec('idle')
      setRecNote(`Couldn't record: ${(e as Error).message}`)
    }
  }

  useEffect(
    () =>
      window.cytv.onLog((line) => {
        setLines((prev) => {
          const next = prev.length >= MAX_LINES ? prev.slice(prev.length - MAX_LINES + 1) : prev.slice()
          next.push(line)
          return next
        })
        if (line.level === 'error') setOpen(true)
      }),
    []
  )

  useEffect(() => {
    if (open && body.current) body.current.scrollTop = body.current.scrollHeight
  }, [lines, open])

  const last = lines[lines.length - 1]
  return (
    <div className="log">
      <button className="log-bar" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span>{open ? '▾' : '▸'} Activity{rec === 'on' ? ' · recording' : ''}</span>
        <span className="last">{last ? (last.level === 'cmd' ? `$ ${last.text}` : last.text) : 'Nothing has run yet.'}</span>
        {lines.length > 0 && <span className="muted small">{lines.length} lines</span>}
      </button>
      {open && (
        <div className="log-body" ref={body}>
          {lines.length === 0 && <p className="muted">Commands sent to the TV show up here.</p>}
          {lines.map((l, i) => (
            <div key={i} className={`log-line ${l.level}`}>
              <span className="t">{new Date(l.time).toLocaleTimeString([], { hour12: false })}</span>
              <span className="task">{l.task}</span>
              <span className="x">{l.text}</span>
            </div>
          ))}
          <div className="row" style={{ marginTop: 8 }}>
            {lines.length > 0 && (
              <>
                <button className="btn small ghost" onClick={() => setLines([])}>
                  Clear
                </button>
                <button className="btn small ghost" onClick={() => navigator.clipboard.writeText(lines.map(line).join('\n'))}>
                  Copy all
                </button>
              </>
            )}
            <button
              className="btn small ghost"
              title="Your TV model, Android version, app versions and the TV's app list, then this log, without IP addresses. Paste it into a GitHub issue."
              onClick={async () => {
                const details = await window.cytv.bugReport().catch((e) => `Could not read the details: ${(e as Error).message}`)
                navigator.clipboard.writeText(noAddresses(`${details}\n\nActivity:\n${lines.map(line).join('\n') || '(empty)'}`))
              }}
            >
              Copy for a bug report
            </button>
            <button
              className={`btn small ${rec === 'on' ? 'primary' : 'ghost'}`}
              disabled={rec === 'saving'}
              title="Records the TV's own log while you make the problem happen, then saves it as a file with the bug report details. Addresses and email addresses are taken out."
              onClick={() => void record()}
            >
              {rec === 'on' ? 'Stop and save' : rec === 'saving' ? 'Saving' : 'Record a problem'}
            </button>
            {rec === 'on' && (
              <button className="btn small ghost" onClick={() => { void window.cytv.recordCancel(); setRec('idle') }}>
                Cancel
              </button>
            )}
          </div>
          {rec === 'on' && <p className="small">Recording. Now make the problem happen on the TV, then press Stop and save.</p>}
          {recNote && <p className="small muted">{recNote}</p>}
        </div>
      )}
    </div>
  )
}
