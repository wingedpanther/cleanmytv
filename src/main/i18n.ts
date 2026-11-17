// Messages the window shows, in the window's language. The window says which one once it opens;
// until then the computer's language counts. The log stays in English.
import { currentLang, fill, isLang, langOf, setLang, translate, type Vars } from '@shared/i18n'

export { t } from '@shared/i18n'

export function setLanguage(l: unknown): void {
  setLang(isLang(l) ? l : 'en')
}

/** From the system language, for messages sent before the window has said. */
export function languageFromLocale(locale: string): void {
  setLang(langOf(locale))
}

/** An error for the window, which also keeps the English for the log. */
export class UiError extends Error {
  readonly english: string
  constructor(english: string, vars?: Vars) {
    super(translate(currentLang(), english, vars))
    this.english = fill('en', english, vars)
  }
}

/** What the log should say about an error: the English when there is one. */
export const englishOf = (e: unknown): string => (e instanceof UiError ? e.english : e instanceof Error ? e.message : String(e))
