import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { ApplyResult, CameBack, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState } from '@shared/api'
import type { ScreenProps } from '../App'
import { ActionButton, CheckupCard, Confirm, ResultNotice, Skeleton, Toggle, UndoToast, errorText, usePhase } from '../components/ui'
import { ShareList } from '../components/ShareList'
import { defaultTick } from '../lib/ticks'
import { usbOffWhileDebugging } from '../lib/usb'
import { t, tx } from '../lib/i18n'

type Filter = 'all' | 'disable' | 'disabled' | 'absent'

export function Debloat({ status, go, markDone }: ScreenProps): ReactNode {
  const [lists, setLists] = useState<DebloatListSummary[] | null>(null)
  const [query, setQuery] = useState('')
  const [listId, setListId] = useState<string | null>(null)
  const [plan, setPlan] = useState<DebloatPlan | null>(null)
  const [loadingPlan, setLoadingPlan] = useState(false)
  const [ticked, setTicked] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState<Filter>('all')
  const [find, setFind] = useState('')
  const [confirm, setConfirm] = useState<'apply' | 'restore' | null>(null)
  const [applyPhase, setApplyPhase] = usePhase()
  const [restorePhase, setRestorePhase] = usePhase()
  const [backPhase, setBackPhase] = usePhase()
  const busy = applyPhase === 'busy' || restorePhase === 'busy'
  /** What the last Apply changed, so the toast can put it back. */
  const [undo, setUndo] = useState<{ text: string; disable: string[]; enable: string[] } | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  /** Bumped after each Apply, so the check-up runs again on its own. */
  const [checkup, setCheckup] = useState(0)
  const [forceHome, setForceHome] = useState(false)
  const [forceSetup, setForceSetup] = useState(false)
  const [showAdvanced, setShowAdvanced] = useState(false)
  /** Packages this app switched off on this TV, with any list, that are on again. */
  const [back, setBack] = useState<CameBack[]>([])
  const backPkgs = back.flatMap((b) => b.packages)

  const refreshBack = (): void => {
    window.cytv.cameBack().then(setBack, () => setBack([]))
  }
  useEffect(refreshBack, [status.host])

  useEffect(() => {
    // Keep the order lists() gives: matches, then the TV's own brand, with "My TV is not listed" on top.
    window.cytv.lists().then((l) => {
      setLists(l)
      const best = l.find((x) => x.score > 0)
      if (best) setListId(best.id)
    })
  }, [status.host])

  /** quiet: a reload after Apply or Restore keeps the table on screen instead of the placeholder. */
  async function loadPlan(id: string, quiet = false): Promise<void> {
    if (!quiet) {
      setLoadingPlan(true)
      setResult(null)
    }
    try {
      const p = await window.cytv.plan(id)
      setPlan(p)
      refreshBack()
      setTicked(new Set(p.entries.filter((e) => defaultTick(e)).map((e) => e.pkg)))
    } catch (e) {
      setPlan(null)
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setLoadingPlan(false)
    }
  }

  useEffect(() => {
    setUndo(null)
    if (listId) loadPlan(listId)
  }, [listId])

  const toDisable = useMemo(() => plan?.entries.filter((e) => ticked.has(e.pkg) && e.state === 'enabled') ?? [], [plan, ticked])
  const toEnable = useMemo(
    () => plan?.entries.filter((e) => !ticked.has(e.pkg) && e.state === 'disabled' && e.recorded === 'enabled') ?? [],
    [plan, ticked]
  )
  const counts = useMemo(() => {
    const e = plan?.entries ?? []
    return {
      all: e.length,
      disable: e.filter((x) => ticked.has(x.pkg) && x.state === 'enabled').length,
      disabled: e.filter((x) => x.state === 'disabled').length,
      absent: e.filter((x) => x.state === 'absent').length
    }
  }, [plan, ticked])

  const shown = useMemo(() => {
    // The search looks at the package name and the list's note, on top of the tab's filter.
    const q = find.trim().toLowerCase()
    const e = (plan?.entries ?? []).filter((x) => !q || x.pkg.toLowerCase().includes(q) || x.note.toLowerCase().includes(q))
    if (filter === 'disable') return e.filter((x) => ticked.has(x.pkg) && x.state === 'enabled')
    if (filter === 'disabled') return e.filter((x) => x.state === 'disabled')
    if (filter === 'absent') return e.filter((x) => x.state === 'absent')
    return e
  }, [plan, ticked, filter, find])

  const visibleLists = useMemo(() => {
    if (!lists) return []
    const q = query.trim().toLowerCase()
    return q ? lists.filter((l) => l.title.toLowerCase().includes(q) || l.id.includes(q)) : lists
  }, [lists, query])

  function toggle(pkg: string, on: boolean): void {
    setTicked((prev) => {
      const n = new Set(prev)
      if (on) n.add(pkg)
      else n.delete(pkg)
      return n
    })
  }

  async function apply(): Promise<void> {
    if (!plan) return
    setConfirm(null)
    setUndo(null)
    setResult(null)
    setApplyPhase('busy')
    const disable = toDisable.map((e) => e.pkg)
    const enable = toEnable.map((e) => e.pkg)
    try {
      const r = await window.cytv.applyDebloat({ listId: plan.listId, disable, enable, forceHome, forceSetupwraith: forceSetup })
      setApplyPhase(r.ok ? 'done' : 'failed')
      // A clean run shows in the toast; anything with notes stays on the page.
      if (!r.ok || r.warnings.length) setResult(r)
      if (r.ok) {
        setCheckup((n) => n + 1)
        markDone('debloat')
        setUndo({ text: r.summary, disable, enable })
      }
    } catch (e) {
      setApplyPhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      loadPlan(plan.listId, true)
    }
  }

  /** Undo turns back on what Apply switched off, and off again what it turned on. */
  async function undoApply(): Promise<void> {
    if (!plan || !undo) return
    try {
      const r = await window.cytv.applyDebloat({ listId: plan.listId, disable: undo.enable, enable: undo.disable, forceHome, forceSetupwraith: forceSetup })
      if (!r.ok || r.warnings.length) setResult(r)
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      await loadPlan(plan.listId, true)
    }
  }

  /** Packages a system update switched back on: off again, each through the list that switched it off. */
  async function switchBackOff(): Promise<void> {
    if (!back.length) return
    setBackPhase('busy')
    try {
      const rs: ApplyResult[] = []
      for (const b of back) rs.push(await window.cytv.applyDebloat({ listId: b.listId, disable: b.packages, enable: [], forceHome, forceSetupwraith: forceSetup }))
      const ok = rs.every((r) => r.ok)
      setBackPhase(ok ? 'done' : 'failed')
      if (!ok || rs.some((r) => r.warnings.length)) setResult({ ok, summary: rs.map((r) => r.summary).join(' '), warnings: rs.flatMap((r) => r.warnings) })
    } catch (e) {
      setBackPhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      if (plan) loadPlan(plan.listId, true)
      else refreshBack()
    }
  }

  async function restore(): Promise<void> {
    if (!plan) return
    setConfirm(null)
    setUndo(null)
    setRestorePhase('busy')
    try {
      const r = await window.cytv.restore(plan.listId)
      setRestorePhase(r.ok ? 'done' : 'failed')
      setResult(r)
    } catch (e) {
      setRestorePhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      loadPlan(plan.listId, true)
    }
  }

  const stockHome = plan?.homePackage && plan.homePackage !== 'com.gathin.tvhome' && plan.entries.some((e) => e.pkg === plan.homePackage && ticked.has(e.pkg))
  const selectedList = lists?.find((l) => l.id === listId)
  // Shown once per TV model; the Finish step says it again.
  const usbKey = `usb-note:${status.probe?.model ?? ''}`
  const [usbHidden, setUsbHidden] = useState(() => {
    try { return localStorage.getItem(usbKey) === '1' } catch { return false }
  })
  const usbNote = usbOffWhileDebugging(status.probe) && !usbHidden
  const tvName = status.probe ? `${status.probe.manufacturer} ${status.probe.model}` : status.host ?? ''
  const hideUsbNote = (): void => {
    setUsbHidden(true)
    try { localStorage.setItem(usbKey, '1') } catch { /* private window: it just shows again next time */ }
  }

  return (
    <>
      <h1>{t('Clean up')}</h1>
      <p className="lede">
        {t('Pick the list for your TV and untick anything you use. Packages are switched off for your user, never uninstalled. Before the first change, the app writes down how the TV was set up, so Restore puts every package back as it was.')}
      </p>

      {usbNote && (
        <div className="notice warn row between" style={{ flexWrap: 'nowrap', alignItems: 'flex-start' }}>
          <span style={{ flex: 1 }}>
            {t("This TV turns its USB port off while debugging is on. Drives and USB adapters come back once you finish and switch debugging off. Clean up didn't cause it.")}
          </span>
          <button className="btn ghost" aria-label={t('Hide this note')} onClick={hideUsbNote}>✕</button>
        </div>
      )}

      {backPkgs.length > 0 && (
        <div className="notice warn">
          <b>{t('{n} {n|app|apps} came back on, probably after a system update.', { n: backPkgs.length })}</b>
          <div className="small mono" style={{ marginTop: 6 }}>{backPkgs.join(', ')}</div>
          <div style={{ marginTop: 10 }}>
            <ActionButton phase={backPhase} label={t('Switch them off again')} busyLabel={t('Switching off')} doneLabel={t('Off again')} onClick={() => void switchBackOff()} />
          </div>
        </div>
      )}

      <div className="card">
        <div className="card-head">
          <div>
            <h2>{t('Which list?')}</h2>
            <p className="muted small" style={{ margin: 0 }}>
              {selectedList
                ? selectedList.score > 0
                  ? t('This list matches your TV. Pick another if you know better.')
                  : t('No list matched your model exactly. A list from the same brand and year is a good start.')
                : t('Lists come from people who cleaned the same TV model.')}
            </p>
          </div>
          <input type="search" placeholder={t('Search brand or model')} value={query} onChange={(e) => setQuery(e.target.value)} style={{ width: 240 }} />
        </div>
        {!lists ? (
          <Skeleton rows={4} label={t('Loading lists')} />
        ) : (
          <div className="list-picker" role="listbox">
            {visibleLists.map((l) => (
              <button
                key={l.id}
                role="option"
                aria-selected={l.id === listId}
                className={`list-opt${l.id === listId ? ' selected' : ''}`}
                onClick={() => setListId(l.id)}
              >
                {l.score > 0 && <span className="chip ok">{t('Match')}</span>}
                {l.id === 'custom' && <span className="chip">{t('Your TV')}</span>}
                <span className="title">{l.title}</span>
                {l.id !== 'custom' && <span className="id">{l.id}</span>}
              </button>
            ))}
            {visibleLists.length === 0 && <p className="muted">{t('No list matches “{query}”.', { query })}</p>}
          </div>
        )}
      </div>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {checkup > 0 && <CheckupCard key={checkup} auto />}

      {loadingPlan && (
        <div className="card white">
          <Skeleton rows={8} label={t("Reading what's installed on the TV")} />
        </div>
      )}

      {plan && !loadingPlan && (
        <div className="card white">
          <div className="card-head">
            <div>
              <h2>{plan.title}</h2>
              {plan.header.length > 0 && (
                <details>
                  <summary className="small muted" style={{ cursor: 'pointer' }}>
                    {t('Notes from the person who made this list')}
                  </summary>
                  <pre className="mono small" style={{ whiteSpace: 'pre-wrap', margin: '8px 0 0', color: 'var(--navy)' }}>
                    {plan.header.join('\n')}
                  </pre>
                </details>
              )}
            </div>
          </div>

          <div className="stat-row">
            <div className="stat">
              <b>{counts.disable}</b>
              <span>{t('will be switched off')}</span>
            </div>
            <div className="stat">
              <b>{toEnable.length}</b>
              <span>{t('will be switched back on')}</span>
            </div>
            <div className="stat">
              <b>{counts.disabled}</b>
              <span>{t('already off')}</span>
            </div>
            <div className="stat">
              <b>{counts.absent}</b>
              <span>{t('not on this TV')}</span>
            </div>
          </div>

          {plan.record ? (
            <div className="notice">
              {tx('Restore point saved {date} in {path}. Keep that file: it is the only copy.', { date: fmtDate(plan.record.created), path: <code>{plan.record.path}</code> })}
            </div>
          ) : (
            <div className="notice">{t('No restore point yet. The app saves one before the first package is switched off.')}</div>
          )}

          {stockHome && (
            <div className="notice warn">
              {tx('This list switches off {pkg}, which is your Home screen right now. The app skips it until the new launcher is Home, so the TV always has something to start. Install CleanMyTV Home (Home screen step), then apply this list again.', { pkg: <code>{plan.homePackage}</code> })}
            </div>
          )}

          <div className="toolbar">
            <div className="seg" role="tablist">
              {(
                [
                  ['all', t('All {n}', { n: counts.all })],
                  ['disable', t('Will switch off {n}', { n: counts.disable })],
                  ['disabled', t('Already off {n}', { n: counts.disabled })],
                  ['absent', t('Not installed {n}', { n: counts.absent })]
                ] as [Filter, string][]
              ).map(([f, label]) => (
                <button key={f} className={filter === f ? 'on' : ''} onClick={() => setFilter(f)}>
                  {label}
                </button>
              ))}
            </div>
            <input type="search" placeholder={t('Find a package')} value={find} onChange={(e) => setFind(e.target.value)} aria-label={t('Find a package')} style={{ width: 200 }} />
            <span className="spacer" />
            <button className="btn small ghost" onClick={() => setTicked(new Set(plan.entries.filter(defaultTick).map((e) => e.pkg)))}>
              {t("Back to the list's choices")}
            </button>
          </div>

          <div className="table-wrap">
            <table className="pkgs">
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th>{t('Package')}</th>
                  <th style={{ width: 120 }}>{t('On the TV')}</th>
                  <th>{t('Why')}</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((e) => (
                  <tr key={e.pkg} className={e.state === 'absent' ? 'dim' : ''}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={t('Switch off {pkg}', { pkg: e.pkg })}
                        checked={ticked.has(e.pkg)}
                        disabled={e.state === 'absent'}
                        onChange={(ev) => toggle(e.pkg, ev.target.checked)}
                      />
                    </td>
                    <td>
                      <div className="pkg-name">{e.pkg}</div>
                      {e.choice && <div className="choice-tag">{t('Your choice · {why}', { why: e.choice })}</div>}
                      <div className="small muted">
                        {[e.added && t('Added by you.'), !e.suggested && t('Kept by the list author.'), e.common > 1 && t('Switched off in {n} lists.', { n: e.common })].filter(Boolean).join(' ')}
                      </div>
                    </td>
                    <td>
                      <StateBadge state={e.state} recorded={e.recorded} ticked={ticked.has(e.pkg)} />
                    </td>
                    <td className="pkg-note">{e.note || '—'}</td>
                  </tr>
                ))}
                {shown.length === 0 && (
                  <tr>
                    <td colSpan={4} className="muted">
                      {find.trim() ? t('No package here matches “{q}”.', { q: find.trim() }) : t('Nothing here.')}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <AddPackage
            have={plan.entries}
            onAdd={(e) => {
              setPlan((p) => (p ? { ...p, entries: [...p.entries, e] } : p))
              toggle(e.pkg, true)
              setFilter('all')
            }}
          />

          <div style={{ marginTop: 16 }}>
            <button className="btn small ghost" onClick={() => setShowAdvanced(!showAdvanced)} aria-expanded={showAdvanced}>
              {showAdvanced ? '▾' : '▸'} {t('Safety overrides')}
            </button>
            {showAdvanced && (
              <div className="card" style={{ marginTop: 8 }}>
                <Toggle
                  label={t('Switch off the current Home app anyway')}
                  hint={t('Only if you can still reach the TV another way. Without a launcher, the Home button lands on a test app or nothing.')}
                  checked={forceHome}
                  onChange={setForceHome}
                />
                <Toggle
                  label={t("Switch off Google TV's setup wizard even if it owns the Wi-Fi network")}
                  hint={t('Android deletes a network saved during setup when the wizard is switched off. Use this on Ethernet only.')}
                  checked={forceSetup}
                  onChange={setForceSetup}
                />
              </div>
            )}
          </div>

          <div className="action-bar">
            {toDisable.length + toEnable.length > 0 && !busy && (
              <span className="basket" key={toDisable.length + toEnable.length}>
                <span className="basket-count">{toDisable.length + toEnable.length}</span>
                {[toDisable.length && t('{n} to switch off', { n: toDisable.length }), toEnable.length && t('{n} back on', { n: toEnable.length })].filter(Boolean).join(' · ')}
                <button
                  className="icon-btn"
                  onClick={() => setTicked(new Set(plan.entries.filter((e) => e.state === 'disabled').map((e) => e.pkg)))}
                  aria-label={t('Clear the changes')}
                  title={t('Leave the TV as it is')}
                >
                  ✕
                </button>
              </span>
            )}
            <ActionButton
              primary
              phase={applyPhase}
              label={t('Apply to TV')}
              busyLabel={t('Applying')}
              doneLabel={t('Applied')}
              disabled={busy || (toDisable.length === 0 && toEnable.length === 0)}
              onClick={() => setConfirm('apply')}
            />
            <ActionButton
              phase={restorePhase}
              label={t('Restore')}
              busyLabel={t('Restoring')}
              doneLabel={t('Restored')}
              disabled={busy || !plan.record}
              onClick={() => setConfirm('restore')}
            />
            <span className="spacer" />
            <button className="btn ghost" onClick={() => go('home')}>
              {t('Next: the home screen')}
            </button>
          </div>
        </div>
      )}

      {lists && <ShareList noMatch={!lists.some((l) => l.score > 0)} applied={!!plan?.record} />}

      {undo && <UndoToast key={undo.text + undo.disable.length} text={undo.text} undoneText={t('Put back as it was')} onUndo={undoApply} onClose={() => setUndo(null)} />}

      {confirm === 'apply' && plan && (
        <Confirm title={t('Apply these changes?')} confirmLabel={t('Apply')} onConfirm={apply} onCancel={() => setConfirm(null)}>
          <p>
            {toEnable.length > 0
              ? tx('Switch off {off} packages and switch {on} back on on {tv}.', { off: <b>{toDisable.length}</b>, on: <b>{toEnable.length}</b>, tv: tvName })
              : tx('Switch off {off} packages on {tv}.', { off: <b>{toDisable.length}</b>, tv: tvName })}
          </p>
          <p className="muted small">
            {plan.record ? t('The restore point from the first run is kept.') : t('A restore point is saved first.')} {t('Apps you keep update through the Play Store as before.')}
          </p>
        </Confirm>
      )}
      {confirm === 'restore' && plan && (
        <Confirm title={t('Restore this TV?')} confirmLabel={t('Restore')} onConfirm={restore} onCancel={() => setConfirm(null)}>
          <p>
            {t('Every package from this list goes back to how it was on {date}. Packages that were already off then stay off. Home goes back to the launcher that owned it.', { date: fmtDate(plan.record?.created) })}
          </p>
          <p className="muted small">{t("CleanMyTV Home stays installed. Remove it from the TV's Settings → Apps if you want it gone.")}</p>
        </Confirm>
      )}
    </>
  )
}

/** Adds a package that isn't in the list (#39). Only packages on the TV can be added. */
function AddPackage({ have, onAdd }: { have: DebloatEntry[]; onAdd: (e: DebloatEntry) => void }): ReactNode {
  const [open, setOpen] = useState(false)
  const [all, setAll] = useState<DebloatEntry[] | null>(null)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const inList = useMemo(() => new Set(have.map((e) => e.pkg)), [have])
  const options = useMemo(() => (all ?? []).filter((e) => !inList.has(e.pkg) && e.state === 'enabled'), [all, inList])

  async function show(): Promise<void> {
    setOpen(true)
    if (all) return
    try {
      setAll(await window.cytv.tvPackages())
    } catch (e) {
      setError(errorText(e))
    }
  }

  function add(): void {
    const pkg = name.trim()
    if (!pkg) return
    const found = all?.find((e) => e.pkg === pkg)
    if (inList.has(pkg)) setError(t('{pkg} is already in the list above.', { pkg }))
    else if (!found) setError(t("{pkg} isn't on this TV, or the app protects it: a launcher it looks after itself, or a core package the TV needs to start. Check the spelling.", { pkg }))
    else if (found.state !== 'enabled') setError(t('{pkg} is already off.', { pkg }))
    else {
      onAdd(found)
      setName('')
      setError(null)
    }
  }

  const picked = options.find((e) => e.pkg === name.trim())
  if (!open) {
    return (
      <div style={{ marginTop: 16 }}>
        <button className="btn small ghost" onClick={show}>
          + {t("Add a package that isn't in the list")}
        </button>
      </div>
    )
  }
  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h3 style={{ marginTop: 0 }}>{t('Add a package')}</h3>
      <p className="small muted" style={{ marginTop: 0 }}>
        {options.length
          ? t('Type a package name, or pick one of the {n} apps that are on. It joins the list ticked, the restore point covers it, and Restore switches it back on.', { n: options.length })
          : t('Type a package name, or pick one of the apps that are on. It joins the list ticked, the restore point covers it, and Restore switches it back on.')}
      </p>
      {!all && !error ? (
        <Skeleton rows={3} label={t("Reading the TV's packages")} />
      ) : (
        <div className="row">
          <input
            className="mono"
            list="tv-packages"
            placeholder="com.vendor.ads"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              setError(null)
            }}
            onKeyDown={(e) => e.key === 'Enter' && add()}
            style={{ flex: 1, minWidth: 260 }}
            aria-label={t('Package name')}
          />
          <datalist id="tv-packages">
            {options.map((e) => (
              <option key={e.pkg} value={e.pkg} />
            ))}
          </datalist>
          <button className="btn" disabled={!name.trim()} onClick={add}>
            {t('Add')}
          </button>
        </div>
      )}
      {picked && (picked.choice || picked.common > 0) && (
        <p className="small muted" style={{ marginBottom: 0 }}>
          {[
            picked.choice && t('Careful: {why}', { why: picked.choice }),
            picked.common > 0 && (picked.note ? t('Switched off in {n} lists: {note}.', { n: picked.common, note: picked.note }) : t('Switched off in {n} lists.', { n: picked.common }))
          ].filter(Boolean).join(' ')}
        </p>
      )}
      {error && <div className="notice warn" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
    </div>
  )
}

function StateBadge({ state, recorded, ticked }: { state: PackageState; recorded?: PackageState; ticked: boolean }): ReactNode {
  if (state === 'absent') return <span className="chip optional">{t('Not installed')}</span>
  if (state === 'disabled') {
    if (!ticked && recorded === 'enabled') return <span className="chip warn">{t('Off · turns on')}</span>
    return <span className="chip">{t('Off')}</span>
  }
  return ticked ? <span className="chip warn">{t('On · turns off')}</span> : <span className="chip ok">{t('On · kept')}</span>
}

function fmtDate(iso?: string): string {
  if (!iso) return t('earlier')
  // recovery.py writes %Y-%m-%dT%H:%M:%S%z, e.g. 2026-10-20T14:03:00+0100.
  const d = new Date(iso.replace(/([+-]\d{2})(\d{2})$/, '$1:$2'))
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}
