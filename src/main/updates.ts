// Is there a newer CleanMyTV? One request to GitHub's latest-release API, with the answer cached in the
// settings file. Each launch asks again, and so does Check for updates; the cache only spares GitHub the
// repeat checks within an hour, like a window reload. Nothing is downloaded here: updater.ts does that.
import type { UpdateInfo } from '@shared/api'

const URL_LATEST = 'https://api.github.com/repos/wingedpanther/cleanmytv/releases/latest'
const RELEASES = 'https://github.com/wingedpanther/cleanmytv/releases/'
const EVERY_MS = 60 * 60 * 1000

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
 * The newest release, from the cache when it is fresh, or from GitHub when `force` is set or the cache is
 * old. Returns the cache to store. A failed check keeps the old answer and says so in `failed`: the
 * sidebar stays quiet about it, and Check for updates shows it.
 */
export async function check(current: string, cache: UpdateCache, now = Date.now(), fetchImpl: typeof fetch = fetch, force = false): Promise<{ info: UpdateInfo; cache: UpdateCache }> {
  let next = cache
  let failed = false
  if (force || !cache.checkedAt || now - cache.checkedAt > EVERY_MS || now < cache.checkedAt) {
    failed = true
    try {
      const res = await fetchImpl(URL_LATEST, { headers: { accept: 'application/vnd.github+json' }, signal: AbortSignal.timeout(10000) })
      if (res.ok) {
        const tag = String(((await res.json()) as { tag_name?: unknown }).tag_name ?? '')
        if (parseVersion(tag)) {
          next = { checkedAt: now, latest: tag.replace(/^v/, '') }
          failed = false
        }
      }
    } catch {
      // offline, or GitHub is slow: the next launch or button press tries again
    }
  }
  const latest = next.latest
  const available = !!latest && isNewer(latest, current)
  return {
    info: { current, latest, available, url: available ? `${RELEASES}tag/v${latest}` : undefined, ...(failed ? { failed } : {}) },
    cache: next
  }
}
