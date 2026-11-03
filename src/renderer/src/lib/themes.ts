// Draws a built-in theme's wallpaper on this computer, so the app ships no pictures for them.
// Every random choice comes from the theme's seed: the same theme draws the same picture each time.
import type { ThemeArt, ThemePaint } from '@shared/themes'
import { TV_H, TV_W, canvasJpeg, type PreparedImage } from './image'

type Ctx = CanvasRenderingContext2D
type Rnd = () => number

export function paintWallpaper(p: ThemePaint, name: string): PreparedImage {
  return canvasJpeg(drawWallpaper(p, TV_W, TV_H), name)
}

/** The wallpaper at any size: the app's swatches use a small one. */
export function drawWallpaper(p: ThemePaint, w: number, h: number): HTMLCanvasElement {
  const canvas = layer(w, h)
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('This computer could not draw the picture.')
  const base = ctx.createLinearGradient(0, 0, w, h)
  base.addColorStop(0, p.base[0])
  base.addColorStop(1, p.base[1])
  ctx.fillStyle = base
  ctx.fillRect(0, 0, w, h)
  for (const g of p.glows) {
    const x = g.x * w
    const y = g.y * h
    const glow = ctx.createRadialGradient(x, y, 0, x, y, g.r * w)
    glow.addColorStop(0, rgba(g.colour, g.a))
    glow.addColorStop(0.5, rgba(g.colour, g.a * 0.35))
    glow.addColorStop(1, rgba(g.colour, 0))
    ctx.fillStyle = glow
    ctx.fillRect(0, 0, w, h)
  }
  if (p.art) ART[p.art.kind](ctx, w, h, p.art, rng(p.art.seed), p.base[0])
  shade(ctx, w, h)
  // A little grain, so dark gradients don't band on the TV.
  const img = ctx.getImageData(0, 0, w, h)
  const d = img.data
  const r = rng(7)
  for (let i = 0; i < d.length; i += 4) {
    const n = (r() - 0.5) * 5
    d[i] += n
    d[i + 1] += n
    d[i + 2] += n
  }
  ctx.putImageData(img, 0, 0)
  return canvas
}

const ART: Record<ThemeArt['kind'], (ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd, dark: string) => void> = {
  aurora, ridges, dunes, flow, stars, contours, bokeh, horizon, rings
}

/** Curtains of light across the top third, softened so they read as light, not stripes. */
function aurora(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd): void {
  const s = w / TV_W
  const off = layer(w, h)
  const o = off.getContext('2d') as Ctx
  o.globalCompositeOperation = 'lighter'
  const step = Math.max(1, Math.round(2 * s))
  a.colours.slice(0, 3).forEach((col, i) => {
    const base = h * (0.2 + 0.11 * i + (r() - 0.5) * 0.05)
    const [f1, f2, f3] = [0.6 + r() * 0.7, 1.6 + r() * 2, 2.5 + r() * 3].map((f) => (f * Math.PI * 2) / w)
    const [p1, p2, p3] = [r(), r(), r()].map((p) => p * Math.PI * 2)
    const amp = h * (0.05 + r() * 0.05)
    for (let x = 0; x < w; x += step) {
      const y = base + amp * Math.sin(x * f1 + p1) + amp * 0.4 * Math.sin(x * f2 + p2)
      const lit = Math.pow(0.5 + 0.5 * Math.sin(x * f3 + p3), 2) * (0.55 + 0.45 * Math.sin(x * f1 * 0.5 + p2))
      const tall = h * (0.1 + 0.2 * lit)
      const g = o.createLinearGradient(0, y - tall, 0, y + h * 0.02)
      g.addColorStop(0, rgba(col, 0))
      g.addColorStop(0.78, rgba(col, 0.04 + 0.17 * lit))
      g.addColorStop(1, rgba(col, 0))
      o.fillStyle = g
      o.fillRect(x, y - tall, step, tall + h * 0.02)
    }
  })
  ctx.globalCompositeOperation = 'screen'
  ctx.filter = `blur(${Math.max(1, Math.round(8 * s))}px)`
  ctx.drawImage(off, 0, 0)
  ctx.filter = 'none'
  ctx.globalAlpha = 0.45
  ctx.drawImage(off, 0, 0)
  ctx.globalAlpha = 1
  ctx.globalCompositeOperation = 'source-over'
}

