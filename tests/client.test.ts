import { mkdtempSync } from 'node:fs'
import type { Server } from 'node:net'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AdbClient } from '../src/main/adb/client'
import { loadKeys } from '../src/main/adb/keys'
import { FakeTv, newTv, startFakeAdbd } from './fake-adbd'

const keys = loadKeys(join(mkdtempSync(join(tmpdir(), 'cytv-client-')), 'adbkey'))

describe('a TV that drops off the network', () => {
  let tv: FakeTv
  let server: Server
  let port: number
  beforeEach(async () => { tv = newTv(); ({ server, port } = await startFakeAdbd(tv)) })
  afterEach(() => server.close())

  it('fails the command in seconds and says the connection is gone', async () => {
    const c = await AdbClient.connect({ host: '127.0.0.1', port, keys, idleMs: 300, probeMs: 300 })
    let closed = ''
    c.onClose = (e) => { closed = e.message }
    tv.gone = true
    const started = Date.now()
    await expect(c.shell('pm list packages', 120000)).rejects.toThrow(/stopped answering/)
    expect(Date.now() - started).toBeLessThan(3000)
    expect(c.closed).toBe(true)
    expect(closed).toMatch(/stopped answering/)
  })

  it('leaves a slow command alone while the TV still answers', async () => {
    const c = await AdbClient.connect({ host: '127.0.0.1', port, keys, idleMs: 200, probeMs: 300 })
    await expect(c.shell('sleep 1200', 5000)).resolves.toBe('')
    expect(c.closed).toBe(false)
    c.close()
  })
})
