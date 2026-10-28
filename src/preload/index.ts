import { contextBridge, ipcRenderer } from 'electron'
import type { CytvApi, ConnStatus, DownloadProgress, LogLine } from '@shared/api'

// Every method of CytvApi except the event hooks is a plain invoke on a channel of the same name.
const methods = [
  'scan', 'connect', 'disconnect', 'status', 'lastHost', 'reconnect', 'checkAdbClosed', 'pair',
  'checks', 'download',
  'lists', 'plan', 'applyDebloat', 'restore',
  'launcherStatus', 'install', 'buildLocal', 'useStockLauncher', 'useCleanYourTv',
  'prefs', 'setPrefs', 'homeStatus', 'installHome', 'useHome', 'readHomeConfig', 'setHomeConfig', 'setHomeWallpaper',
  'knownTvs', 'connectKnown', 'forgetTv', 'saveTvThumb',
  'apps', 'readTheme', 'applyTheme', 'resetTheme', 'presets', 'screenshot', 'webGet',
  'systemState', 'setClockScreensaver', 'setPrivateDns', 'setAnimationScale', 'setLauncherLocale', 'key', 'reboot'
] as const

function listen<T>(channel: string, cb: (value: T) => void): () => void {
  const handler = (_: unknown, value: T): void => cb(value)
  ipcRenderer.on(channel, handler)
  return () => ipcRenderer.removeListener(channel, handler)
}

const api = Object.fromEntries(
  methods.map((name) => [name, (...args: unknown[]) => ipcRenderer.invoke(name, ...args)])
) as unknown as CytvApi

api.platform = process.platform as CytvApi['platform']
api.onLog = (cb) => listen<LogLine>('log', cb)
api.onProgress = (cb) => listen<DownloadProgress>('progress', cb)
api.onStatus = (cb) => listen<ConnStatus>('status', cb)

contextBridge.exposeInMainWorld('cytv', api)
