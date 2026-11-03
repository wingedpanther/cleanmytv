// The details a bug report needs, for the activity drawer's copy button. No address, serial or settings.
import { release } from 'node:os'
import * as T from './tv'
import * as debloat from './debloat'
import * as home from './home'

export async function bugReport(appVersion: string): Promise<string> {
  const out = [`CleanMyTV ${appVersion}, ${process.platform} ${release()} ${process.arch}`]
  const p = T.getStatus().probe
  if (!p) return [...out, 'TV: not connected'].join('\n')
  out.push(`TV: ${p.manufacturer} ${p.model} (device ${p.device}, product ${p.product}), Android ${p.release} (SDK ${p.sdk}), build ${p.build}`)
  const best = debloat.lists(p).find((l) => l.score > 0)
  out.push(`Matching list: ${best ? `${best.id} (score ${best.score})` : 'none'}`)
  out.push(`Home: ${(await T.homeComponent().catch(() => null)) ?? 'unknown'}`)
  const claims = await T.homeClaimants().catch(() => [] as string[])
  out.push(`Apps that answer Home: ${claims.join(', ') || 'none'}`)
  const h = await home.status().catch(() => null)
  out.push(`CleanMyTV Home: ${!h ? 'unknown' : h.installed ? `${h.versionName ?? '?'} installed${h.isHome ? ', is Home' : ''}` : 'not installed'}` +
    (h?.bundledVersion ? `, this app carries ${h.bundledVersion}` : ''))
  return out.join('\n')
}
