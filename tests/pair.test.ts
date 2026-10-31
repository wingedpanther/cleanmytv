// Pairing against a stand-in adb: a long-running server fails with a protocol fault until it restarts.
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'
import { configurePaths, paths } from '../src/main/paths'
import { pair } from '../src/main/toolchain'

let calls: string

function fakeAdb(pairOutput: string): void {
  const data = mkdtempSync(join(tmpdir(), 'cytv-pair-'))
  configurePaths(data, data)
  const dir = join(paths.tools, 'platform-tools')
  mkdirSync(dir, { recursive: true })
  calls = join(data, 'calls')
  writeFileSync(
    join(dir, 'adb'),
    `#!/bin/sh
echo "$*" >> "${calls}"
case "$1" in
  kill-server) touch "${data}/restarted" ;;
  pair) if [ -e "${data}/restarted" ]; then echo "Successfully paired to $2"; else echo "${pairOutput}" >&2; fi ;;
  connect) echo "connected to $2" ;;
  -s) echo "restarting in TCP mode port: 5555" ;;
esac
`
  )
  chmodSync(join(dir, 'adb'), 0o755)
}

const made = (): string[] => (existsSync(calls) ? readFileSync(calls, 'utf8').trim().split('\n') : [])

describe.skipIf(process.platform === 'win32')('pair', () => {
  beforeEach(() => fakeAdb(''))

  it('restarts a stuck adb server and tries again with the same code', async () => {
    fakeAdb("error: protocol fault (couldn't read status message): Undefined error: 0")
    const r = await pair('192.168.1.50', 37000, '123456', 41000)
    expect(r.ok).toBe(true)
    expect(made().slice(0, 3)).toEqual(['pair 192.168.1.50:37000', 'kill-server', 'pair 192.168.1.50:37000'])
  })

  it("doesn't restart anything for a wrong code", async () => {
    fakeAdb('error: Failed: wrong password')
    const r = await pair('192.168.1.50', 37000, '123456', 41000)
    expect(r.ok).toBe(false)
    expect(made()).toEqual(['pair 192.168.1.50:37000'])
  })
})
