// Coming back later: connect to the TV saved last time, and only to it.
//
// The ADB handshake signs a challenge chosen by whoever answers, so the app must not hand that signature
// to devices it found by scanning. If the TV is not at the saved address, or something else answers
// there, the user picks the TV from a scan instead.
import type { ConnStatus } from '@shared/api'
import { t } from './i18n'

export interface SavedTv {
  lastHost?: string
  lastDevice?: string
  lastModel?: string
}

export interface ReconnectDeps {
  connect(host: string): Promise<ConnStatus>
  disconnect(): void
  report(s: ConnStatus): ConnStatus
}

export async function reconnect(saved: SavedTv, deps: ReconnectDeps): Promise<ConnStatus | null> {
  const host = saved.lastHost
  if (!host) return null
  const s = await deps.connect(host)
  const name = saved.lastModel
  if (s.state === 'connected') {
    // The model is self-reported, so this is a check against mistakes (a new device at a reused
    // address), not proof of identity; it keeps the app from acting on the wrong device.
    if (saved.lastDevice && s.probe?.device !== saved.lastDevice) {
      deps.disconnect()
      return deps.report({
        state: 'error', host, moved: true,
        error: [
          s.probe?.model
            ? t('A different device is answering at {host} (it says {model}).', { host, model: s.probe.model })
            : t('A different device is answering at {host} (it says nothing about its model).', { host }),
          name ? t('Pick {name} from the list below.', { name }) : t('Pick your TV from the list below.')
        ].join(' ')
      })
    }
    return s
  }
  // Waiting on Allow or pairing needed: the address is right, the TV just needs a step.
  if (s.state === 'waiting-auth' || s.needsPairing) return s
  // A refusal means debugging is off, or that the router gave this address to another device.
  const refused = s.refused || /network debugging is off/.test(s.error ?? '')
  return deps.report({
    state: 'error', host, moved: true,
    error: refused
      ? name
        ? t('Nothing is accepting connections at {host}. Turn on USB debugging on {name} if it is off; if it is on, the TV has a new address: pick it from the list below.', { host, name })
        : t('Nothing is accepting connections at {host}. Turn on USB debugging on your TV if it is off; if it is on, the TV has a new address: pick it from the list below.', { host })
      : name
        ? t("{name} didn't answer at {host}. If it is on, it may have a new address: pick it from the list below.", { host, name })
        : t("your TV didn't answer at {host}. If it is on, it may have a new address: pick it from the list below.", { host })
  })
}
