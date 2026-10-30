// Place search for the weather widget: Open-Meteo's geocoding, through the main process (the page's CSP
// blocks the network). The TV then asks Open-Meteo for the weather at the chosen coordinates.

export interface Place {
  name: string
  /** Region and country, for telling two Springfields apart. */
  detail: string
  lat: number
  lon: number
}

export async function findPlaces(query: string): Promise<Place[]> {
  const q = query.trim()
  if (q.length < 2) return []
  const r = await window.cytv.webGet(`https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(q)}&count=6&language=en&format=json`)
  if (r.status !== 200) throw new Error(`The place search answered ${r.status}. Try again in a minute.`)
  const bin = atob(r.base64)
  const text = new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0)))
  const results = (JSON.parse(text) as { results?: Record<string, unknown>[] }).results ?? []
  return results.flatMap((p) => {
    if (typeof p.name !== 'string' || typeof p.latitude !== 'number' || typeof p.longitude !== 'number') return []
    const detail = [p.admin1, p.country].filter((x): x is string => typeof x === 'string' && x !== p.name).join(', ')
    return [{ name: p.name.slice(0, 40), detail, lat: p.latitude, lon: p.longitude }]
  })
}
