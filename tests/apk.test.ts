import { deflateRawSync } from 'node:zlib'
import { describe, expect, it } from 'vitest'
import { apkInfo, manifestInfo } from '../src/main/apk'

/** A binary AndroidManifest.xml with one <manifest> element: package and versionName as strings, versionCode as an int. */
function manifest(pkg: string, versionName: string, versionCode: number): Buffer {
  const strings = ['manifest', 'package', 'versionName', 'versionCode', pkg, versionName]
  const data = Buffer.concat(strings.map((s) => { const b = Buffer.from(s, 'utf8'); return Buffer.concat([Buffer.from([s.length, b.length]), b, Buffer.from([0])]) }))
  const pad = Buffer.alloc((4 - (data.length % 4)) % 4)
  const poolHeader = 28
  const pool = Buffer.alloc(poolHeader + strings.length * 4)
  let off = 0
  strings.forEach((s, i) => { pool.writeUInt32LE(off, poolHeader + i * 4); off += 2 + Buffer.byteLength(s) + 1 })
  pool.writeUInt16LE(0x0001, 0); pool.writeUInt16LE(poolHeader, 2); pool.writeUInt32LE(pool.length + data.length + pad.length, 4)
  pool.writeUInt32LE(strings.length, 8); pool.writeUInt32LE(0x100, 16); pool.writeUInt32LE(pool.length, 20)
  const attrs = [[1, 4, 0x03, 4], [2, 5, 0x03, 5], [3, -1, 0x10, versionCode]] as const
  const el = Buffer.alloc(36 + attrs.length * 20)
  el.writeUInt16LE(0x0102, 0); el.writeUInt16LE(16, 2); el.writeUInt32LE(el.length, 4)
  el.writeInt32LE(-1, 16); el.writeUInt32LE(0, 20); el.writeUInt16LE(20, 24); el.writeUInt16LE(20, 26); el.writeUInt16LE(attrs.length, 28)
  attrs.forEach(([name, raw, type, value], i) => {
    const a = 36 + i * 20
    el.writeInt32LE(-1, a); el.writeUInt32LE(name, a + 4); el.writeInt32LE(raw, a + 8); el.writeUInt16LE(8, a + 12); el.writeUInt8(type, a + 15); el.writeUInt32LE(value, a + 16)
  })
  const body = Buffer.concat([pool, data, pad, el])
  const head = Buffer.alloc(8)
  head.writeUInt16LE(0x0003, 0); head.writeUInt16LE(8, 2); head.writeUInt32LE(8 + body.length, 4)
  return Buffer.concat([head, body])
}

/** A zip holding one deflated file. */
function zip(name: string, content: Buffer): Buffer {
  const packed = deflateRawSync(content)
  const n = Buffer.from(name)
  const local = Buffer.alloc(30); local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(8, 8); local.writeUInt32LE(packed.length, 18); local.writeUInt32LE(content.length, 22); local.writeUInt16LE(n.length, 26)
  const central = Buffer.alloc(46); central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(8, 10); central.writeUInt32LE(packed.length, 20); central.writeUInt32LE(content.length, 24); central.writeUInt16LE(n.length, 28); central.writeUInt32LE(0, 42)
  const cdStart = local.length + n.length + packed.length
  const end = Buffer.alloc(22); end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(1, 8); end.writeUInt16LE(1, 10); end.writeUInt32LE(central.length + n.length, 12); end.writeUInt32LE(cdStart, 16)
  return Buffer.concat([local, n, packed, central, n, end])
}

describe('reading an APK', () => {
  it('finds the package and version in the manifest', () => {
    expect(manifestInfo(manifest('org.videolan.vlc', '3.6.4', 13060406))).toEqual({ package: 'org.videolan.vlc', versionName: '3.6.4', versionCode: 13060406 })
  })

  it('reads them out of the zip', () => {
    expect(apkInfo(zip('AndroidManifest.xml', manifest('com.example.tv', '1.0', 7)))).toEqual({ package: 'com.example.tv', versionName: '1.0', versionCode: 7 })
  })

  it('says null for anything that is not an APK', () => {
    expect(apkInfo(Buffer.from('not a zip at all'))).toBeNull()
    expect(apkInfo(zip('readme.txt', Buffer.from('hi')))).toBeNull()
  })
})
