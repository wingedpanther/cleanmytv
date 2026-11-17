// The translation core, shared by the window and the main process. English is the source: each
// English text is its own key, and a language file maps it to the translation.
import de from '../renderer/src/locales/de.json'
import ru from '../renderer/src/locales/ru.json'

/** The languages the app speaks. Another one is its file in locales/ and a line here. */
export const LANGUAGES = {
  en: { name: 'English', dict: {} as Record<string, string> },
  de: { name: 'Deutsch', dict: de as Record<string, string> },
  ru: { name: 'Русский', dict: ru as Record<string, string> }
}

export type Lang = keyof typeof LANGUAGES
export type Vars = Record<string, string | number>

export const isLang = (v: unknown): v is Lang => typeof v === 'string' && Object.hasOwn(LANGUAGES, v)

/** {name}, or {n|one|few|many} for the word that goes with the number n. */
const TOKEN = /\{(\w+)(?:\|([^{}]*))?\}/g

const ORDER = ['zero', 'one', 'two', 'few', 'many', 'other']
const rules = new Map<Lang, { select: (n: number) => string; forms: string[] }>()

function pluralRules(lang: Lang): { select: (n: number) => string; forms: string[] } {
  let r = rules.get(lang)
  if (!r) {
    const p = new Intl.PluralRules(lang)
    const forms = [...p.resolvedOptions().pluralCategories].sort((a, b) => ORDER.indexOf(a) - ORDER.indexOf(b))
    r = { select: (n) => p.select(n), forms }
    rules.set(lang, r)
  }
  return r
}

/** How many forms a plural takes in [lang]: English and German two, Russian three (one, few, many). */
export const pluralForms = (lang: Lang): number => (lang === 'ru' ? 3 : pluralRules(lang).forms.length)

/**
 * Which plural form goes with n, in the language's own order: English has one and other, Russian one (21),
 * few (22) and many (25). Russian says 1.5 with the few form.
 */
export function pluralIndex(lang: Lang, n: number): number {
  if (lang === 'ru' && !Number.isInteger(n)) return 1
  const r = pluralRules(lang)
  return r.forms.indexOf(r.select(n))
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
