// Reads an APK's package name and version before it goes to the TV, so the app can say what it is about
// to install and whether that's an update. An APK is a zip; its AndroidManifest.xml is Android's binary
// XML. This reads only the <manifest> element, with Node's own zlib and no extra library.
import { inflateRawSync } from 'node:zlib'

export interface ApkInfo {
  package: string
  versionName?: string
  versionCode?: number
}

/** The raw bytes of one file inside a zip, or null when it isn't there. */
export function zipEntry(zip: Buffer, name: string): Buffer | null {
  // The end-of-central-directory record sits in the last 64 KB plus 22 bytes.
  const from = Math.max(0, zip.length - 65557)
  let eocd = -1
  for (let i = zip.length - 22; i >= from; i--) {
    if (zip.readUInt32LE(i) === 0x06054b50) { eocd = i; break }
  }
  if (eocd < 0) return null
  const count = zip.readUInt16LE(eocd + 10)
  let p = zip.readUInt32LE(eocd + 16)
  for (let n = 0; n < count && p + 46 <= zip.length; n++) {
    if (zip.readUInt32LE(p) !== 0x02014b50) return null
    const method = zip.readUInt16LE(p + 10)
    const csize = zip.readUInt32LE(p + 20)
    const nameLen = zip.readUInt16LE(p + 28)
    const extraLen = zip.readUInt16LE(p + 30)
    const commentLen = zip.readUInt16LE(p + 32)
    const local = zip.readUInt32LE(p + 42)
    if (zip.toString('utf8', p + 46, p + 46 + nameLen) === name) {
      if (zip.readUInt32LE(local) !== 0x04034b50) return null
      const start = local + 30 + zip.readUInt16LE(local + 26) + zip.readUInt16LE(local + 28)
      const data = zip.subarray(start, start + csize)
      if (method === 0) return Buffer.from(data)
      if (method === 8) return inflateRawSync(data)
      return null
    }
    p += 46 + nameLen + extraLen + commentLen
  }
  return null
}

// android:versionCode and android:versionName, for manifests whose attribute names were stripped.
const RES_VERSION_CODE = 0x0101021b
const RES_VERSION_NAME = 0x0101021c

/** The package and version from a binary AndroidManifest.xml. */
export function manifestInfo(xml: Buffer): ApkInfo | null {
  if (xml.length < 8 || xml.readUInt16LE(0) !== 0x0003) return null
  let strings: string[] = []
  let resIds: number[] = []
  let p = xml.readUInt16LE(2)
  while (p + 8 <= xml.length) {
    const type = xml.readUInt16LE(p)
    const headerSize = xml.readUInt16LE(p + 2)
    const size = xml.readUInt32LE(p + 4)
    if (size < 8) return null
    if (type === 0x0001) strings = stringPool(xml, p)
    else if (type === 0x0180) resIds = Array.from({ length: (size - headerSize) / 4 }, (_, i) => xml.readUInt32LE(p + headerSize + i * 4))
    else if (type === 0x0102) {
      const name = strings[xml.readUInt32LE(p + 20)]
      if (name === 'manifest') {
        const attrStart = xml.readUInt16LE(p + 24)
        const attrSize = xml.readUInt16LE(p + 26)
        const attrCount = xml.readUInt16LE(p + 28)
        const info: ApkInfo = { package: '' }
        for (let i = 0; i < attrCount; i++) {
          const a = p + 16 + attrStart + i * attrSize
          const nameIdx = xml.readUInt32LE(a + 4)
          const attr = strings[nameIdx] || ''
          const res = resIds[nameIdx]
          const raw = xml.readInt32LE(a + 8)
          const dataType = xml.readUInt8(a + 15)
          const data = xml.readUInt32LE(a + 16)
          const text = raw >= 0 ? strings[raw] : dataType === 0x03 ? strings[data] : undefined
          if (attr === 'package') info.package = text ?? ''
          else if (attr === 'versionName' || res === RES_VERSION_NAME) info.versionName = text
          else if ((attr === 'versionCode' || res === RES_VERSION_CODE) && dataType >= 0x10 && dataType <= 0x1f) info.versionCode = data
        }
        return info.package ? info : null
      }
    }
    p += size
  }
  return null
}

function stringPool(xml: Buffer, at: number): string[] {
  const headerSize = xml.readUInt16LE(at + 2)
  const count = xml.readUInt32LE(at + 8)
  const utf8 = (xml.readUInt32LE(at + 16) & 0x100) !== 0
  const data = at + xml.readUInt32LE(at + 20)
  const out: string[] = []
  for (let i = 0; i < count; i++) {
    let s = data + xml.readUInt32LE(at + headerSize + i * 4)
    if (utf8) {
      // Length in characters, then in bytes; each is one byte, or two with the top bit set.
      s += xml[s] & 0x80 ? 2 : 1
      let len = xml[s]
      if (len & 0x80) { len = ((len & 0x7f) << 8) | xml[s + 1]; s += 2 } else s += 1
      out.push(xml.toString('utf8', s, s + len))
    } else {
      let len = xml.readUInt16LE(s)
      if (len & 0x8000) { len = ((len & 0x7fff) << 16) | xml.readUInt16LE(s + 2); s += 4 } else s += 2
      out.push(xml.toString('utf16le', s, s + len * 2))
    }
  }
  return out
}

/** Package and version of an APK file's bytes; null when it isn't a readable APK. */
export function apkInfo(apk: Buffer): ApkInfo | null {
  try {
    const xml = zipEntry(apk, 'AndroidManifest.xml')
    return xml ? manifestInfo(xml) : null
  } catch {
    return null
  }
}
