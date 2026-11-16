// The translation core, shared by the window and the main process. English is the source: each
// English text is its own key, and a language file maps it to the translation.
import ru from '../renderer/src/locales/ru.json'

export type Lang = 'en' | 'ru'
export type Vars = Record<string, string | number>

const DICTS: Record<Lang, Record<string, string>> = { en: {}, ru }

/** {name}, or {n|one|few|many} for the word that goes with the number n. */
const TOKEN = /\{(\w+)(?:\|([^{}]*))?\}/g

/** Which plural form goes with n: English has one and other, Russian one (21), few (22) and many (25). */
export function pluralIndex(lang: Lang, n: number): number {
  if (lang === 'en') return n === 1 ? 0 : 1
  if (!Number.isInteger(n)) return 1
  const ten = Math.abs(n) % 10
  const hundred = Math.abs(n) % 100
  if (ten === 1 && hundred !== 11) return 0
  if (ten >= 2 && ten <= 4 && (hundred < 12 || hundred > 14)) return 1
  return 2
}

/** The text cut at its tokens, each token replaced by its value. A token without a value stays as it is. */
export function pieces<T>(lang: Lang, text: string, vars: Record<string, T | string | number> = {}): (T | string | number)[] {
  const out: (T | string | number)[] = []
  let last = 0
  for (const m of text.matchAll(TOKEN)) {
    const [whole, name, forms] = m
    const value = vars[name]
    out.push(text.slice(last, m.index))
    last = m.index + whole.length
    if (value === undefined) out.push(whole)
    else if (forms === undefined) out.push(value)
    else {
      const all = forms.split('|')
      out.push(all[Math.min(pluralIndex(lang, Number(value)), all.length - 1)])
    }
  }
  out.push(text.slice(last))
  return out.filter((p) => p !== '')
}

export function fill(lang: Lang, text: string, vars?: Vars): string {
  return pieces(lang, text, vars).join('')
}

/** The text in [lang], or the English when there's no translation, with its tokens filled in. */
export function translated(lang: Lang, english: string): { lang: Lang; text: string } {
  const text = DICTS[lang][english]
  return text ? { lang, text } : { lang: 'en', text: english }
}

export function translate(lang: Lang, english: string, vars?: Vars): string {
  const tr = translated(lang, english)
  return fill(tr.lang, tr.text, vars)
}

let current: Lang = 'en'

/** Sets the language for t: the window does at start, the main process when the window says. */
export function setLang(l: Lang): void {
  current = l
}

export const currentLang = (): Lang => current

/** The English text in the current language, with {name} filled in from vars. */
export function t(english: string, vars?: Vars): string {
  return translate(current, english, vars)
}

/** Russian for a system language such as "ru-RU", English for anything else. */
export const langOf = (locale: string | undefined): Lang => (/^ru\b/i.test(locale ?? '') ? 'ru' : 'en')
