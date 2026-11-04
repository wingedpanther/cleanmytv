import { contextBridge, ipcRenderer } from 'electron'
import type { CytvApi, ConnStatus, DownloadProgress, LogLine } from '@shared/api'

// Every method of CytvApi except the event hooks is a plain invoke on a channel of the same name.
const methods = [
  'scan', 'discover', 'stopDiscovery', 'connect', 'disconnect', 'status', 'lastHost', 'reconnect', 'checkAdbClosed', 'bootCheck', 'checkUpdate', 'pair',
  'bugReport', 'checks', 'download',
  'lists', 'plan', 'tvPackages', 'applyDebloat', 'restore',
  'useStockLauncher', 'removeOldLauncher', 'homeStatus', 'installHome', 'useHome', 'homeApps', 'readHomeConfig', 'setRemoteButtons', 'setLockPin', 'setAppLock', 'clearAppLock', 'themePackBegin', 'themePackPut', 'themePackEnd', 'homeKeys', 'setHomeConfig', 'setHomeWallpaper', 'applyHomeTheme', 'themeBackup', 'restoreThemeBackup', 'forgetThemeBackup', 'saveHomeTheme',
  'knownTvs', 'connectKnown', 'forgetTv', 'saveTvThumb',
  'screenshot', 'thumbShot', 'webGet',
  'systemState', 'setPrivateDns', 'setAnimationScale', 'clearCaches', 'key', 'reboot'
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
