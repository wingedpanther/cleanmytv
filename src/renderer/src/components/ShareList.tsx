import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { ShareData } from '@shared/api'
import { shareIssue } from '@shared/share'
import { Modal, Skeleton, errorText } from './ui'
import { t } from '../lib/i18n'

/**
 * Share my list: a GitHub issue with the TV's details and what Clean up switched off, so the TV can get
 * a list of its own. A card when no list matches the TV, a small button on any TV after a Clean up.
 */
export function ShareList({ noMatch, applied }: { noMatch: boolean; applied: boolean }): ReactNode {
  const [open, setOpen] = useState(false)
  if (!noMatch && !applied) return null
  return (
    <>
      {noMatch ? (
        <div className="card">
          <h2>{t('Share my list')}</h2>
          <p className="muted small">
            {t('No list in the app fits this TV yet. Share what you switched off and what you kept, and it can become a list for everyone with this model.')}
          </p>
          <button className="btn" onClick={() => setOpen(true)}>
            {t('Share my list')}
          </button>
        </div>
      ) : (
        <div className="row" style={{ marginBottom: 16 }}>
          <button className="btn small ghost" onClick={() => setOpen(true)}>
            {t('Share my list')}
          </button>
          <span className="muted small">{t('Send what you switched off and kept to the lists on GitHub.')}</span>
        </div>
      )}
      {open && <ShareDialog onClose={() => setOpen(false)} />}
    </>
  )
}

function ShareDialog({ onClose }: { onClose: () => void }): ReactNode {
  const [data, setData] = useState<ShareData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [broke, setBroke] = useState('')
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    window.cytv.shareInfo().then(setData, (e) => setError(errorText(e)))
  }, [])

  const issue = useMemo(() => (data ? shareIssue(data, broke) : null), [data, broke])

  async function copy(): Promise<void> {
    if (!issue) return
    try {
      await navigator.clipboard.writeText(issue.body)
      setCopied(true)
    } catch (e) {
      setError(errorText(e))
    }
  }

  return (
    <Modal onClose={onClose} wide>
      <h2>{t('Share my list')}</h2>
      <p className="muted small">
        {t("This opens a new issue on GitHub with the text below. Nothing is sent until you submit it there. It has no address, Wi-Fi name or account.")}
      </p>
      {error && <div className="notice error">{error}</div>}
      {!data && !error && <Skeleton rows={6} label={t('Reading what Clean up did on this TV')} />}
      {data && issue && (
        <>
          {!data.lists.length && <div className="notice warn">{t("Clean up hasn't switched anything off on this TV yet, so the issue only has the TV's details.")}</div>}
          <label className="field">
            {t('Anything that broke?')}
            <textarea rows={3} value={broke} onChange={(e) => setBroke(e.target.value)} placeholder={t("For example: the remote's voice button stopped working.")} />
          </label>
          <h3 style={{ marginTop: 16 }}>{t('The issue')}</h3>
          <p className="small"><b>{issue.title}</b></p>
          <pre className="share-text mono small">{issue.body}</pre>
          {!issue.fits && (
            <div className="notice warn" style={{ marginTop: 12, marginBottom: 0 }}>
              {t('This is too long for a link. Open on GitHub copies the full text, and the issue opens with a short version: paste the text into it.')}
            </div>
          )}
          {copied && <div className="notice ok" style={{ marginTop: 12, marginBottom: 0 }}>{t('Copied. Paste it into the issue on GitHub.')}</div>}
        </>
      )}
      <div className="modal-actions">
        <button className="btn ghost" onClick={onClose}>
          {t('Cancel')}
        </button>
        <button className="btn" disabled={!issue} onClick={() => void copy()}>
          {t('Copy the text')}
        </button>
        {issue ? (
          // Links open in the browser, as every link in the app does.
          <a className="btn primary" href={issue.url} target="_blank" rel="noreferrer" onClick={() => { if (!issue.fits) void copy() }}>
            {t('Open on GitHub')}
          </a>
        ) : (
          <button className="btn primary" disabled>
            {t('Open on GitHub')}
          </button>
        )}
      </div>
    </Modal>
  )
}
