import { useEffect, useRef, useState, type DragEvent, type ReactNode } from 'react'
import type { ApplyResult, SideloadInfo } from '@shared/api'
import type { ScreenProps } from '../App'
import { ActionButton, ResultNotice, errorText, usePhase } from '../components/ui'

const mb = (bytes: number): string => (bytes >= 10 * 1024 * 1024 ? `${Math.round(bytes / 1024 / 1024)} MB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`)

/** Install an app the owner downloaded: drop the .apk here, check what it is, then send it to the TV. */
export function Sideload(_props: ScreenProps): ReactNode {
  const [path, setPath] = useState<string | null>(null)
  const [info, setInfo] = useState<SideloadInfo | null>(null)
  const [reading, setReading] = useState(false)
  const [over, setOver] = useState(false)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [phase, setPhase] = usePhase()
  const [sent, setSent] = useState<{ received: number; total: number } | null>(null)
  const file = useRef<HTMLInputElement>(null)

  useEffect(() => window.cytv.onProgress((p) => {
    if (p.tool === 'sideload') setSent(p.phase === 'done' ? null : { received: p.received, total: p.total })
  }), [])

  async function pick(f: File | undefined): Promise<void> {
    if (!f) return
    setResult(null)
    setInfo(null)
    const p = window.cytv.pathForFile(f)
    setPath(p)
    setReading(true)
    try {
      setInfo(await window.cytv.sideloadInfo(p))
    } catch (e) {
      setPath(null)
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setReading(false)
    }
  }

  async function install(): Promise<void> {
    if (!path) return
    setPhase('busy')
    setResult(null)
    try {
      const r = await window.cytv.sideload(path)
      setPhase(r.ok ? 'done' : 'failed')
      setResult(r)
      if (r.ok) { setInfo(null); setPath(null) }
    } catch (e) {
      setPhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setSent(null)
    }
  }

  const drop = (e: DragEvent): void => {
    e.preventDefault()
    setOver(false)
    void pick(e.dataTransfer.files?.[0])
  }

  const busy = phase === 'busy'
  return (
    <>
      <h1>Установка приложения</h1>
      <p className="lede">Put an app on the TV that isn't in its store. Download the app's .apk file on this computer, then drop it here.</p>

      <div className="notice warn">
        <b>Устанавливайте только приложения, которым доверяете.</b> An app from outside the Play Store hasn't been checked by Google. It can show ads, collect what
        you watch, or harm the TV. Get apps from their maker's own website, and skip anything that asks for more than it needs.
      </div>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />

      <div
        className={`drop-zone${over ? ' over' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setOver(true) }}
        onDragLeave={() => setOver(false)}
        onDrop={drop}
      >
        <input
          ref={file}
          type="file"
          accept=".apk"
          hidden
          onChange={(e) => {
            const f = e.target.files?.[0]
            e.target.value = ''
            void pick(f)
          }}
        />
        <p className="drop-title">{reading ? 'Чтение данных приложения…' : 'Перетащите файл .apk сюда'}</p>
        <button className="btn" disabled={busy || reading} onClick={() => file.current?.click()}>Выбрать файл…</button>
        <p className="small muted" style={{ marginBottom: 0 }}>Single .apk files only. Bundles (.apks, .xapk) aren't supported.</p>
      </div>

      {info && (
        <div className="card">
          <div className="card-head">
            <div>
              <h2 style={{ marginBottom: 4 }}>{info.file}</h2>
              <p className="small muted" style={{ margin: 0 }}>
                <code>{info.package}</code>{info.versionName ? ` · version ${info.versionName}` : ''} · {mb(info.size)}
              </p>
            </div>
          </div>
          <p>
            {info.installed
              ? `The TV has version ${info.installed}. Installing replaces it and keeps the app's data.`
              : "The TV doesn't have this app yet."}
          </p>
          <div className="row">
            <ActionButton phase={phase} primary label={info.installed ? 'Update on the TV' : 'Install on the TV'} busyLabel="Installing" doneLabel="Installed" disabled={busy} onClick={() => void install()} />
            {!busy && <button className="btn ghost" onClick={() => { setInfo(null); setPath(null) }}>Отмена</button>}
          </div>
          {sent && (
            <>
              <div className="progress"><div style={{ width: `${Math.round((sent.received / Math.max(1, sent.total)) * 100)}%` }} /></div>
              <p className="small muted">Sending to the TV: {Math.round((sent.received / Math.max(1, sent.total)) * 100)}%</p>
            </>
          )}
        </div>
      )}
    </>
  )
}
