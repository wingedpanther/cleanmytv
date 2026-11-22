import { useState, type ReactNode } from 'react'
import type { ConnStatus, KnownTv } from '@shared/api'
import { errorText } from './ui'
import { t } from '../lib/i18n'

/** The TV's last snapshot, or a plain screen shape until there is one. */
export function TvThumb({ tv, className }: { tv?: KnownTv; className?: string }): ReactNode {
  return tv?.thumb ? (
    <img className={`tv-thumb ${className ?? ''}`} src={tv.thumb} alt={tv.model ? t('Screen of {model}', { model: tv.model }) : t('Screen of the TV')} />
  ) : (
    <div className={`tv-thumb no-shot ${className ?? ''}`} aria-hidden />
  )
}

function ago(at: number): string {
  if (!at) return t('used a while ago')
  const m = Math.round((Date.now() - at) / 60000)
  if (m < 2) return t('used just now')
  if (m < 60) return t('used {n} min ago', { n: m })
  const h = Math.round(m / 60)
  if (h < 24) return t('used {n} h ago', { n: h })
  return t('used {n} d ago', { n: Math.round(h / 24) })
}

const tvName = (tv: KnownTv): string => [tv.manufacturer, tv.model].filter(Boolean).join(' ') || tv.host

/**
 * The other TVs in the sidebar: one click disconnects this one and connects to that one. Anything short of
 * connected (pairing, Allow, a new address) is Setup's job, so `onNeedsSetup` opens it.
 */
export function SidebarTvs(props: { tvs: KnownTv[]; status: ConnStatus; onChange: () => void; onNeedsSetup: () => void }): ReactNode {
  const [busy, setBusy] = useState<string | null>(null)
  const current = props.status.state === 'connected' ? props.status.host : undefined
  const list = props.tvs.filter((t) => t.host !== current)
  if (!list.length || (!current && props.tvs.length < 2)) return null
  const waiting = props.status.state === 'connecting' || props.status.state === 'waiting-auth'

  async function connect(host: string): Promise<void> {
    setBusy(host)
    try {
      const s = await window.cytv.connectKnown(host)
      if (s.state !== 'connected') props.onNeedsSetup()
    } catch {
      props.onNeedsSetup()
    } finally {
      setBusy(null)
      props.onChange()
    }
  }

  return (
    <div className="sidebar-tvs">
      <span className="sidebar-tvs-head">{current ? t('Switch to') : t('Your TVs')}</span>
      {list.map((tv) => (
        <button key={tv.host} className="sidebar-tv-item" disabled={!!busy || waiting} onClick={() => void connect(tv.host)} title={t('Connect to {name}', { name: tvName(tv) })}>
          <TvThumb tv={tv} />
          <span>{busy === tv.host ? t('Connecting…') : tvName(tv)}</span>
        </button>
      ))}
    </div>
  )
}

/** TVs used before. Picking one connects to it (checking the same model answers there). */
export function TvPicker(props: { tvs: KnownTv[]; status: ConnStatus; onChange: () => void; title: string; hint?: string }): ReactNode {
  const [busy, setBusy] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const current = props.status.state === 'connected' ? props.status.host : undefined
  const list = props.tvs.filter((t) => t.host !== current)
  if (!list.length) return null

  async function connect(host: string): Promise<void> {
    setBusy(host)
    setError(null)
    try {
      const s = await window.cytv.connectKnown(host)
      if (s.state === 'error') setError(s.error ?? t('Could not connect.'))
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(null)
      props.onChange()
    }
  }

  async function forget(host: string): Promise<void> {
    await window.cytv.forgetTv(host)
    props.onChange()
  }

  return (
    <div className="card white">
      <h2>{props.title}</h2>
      {props.hint && <p className="muted small">{props.hint}</p>}
      {error && <div className="notice warn">{error}</div>}
      <div className="tv-list">
        {list.map((tv) => (
          <div key={tv.host} className="tv-item">
            <TvThumb tv={tv} />
            <div className="tv-item-text">
              <b>{tvName(tv)}</b>
              <span className="muted small">
                {tv.host} · {ago(tv.lastUsed)}
              </span>
            </div>
            <div className="row">
              <button className="btn primary small" disabled={!!busy} onClick={() => connect(tv.host)}>
                {busy === tv.host ? t('Connecting…') : t('Connect')}
              </button>
              <button className="btn ghost small" disabled={!!busy} onClick={() => forget(tv.host)} title={t('Remove from this list')}>
                {t('Forget')}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
