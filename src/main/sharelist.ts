// Share my list: the TV's details and what Clean up did on it, for a GitHub issue the owner files
// themselves. The window builds the text (src/shared/share.ts), so the owner sees it before anything opens.
import * as T from './tv'
import * as debloat from './debloat'
import { keptLauncher, mustKeep } from './kept'
import { stockOnTv } from './launcher'
import { UiError } from './i18n'
import type { ShareData } from '@shared/api'

export async function info(appVersion: string): Promise<ShareData> {
  const p = T.getStatus().probe
  if (!p) throw new UiError('Connect to a TV first.')
  const fingerprint = (await T.sh('share', 'getprop ro.build.fingerprint', { quiet: true })).trim()
  const { installed } = await T.packageSets()
  const kept = keptLauncher()
  return {
    app: appVersion,
    tv: { manufacturer: p.manufacturer, model: p.model, device: p.device, product: p.product, android: p.release, sdk: p.sdk, build: p.build, fingerprint },
    matched: debloat.lists(p).find((l) => l.score > 0)?.id ?? null,
    home: await T.homeComponent().catch(() => null),
    stockLauncher: stockOnTv(installed) ?? null,
    lists: await debloat.shareLists(),
    keptLauncher: kept ? { pkg: kept, why: kept === mustKeep() ? 'tv' : 'owner' } : null
  }
}
