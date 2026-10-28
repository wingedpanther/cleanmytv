import type { ImageFile } from '@shared/api'

export const TV_W = 1920
export const TV_H = 1080
/** Upstream themes/README.md: keep wallpapers under about 300 KB; a 1 GB TV decodes them on every Home start. */
export const MAX_BYTES = 300 * 1024

export interface PreparedImage extends ImageFile {
  dataUrl: string
  bytes: number
}

/** Centre-crops to 1920x1080 and exports a JPEG, lowering quality until it fits under 300 KB. */
export async function prepareWallpaper(file: File, name?: string): Promise<PreparedImage> {
  const bitmap = await createImageBitmap(file)
  const canvas = document.createElement('canvas')
  canvas.width = TV_W
  canvas.height = TV_H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('This computer could not draw the picture.')
  const scale = Math.max(TV_W / bitmap.width, TV_H / bitmap.height)
  const w = bitmap.width * scale
  const h = bitmap.height * scale
  ctx.fillStyle = '#000'
  ctx.fillRect(0, 0, TV_W, TV_H)
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(bitmap, (TV_W - w) / 2, (TV_H - h) / 2, w, h)
  bitmap.close()

  let quality = 0.88
  let dataUrl = canvas.toDataURL('image/jpeg', quality)
  while (bytesOf(dataUrl) > MAX_BYTES && quality > 0.3) {
    quality -= 0.06
    dataUrl = canvas.toDataURL('image/jpeg', quality)
  }
  const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
  return { name: safeName(name ?? file.name), base64, dataUrl, bytes: bytesOf(dataUrl) }
}

/** Letters, digits, underscores, dots and hyphens only (theme.sh refuses anything else), always .jpg. */
export function safeName(name: string): string {
  const stem = name.replace(/\.[^.]*$/, '').replace(/[^A-Za-z0-9_.-]+/g, '_').replace(/^[._-]+|_+$/g, '')
  return `${(stem || 'wallpaper').slice(0, 60)}.jpg`
}

export function uniqueNames(images: PreparedImage[]): PreparedImage[] {
  const seen = new Set<string>()
  return images.map((img) => {
    let n = img.name
    for (let i = 2; seen.has(n); i++) n = img.name.replace(/\.jpg$/, `-${i}.jpg`)
    seen.add(n)
    return { ...img, name: n }
  })
}

function bytesOf(dataUrl: string): number {
  const b64 = dataUrl.length - dataUrl.indexOf(',') - 1
  return Math.floor((b64 * 3) / 4)
}

export function dataUrlToImage(dataUrl: string, name: string): PreparedImage {
  const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
  return { name: safeName(name), base64, dataUrl, bytes: bytesOf(dataUrl) }
}

/** A small JPEG of a screenshot, for the TV list and the sidebar. */
export async function thumbnail(dataUrl: string, w = 320, h = 180): Promise<string> {
  // An <img>, not fetch(): the page's CSP allows data: images but no fetching.
  const img = new Image()
  img.src = dataUrl
  await img.decode()
  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('This computer could not draw the picture.')
  ctx.imageSmoothingQuality = 'high'
  ctx.drawImage(img, 0, 0, w, h)
  return canvas.toDataURL('image/jpeg', 0.75)
}
