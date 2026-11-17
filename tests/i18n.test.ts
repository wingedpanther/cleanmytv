import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { LANGUAGES, fill, langOf, pluralForms, pluralIndex, translate, type Lang } from '@shared/i18n'

const dict = LANGUAGES.ru.dict
const LOCALES = 'src/renderer/src/locales'

function sources(dir: string): string[] {
  return readdirSync(dir).flatMap((f) => {
    const p = join(dir, f)
    return statSync(p).isDirectory() ? sources(p) : /\.tsx?$/.test(f) && !f.endsWith('.d.ts') ? [p] : []
  })
}

/** The string literal starting at [at] (a quote or backtick), unescaped, and whether it has ${…} in it. */
function literal(src: string, at: number): { text: string; dynamic: boolean } {
  const quote = src[at]
  let text = ''
  for (let i = at + 1; i < src.length; i++) {
    const c = src[i]
    if (c === '\\') {
      const n = src[++i]
      text += n === 'n' ? '\n' : n === 't' ? '\t' : n
    } else if (c === quote) return { text, dynamic: quote === '`' && text.includes('${') }
    else text += c
  }
  throw new Error(`unclosed string at ${at}`)
}

/** Every t('…'), tx('…'), new UiError('…') and new AdbError('…') in the app, and the calls whose first argument isn't a plain string. */
function calls(): { keys: Map<string, string>; dynamic: string[] } {
  const keys = new Map<string, string>()
  const dynamic: string[] = []
  for (const file of sources('src')) {
    const src = readFileSync(file, 'utf8')
    for (const m of src.matchAll(/(?<![\w.$])(t|tx|UiError|AdbError)\(\s*/g)) {
      const at = m.index + m[0].length
      const where = `${file}:${src.slice(0, at).split('\n').length}`
      if (!`'"\``.includes(src[at])) {
        // A function's own declaration, not a call.
        if (!/^(english|text)\b/.test(src.slice(at))) dynamic.push(where)
        continue
      }
      const lit = literal(src, at)
      if (lit.dynamic) dynamic.push(where)
      else keys.set(lit.text, where)
    }
  }
  return { keys, dynamic }
}

// Each name once: a translation may add a plural form the English doesn't need.
const tokens = (s: string): string[] => [...new Set([...s.matchAll(/\{(\w+)(?:\|[^{}]*)?\}/g)].map((m) => m[1]))].sort()

describe.each(Object.keys(LANGUAGES).filter((l) => l !== 'en') as Lang[])('the %s translation', (lang) => {
  const { keys, dynamic } = calls()
  const dict = LANGUAGES[lang].dict

  it('is asked for with plain strings only, so these checks see every key', () => {
    expect(dynamic).toEqual([])
  })

  it('has every text the app asks for', () => {
    const missing = [...keys].filter(([k]) => !(k in dict)).map(([k, where]) => `${where}: ${k}`)
    expect(missing).toEqual([])
  })

  it('has nothing the app no longer asks for', () => {
    expect(Object.keys(dict).filter((k) => !keys.has(k))).toEqual([])
  })

  it('keeps the same {placeholders} as the English', () => {
    const wrong = Object.entries(dict).filter(([en, tr]) => tokens(en).join() !== tokens(tr).join())
    expect(wrong).toEqual([])
  })

  it("gives every plural the language's number of forms, and no empty text", () => {
    const wrong = Object.entries(dict).filter(([, tr]) => !tr.trim() || [...tr.matchAll(/\{\w+\|([^{}]*)\}/g)].some((m) => m[1].split('|').length !== pluralForms(lang)))
    expect(wrong).toEqual([])
  })
})

describe('the languages', () => {
  it('list every file in locales/', () => {
    const files = readdirSync(LOCALES).filter((f) => f.endsWith('.json')).map((f) => f.slice(0, -5))
    expect(files.sort()).toEqual(Object.keys(LANGUAGES).filter((l) => l !== 'en').sort())
  })

  it("follow the computer's language, English when the app doesn't speak it", () => {
    expect(['ru-RU', 'de_AT', 'de', 'DE-de', 'fr-FR', '', undefined].map(langOf)).toEqual(['ru', 'de', 'de', 'de', 'en', 'en', 'en'])
  })
})

describe('t', () => {
  it('falls back to the English, filled in', () => {
    expect(translate('ru', 'Not a key {x}', { x: 5 })).toBe('Not a key 5')
    expect(translate('en', 'Hello {name}', { name: 'Sam' })).toBe('Hello Sam')
  })

  it('fills placeholders in a translation', () => {
    const [en, tr] = Object.entries(dict).find(([k]) => tokens(k).length === 0) ?? ['', '']
    expect(translate('ru', en)).toBe(tr)
    expect(translate('en', en)).toBe(en)
  })

  it('leaves a placeholder with no value as it is', () => {
    expect(fill('en', 'Version {v} is out', {})).toBe('Version {v} is out')
  })

  it('picks the plural form for each language', () => {
    expect(fill('en', '{n} {n|app|apps}', { n: 1 })).toBe('1 app')
    expect(fill('en', '{n} {n|app|apps}', { n: 3 })).toBe('3 apps')
    const ru = (n: number): string => fill('ru', '{n} {n|приложение|приложения|приложений}', { n })
    expect([1, 2, 5, 11, 21, 22, 25, 112].map(ru)).toEqual([
      '1 приложение', '2 приложения', '5 приложений', '11 приложений', '21 приложение', '22 приложения', '25 приложений', '112 приложений'
    ])
    expect(pluralIndex('ru', 1.5)).toBe(1)
    const de = (n: number): string => fill('de', '{n} {n|Zeile|Zeilen}', { n })
    expect([0, 1, 2, 21].map(de)).toEqual(['0 Zeilen', '1 Zeile', '2 Zeilen', '21 Zeilen'])
  })

  it('uses English plurals when the English is the fallback', () => {
    expect(translate('ru', 'Not a key: {n} {n|thing|things}', { n: 5 })).toBe('Not a key: 5 things')
  })
})
