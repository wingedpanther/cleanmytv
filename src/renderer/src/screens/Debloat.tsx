import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { ApplyResult, DebloatEntry, DebloatListSummary, DebloatPlan, PackageState } from '@shared/api'
import type { ScreenProps } from '../App'
import { Busy, Confirm, ResultNotice, Toggle, errorText } from '../components/ui'

type Filter = 'all' | 'disable' | 'disabled' | 'absent'

export function Debloat({ status, go, markDone }: ScreenProps): ReactNode {
  const [lists, setLists] = useState<DebloatListSummary[] | null>(null)
  const [query, setQuery] = useState('')
  const [listId, setListId] = useState<string | null>(null)
  const [plan, setPlan] = useState<DebloatPlan | null>(null)
  const [loadingPlan, setLoadingPlan] = useState(false)
  const [ticked, setTicked] = useState<Set<string>>(new Set())
  const [filter, setFilter] = useState<Filter>('all')
  const [confirm, setConfirm] = useState<'apply' | 'restore' | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [forceHome, setForceHome] = useState(false)
  const [forceSetup, setForceSetup] = useState(false)
  const [showAdvanced, setShowAdvanced] = useState(false)

  useEffect(() => {
    window.cytv.lists().then((l) => {
      const sorted = [...l].sort((a, b) => b.score - a.score || a.title.localeCompare(b.title))
      setLists(sorted)
      const best = sorted.find((x) => x.score > 0)
      if (best) setListId(best.id)
    })
  }, [status.host])

  async function loadPlan(id: string): Promise<void> {
    setLoadingPlan(true)
    setResult(null)
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
    const e = plan?.entries ?? []
    if (filter === 'disable') return e.filter((x) => ticked.has(x.pkg) && x.state === 'enabled')
    if (filter === 'disabled') return e.filter((x) => x.state === 'disabled')
    if (filter === 'absent') return e.filter((x) => x.state === 'absent')
    return e
  }, [plan, ticked, filter])

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
    setBusy('Applying the list on the TV')
    try {
      const r = await window.cytv.applyDebloat({
        listId: plan.listId,
        disable: toDisable.map((e) => e.pkg),
        enable: toEnable.map((e) => e.pkg),
        forceHome,
        forceSetupwraith: forceSetup
      })
      setResult(r)
      if (r.ok) markDone('debloat')
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      loadPlan(plan.listId)
    }
  }

  async function restore(): Promise<void> {
    if (!plan) return
    setConfirm(null)
    setBusy('Putting the recorded state back')
    try {
      setResult(await window.cytv.restore(plan.listId))
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setBusy(null)
      loadPlan(plan.listId)
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
          <Busy text="Loading lists" />
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
      {busy && (
        <div className="notice">
          <Busy text={busy} />
        </div>
      )}

      {loadingPlan && <Busy text="Reading what's installed on the TV" />}

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
              <span>not on this TV</span>
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

          {stockHome && (
            <div className="notice warn">
              This list switches off <code>{plan.homePackage}</code>, which is your Home screen right now. The app skips it until
              the new launcher is Home, so the TV always has something to start. Install CleanMyTV Home (Home screen step), then apply this list
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
                  ['absent', `Not installed ${counts.absent}`]
                ] as [Filter, string][]
              ).map(([f, label]) => (
                <button key={f} className={filter === f ? 'on' : ''} onClick={() => setFilter(f)}>
                  {label}
                </button>
              ))}
            </div>
            <span className="spacer" />
            <button className="btn small ghost" onClick={() => setTicked(new Set(plan.entries.filter(defaultTick).map((e) => e.pkg)))}>
              Back to the list's choices
            </button>
          </div>

          <div className="table-wrap">
            <table className="pkgs">
              <thead>
                <tr>
                  <th style={{ width: 36 }} />
                  <th>Package</th>
                  <th style={{ width: 120 }}>On the TV</th>
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
                        {e.added && 'Added by you. '}
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
                      Nothing here.
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

          <div className="row" style={{ marginTop: 18 }}>
            <button
              className="btn primary"
              disabled={!!busy || (toDisable.length === 0 && toEnable.length === 0)}
              onClick={() => setConfirm('apply')}
            >
              Apply to TV
            </button>
            <button className="btn" disabled={!!busy || !plan.record} onClick={() => setConfirm('restore')}>
              Restore
            </button>
            <span className="spacer" />
            <button className="btn ghost" onClick={() => go('home')}>
              Next: the home screen
            </button>
          </div>
        </div>
      )}

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
            {plan.record ? 'The restore point from the first run is kept.' : 'A restore point is saved first.'} Apps you keep
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
          <p className="muted small">CleanMyTV Home stays installed. Remove it from the TV's Settings → Apps if you want it gone.</p>
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
    else if (!found) setError(`${pkg} isn't on this TV, or it's a launcher the app looks after itself. Check the spelling.`)
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
        <Busy text="Reading the TV's packages" />
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

function defaultTick(e: DebloatEntry): boolean {
  // Keep earlier choices: a package this app switched off stays ticked.
  if (e.state === 'disabled') return e.suggested || e.recorded === 'enabled'
  // Features people use (phone remote, casting, voice) are the user's call, never a default.
  return e.suggested && e.state === 'enabled' && !e.choice
}

function StateBadge({ state, recorded, ticked }: { state: PackageState; recorded?: PackageState; ticked: boolean }): ReactNode {
  if (state === 'absent') return <span className="chip optional">Not installed</span>
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
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' })
}
