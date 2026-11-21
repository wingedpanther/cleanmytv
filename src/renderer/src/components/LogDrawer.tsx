import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { LogLine } from '@shared/api'
import { t } from '../lib/i18n'

const MAX_LINES = 500
const NEW_ISSUE = 'https://github.com/wingedpanther/cleanmytv/issues/new'

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
  /** Send a report: saving, or where the last one went and whether a TV was in it. */
  const [reportBusy, setReportBusy] = useState(false)
  const [report, setReport] = useState<{ path: string; noTv: boolean } | null>(null)
  const [reportError, setReportError] = useState('')
  const saveReport = async (): Promise<void> => {
    setReportBusy(true)
    setReport(null)
    setReportError('')
    try {
      const noTv = (await window.cytv.status()).state !== 'connected'
      const path = await window.cytv.saveReport()
      if (path) setReport({ path, noTv })
    } catch (e) {
      setReportError(t("Couldn't save the report: {error}", { error: (e as Error).message }))
    } finally {
      setReportBusy(false)
    }
  }
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
      setRecNote(path ? t('Saved to {path}. Look through it, then attach it to your GitHub issue.', { path }) : t('Not saved.'))
      setRec('idle')
    } catch (e) {
      setRec('idle')
      setRecNote(t("Couldn't record: {error}", { error: (e as Error).message }))
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
        <span>{open ? '▾' : '▸'} {rec === 'on' ? t('Activity · recording') : t('Activity')}</span>
        <span className="last">{last ? (last.level === 'cmd' ? `$ ${last.text}` : last.text) : t('Nothing has run yet.')}</span>
        {lines.length > 0 && <span className="muted small">{t('{n} {n|line|lines}', { n: lines.length })}</span>}
      </button>
      {open && (
        <div className="log-body" ref={body}>
          {lines.length === 0 && <p className="muted">{t('Commands sent to the TV show up here.')}</p>}
          {lines.map((l, i) => (
            <div key={i} className={`log-line ${l.level}`}>
              <span className="t">{new Date(l.time).toLocaleTimeString([], { hour12: false })}</span>
              <span className="task">{l.task}</span>
              <span className="x">{l.text}</span>
            </div>
          ))}
          <div className="row" style={{ marginTop: 8 }}>
            <button
              className="btn small"
              disabled={reportBusy}
              title={t("Saves one text file for a GitHub issue: this computer, the TV, its apps, Home's settings and both logs.")}
              onClick={() => void saveReport()}
            >
              {reportBusy ? t('Saving') : t('Send a report')}
            </button>
            {lines.length > 0 && (
              <>
                <button className="btn small ghost" onClick={() => setLines([])}>
                  {t('Clear')}
                </button>
                <button className="btn small ghost" onClick={() => navigator.clipboard.writeText(lines.map(line).join('\n'))}>
                  {t('Copy all')}
                </button>
              </>
            )}
            <button
              className="btn small ghost"
              title={t("Your TV model, Android version, app versions and the TV's app list, then this log, without IP addresses. Paste it into a GitHub issue.")}
              onClick={async () => {
                const details = await window.cytv.bugReport().catch((e) => `Could not read the details: ${(e as Error).message}`)
                navigator.clipboard.writeText(noAddresses(`${details}\n\nActivity:\n${lines.map(line).join('\n') || '(empty)'}`))
              }}
            >
              {t('Copy for a bug report')}
            </button>
            <button
              className={`btn small ${rec === 'on' ? 'primary' : 'ghost'}`}
              disabled={rec === 'saving'}
              title={t("Records the TV's own log while you make the problem happen, then saves it as a file with the bug report details. Addresses and email addresses are taken out.")}
              onClick={() => void record()}
            >
              {rec === 'on' ? t('Stop and save') : rec === 'saving' ? t('Saving') : t('Record a problem')}
            </button>
            {rec === 'on' && (
              <button className="btn small ghost" onClick={() => { void window.cytv.recordCancel(); setRec('idle') }}>
                {t('Cancel')}
              </button>
            )}
          </div>
          {rec === 'on' && <p className="small">{t('Recording. Now make the problem happen on the TV, then press Stop and save.')}</p>}
          {recNote && <p className="small muted">{recNote}</p>}
          {report && (
            <div className="small" role="status">
              <p style={{ margin: '8px 0 4px' }}>
                {t('Saved to {path}.', { path: report.path })}{' '}
                <a href={NEW_ISSUE} target="_blank" rel="noreferrer">{t('Open a GitHub issue')}</a>
              </p>
              {report.noTv && <p className="muted" style={{ margin: '0 0 4px' }}>{t("No TV was connected, so it has this computer's details only.")}</p>}
              <p className="muted" style={{ margin: 0 }}>{t('Look through the file before you share it. Logs can name your apps and networks.')}</p>
            </div>
          )}
          {reportError && <p className="small muted">{reportError}</p>}
        </div>
      )}
    </div>
  )
}
