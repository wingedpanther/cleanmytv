import { useEffect, useRef, useState, type DragEvent, type ReactNode } from 'react'
import type { ApplyResult, OpenShelfInfo, SideloadInfo } from '@shared/api'
import type { ScreenProps } from '../App'
import { ActionButton, ResultNotice, errorText, usePhase } from '../components/ui'
import { t } from '../lib/i18n'

const mb = (bytes: number): string => t('{n} MB', { n: bytes >= 10 * 1024 * 1024 ? Math.round(bytes / 1024 / 1024) : (bytes / 1024 / 1024).toFixed(1) })

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
  const [shelf, setShelf] = useState<OpenShelfInfo | null>(null)
  const [shelfPhase, setShelfPhase] = usePhase()

  useEffect(() => { window.cytv.openShelf().then(setShelf, () => setShelf(null)) }, [])

  async function getShelf(): Promise<void> {
    setShelfPhase('busy')
    setResult(null)
    try {
      const r = await window.cytv.installOpenShelf()
      setShelfPhase(r.ok ? 'done' : 'failed')
      setResult(r)
      setShelf(await window.cytv.openShelf())
    } catch (e) {
      setShelfPhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setSent(null)
    }
  }

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

  const busy = phase === 'busy' || shelfPhase === 'busy'
  const sending = sent && (
    <>
      <div className="progress"><div style={{ width: `${Math.round((sent.received / Math.max(1, sent.total)) * 100)}%` }} /></div>
      <p className="small muted">{t('Sending to the TV: {pct}%', { pct: Math.round((sent.received / Math.max(1, sent.total)) * 100) })}</p>
    </>
  )
  return (
    <>
      <h1>{t('Install an app')}</h1>
      <p className="lede">{t("Put an app on the TV that isn't in its store. Download the app's .apk file on this computer, then drop it here.")}</p>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />

      <div className="card">
        <h2>{t('Open Shelf')}</h2>
        <p className="muted">
          {t('An app store for the TV, from the maker of CleanMyTV. It lists free, open-source TV apps from GitHub and installs them with the remote.')}
        </p>
        {shelf?.installed && !shelf.update ? (
          <p className="small muted" style={{ marginBottom: 0 }}>{t('Open Shelf {v} is on the TV. It updates itself.', { v: shelf.installed })}</p>
        ) : (
          <div className="row">
            <ActionButton
              phase={shelfPhase}
              primary
              label={shelf?.update ? t('Update Open Shelf to {v}', { v: shelf.latest ?? '' }) : t('Install Open Shelf')}
              busyLabel={t('Installing')}
              doneLabel={t('Installed')}
              disabled={busy}
              onClick={() => void getShelf()}
            />
          </div>
        )}
        {shelfPhase === 'busy' && sending}
      </div>

      <div className="notice warn">
        <b>{t('Only install apps you trust.')}</b>{' '}
        {t("An app from outside the Play Store hasn't been checked by Google. It can show ads, collect what you watch, or harm the TV. Get apps from their maker's own website, and skip anything that asks for more than it needs.")}
      </div>

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
        <p className="drop-title">{reading ? t('Reading the app…') : t('Drop an .apk file here')}</p>
        <button className="btn" disabled={busy || reading} onClick={() => file.current?.click()}>{t('Choose a file…')}</button>
        <p className="small muted" style={{ marginBottom: 0 }}>{t("Single .apk files only. Bundles (.apks, .xapk) aren't supported.")}</p>
      </div>

      {info && (
        <div className="card">
          <div className="card-head">
            <div>
              <h2 style={{ marginBottom: 4 }}>{info.file}</h2>
              <p className="small muted" style={{ margin: 0 }}>
                <code>{info.package}</code>{info.versionName ? ` · ${t('version {v}', { v: info.versionName })}` : ''} · {mb(info.size)}
              </p>
            </div>
          </div>
          <p>
            {info.installed
              ? t("The TV has version {v}. Installing replaces it and keeps the app's data.", { v: info.installed })
              : t("The TV doesn't have this app yet.")}
          </p>
          <div className="row">
            <ActionButton phase={phase} primary label={info.installed ? t('Update on the TV') : t('Install on the TV')} busyLabel={t('Installing')} doneLabel={t('Installed')} disabled={busy} onClick={() => void install()} />
            {!busy && <button className="btn ghost" onClick={() => { setInfo(null); setPath(null) }}>{t('Cancel')}</button>}
          </div>
          {phase === 'busy' && sending}
        </div>
      )}
    </>
  )
}