/** Mountain ranges fading into mist, nearest at the bottom. */
function ridges(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd, dark: string): void {
  const s = w / TV_W
  const n = 5
  for (let i = 0; i < n; i++) {
    const noise = fbm(r, 4)
    const top = h * (0.4 + i * 0.085)
    const rise = h * (0.15 - i * 0.018)
    const freq = 2.2 + i * 0.9
    const near = i / (n - 1)
    const body = mix(a.colours[0], dark, 0.55 + near * 0.4)
    const path = new Path2D()
    path.moveTo(0, h)
    for (let x = 0; x <= w; x += Math.max(2, 4 * s)) path.lineTo(x, top - rise * noise((x / w) * freq))
    path.lineTo(w, h)
    path.closePath()
    const fill = ctx.createLinearGradient(0, top - rise, 0, h)
    fill.addColorStop(0, body)
    fill.addColorStop(1, mix(body, dark, 0.6))
    ctx.fillStyle = fill
    ctx.fill(path)
    // Mist between ranges.
    const mist = ctx.createLinearGradient(0, top - rise * 0.4, 0, top + h * 0.12)
    mist.addColorStop(0, rgba(a.colours[1] ?? a.colours[0], 0))
    mist.addColorStop(1, rgba(a.colours[1] ?? a.colours[0], 0.07 * (1 - near)))
    ctx.fillStyle = mist
    ctx.fillRect(0, top - rise, w, h)
  }
}

/** Smooth dunes with a lit crest. */
function dunes(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd, dark: string): void {
  const s = w / TV_W
  const n = 4
  for (let i = 0; i < n; i++) {
    const near = i / (n - 1)
    const top = h * (0.5 + i * 0.11)
    const amp = h * (0.07 - i * 0.008)
    const [f1, f2] = [0.8 + r() * 0.8, 1.8 + r() * 1.2].map((f) => (f * Math.PI * 2) / w)
    const [p1, p2] = [r() * 6.3, r() * 6.3]
    const y = (x: number): number => top + amp * Math.sin(x * f1 + p1) + amp * 0.35 * Math.sin(x * f2 + p2)
    const path = new Path2D()
    path.moveTo(0, h)
    for (let x = 0; x <= w; x += Math.max(2, 4 * s)) path.lineTo(x, y(x))
    path.lineTo(w, h)
    path.closePath()
    const body = mix(a.colours[0], dark, 0.5 + near * 0.42)
    const fill = ctx.createLinearGradient(0, top - amp, 0, top + h * 0.25)
    fill.addColorStop(0, body)
    fill.addColorStop(1, mix(body, dark, 0.7))
    ctx.fillStyle = fill
    ctx.fill(path)
    ctx.strokeStyle = rgba(a.colours[1] ?? a.colours[0], 0.28 * (1 - near * 0.6))
    ctx.lineWidth = 1.5 * s
    ctx.beginPath()
    for (let x = 0; x <= w; x += Math.max(2, 4 * s)) ctx.lineTo(x, y(x))
    ctx.stroke()
  }
}

/** A bundle of fine lines bending together like silk. */
function flow(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd): void {
  const s = w / TV_W
  const n = 70
  const [f1, f2] = [0.6 + r() * 0.4, 1.4 + r() * 0.8].map((f) => (f * Math.PI * 2) / w)
  const [p1, p2] = [r() * 6.3, r() * 6.3]
  const amp = h * (0.1 + r() * 0.04)
  ctx.globalCompositeOperation = 'screen'
  ctx.lineWidth = 1.4 * s
  for (let i = 0; i < n; i++) {
    const t = i / (n - 1)
    ctx.strokeStyle = rgba(mix(a.colours[0], a.colours[1] ?? a.colours[0], t), 0.08 + 0.3 * Math.sin(Math.PI * t))
    ctx.beginPath()
    for (let x = -10; x <= w + 10; x += Math.max(2, 6 * s)) {
      // The bundle fans out to the right, so the lines never cross into one streak.
      const fan = 0.35 + 0.65 * (x / w)
      const y = h * (0.22 + 0.2 * t * fan) + amp * (1 + t * 0.6) * Math.sin(x * f1 + p1 + t * 0.5) + amp * 0.35 * Math.sin(x * f2 + p2 + t * 0.9)
      ctx.lineTo(x, y)
    }
    ctx.stroke()
  }
  ctx.globalCompositeOperation = 'source-over'
}

