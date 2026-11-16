// Downloads for the Bing picture of the day and the weather place search. The page's CSP blocks the
// network, so the renderer asks here, and only for these hosts, over HTTPS, with each redirect checked too.
import type { WebResponse } from '@shared/api'
import { UiError } from './i18n'

const HOSTS = new Set(['www.bing.com', 'geocoding-api.open-meteo.com'])
const MAX_BYTES = 25 * 1024 * 1024

function allowed(url: string): URL {
  let u: URL
  try { u = new URL(url) } catch { throw new UiError('not a web address') }
  if (u.protocol !== 'https:' || !HOSTS.has(u.hostname) || u.username || u.password || u.port) {
    throw new UiError('the app only downloads from {hosts}', { hosts: [...HOSTS].join(', ') })
  }
  return u
}

export async function get(url: string, fetchImpl: typeof fetch = fetch): Promise<WebResponse> {
  let u = allowed(url)
  for (let hop = 0; hop < 5; hop++) {
    const res = await fetchImpl(u, { redirect: 'manual', signal: AbortSignal.timeout(30000) })
    const next = res.headers.get('location')
    if (res.status >= 300 && res.status < 400 && next) {
      u = allowed(new URL(next, u).href)
      continue
    }
    if (Number(res.headers.get('content-length') ?? 0) > MAX_BYTES) throw new UiError('the download is too large')
    const body = Buffer.from(await res.arrayBuffer())
    if (body.length > MAX_BYTES) throw new UiError('the download is too large')
    return { status: res.status, contentType: res.headers.get('content-type') ?? '', base64: body.toString('base64') }
  }
  throw new UiError('too many redirects')
}
