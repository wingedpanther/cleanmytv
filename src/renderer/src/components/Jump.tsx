import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'

export interface JumpItem {
  id: string
  title: string
  /** Where it lives, shown on the right. */
  where: string
  /** Extra words a search should find it by. */
  words: string
}

const ROW = 44

/** ⌘K / Ctrl+K: type a few letters, Enter to go there. After EasyUI's Spotlight Search (MIT). */
export function Jump({ items, onPick, onClose }: { items: JumpItem[]; onPick: (id: string) => void; onClose: () => void }): ReactNode {
  const [q, setQ] = useState('')
  const [hi, setHi] = useState(0)
  const list = useRef<HTMLDivElement>(null)

  const shown = useMemo(() => {
    const terms = q.toLowerCase().split(/\s+/).filter(Boolean)
    if (!terms.length) return items
    // Every word must appear somewhere; a title hit ranks above a keyword hit.
    return items
      .map((it) => {
        const title = it.title.toLowerCase()
        const all = `${title} ${it.where.toLowerCase()} ${it.words}`
        return { it, ok: terms.every((t) => all.includes(t)), score: terms.filter((t) => title.includes(t)).length }
      })
      .filter((x) => x.ok)
      .sort((a, b) => b.score - a.score)
      .map((x) => x.it)
  }, [q, items])

  useEffect(() => setHi(0), [q])
  useEffect(() => {
    const el = list.current
    if (!el) return
    const top = hi * ROW
    if (top < el.scrollTop) el.scrollTop = top
    else if (top + ROW + 12 > el.scrollTop + el.clientHeight) el.scrollTop = top + ROW + 12 - el.clientHeight
  }, [hi])

  function key(e: KeyboardEvent): void {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHi((h) => Math.min(h + 1, shown.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHi((h) => Math.max(h - 1, 0))
    } else if (e.key === 'Enter' && shown[hi]) {
      onPick(shown[hi].id)
    } else if (e.key === 'Escape') {
      onClose()
    }
  }

  return (
    <div className="jump-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="jump" role="dialog" aria-modal="true" aria-label="Jump to">
        <input
          autoFocus
          placeholder="Jump to a step or setting"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={key}
          role="combobox"
          aria-expanded="true"
          aria-controls="jump-list"
          aria-activedescendant={shown[hi] ? `jump-${shown[hi].id}` : undefined}
        />
        <div className="jump-list" id="jump-list" role="listbox" ref={list}>
          {shown.length > 0 && <span className="jump-hl" style={{ transform: `translateY(${hi * ROW}px)` }} />}
          {shown.map((it, i) => (
            <button
              key={it.id}
              id={`jump-${it.id}`}
              role="option"
              aria-selected={i === hi}
              className="jump-item"
              tabIndex={-1}
              onMouseMove={() => setHi(i)}
              onClick={() => onPick(it.id)}
            >
              <b>{it.title}</b>
              <span>{it.where}</span>
            </button>
          ))}
          {shown.length === 0 && <div className="jump-empty">Nothing called “{q}”.</div>}
        </div>
        <div className="jump-foot">
          <span><kbd>↑</kbd> <kbd>↓</kbd> move</span>
          <span><kbd>Enter</kbd> open</span>
          <span><kbd>Esc</kbd> close</span>
        </div>
      </div>
    </div>
  )
}

export const MOD_KEY = /Mac/.test(navigator.platform) ? '⌘' : 'Ctrl+'
