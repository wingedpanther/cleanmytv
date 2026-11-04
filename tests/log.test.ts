import { afterEach, describe, expect, it, vi } from 'vitest'

const os = vi.hoisted(() => ({ home: '/Users/jane' }))
vi.mock('node:os', async (importOriginal) => ({ ...(await importOriginal<typeof import('node:os')>()), homedir: () => os.home }))

const { log, noHomeFolder, setSinks } = await import('../src/main/log')

describe('the activity log', () => {
  afterEach(() => { os.home = '/Users/jane' })

  it("shows the home folder as ~, so a copied log doesn't name the computer's user", () => {
    const lines: string[] = []
    setSinks((l) => lines.push(l.text), () => {})
    log('clean up', 'info', 'recovery record: /Users/jane/.cleanyourtv/recovery-abc.json')
    log('pair', 'cmd', "/Users/jane/Library/Application Support/cleanmytv/platform-tools/adb connect x.x.x.x:5555")
    expect(lines).toEqual(['recovery record: ~/.cleanyourtv/recovery-abc.json', '~/Library/Application Support/cleanmytv/platform-tools/adb connect x.x.x.x:5555'])
  })

  it('handles Windows home folders with spaces', () => {
    os.home = 'C:\\Users\\Jane Doe'
    expect(noHomeFolder('adb: C:\\Users\\Jane Doe\\AppData\\Roaming\\cleanmytv\\adb.exe')).toBe('adb: ~\\AppData\\Roaming\\cleanmytv\\adb.exe')
  })

  it("leaves other users' folders and TV paths alone", () => {
    expect(noHomeFolder('/Users/janet/x /data/data/home/files /Users/jane')).toBe('/Users/janet/x /data/data/home/files ~')
    expect(noHomeFolder('/Users/jané/x')).toBe('/Users/jané/x')
    os.home = '/'
    expect(noHomeFolder('/sdcard/x')).toBe('/sdcard/x')
  })

  it('handles a home folder set with a trailing slash', () => {
    os.home = '/home/jane/'
    expect(noHomeFolder('list saved to /home/jane/.cleanyourtv/lists/x.txt')).toBe('list saved to ~/.cleanyourtv/lists/x.txt')
  })
})
