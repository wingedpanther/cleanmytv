// Pictures the app window can't open itself, like the HEIC photos iPhones and Macs save, become a JPEG
// here. macOS turns them over with its own sips; Windows has no tool for it that every PC carries.
import { execFile } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { promisify } from 'node:util'

const run = promisify(execFile)
const MAX_BYTES = 80 * 1024 * 1024

export const CANT_OPEN = "This picture's format can't be opened here. Save it as a JPEG or PNG and choose that."

export async function toJpeg(bytes: Uint8Array, platform: NodeJS.Platform = process.platform): Promise<Uint8Array> {
  if (!(bytes instanceof Uint8Array) || bytes.length === 0 || bytes.length > MAX_BYTES) throw new Error(CANT_OPEN)
  if (platform !== 'darwin') throw new Error(CANT_OPEN)
  const dir = mkdtempSync(join(tmpdir(), 'cmtv-picture-'))
  try {
    const src = join(dir, 'in')
    const out = join(dir, 'out.jpg')
    writeFileSync(src, bytes)
    // Long side capped at 3840: the TV shows 1920x1080, and a 48 MP photo is slow to draw.
    await run('/usr/bin/sips', ['-s', 'format', 'jpeg', '-Z', '3840', src, '--out', out], { timeout: 60000 }).catch(() => {
      throw new Error(CANT_OPEN)
    })
    return new Uint8Array(readFileSync(out))
  } finally {
    rmSync(dir, { recursive: true, force: true })
  }
}
