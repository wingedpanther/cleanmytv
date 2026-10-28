// First-party background sources (issue #5). Downloads happen on this computer, in the main process;
// only prepared JPEGs (1920x1080, under 300 KB) ever reach the TV.
import { prepareWallpaper, type PreparedImage } from './image'

const BING_BASE = 'https://www.bing.com'

// The page has no network access (CSP), so downloads go through the main process.
async function get(url: string): Promise<{ ok: boolean; status: number; type: string; bytes: Uint8Array<ArrayBuffer> }> {
  const r = await window.cytv.webGet(url)
  const bin = atob(r.base64)
  const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return { ok: r.status >= 200 && r.status < 300, status: r.status, type: r.contentType.split(';')[0].trim(), bytes }
}

function json<T>(bytes: Uint8Array): T {
  return JSON.parse(new TextDecoder().decode(bytes)) as T
}

export interface BingImage {
  urlbase: string
  copyright: string
  startdate: string // yyyymmdd
}

/** Picks the day's Bing image file URL at 1920x1080. */
export function bingFileUrl(img: BingImage): string {
  return `${BING_BASE}${img.urlbase}_1920x1080.jpg`
}

/** "bing-OHR.OryxCanyon_EN-US1234.jpg" style launcher-safe name from a Bing entry. */
export function bingName(img: BingImage): string {
  const last = img.urlbase.split('/').pop() ?? 'bing'
  const stem = last.replace(/[^A-Za-z0-9_.-]+/g, '-').replace(/^[.-]+|[.-]+$/g, '').slice(0, 50)
  return `bing-${stem || 'daily'}.jpg`
}

async function toPrepared(url: string, name: string): Promise<PreparedImage> {
  const res = await get(url)
  if (!res.ok) throw new Error(`download failed (${res.status})`)
  if (!res.type.startsWith('image/')) throw new Error('the source did not return a picture')
  return prepareWallpaper(new File([res.bytes], name, { type: res.type }), name)
}

/** Fetches today's (or the newest available) Bing daily image, prepared for the TV. */
export async function fetchBingDaily(market = 'en-US'): Promise<PreparedImage> {
  const res = await get(`${BING_BASE}/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=${market}`)
  if (!res.ok) throw new Error(`Bing daily is unavailable (${res.status})`)
  const data = json<{ images?: BingImage[] }>(res.bytes)
  const img = data.images?.[0]
  if (!img?.urlbase) throw new Error('Bing daily returned no picture today.')
  return toPrepared(bingFileUrl(img), bingName(img))
}
