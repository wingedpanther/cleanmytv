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
        <span>{open ? '▾' : '▸'} Activity</span>
        <span className="last">{last ? (last.level === 'cmd' ? `$ ${last.text}` : last.text) : 'Пока ничего не выполнялось.'}</span>
        {lines.length > 0 && <span className="muted small">{lines.length} lines</span>}
      </button>
      {open && (
        <div className="log-body" ref={body}>
          {lines.length === 0 && <p className="muted">Здесь отображаются команды, отправленные телевизору.</p>}
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
              title="Your TV model, Android version and app versions, then this log, without IP addresses. Paste it into a GitHub issue."
              onClick={async () => {
                const details = await window.cytv.bugReport().catch((e) => `Could not read the details: ${(e as Error).message}`)
                navigator.clipboard.writeText(noAddresses(`${details}\n\nActivity:\n${lines.map(line).join('\n') || '(empty)'}`))
              }}
            >
              Copy for a bug report
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