/** Stars over a soft nebula; variant 1 adds a planet's edge in the corner. */
function stars(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd, dark: string): void {
  const s = w / TV_W
  const off = layer(w, h)
  const o = off.getContext('2d') as Ctx
  for (let i = 0; i < 7; i++) {
    const t = r()
    const x = w * (0.1 + 0.8 * t)
    const y = h * (0.75 - 0.6 * t + (r() - 0.5) * 0.25)
    const rad = w * (0.08 + r() * 0.12)
    const g = o.createRadialGradient(x, y, 0, x, y, rad)
    g.addColorStop(0, rgba(a.colours[i % a.colours.length], 0.12 + r() * 0.08))
    g.addColorStop(1, rgba(a.colours[i % a.colours.length], 0))
    o.fillStyle = g
    o.fillRect(0, 0, w, h)
  }
  ctx.globalCompositeOperation = 'screen'
  ctx.filter = `blur(${Math.max(1, Math.round(30 * s))}px)`
  ctx.drawImage(off, 0, 0)
  ctx.filter = 'none'
  ctx.globalCompositeOperation = 'source-over'
  const count = Math.round(1100 * s * s)
  for (let i = 0; i < count; i++) {
    const x = r() * w
    const y = Math.pow(r(), 1.3) * h
    const size = (Math.pow(r(), 6) * 2.2 + 0.45) * s
    ctx.fillStyle = `rgba(255, 255, 255, ${0.2 + 0.65 * r()})`
    ctx.beginPath()
    ctx.arc(x, y, size, 0, Math.PI * 2)
    ctx.fill()
  }
  for (let i = 0; i < 10; i++) {
    const x = r() * w
    const y = Math.pow(r(), 1.5) * h * 0.7
    const g = ctx.createRadialGradient(x, y, 0, x, y, 14 * s)
    g.addColorStop(0, 'rgba(255, 255, 255, 0.85)')
    g.addColorStop(0.15, rgba(a.colours[1] ?? a.colours[0], 0.35))
    g.addColorStop(1, 'rgba(255, 255, 255, 0)')
    ctx.fillStyle = g
    ctx.fillRect(x - 14 * s, y - 14 * s, 28 * s, 28 * s)
  }
  if (a.variant === 1) {
    // Low in the corner, under the clock's side, clear of the rows of apps.
    const cx = w * 1.02
    const cy = h * 0.02
    const rad = h * 0.3
    ctx.fillStyle = mix(dark, '#000000', 0.3)
    ctx.beginPath()
    ctx.arc(cx, cy, rad, 0, Math.PI * 2)
    ctx.fill()
    const rim = ctx.createRadialGradient(cx, cy, rad * 0.94, cx, cy, rad * 1.06)
    rim.addColorStop(0, rgba(a.colours[0], 0))
    rim.addColorStop(0.5, rgba(a.colours[0], 0.45))
    rim.addColorStop(1, rgba(a.colours[0], 0))
    ctx.fillStyle = rim
    ctx.fillRect(0, 0, w, h)
  }
}

/** A topographic map: lines of equal height over a few hills and hollows. */
function contours(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd): void {
  const s = w / TV_W
  const bumps = Array.from({ length: 7 }, () => ({ x: r() * w, y: r() * h, sd: w * (0.1 + r() * 0.2), k: r() < 0.3 ? -1 : 1 }))
  const field = (x: number, y: number): number =>
    bumps.reduce((v, b) => v + b.k * Math.exp(-((x - b.x) ** 2 + (y - b.y) ** 2) / (2 * b.sd * b.sd)), 0)
  const cell = Math.max(3, Math.round(8 * s))
  const nx = Math.ceil(w / cell) + 1
  const ny = Math.ceil(h / cell) + 1
  const v = new Float32Array(nx * ny)
  let lo = Infinity
  let hi = -Infinity
  for (let j = 0; j < ny; j++) {
    for (let i = 0; i < nx; i++) {
      const f = field(i * cell, j * cell)
      v[j * nx + i] = f
      lo = Math.min(lo, f)
      hi = Math.max(hi, f)
    }
  }
  const levels = 22
  for (let l = 1; l < levels; l++) {
    const at = lo + ((hi - lo) * l) / levels
    const major = l % 5 === 0
    ctx.strokeStyle = rgba(major ? a.colours[0] : (a.colours[1] ?? a.colours[0]), major ? 0.5 : 0.22)
    ctx.lineWidth = (major ? 2 : 1.2) * s
    ctx.beginPath()
    for (let j = 0; j < ny - 1; j++) {
      for (let i = 0; i < nx - 1; i++) {
        const tl = v[j * nx + i]
        const tr = v[j * nx + i + 1]
        const br = v[(j + 1) * nx + i + 1]
        const bl = v[(j + 1) * nx + i]
        const idx = (tl > at ? 8 : 0) | (tr > at ? 4 : 0) | (br > at ? 2 : 0) | (bl > at ? 1 : 0)
        if (idx === 0 || idx === 15) continue
        const x0 = i * cell
        const y0 = j * cell
        const cut = (p: number, q: number): number => (at - p) / (q - p)
        const T: [number, number] = [x0 + cut(tl, tr) * cell, y0]
        const R: [number, number] = [x0 + cell, y0 + cut(tr, br) * cell]
        const B: [number, number] = [x0 + cut(bl, br) * cell, y0 + cell]
        const L: [number, number] = [x0, y0 + cut(tl, bl) * cell]
        for (const [p, q] of SEGMENTS[idx].map(([m, n]) => [{ T, R, B, L }[m], { T, R, B, L }[n]])) {
          ctx.moveTo(p[0], p[1])
          ctx.lineTo(q[0], q[1])
        }
      }
    }
    ctx.stroke()
  }
}

