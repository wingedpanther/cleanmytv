// Pairing against a stand-in adb: a long-running server fails with a protocol fault until it restarts.
import { chmodSync, existsSync, mkdirSync, mkdtempSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { beforeEach, describe, expect, it } from 'vitest'
import { configurePaths, paths } from '../src/main/paths'
import { pair, unzipFile } from '../src/main/toolchain'

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

function buildZip(entries: { name: string; data: Buffer; unixMode?: number }[]): Buffer {
  const locals: Buffer[] = []
  const centrals: Buffer[] = []
  let offset = 0
  for (const e of entries) {
    const nameBuf = Buffer.from(e.name, 'utf8')
    const local = Buffer.alloc(30 + nameBuf.length + e.data.length)
    local.writeUInt32LE(0x04034b50, 0)
    local.writeUInt16LE(20, 4)
    local.writeUInt32LE(e.data.length, 18)
    local.writeUInt32LE(e.data.length, 22)
    local.writeUInt16LE(nameBuf.length, 26)
    nameBuf.copy(local, 30)
    e.data.copy(local, 30 + nameBuf.length)
    locals.push(local)

    const central = Buffer.alloc(46 + nameBuf.length)
    central.writeUInt32LE(0x02014b50, 0)
    central.writeUInt16LE((3 << 8) | 20, 4)
    central.writeUInt16LE(20, 6)
    central.writeUInt32LE(e.data.length, 20)
    central.writeUInt32LE(e.data.length, 24)
    central.writeUInt16LE(nameBuf.length, 28)
    central.writeUInt32LE(((e.unixMode ?? 0o100644) << 16) >>> 0, 38)
    central.writeUInt32LE(offset, 42)
    nameBuf.copy(central, 46)
    centrals.push(central)
    offset += local.length
  }
  const cd = Buffer.concat(centrals)
  const eocd = Buffer.alloc(22)
  eocd.writeUInt32LE(0x06054b50, 0)
  eocd.writeUInt16LE(entries.length, 8)
  eocd.writeUInt16LE(entries.length, 10)
  eocd.writeUInt32LE(cd.length, 12)
  eocd.writeUInt32LE(offset, 16)
  return Buffer.concat([...locals, cd, eocd])
}

describe('unzipFile', () => {
  it('unpacks files and preserves Unix executable permissions', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cytv-unzip-'))
    const archive = join(dir, 'test.zip')
    const out = join(dir, 'out')
    writeFileSync(archive, buildZip([{ name: 'platform-tools/adb', data: Buffer.from('#!/bin/sh\n'), unixMode: 0o100755 }]))
    unzipFile(archive, out)
    const unpacked = join(out, 'platform-tools', 'adb')
    expect(readFileSync(unpacked, 'utf8')).toBe('#!/bin/sh\n')
    if (process.platform !== 'win32') {
      expect(statSync(unpacked).mode & 0o111).toBe(0o111)
    }
  })

  it('refuses path traversal entries', () => {
    const dir = mkdtempSync(join(tmpdir(), 'cytv-unzip-bad-'))
    const archive = join(dir, 'evil.zip')
    writeFileSync(archive, buildZip([{ name: '../evil', data: Buffer.from('nope') }]))
    expect(() => unzipFile(archive, join(dir, 'out'))).toThrow(/unsafe path/)
  })
})
