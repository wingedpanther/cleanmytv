/// <reference types="vite/client" />
// The translation core, shared by the window and the main process. English is the source: each
// English text is its own key, and a language file maps it to the translation.

/** Where a language file keeps the language's own name, as its speakers write it ("Español"). */
export const NAME_KEY = '_name'

/**
 * Every file in locales/, bundled at build time, so another language is only its file. The build scripts run
 * outside Vite, with no glob, and English is all they need.
 */
const FILES: Record<string, Record<string, string>> = (() => {
  try {
    return import.meta.glob<Record<string, string>>('../renderer/src/locales/*.json', { eager: true, import: 'default' })
  } catch {
    return {}
  }
})()

/** The languages the app speaks, by code: English, then one per file in locales/, named by its code. */
export const LANGUAGES: Record<string, { name: string; dict: Record<string, string> }> = {
  en: { name: 'English', dict: {} },
  ...Object.fromEntries(
    Object.entries(FILES).map(([path, file]) => {
      const { [NAME_KEY]: name, ...dict } = file
      const code = path.slice(path.lastIndexOf('/') + 1, -'.json'.length)
      return [code, { name: name || code, dict }]
    })
  )
}

/** A language code from LANGUAGES, like "en" or "ru". */
export type Lang = string
export type Vars = Record<string, string | number>

export const isLang = (v: unknown): v is Lang => typeof v === 'string' && Object.hasOwn(LANGUAGES, v)

/** {name}, or {n|one|few|many} for the word that goes with the number n. */
const TOKEN = /\{(\w+)(?:\|([^{}]*))?\}/g

const ORDER = ['zero', 'one', 'two', 'few', 'many', 'other']
const rules = new Map<Lang, { select: (n: number) => string; forms: string[] }>()

/**
 * A language's plural forms are the categories whole numbers below 200 fall in, in CLDR's order. That leaves
 * out Russian's "other", which only fractions take, and the "many" Spanish, French and Italian keep for
 * round millions ("un millón de apps"): those read fine with the last form.
 */
function pluralRules(lang: Lang): { select: (n: number) => string; forms: string[] } {
  let r = rules.get(lang)
  if (!r) {
    const p = new Intl.PluralRules(lang)
    const forms = [...new Set(Array.from({ length: 200 }, (_, n) => p.select(n)))].sort((a, b) => ORDER.indexOf(a) - ORDER.indexOf(b))
    r = { select: (n) => p.select(n), forms }
    rules.set(lang, r)
  }
  return r
}

/** How many forms a plural takes in [lang]: English, German and Spanish two, Russian three (one, few, many). */
export const pluralForms = (lang: Lang): number => pluralRules(lang).forms.length

/**
 * Which plural form goes with n, in the language's own order: English has one and other, Russian one (21),
 * few (22) and many (25). A category outside the forms takes the last one. Russian says 1.5 with the few form.
 */
export function pluralIndex(lang: Lang, n: number): number {
  if (lang === 'ru' && !Number.isInteger(n)) return 1
  const r = pluralRules(lang)
  const i = r.forms.indexOf(r.select(n))
  return i < 0 ? r.forms.length - 1 : i
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
  const text = LANGUAGES[lang].dict[english]
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

/** The app's language for a system language such as "ru-RU" or "de_AT", English when it has none. */
export function langOf(locale: string | undefined): Lang {
  const code = (locale ?? '').toLowerCase().split(/[-_]/)[0]
  return isLang(code) ? code : 'en'
}