type Edge = 'T' | 'R' | 'B' | 'L'
// Marching squares: corners top-left 8, top-right 4, bottom-right 2, bottom-left 1.
const SEGMENTS: Edge[][][] = [
  [], [['L', 'B']], [['B', 'R']], [['L', 'R']], [['T', 'R']], [['L', 'B'], ['T', 'R']], [['T', 'B']], [['L', 'T']],
  [['L', 'T']], [['T', 'B']], [['L', 'T'], ['B', 'R']], [['T', 'R']], [['L', 'R']], [['B', 'R']], [['L', 'B']], []
]

/** Out-of-focus lights, far ones softer. */
function bokeh(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd): void {
  const s = w / TV_W
  for (const [count, blur, big] of [[40, 14, 0.09], [26, 3, 0.05]] as const) {
    const off = layer(w, h)
    const o = off.getContext('2d') as Ctx
    for (let i = 0; i < count; i++) {
      const rad = w * (0.008 + Math.pow(r(), 2) * big)
      const x = r() * w
      const y = Math.pow(r(), 1.25) * h * 0.95
      const col = a.colours[i % a.colours.length]
      const alpha = 0.05 + r() * 0.12
      o.fillStyle = rgba(col, alpha)
      o.strokeStyle = rgba(col, alpha * 1.8)
      o.lineWidth = 1.5 * s
      o.beginPath()
      o.arc(x, y, rad, 0, Math.PI * 2)
      o.fill()
      o.stroke()
    }
    ctx.globalCompositeOperation = 'screen'
    ctx.filter = `blur(${Math.max(1, Math.round(blur * s))}px)`
    ctx.drawImage(off, 0, 0)
    ctx.filter = 'none'
    ctx.globalCompositeOperation = 'source-over'
  }
}

/** A striped sun on the horizon over a grid floor running off to the distance. */
function horizon(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd, dark: string): void {
  const s = w / TV_W
  const hy = h * 0.56
  const sx = w * (0.62 + r() * 0.16)
  const sr = h * 0.2
  const sun = layer(w, h)
  const o = sun.getContext('2d') as Ctx
  const g = o.createLinearGradient(0, hy - sr, 0, hy)
  g.addColorStop(0, a.colours[2] ?? a.colours[1] ?? a.colours[0])
  g.addColorStop(1, a.colours[0])
  o.fillStyle = g
  o.beginPath()
  o.arc(sx, hy, sr, Math.PI, 0)
  o.fill()
  o.globalCompositeOperation = 'destination-out'
  for (let k = 0; k < 6; k++) {
    const y = hy - sr * 0.5 + k * sr * 0.09
    o.fillRect(sx - sr, y, sr * 2, sr * (0.012 + k * 0.012))
  }
  ctx.globalAlpha = 0.55
  ctx.drawImage(sun, 0, 0)
  ctx.globalAlpha = 1
  const floor = ctx.createLinearGradient(0, hy, 0, h)
  floor.addColorStop(0, mix(dark, a.colours[0], 0.12))
  floor.addColorStop(1, dark)
  ctx.fillStyle = floor
  ctx.fillRect(0, hy, w, h - hy)
  const line = ctx.createLinearGradient(0, hy - 6 * s, 0, hy + 6 * s)
  line.addColorStop(0, rgba(a.colours[0], 0))
  line.addColorStop(0.5, rgba(a.colours[0], 0.6))
  line.addColorStop(1, rgba(a.colours[0], 0))
  ctx.fillStyle = line
  ctx.fillRect(0, hy - 6 * s, w, 12 * s)
  ctx.lineWidth = 1.2 * s
  for (let k = 1; k <= 16; k++) {
    const t = k / 16
    const y = hy + (h - hy) * t * t
    ctx.strokeStyle = rgba(a.colours[0], 0.06 + 0.22 * t)
    ctx.beginPath()
    ctx.moveTo(0, y)
    ctx.lineTo(w, y)
    ctx.stroke()
  }
  for (let j = -24; j <= 24; j++) {
    ctx.strokeStyle = rgba(a.colours[0], 0.14)
    ctx.beginPath()
    ctx.moveTo(sx + j * w * 0.006, hy)
    ctx.lineTo(sx + j * w * 0.1, h)
    ctx.stroke()
  }
}

