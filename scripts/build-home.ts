// Builds resources/home.apk, CleanMyTV Home (the preview launcher in home/), signed with the same key
// as the bundled CleanYourTV launcher.
//   npm run home                                  JDK 17 and the Android SDK from .cytv-build/, as npm run apk
//   JAVA_HOME=… ANDROID_HOME=… npm run home       use the ones already on this computer
import { chmodSync, copyFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { configurePaths, paths } from '../src/main/paths'
import * as tools from '../src/main/toolchain'

const win = process.platform === 'win32'

async function main(): Promise<void> {
  configurePaths(resolve('resources'), resolve('.cytv-build'))
  let javaHome = process.env.JAVA_HOME
  let sdk = process.env.ANDROID_HOME
  if (!javaHome || !sdk) {
    for (const tool of ['jdk', 'sdk'] as const) {
      const ready = tool === 'jdk' ? !!tools.javaHome() : tools.sdkReady()
      if (!ready) {
        const r = await tools.get(tool)
        if (!r.ok) throw new Error(r.summary)
      }
    }
    javaHome = tools.javaHome()!
    sdk = tools.dirs.sdk
  }
  if (!existsSync(paths.keystore)) throw new Error(`missing ${paths.keystore}; run npm run apk first`)
  const dir = resolve('home')
  writeFileSync(join(dir, 'local.properties'), `sdk.dir=${sdk.replace(/\\/g, '\\\\').replace(/:/g, '\\:')}\n`)
  const gradlew = join(dir, win ? 'gradlew.bat' : 'gradlew')
  if (!win) chmodSync(gradlew, 0o755)
  const r = await tools.run('build', gradlew, [
    'assembleRelease', '--no-daemon', '--console=plain',
    `-Pandroid.injected.signing.store.file=${paths.keystore}`,
    `-Pandroid.injected.signing.store.password=${tools.KEYSTORE.password}`,
    `-Pandroid.injected.signing.key.alias=${tools.KEYSTORE.alias}`,
    `-Pandroid.injected.signing.key.password=${tools.KEYSTORE.password}`
  ], {
    cwd: dir,
    env: { JAVA_HOME: javaHome, ANDROID_HOME: sdk, ...(process.env.JAVA_HOME ? {} : { GRADLE_USER_HOME: tools.dirs.gradleHome }) }
  })
  const apk = join(dir, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk')
  if (r.code !== 0 || !existsSync(apk)) throw new Error('Gradle build failed; see the output above.')
  copyFileSync(apk, paths.bundledHomeApk)
  const versionName = /versionName = "([^"]+)"/.exec(readFileSync(join(dir, 'app', 'build.gradle.kts'), 'utf8'))?.[1] ?? 'unknown'
  writeFileSync(paths.bundledHomeInfo, JSON.stringify({ versionName, built: new Date().toISOString() }, null, 2))
  console.log(`built CleanMyTV Home ${versionName} -> ${paths.bundledHomeApk}`)
}

main().catch((e) => {
  console.error(e.message)
  process.exit(1)
})
