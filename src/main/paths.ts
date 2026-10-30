import { join } from 'node:path'

let resourcesDir = join(process.cwd(), 'resources')
let dataDir = join(process.cwd(), '.cytv-data')

/** resources: bundled upstream lists, CleanMyTV Home's APK and the signing key. data: the app's own writable folder. */
export function configurePaths(resources: string, data: string): void {
  resourcesDir = resources
  dataDir = data
}

export const paths = {
  get resources(): string { return resourcesDir },
  get data(): string { return dataDir },
  get upstream(): string { return join(resourcesDir, 'upstream') },
  get examples(): string { return join(resourcesDir, 'upstream', 'examples') },
  get themes(): string { return join(resourcesDir, 'upstream', 'themes') },
  get bundledHomeApk(): string { return join(resourcesDir, 'home.apk') },
  get bundledHomeInfo(): string { return join(resourcesDir, 'home.json') },
  get keystore(): string { return join(resourcesDir, 'signing', 'cleanmytv.jks') },
  get adbKey(): string { return join(dataDir, 'adbkey') },
  get tools(): string { return join(dataDir, 'tools') },
  get settings(): string { return join(dataDir, 'settings.json') },
  /** A copy of each TV's CleanMyTV Home settings and wallpaper, put back if a fresh install starts empty. */
  get homeBackups(): string { return join(dataDir, 'home-backups') }
}
