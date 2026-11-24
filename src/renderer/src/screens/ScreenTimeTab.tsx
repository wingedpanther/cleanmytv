import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { HomeConfig, ScreenDay, ScreenTimeData, TvApp } from '@shared/api'
import { addDays, appTotals, daysBetween } from '@shared/screentime'
import { Busy, Confirm, Slider, Toggle, errorText } from '../components/ui'
import { ScreenTimeCard } from '../components/HomePreview'
import { t } from '../lib/i18n'

type SetFn = (change: Partial<HomeConfig>, wait?: number) => void
type Period = '1' | '7' | '30' | 'custom'

/** Minutes; 0 is no goal. */
const GOALS = [0, 60, 90, 120, 150, 180, 240, 300, 360]

/** "2 h 15 min", "45 min", from seconds. */
function words(sec: number): string {
  const m = Math.round(sec / 60)
  return m < 60 ? t('{n} min', { n: m }) : m % 60 === 0 ? t('{n} h', { n: m / 60 }) : t('{h} h {m} min', { h: Math.floor(m / 60), m: m % 60 })
}

const dayDate = (day: string): Date => new Date(`${day}T12:00:00Z`)
const weekday = (day: string): string => dayDate(day).toLocaleDateString(undefined, { weekday: 'short', timeZone: 'UTC' })
const shortDate = (day: string): string => dayDate(day).toLocaleDateString(undefined, { day: 'numeric', month: 'short', timeZone: 'UTC' })

/** Every day from `from` to `to`, empty where Home has nothing. */
function fill(days: ScreenDay[], from: string, to: string): ScreenDay[] {
  const by = new Map(days.map((d) => [d.day, d]))
  const out: ScreenDay[] = []
  for (let d = from; d <= to; d = addDays(d, 1)) out.push(by.get(d) ?? { day: d, apps: {}, hours: Array(24).fill(0), stats: {} })
  return out
}

const total = (d: ScreenDay): number => Object.values(d.apps).reduce((a, b) => a + b, 0)

/** Under a run of days: weekdays up to a week, then every few days' dates counted back from the last. */
function dayLabels(days: ScreenDay[]): string[] {
  const every = Math.max(1, Math.ceil(days.length / 8))
  return days.map((d, i) => (days.length <= 7 ? weekday(d.day) : (days.length - 1 - i) % every === 0 ? shortDate(d.day) : ''))
}

const HOUR_LABELS = [0, 6, 12, 18].map((h) => new Date(Date.UTC(2026, 0, 1, h)).toLocaleTimeString(undefined, { hour: 'numeric', timeZone: 'UTC' }))

/**
 * Bars in seconds. The last bar (or `mark`) is solid, the rest lighter; `line` is a daily limit drawn
 * dashed. The top line is the next whole hour, or quarter hour under an hour. The bars stretch to the
 * card's width at a fixed height; the words stay HTML so they keep the app's size.
 */
function Bars({ values, labels, mark = values.length - 1, line, hours }: { values: number[]; labels: string[]; mark?: number; line?: number; hours?: boolean }): ReactNode {
  const W = 640
  const H = 160
  const max = Math.max(...values, line ?? 0, 1)
  const step = max >= 3600 ? 3600 : 900
  const scale = Math.max(step, Math.ceil(max / step) * step)
  const slot = W / Math.max(values.length, 1)
  const bw = Math.max(2, slot * 0.62)
  const small = { fontSize: 11.5, color: 'var(--navy-70)', whiteSpace: 'nowrap' } as const
  return (
    <div>
      <div style={{ ...small, marginBottom: 4 }}>{words(scale)}</div>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" width="100%" height={H} role="img" aria-label={labels.filter(Boolean).join(', ')} style={{ display: 'block', borderTop: '1px solid var(--line)' }}>
        {values.map((v, i) => {
          const h = v > 0 ? Math.max(3, (H * v) / scale) : 2
          return (
            <rect key={i} x={i * slot + (slot - bw) / 2} y={H - h} width={bw} height={h} rx={2}
              fill={v <= 0 ? 'var(--navy-15)' : i === mark ? 'var(--primary)' : 'color-mix(in srgb, var(--primary) 50%, transparent)'}>
              <title>{`${labels[i] || ''} ${words(v)}`.trim()}</title>
            </rect>
          )
        })}
        {line !== undefined && <line x1={0} x2={W} y1={H - (H * line) / scale} y2={H - (H * line) / scale} stroke="var(--navy)" strokeWidth={1.5} strokeDasharray="6 5" vectorEffect="non-scaling-stroke" />}
      </svg>
      <div style={{ display: 'grid', gridTemplateColumns: `repeat(${hours ? 4 : Math.max(values.length, 1)}, 1fr)`, marginTop: 6 }}>
        {(hours ? HOUR_LABELS : labels).map((l, i) => <span key={i} style={{ ...small, textAlign: hours ? 'left' : 'center', overflow: 'visible' }}>{l}</span>)}
      </div>
    </div>
  )
}

