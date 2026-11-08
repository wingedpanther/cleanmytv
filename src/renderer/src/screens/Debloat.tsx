import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { ApplyResult, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState } from '@shared/api'
import type { ScreenProps } from '../App'
import { ActionButton, Confirm, ResultNotice, Skeleton, Toggle, UndoToast, errorText, usePhase } from '../components/ui'
import { defaultTick } from '../lib/ticks'

type Filter = 'all' | 'disable' | 'disabled' | 'absent'

export function Debloat({ status, go, markDone }: ScreenProps): ReactNode {
  const [lists, setLists] = useState<DebloatListSummary[] | null>(null)
  const [query, setQuery] = useState('')
  const [listId, setListId] = useState<string | null>(null)
  const [plan, setPlan] = useState<DebloatPlan | null>(null)
  const [loadingPlan, setLoadingPlan] = useState(false)
  const [ticked, setTicked] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState<Filter>('all')
  const [find, setНайти] = useState('')
  const [confirm, setConfirm] = useState<'apply' | 'restore' | null>(null)
  const [applyPhase, setApplyPhase] = usePhase()
  const [restorePhase, setRestorePhase] = usePhase()
  const [backPhase, setНазадPhase] = usePhase()
  const busy = applyPhase === 'busy' || restorePhase === 'busy'
  /** What the last Apply changed, so the toast can put it back. */
  const [undo, setUndo] = useState<{ text: string; disable: string[]; enable: string[] } | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [forceHome, setForceHome] = useState(false)
  const [forceSetup, setForceSetup] = useState(false)
  const [showAdvanced, setShowAdvanced] = useState(false)

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

  /** Packages a firmware update switched back on: off again, as they were. */
  async function switchНазадOff(): Promise<void> {
    if (!plan?.cameНазад?.length) return
    setНазадPhase('busy')
    try {
      const r = await window.cytv.applyDebloat({ listId: plan.listId, disable: plan.cameНазад, enable: [], forceHome, forceSetupwraith: forceSetup })
      setНазадPhase(r.ok ? 'done' : 'failed')
      if (!r.ok || r.warnings.length) setResult(r)
    } catch (e) {
      setНазадPhase('failed')
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      loadPlan(plan.listId, true)
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

  return (
    <>
      <h1>Clean up</h1>
      <p className="lede">
        Pick the list for your TV and untick anything you use. Packages are switched off for your user, never uninstalled.
        Before the first change, the app writes down how the TV was set up, so Restore puts every package back as it was.
      </p>

      <div className="card">
        <div className="card-head">
          <div>
            <h2>Which list?</h2>
            <p className="muted small" style={{ margin: 0 }}>
              {selectedList
                ? selectedList.score > 0
                  ? 'This list matches your TV. Pick another if you know better.'
                  : 'No list matched your model exactly. A list from the same brand and year is a good start.'
                : 'Lists come from people who cleaned the same TV model.'}
            </p>
          </div>
          <input type="search" placeholder="Search brand or model" value={query} onChange={(e) => setQuery(e.target.value)} style={{ width: 240 }} />
        </div>
        {!lists ? (
          <Skeleton rows={4} label="Loading lists" />
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
                {l.score > 0 && <span className="chip ok">Match</span>}
                {l.id === 'custom' && <span className="chip">Your TV</span>}
                <span className="title">{l.title}</span>
                {l.id !== 'custom' && <span className="id">{l.id}</span>}
              </button>
            ))}
            {visibleLists.length === 0 && <p className="muted">No list matches “{query}”.</p>}
          </div>
        )}
      </div>

      <ResultNotice result={result} onDismiss={() => setResult(null)} />

      {loadingPlan && (
        <div className="card white">
          <Skeleton rows={8} label="Reading what's installed on the TV" />
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
                    Notes from the person who made this list
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
              <span>will be switched off</span>
            </div>
            <div className="stat">
              <b>{toEnable.length}</b>
              <span>will be switched back on</span>
            </div>
            <div className="stat">
              <b>{counts.disabled}</b>
              <span>already off</span>
            </div>
            <div className="stat">
              <b>{counts.absent}</b>
              <span>не установлено на этом ТВ</span>
            </div>
          </div>

          {plan.record ? (
            <div className="notice">
              Restore point saved {fmtDate(plan.record.created)} in <code>{plan.record.path}</code>. Keep that file: it is the
              only copy.
            </div>
          ) : (
            <div className="notice">No restore point yet. The app saves one before the first package is switched off.</div>
          )}

          {!!plan.cameНазад?.length && (
            <div className="notice warn">
              {plan.cameНазад.length === 1 ? '1 package' : `${plan.cameНазад.length} packages`} you switched off {plan.cameНазад.length === 1 ? 'is' : 'are'} on
              again, often after a firmware update: <code>{plan.cameНазад.join(', ')}</code>.
              <div style={{ marginTop: 10 }}>
                <ActionButton phase={backPhase} label="Switch them off again" busyLabel="Switching off" doneLabel="Off again" onClick={() => void switchНазадOff()} />
              </div>
            </div>
          )}

          {stockHome && (
            <div className="notice warn">
              This list switches off <code>{plan.homePackage}</code>, which is your Главный экран right now. The app skips it until
              the new launcher is Home, so the TV always has something to start. Install CleanMyTV Home (Главный экран step), then apply this list
              again.
            </div>
          )}

          <div className="toolbar">
            <div className="seg" role="tablist">
              {(
                [
                  ['all', `All ${counts.all}`],
                  ['disable', `Will switch off ${counts.disable}`],
                  ['disabled', `Already off ${counts.disabled}`],
                  ['absent', `Не установлено ${counts.absent}`]
                ] as [Filter, string][]
              ).map(([f, label]) => (
                <button key={f} className={filter === f ? 'on' : ''} onClick={() => setFilter(f)}>
                  {label}
                </button>
              ))}
            </div>
            <input type="search" placeholder="Найти a package" value={find} onChange={(e) => setНайти(e.target.value)} aria-label="Найти a package" style={{ width: 200 }} />
            <span className="spacer" />
            <button className="btn small ghost" onClick={() => setTicked(new Set(plan.entries.filter(defaultTick).map((e) => e.pkg)))}>
              Назад to the list's choices
            </button>
          </div>

          <div className="table-wrap">
            <table className="pkgs">
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th>Package</th>
                  <th style={{ width: 120 }}>На телевизоре</th>
                  <th>Why</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((e) => (
                  <tr key={e.pkg} className={e.state === 'absent' ? 'dim' : ''}>
                    <td>
                      <input
                        type="checkbox"
                        aria-label={`Switch off ${e.pkg}`}
                        checked={ticked.has(e.pkg)}
                        disabled={e.state === 'absent'}
                        onChange={(ev) => toggle(e.pkg, ev.target.checked)}
                      />
                    </td>
                    <td>
                      <div className="pkg-name">{e.pkg}</div>
                      {e.choice && <div className="choice-tag">Your choice · {e.choice}</div>}
                      <div className="small muted">
                        {e.added && 'Добавлено by you. '}
                        {!e.suggested && 'Kept by the list author. '}
                        {e.common > 1 && `Switched off in ${e.common} lists.`}
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
                      {find.trim() ? `No package here matches “${find.trim()}”.` : 'Nothing here.'}
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
              {showAdvanced ? '▾' : '▸'} Safety overrides
            </button>
            {showAdvanced && (
              <div className="card" style={{ marginTop: 8 }}>
                <Toggle
                  label="Switch off the current Home app anyway"
                  hint="Only if you can still reach the TV another way. Without a launcher, the Home button lands on a test app or nothing."
                  checked={forceHome}
                  onChange={setForceHome}
                />
                <Toggle
                  label="Switch off Google TV's setup wizard even if it owns the Wi-Fi network"
                  hint="Android deletes a network saved during setup when the wizard is switched off. Use this on Ethernet only."
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
                {[toDisable.length && `${toDisable.length} to switch off`, toEnable.length && `${toEnable.length} back on`].filter(Boolean).join(' · ')}
                <button
                  className="icon-btn"
                  onClick={() => setTicked(new Set(plan.entries.filter((e) => e.state === 'disabled').map((e) => e.pkg)))}
                  aria-label="Clear the changes"
                  title="Leave the TV as it is"
                >
                  ✕
                </button>
              </span>
            )}
            <ActionButton
              primary
              phase={applyPhase}
              label="Apply to TV"
              busyLabel="Applying"
              doneLabel="Applied"
              disabled={busy || (toDisable.length === 0 && toEnable.length === 0)}
              onClick={() => setConfirm('apply')}
            />
            <ActionButton
              phase={restorePhase}
              label="Restore"
              busyLabel="Restoring"
              doneLabel="Restored"
              disabled={busy || !plan.record}
              onClick={() => setConfirm('restore')}
            />
            <span className="spacer" />
            <button className="btn ghost" onClick={() => go('home')}>
              Next: the home screen
            </button>
          </div>
        </div>
      )}

      {undo && <UndoToast key={undo.text + undo.disable.length} text={undo.text} undoneText="Put back as it was" onUndo={undoApply} onЗакрыть={() => setUndo(null)} />}

      {confirm === 'apply' && plan && (
        <Confirm title="Apply these changes?" confirmLabel="Apply" onConfirm={apply} onCancel={() => setConfirm(null)}>
          <p>
            Switch off <b>{toDisable.length}</b> packages
            {toEnable.length > 0 && (
              <>
                {' '}and switch <b>{toEnable.length}</b> back on
              </>
            )}{' '}
            on {status.probe ? `${status.probe.manufacturer} ${status.probe.model}` : status.host}.
          </p>
          <p className="muted small">
            {plan.record ? 'The restore point from the first run is kept.' : 'A restore point is saved first.'} Приложения you keep
            update through the Play Store as before.
          </p>
        </Confirm>
      )}
      {confirm === 'restore' && plan && (
        <Confirm title="Restore this TV?" confirmLabel="Restore" onConfirm={restore} onCancel={() => setConfirm(null)}>
          <p>
            Every package from this list goes back to how it was on {fmtDate(plan.record?.created)}. Packages that were already
            off then stay off. Home goes back to the launcher that owned it.
          </p>
          <p className="muted small">CleanMyTV Home stays installed. Удалить it from the TV's Settings → Приложения if you want it gone.</p>
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
    if (inList.has(pkg)) setError(`${pkg} is already in the list above.`)
    else if (!found) setError(`${pkg} isn't on this TV, or the app protects it: a launcher it looks after itself, or a core package the TV needs to start. Check the spelling.`)
    else if (found.state !== 'enabled') setError(`${pkg} is already off.`)
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
          + Add a package that isn't in the list
        </button>
      </div>
    )
  }
  return (
    <div className="card" style={{ marginTop: 16 }}>
      <h3 style={{ marginTop: 0 }}>Add a package</h3>
      <p className="small muted" style={{ marginTop: 0 }}>
        Type a package name, or pick one of the {options.length || ''} apps that are on. It joins the list ticked, the restore point
        covers it, and Restore switches it back on.
      </p>
      {!all && !error ? (
        <Skeleton rows={3} label="Reading the TV's packages" />
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
            aria-label="Package name"
          />
          <datalist id="tv-packages">
            {options.map((e) => (
              <option key={e.pkg} value={e.pkg} />
            ))}
          </datalist>
          <button className="btn" disabled={!name.trim()} onClick={add}>
            Add
          </button>
        </div>
      )}
      {picked && (picked.choice || picked.common > 0) && (
        <p className="small muted" style={{ marginBottom: 0 }}>
          {picked.choice ? `Careful: ${picked.choice} ` : ''}
          {picked.common > 0 ? `Switched off in ${picked.common} lists${picked.note ? `: ${picked.note}` : ''}.` : ''}
        </p>
      )}
      {error && <div className="notice warn" style={{ marginTop: 12, marginBottom: 0 }}>{error}</div>}
    </div>
  )
}

function StateBadge({ state, recorded, ticked }: { state: PackageState; recorded?: PackageState; ticked: boolean }): ReactNode {
  if (state === 'absent') return <span className="chip optional">Не установлено</span>
  if (state === 'disabled') {
    if (!ticked && recorded === 'enabled') return <span className="chip warn">Off · turns on</span>
    return <span className="chip">Off</span>
  }
  return ticked ? <span className="chip warn">On · turns off</span> : <span className="chip ok">On · kept</span>
}

function fmtDate(iso?: string): string {
  if (!iso) return 'earlier'
  // recovery.py writes %Y-%m-%dT%H:%M:%S%z, e.g. 2026-10-20T14:03:00+0100.
  const d = new Date(iso.replace(/([+-]\d{2})(\d{2})$/, '$1:$2'))
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString([], { dateСтиль: 'medium', timeСтиль: 'short' })
}
