// TVs this app has connected to before, kept in settings.json. With more than one, nothing connects by
// itself at start: the user picks, so a second TV in the house never gets changes meant for the first.
import type { ConnStatus, KnownTv, Probe } from '@shared/api'

export interface TvSettings {
  lastHost?: string
  lastDevice?: string
  lastModel?: string
  lastId?: string
  tvs?: KnownTv[]
}

export function listTvs(s: TvSettings): KnownTv[] {
  // Settings from before the TV list: the one saved TV.
  const tvs = s.tvs ?? (s.lastHost ? [{ host: s.lastHost, device: s.lastDevice, model: s.lastModel, lastUsed: 0 }] : [])
  return [...tvs].sort((a, b) => b.lastUsed - a.lastUsed)
}

export const autoConnect = (s: TvSettings): boolean => listTvs(s).length <= 1

/**
 * The entry `p` is: the one with its id, else, for entries saved before ids, the same device and model.
 * Two TVs of one model that both have ids stay two entries.
 */
function sameTv(t: KnownTv, p: Probe | undefined): boolean {
  if (!p?.device) return false
  if (t.id && p.id) return t.id === p.id
  return t.device === p.device && t.model === p.model
}

/** A successful connection: this TV moves to the top and is the one to reconnect to. */
export function rememberTv<S extends TvSettings>(s: S, host: string, st: ConnStatus, now = Date.now()): S {
  const p = st.probe
  const tvs = listTvs(s)
  // The same TV at a new address replaces its old entry; a different TV at this address replaces that one.
  const old = tvs.find((t) => sameTv(t, p)) ?? tvs.find((t) => t.host === host)
  const entry: KnownTv = { ...old, host, device: p?.device, model: p?.model, manufacturer: p?.manufacturer, lastUsed: now }
  if (p?.id) entry.id = p.id
  else delete entry.id
  if (old && !sameTv(old, p)) {
    delete entry.thumb
    delete entry.thumbAt
  }
  const next: S = { ...s, lastHost: host, lastDevice: p?.device, lastModel: p?.model, tvs: [entry, ...tvs.filter((t) => t !== old && t.host !== host)] }
  if (p?.id) next.lastId = p.id
  else delete next.lastId
  return next
}

export function forgetTv<S extends TvSettings>(s: S, host: string): S {
  const next: S = { ...s, tvs: listTvs(s).filter((t) => t.host !== host) }
  if (s.lastHost === host) {
    delete next.lastHost
    delete next.lastDevice
    delete next.lastModel
    delete next.lastId
  }
  return next
}

const THUMB_RE = /^data:image\/jpeg;base64,[A-Za-z0-9+/]+=*$/

export function setThumb<S extends TvSettings>(s: S, host: string, dataUrl: string, now = Date.now()): S {
  if (typeof dataUrl !== 'string' || dataUrl.length > 270_000 || !THUMB_RE.test(dataUrl)) throw new Error('the snapshot must be a small JPEG')
  return { ...s, tvs: listTvs(s).map((t) => (t.host === host ? { ...t, thumb: dataUrl, thumbAt: now } : t)) }
}
