// Xiaomi boards whose one USB port is either an ADB port or a USB host, never both: while debugging is on,
// drives, speakers and USB Ethernet adapters go dead (#71). Debugging off and a restart bring them back.
import type { Probe } from '@shared/api'

const MODELS = [/^MIBOX4$/i, /^MiTV-AFKR\d$/i, /^MiTV-AFMU\d$/i, /^MiTV-MZTU\d$/i]

export function usbOffWhileDebugging(probe: Pick<Probe, 'model'> | undefined): boolean {
  const model = probe?.model.trim() ?? ''
  return MODELS.some((m) => m.test(model))
}
