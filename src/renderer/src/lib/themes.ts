// Draws a built-in theme's wallpaper on this computer, so the app ships no pictures for them.
import type { ThemePaint } from '@shared/themes'
import { TV_H, TV_W, canvasJpeg, type PreparedImage } from './image'

export function paintWallpaper(p: ThemePaint, name: string): PreparedImage {
  const canvas = document.createElement('canvas')
  canvas.width = TV_W
  canvas.height = TV_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('This computer could not draw the picture.')
  const base = ctx.createLinearGradient(0, 0, TV_W, TV_H)
  base.addColorStop(0, p.base[0])
  base.addColorStop(1, p.base[1])
  ctx.fillStyle = base
  ctx.fillRect(0, 0, TV_W, TV_H)
  for (const g of p.glows) {
    const x = g.x * TV_W
    const y = g.y * TV_H
    const glow = ctx.createRadialGradient(x, y, 0, x, y, g.r * TV_W)
    glow.addColorStop(0, rgba(g.colour, g.a))
    glow.addColorStop(0.5, rgba(g.colour, g.a * 0.35))
    glow.addColorStop(1, rgba(g.colour, 0))
    ctx.fillStyle = glow
    ctx.fillRect(0, 0, TV_W, TV_H)
  }
  // A little grain, so dark gradients don't band on the TV.
  const img = ctx.getImageData(0, 0, TV_W, TV_H)
  const d = img.data
  for (let i = 0; i < d.length; i += 4) {
    const n = (Math.random() - 0.5) * 5
    d[i] += n
    d[i + 1] += n
    d[i + 2] += n
  }
  ctx.putImageData(img, 0, 0)
  return canvasJpeg(canvas, name)
}

function rgba(hex: string, a: number): string {
  const n = parseInt(hex.slice(1), 16)
  return `rgba(${(n >> 16) & 255}, ${(n >> 8) & 255}, ${n & 255}, ${a})`
}
