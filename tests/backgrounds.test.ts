import { describe, expect, it } from 'vitest'
import { bingFileUrl, bingName } from '../src/renderer/src/lib/backgrounds'
import { get } from '../src/main/web'

describe('bing daily source', () => {
  it('builds the 1920x1080 file url from a urlbase', () => {
    expect(bingFileUrl({ urlbase: '/th?id=OHR.OryxCanyon_EN-US1234', copyright: '', startdate: '20261025' })).toBe(
      'https://www.bing.com/th?id=OHR.OryxCanyon_EN-US1234_1920x1080.jpg'
    )
  })

  it('derives a launcher-safe name from the urlbase', () => {
    const name = bingName({ urlbase: '/th?id=OHR.Some_Place Name.EN-US9999', copyright: '', startdate: '' })
    expect(name).toMatch(/^bing-[A-Za-z0-9_.-]+\.jpg$/)
  })
})

describe('background downloads in the main process', () => {
  const ok = (body: string, headers: Record<string, string> = {}, status = 200): Response => new Response(body, { status, headers })

  it('fetches from the allowed hosts', async () => {
    const r = await get('https://www.bing.com/HPImageArchive.aspx?format=js', async () => ok('{}', { 'content-type': 'application/json' }))
    expect(r).toEqual({ status: 200, contentType: 'application/json', base64: Buffer.from('{}').toString('base64') })
  })

  it('refuses other hosts, TMDB, plain http and redirects that leave the list', async () => {
    const never = async (): Promise<Response> => { throw new Error('should not fetch') }
    await expect(get('https://example.com/a.jpg', never)).rejects.toThrow(/only downloads/)
    await expect(get('https://image.tmdb.org/t/p/original/a.jpg', never)).rejects.toThrow(/only downloads/)
    await expect(get('http://www.bing.com/a.jpg', never)).rejects.toThrow(/only downloads/)
    await expect(get('https://www.bing.com.evil.test/a.jpg', never)).rejects.toThrow(/only downloads/)
    const away = async (): Promise<Response> => ok('', { location: 'https://evil.test/x' }, 302)
    await expect(get('https://www.bing.com/a.jpg', away)).rejects.toThrow(/only downloads/)
  })

  it('follows a redirect within the list', async () => {
    const seen: string[] = []
    const r = await get('https://www.bing.com/a.jpg', async (u) => {
      seen.push(String(u))
      return seen.length === 1 ? ok('', { location: '/b.jpg' }, 301) : ok('x', { 'content-type': 'image/jpeg' })
    })
    expect(seen).toEqual(['https://www.bing.com/a.jpg', 'https://www.bing.com/b.jpg'])
    expect(r.contentType).toBe('image/jpeg')
  })
})
