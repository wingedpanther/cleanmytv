import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { CANT_OPEN, toJpeg } from '../src/main/picture'

describe('pictures the window cannot open', () => {
  it('says to save a JPEG off the Mac, and for empty or broken files', async () => {
    await expect(toJpeg(new Uint8Array([1, 2, 3]), 'win32')).rejects.toThrow(CANT_OPEN)
    await expect(toJpeg(new Uint8Array(), 'darwin')).rejects.toThrow(CANT_OPEN)
  })

  it.skipIf(process.platform !== 'darwin')('turns a HEIC photo into a JPEG on a Mac', async () => {
    const dir = mkdtempSync(join(tmpdir(), 'cmtv-heic-'))
    const png = join(dir, 'dot.png')
    const heic = join(dir, 'photo.heic')
    // A one-pixel PNG, saved as HEIC the way an iPhone would.
    writeFileSync(png, Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64'))
    execFileSync('/usr/bin/sips', ['-s', 'format', 'heic', png, '--out', heic], { stdio: 'ignore' })
    const jpeg = await toJpeg(new Uint8Array(readFileSync(heic)), 'darwin')
    expect([...jpeg.subarray(0, 3)]).toEqual([0xff, 0xd8, 0xff])
    await expect(toJpeg(new Uint8Array(Buffer.from('not a picture')), 'darwin')).rejects.toThrow(CANT_OPEN)
  })
})
