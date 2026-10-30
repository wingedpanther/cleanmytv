// Is there a newer CleanMyTV? One request to GitHub's latest-release API, at most every 12 hours,
// with the answer cached in the settings file. Nothing is downloaded: the app links to the release page.
import type { UpdateInfo } from '@shared/api'

const URL_LATEST = 'https://api.github.com/repos/wingedpanther/cleanmytv/releases/latest'
const RELEASES = 'https://github.com/wingedpanther/cleanmytv/releases/'
const EVERY_MS = 12 * 60 * 60 * 1000

export interface UpdateCache {
  checkedAt?: number
  latest?: string
}

/** Parses "v1.2.3" or "1.2.3"; null for anything else. */
export function parseVersion(v: string): number[] | null {
  const m = /^v?(\d+)\.(\d+)\.(\d+)$/.exec(v.trim())
  return m ? m.slice(1).map(Number) : null
}

export function isNewer(candidate: string, current: string): boolean {
  const a = parseVersion(candidate)
  const b = parseVersion(current)
  if (!a || !b) return false
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i]
  return false
}

/**
 * The newest release, from the cache when it is fresh. Returns the cache to store. A failed check keeps
 * the old answer and is retried next launch: being offline is not an error worth showing.
 */
export async function check(current: string, cache: UpdateCache, now = Date.now(), fetchImpl: typeof fetch = fetch): Promise<{ info: UpdateInfo; cache: UpdateCache }> {
  let next = cache
  if (!cache.checkedAt || now - cache.checkedAt > EVERY_MS || now < cache.checkedAt) {
    try {
      const res = await fetchImpl(URL_LATEST, { headers: { accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(10000) })
      if (res.ok) {
        const tag = String(((await res.json()) as { tag_name?: unknown }).tag_name ?? '')
        if (parseVersion(tag)) next = { checkedAt: now, latest: tag.replace(/^v/, '') }
      }
    } catch {
      // offline, or GitHub is slow: try again next launch
    }
  }
  const latest = next.latest
  const available = !!latest && isNewer(latest, current)
  return {
    info: { current, latest, available, url: available ? `${RELEASES}tag/v${latest}` : undefined },
    cache: next
  }
}
