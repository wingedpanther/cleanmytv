import { join } from 'node:path'

let resourcesDir = join(process.cwd(), 'resources')
let dataDir = join(process.cwd(), '.cytv-data')

/** resources: bundled upstream lists, themes and the APK. data: the app's own writable folder. */
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
  get bundledApk(): string { return join(resourcesDir, 'launcher.apk') },
  get bundledApkInfo(): string { return join(resourcesDir, 'launcher.json') },
  get bundledHomeApk(): string { return join(resourcesDir, 'home.apk') },
  get bundledHomeInfo(): string { return join(resourcesDir, 'home.json') },
  get keystore(): string { return join(resourcesDir, 'signing', 'cleanmytv.jks') },
  get adbKey(): string { return join(dataDir, 'adbkey') },
  get tools(): string { return join(dataDir, 'tools') },
  get localApk(): string { return join(dataDir, 'build', 'launcher.apk') },
  get settings(): string { return join(dataDir, 'settings.json') }
}