/** Fine circles from a point off the screen, a few picked out in the accent. */
function rings(ctx: Ctx, w: number, h: number, a: ThemeArt, r: Rnd): void {
  const s = w / TV_W
  const corner = a.variant === 1
  const cx = corner ? w * 0.08 : w * 0.88
  const cy = corner ? h * 1.05 : -h * 0.12
  for (let i = 0; i < 40; i++) {
    const rad = h * (0.12 + i * 0.045)
    const lit = i % 6 === 3
    ctx.strokeStyle = lit ? rgba(a.colours[0], 0.6) : rgba(a.colours[1] ?? '#FFFFFF', 0.12 + 0.07 * Math.sin(i * 0.7))
    ctx.lineWidth = (lit ? 2.5 : 1.2) * s
    ctx.beginPath()
    if (lit) {
      const start = r() * Math.PI * 2
      ctx.arc(cx, cy, rad, start, start + Math.PI * (0.3 + r() * 0.5))
    } else ctx.arc(cx, cy, rad, 0, Math.PI * 2)
    ctx.stroke()
  }
}

/** Darker edges and a darker floor, so the rows of apps stay readable. */
function shade(ctx: Ctx, w: number, h: number): void {
  const v = ctx.createRadialGradient(w / 2, h * 0.45, h * 0.35, w / 2, h * 0.5, w * 0.78)
  v.addColorStop(0, 'rgba(0, 0, 0, 0)')
  v.addColorStop(1, 'rgba(0, 0, 0, 0.4)')
  ctx.fillStyle = v
  ctx.fillRect(0, 0, w, h)
  const floor = ctx.createLinearGradient(0, h * 0.55, 0, h)
  floor.addColorStop(0, 'rgba(0, 0, 0, 0)')
  floor.addColorStop(1, 'rgba(0, 0, 0, 0.3)')
  ctx.fillStyle = floor
  ctx.fillRect(0, h * 0.55, w, h * 0.45)
}

function layer(w: number, h: number): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = w
  c.height = h
  return c
}

/** Mulberry32. */
function rng(seed: number): Rnd {
  let t = seed >>> 0 || 1
  return () => {
    t = (t + 0x6d2b79f5) | 0
    let x = Math.imul(t ^ (t >>> 15), 1 | t)
    x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x
    return ((x ^ (x >>> 14)) >>> 0) / 4294967296
  }
}

/** Smooth 1D noise in 0..1, a few octaves deep. */
function fbm(r: Rnd, octaves: number): (t: number) => number {
  const pts = Array.from({ length: 256 }, r)
  const one = (t: number): number => {
    const i = Math.floor(t)
    const f = t - i
    const u = (1 - Math.cos(f * Math.PI)) / 2
    return pts[i & 255] + (pts[(i + 1) & 255] - pts[i & 255]) * u
  }
  return (t) => {
    let v = 0
    let norm = 0
    for (let k = 0; k < octaves; k++) {
      v += one(t * 2 ** k + k * 17) / 2 ** k
      norm += 1 / 2 ** k
    }
    return v / norm
  }
}

function rgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16)
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255]
}

function rgba(hex: string, a: number): string {
  const [r, g, b] = rgb(hex)
  return `rgba(${r}, ${g}, ${b}, ${a})`
}

/** From a towards b by t, as a hex colour. */
function mix(a: string, b: string, t: number): string {
  const x = rgb(a)
  const y = rgb(b)
  return '#' + x.map((v, i) => Math.round(v + (y[i] - v) * t).toString(16).padStart(2, '0')).join('')
}
