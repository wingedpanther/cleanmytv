import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { THEME_KEYS, parseTheme } from '@shared/themes'

const DIR = 'themes/community'
const files = readdirSync(DIR).filter((f) => f.endsWith('.cmtvtheme'))

describe('community themes', () => {
  it('has some', () => expect(files.length).toBeGreaterThan(0))

  for (const f of files) {
    it(`${f} loads, names its author, uses known look keys and has no wallpaper`, () => {
      const text = readFileSync(join(DIR, f), 'utf8')
      const theme = parseTheme(text)
      const raw = JSON.parse(text) as { author?: unknown; wallpaper?: unknown; look: Record<string, unknown> }
      expect(theme.name).not.toBe('My theme')
      expect(typeof raw.author === 'string' && raw.author.trim() !== '').toBe(true)
      expect(raw.wallpaper).toBeUndefined()
      expect(Object.keys(raw.look).filter((k) => !(THEME_KEYS as readonly string[]).includes(k))).toEqual([])
    })
  }
})
