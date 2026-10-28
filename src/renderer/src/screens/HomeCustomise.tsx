import { useEffect, useRef, useState, type ReactNode } from 'react'
import type { ApplyResult, HomeConfig } from '@shared/api'
import { Busy, ResultNotice, Toggle, errorText } from '../components/ui'

const ACCENTS = ['#FF3000', '#2EC4B6', '#FFB23C', '#7C5CFF', '#3DDC84', '#FFFFFF']

const FOCUS: [HomeConfig['focus'], string, string][] = [
  ['glow', 'Glow', 'A soft glow in the accent colour, and the tile grows a little.'],
  ['outline', 'Outline', 'A crisp border in the accent colour, and the tile grows a little.'],
  ['none', 'None', 'No glow, no border, no zoom. Only the tile colour changes.']
]

/** Customise for CleanMyTV Home: each change goes to the TV as soon as it's made, no Apply. */
export function HomeCustomise({ onDone }: { onDone: () => void }): ReactNode {
  const [cfg, setCfg] = useState<HomeConfig | null>(null)
  const [result, setResult] = useState<ApplyResult | null>(null)
  const [saving, setSaving] = useState(false)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    window.cytv.readHomeConfig().then(setCfg, (e) => setResult({ ok: false, summary: errorText(e), warnings: [] }))
  }, [])

  async function send(change: Partial<HomeConfig>): Promise<void> {
    setSaving(true)
    try {
      setCfg(await window.cytv.setHomeConfig(change))
      onDone()
    } catch (e) {
      setResult({ ok: false, summary: errorText(e), warnings: [] })
    } finally {
      setSaving(false)
    }
  }

  // Show a change at once, then send it; dragging the colour picker sends only where it stops.
  function change(c: Partial<HomeConfig>, wait = 0): void {
    setCfg((prev) => (prev ? { ...prev, ...c } : prev))
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => send(c), wait)
  }

  return (
    <div className="content">
      <h1>Customise</h1>
      <p className="lede">
        CleanMyTV Home is Home on this TV. Changes here show on the TV straight away and stay after a reboot. It's a preview, so
        there's less to change than with CleanYourTV for now.
      </p>
      <ResultNotice result={result} onDismiss={() => setResult(null)} />
      {!cfg ? (
        !result && <Busy text="Reading the settings from the TV" />
      ) : (
        <>
          <div className="card">
            <h2>Accent colour</h2>
            <p className="muted">Used for the focus glow or outline and the music bars.</p>
            <div className="row">
              {ACCENTS.map((c) => (
                <button
                  key={c}
                  className="swatch"
                  style={{ background: c, width: 32, height: 32, borderRadius: 8, border: cfg.accent.toUpperCase() === c ? '3px solid var(--navy)' : '1px solid var(--line)' }}
                  aria-label={c}
                  title={c}
                  onClick={() => change({ accent: c })}
                />
              ))}
              <input type="color" value={cfg.accent} onChange={(e) => change({ accent: e.target.value.toUpperCase() }, 400)} aria-label="Pick any colour" />
            </div>
          </div>

          <div className="card">
            <h2>Focus</h2>
            <p className="muted">How the tile under the remote's cursor stands out.</p>
            <div className="seg">
              {FOCUS.map(([id, label]) => (
                <button key={id} className={cfg.focus === id ? 'on' : ''} onClick={() => change({ focus: id })}>
                  {label}
                </button>
              ))}
            </div>
            <p className="small muted" style={{ marginTop: 8 }}>
              {FOCUS.find(([id]) => id === cfg.focus)?.[2]}
            </p>
          </div>

          <div className="card">
            <h2>Music</h2>
            <Toggle
              label="Equaliser bars while music plays"
              hint="Bars along the bottom of the home screen that move with whatever the TV is playing. Some TVs only give silence to apps; then the bars stay flat."
              checked={cfg.visualiser}
              onChange={(v) => change({ visualiser: v })}
            />
          </div>
          <p className="small muted">{saving ? 'Sending to the TV…' : 'Saved on the TV.'}</p>
        </>
      )}
    </div>
  )
}
