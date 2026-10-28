import type { CSSProperties, ReactNode } from 'react'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent
} from '@dnd-kit/core'
import { SortableContext, arrayMove, rectSortingStrategy, sortableKeyboardCoordinates, useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { TvApp } from '@shared/api'
import { appColour, monogram } from '../lib/apps'

/**
 * Drag tiles to set the Home order. The first `columns` visible tiles are the dock.
 * The parent writes the whole order to dock_apps: the launcher puts those apps first, in that order.
 */
export function AppOrder(props: {
  apps: TvApp[]
  hidden: Set<string>
  columns: number
  /** How many visible tiles are the dock, when that differs from the columns. */
  dockCount?: number
  onOrder: (pkgs: string[]) => void
  onHidden: (pkg: string, hide: boolean) => void
}): ReactNode {
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  )
  const ids = props.apps.map((a) => a.pkg)

  // Dock membership counts visible tiles only, as on the TV.
  const dockSet = new Set<string>()
  for (const a of props.apps) {
    if (dockSet.size >= (props.dockCount ?? props.columns)) break
    if (!props.hidden.has(a.pkg)) dockSet.add(a.pkg)
  }

  function onDragEnd(e: DragEndEvent): void {
    if (!e.over || e.active.id === e.over.id) return
    const from = ids.indexOf(String(e.active.id))
    const to = ids.indexOf(String(e.over.id))
    props.onOrder(arrayMove(ids, from, to))
  }

  function move(i: number, by: number): void {
    const to = i + by
    if (to < 0 || to >= ids.length) return
    props.onOrder(arrayMove(ids, i, to))
  }

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
      <SortableContext items={ids} strategy={rectSortingStrategy}>
        <div className="apps-grid" style={{ '--cols': Math.min(props.columns, 6) } as CSSProperties}>
          {props.apps.map((a, i) => (
            <SortableTile
              key={a.pkg}
              app={a}
              dock={dockSet.has(a.pkg)}
              hidden={props.hidden.has(a.pkg)}
              first={i === 0}
              last={i === props.apps.length - 1}
              onLeft={() => move(i, -1)}
              onRight={() => move(i, 1)}
              onHide={() => props.onHidden(a.pkg, !props.hidden.has(a.pkg))}
            />
          ))}
        </div>
      </SortableContext>
    </DndContext>
  )
}

function SortableTile(props: {
  app: TvApp
  dock: boolean
  hidden: boolean
  first: boolean
  last: boolean
  onLeft: () => void
  onRight: () => void
  onHide: () => void
}): ReactNode {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({ id: props.app.pkg })
  const style: CSSProperties = { transform: CSS.Transform.toString(transform), transition }
  const cls = `app-tile${props.dock ? ' dock' : ''}${props.hidden ? ' hidden-app' : ''}${isDragging ? ' dragging' : ''}`
  return (
    <div ref={setNodeRef} style={style} className={cls} {...attributes} {...listeners} aria-label={`${props.app.label}${props.dock ? ', in the dock' : ''}${props.hidden ? ', hidden' : ''}`}>
      <div className="app-art" style={{ background: appColour(props.app.pkg) }}>
        {monogram(props.app.label)}
      </div>
      <div className="app-label" title={props.app.pkg}>
        {props.app.label}
      </div>
      {/* Buttons stop the pointer so a click doesn't start a drag. */}
      <div className="app-tools" onPointerDown={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
        <button className="icon-btn" onClick={props.onLeft} disabled={props.first} aria-label={`Move ${props.app.label} earlier`}>
          ←
        </button>
        <button className="icon-btn" onClick={props.onHide} aria-label={props.hidden ? `Show ${props.app.label}` : `Hide ${props.app.label}`}>
          {props.hidden ? 'Show' : 'Hide'}
        </button>
        <button className="icon-btn" onClick={props.onRight} disabled={props.last} aria-label={`Move ${props.app.label} later`}>
          →
        </button>
      </div>
    </div>
  )
}