/**
 * Screen time from CleanMyTV Home: the totals Home keeps on the TV, bigger than the TV's own dashboard,
 * with each app's times opened and longest stretch, a chart per app, and a CSV of any days.
 */
/** A colour, or the default when it's empty. */
function ColourPick({ label, value, shown, standard, onChange }: { label: string; value: string; shown: string; standard: string; onChange: (v: string) => void }): ReactNode {
  return (
    <div className="row" style={{ gap: 10, margin: '10px 0' }}>
      <span style={{ width: 110 }}>{label}</span>
      <input type="color" value={value || shown} aria-label={label} onChange={(e) => onChange(e.target.value.toUpperCase())} />
      {value ? <button className="btn small ghost" onClick={() => onChange('')}>{t('Use the default')}</button> : <span className="small muted">{standard}</span>}
    </div>
  )
}

export function ScreenTimeTab({ cfg, set, apps }: { cfg: HomeConfig; set: SetFn; apps: TvApp[] }): ReactNode {
  const [data, setData] = useState<ScreenTimeData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [period, setPeriod] = useState<Period>('7')
  const [customFrom, setCustomFrom] = useState('')
  const [customTo, setCustomTo] = useState('')
  const [picked, setPicked] = useState<string | null>(null)
  const [clearing, setClearing] = useState(false)
  const [note, setNote] = useState<string | null>(null)
  const on = cfg.sections.includes('screentime')

  const load = (): void => {
    setError(null)
    window.cytv.screenTime().then(setData, (e) => setError(errorText(e)))
  }
  useEffect(load, [on])

  const label = (p: string): string => data?.names[p] ?? apps.find((a) => a.pkg === p)?.label ?? p
  const today = data?.today || new Date().toISOString().slice(0, 10)
  const first = data?.first || today
  const from = period === 'custom' ? (customFrom && customFrom >= first ? customFrom : addDays(today, -13) < first ? first : addDays(today, -13)) : addDays(today, 1 - Number(period))
  const to = period === 'custom' && customTo && customTo >= from && customTo <= today ? customTo : today
  const view = useMemo(() => {
    if (!data) return null
    const cur = fill(data.days, from, to)
    const n = cur.length
    const before = fill(data.days, addDays(from, -n), addDays(from, -1))
    const seen = (d: ScreenDay): boolean => d.day >= first
    const counted = Math.max(1, cur.filter(seen).length)
    const prevSeen = before.filter(seen).length
    const sum = cur.reduce((a, d) => a + total(d), 0)
    const prev = before.reduce((a, d) => a + total(d), 0)
    const hours = Array.from({ length: 24 }, (_, h) => cur.reduce((a, d) => a + d.hours[h], 0) / counted)
    const list = appTotals(daysBetween(cur, from, to)).filter(([, s]) => s >= 60)
    return { cur, n, sum, avg: sum / counted, prevAvg: prevSeen ? prev / prevSeen : null, yesterday: before.length ? total(before[before.length - 1]) : null, hours, list }
  }, [data, from, to, first])

  if (error)
    return (
      <div className="card">
        <h2>{t('Screen time')}</h2>
        <p className="muted">{error}</p>
        <button className="btn small" onClick={load}>{t('Try again')}</button>
      </div>
    )
  if (!data || !view) return <div className="card"><Busy text={t('Reading screen time from the TV')} /></div>
  if (!data.allowed)
    return (
      <div className="card">
        <h2>{t('Screen time')}</h2>
        <p className="muted">{t('CleanMyTV Home counts how long each app is on, day by day, and keeps the totals on the TV. It needs usage access, which this app gives while the Screen time section is on.')}</p>
        <button className="btn primary" onClick={() => { set({ sections: ['screentime', ...cfg.sections.filter((s) => s !== 'screentime')] }); setTimeout(load, 1500) }}>
          {t('Turn on Screen time')}
        </button>
      </div>
    )

  const app = picked && view.list.some(([p]) => p === picked) ? picked : null
  const stats = (p: string): { sessions: number; longest: number; last: number } =>
    view.cur.reduce((a, d) => { const s = d.stats[p]; return s ? { sessions: a.sessions + s.sessions, longest: Math.max(a.longest, s.longest), last: Math.max(a.last, s.last) } : a }, { sessions: 0, longest: 0, last: 0 })
  const lastWords = (sec: number): string => (sec ? new Date(sec * 1000).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '–')
  const limitOf = (p: string): number | undefined => cfg.limits[p]
  const periods: [Period, string][] = [['1', t('Today')], ['7', t('Last 7 days')], ['30', t('Last 30 days')], ['custom', t('Pick the days')]]

  return (
    <>
      <div className="card">
        <div className="row" style={{ justifyContent: 'space-between', flexWrap: 'wrap', gap: 10 }}>
          <h2 style={{ margin: 0 }}>{t('Screen time')}</h2>
          <div className="seg" role="tablist">
            {periods.map(([id, l]) => (
              <button key={id} role="tab" aria-selected={period === id} className={period === id ? 'on' : ''} onClick={() => setPeriod(id)}>{l}</button>
            ))}
          </div>
        </div>
        {period === 'custom' && (
          <div className="row" style={{ marginTop: 12, gap: 10, flexWrap: 'wrap' }}>
            <label className="row" style={{ gap: 6 }}>{t('From')} <input type="date" min={first} max={to} value={from} onChange={(e) => setCustomFrom(e.target.value)} /></label>
            <label className="row" style={{ gap: 6 }}>{t('To')} <input type="date" min={from} max={today} value={to} onChange={(e) => setCustomTo(e.target.value)} /></label>
            <span className="muted small">{t('Home has days back to {date}.', { date: shortDate(first) })}</span>
          </div>
        )}
        <div style={{ marginTop: 16 }}>
          <div style={{ fontSize: 34, fontWeight: 700, lineHeight: 1.1 }}>{view.n === 1 ? words(view.sum) : t('{time} a day', { time: words(view.avg) })}</div>
          <div className="muted" style={{ marginTop: 4 }}>
            {view.n === 1
              ? view.yesterday !== null && (to === today ? t('Yesterday: {time}', { time: words(view.yesterday) }) : t('The day before: {time}', { time: words(view.yesterday) }))
              : [t('{time} in all', { time: words(view.sum) }), view.prevAvg !== null ? t('the {n} days before: {time} a day', { n: view.n, time: words(view.prevAvg) }) : null].filter(Boolean).join(' · ')}
          </div>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h2>{view.n === 1 ? t('By hour') : t('By day')}</h2>
          <div style={{ paddingTop: 16 }}>
            {view.n === 1
              ? <Bars values={view.cur[0].hours} labels={[]} hours mark={to === today ? new Date().getHours() : -1} />
              : <Bars values={view.cur.map(total)} labels={dayLabels(view.cur)} />}
          </div>
        </div>
        {view.n > 1 && (
          <div className="card">
            <h2>{t('When the TV is on, on average')}</h2>
            <div style={{ paddingTop: 16 }}><Bars values={view.hours} labels={[]} hours mark={-1} /></div>
          </div>
        )}
      </div>

      {app && (
        <div className="card">
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <h2 style={{ margin: 0 }}>{t('{app} by day', { app: label(app) })}</h2>
            <button className="btn small ghost" onClick={() => setPicked(null)}>{t('Close')}</button>
          </div>
          {limitOf(app) !== undefined && <p className="muted small">{t('The dashed line is its daily limit of {time}.', { time: words(limitOf(app)! * 60) })}</p>}
          <div style={{ paddingTop: 16 }}>
            {(() => {
              const shown = view.n === 1 ? fill(data.days, addDays(to, -6), to) : view.cur
              return <Bars values={shown.map((d) => d.apps[app] ?? 0)} labels={dayLabels(shown)} line={limitOf(app) !== undefined ? limitOf(app)! * 60 : undefined} />
            })()}
          </div>
        </div>
      )}

      <div className="card">
        <h2>{t('Apps')}</h2>
        {view.list.length === 0 ? (
          <p className="muted">{t('Nothing on in these days.')}</p>
        ) : (
          <>
            <p className="muted small">{t('Click an app for its own chart. Daily limits are set under App lock.')}</p>
            <table className="pkgs">
              <thead>
                <tr><th>{t('App')}</th><th>{t('Time')}</th><th>{t('Share')}</th><th>{t('Times opened')}</th><th>{t('Longest')}</th><th>{t('Last on')}</th><th>{t('Daily limit')}</th></tr>
              </thead>
              <tbody>
                {view.list.map(([p, s]) => {
                  const st = stats(p)
                  return (
                    <tr key={p} onClick={() => setPicked(p)} style={{ cursor: 'pointer', fontWeight: p === app ? 700 : undefined }}>
                      <td>{label(p)}</td>
                      <td>{words(s)}</td>
                      <td>{Math.round((100 * s) / Math.max(view.sum, 1))}%</td>
                      <td>{st.sessions || '–'}</td>
                      <td>{st.longest ? words(st.longest) : '–'}</td>
                      <td>{lastWords(st.last)}</td>
                      <td>{limitOf(p) !== undefined ? words(limitOf(p)! * 60) : '–'}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </>
        )}
        <div className="row" style={{ marginTop: 14, gap: 8, flexWrap: 'wrap' }}>
          <button className="btn small" onClick={() => window.cytv.saveScreenTimeCsv(from, to).then((p) => setNote(p ? t('Saved to {path}', { path: p }) : null), (e) => setNote(errorText(e)))}>
            {t('Save as CSV')}
          </button>
          <button className="btn small ghost" onClick={load}>{t('Read again')}</button>
          <button className="btn small ghost" onClick={() => setClearing(true)}>{t('Clear the history')}</button>
        </div>
        {note && <p className="muted small" style={{ marginTop: 8 }}>{note}</p>}
        <p className="muted small" style={{ marginTop: 8 }}>{t('The totals stay on the TV. This app only reads them when this tab is open.')}</p>
      </div>
      <div className="card">
        <h2>{t('Goal and PIN')}</h2>
        <label className="field">
          <span>{t('Daily goal')}</span>
          <span className="hint">{t('Once the TV has been on this long in a day, a note says so over whatever is on, once a day.')}</span>
          <select value={cfg.screen_goal} onChange={(e) => set({ screen_goal: Number(e.target.value) })}>
            {GOALS.map((m) => <option key={m} value={m}>{m ? words(m * 60) : t('No goal')}</option>)}
          </select>
        </label>
        <Toggle label={t('Ask for the PIN to open Screen time on the TV')}
          hint={cfg.lock_on && cfg.lock_pin_set ? t('Changing a daily limit there always asks for it.') : t('Needs App lock on, with a PIN.')}
          checked={cfg.lock_screentime} disabled={!(cfg.lock_on && cfg.lock_pin_set)} onChange={(v) => set({ lock_screentime: v })} />
      </div>
      <div className="card">
        <h2>{t('The card on Home')}</h2>
        <p className="muted small">{t("How the Screen time card looks on the TV's Home screen. The picture behind it here stands in for your wallpaper.")}</p>
        <div style={{ padding: 14, borderRadius: 10, overflowX: 'auto', background: 'linear-gradient(135deg, #47606F, #B88A5E 60%, #E7D3A8)', margin: '6px 0 14px' }}>
          <ScreenTimeCard cfg={cfg} labels={(view.list.length ? view.list.map(([p]) => label(p)) : apps.map((a) => a.label)).slice(0, 3)} />
        </div>
        <Slider label={t('Background opacity')} hint={t('0 lets the wallpaper through completely, 100 is solid.')} value={cfg.st_opacity} min={0} max={100} step={5} unit="%" onChange={(v) => set({ st_opacity: v }, 250)} />
        <ColourPick label={t('Background')} value={cfg.st_bg} shown="#000000" standard={t('Black')} onChange={(v) => set({ st_bg: v }, 300)} />
        <ColourPick label={t('Text')} value={cfg.st_text} shown="#F0EBE5" standard={t('Light or dark to suit the background')} onChange={(v) => set({ st_text: v }, 300)} />
        <ColourPick label={t('Bars')} value={cfg.st_bars} shown={cfg.layout === 'bau' ? '#F2EEE3' : cfg.accent} standard={cfg.layout === 'bau' ? t("Bau's cream") : t('The accent colour')} onChange={(v) => set({ st_bars: v }, 300)} />
      </div>
      {clearing && (
        <Confirm title={t('Clear the screen time history?')} confirmLabel={t('Clear it')} onCancel={() => setClearing(false)}
          onConfirm={() => { setClearing(false); window.cytv.screenTimeClear().then(setData, (e) => setError(errorText(e))) }}>
          {t('Home forgets the days it saved. Android keeps about a week of its own, so the last few days come back.')}
        </Confirm>
      )}
    </>
  )
}
