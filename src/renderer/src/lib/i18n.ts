// The window's language: the owner's choice, or the computer's. Changing it reloads the window,
// so every text, even the ones worked out once at start, comes out in the new language.
import { Fragment, createElement, type ReactNode } from 'react'
import { langOf, pieces, setLang, translated, type Lang } from '@shared/i18n'

export { t } from '@shared/i18n'

export type LangChoice = 'auto' | Lang

const KEY = 'language'

export function langChoice(): LangChoice {
  // Outside a window (the tests) there's no saved choice.
  if (typeof window === 'undefined') return 'auto'
  try {
    const v = localStorage.getItem(KEY)
    return v === 'en' || v === 'ru' ? v : 'auto'
  } catch {
    return 'auto'
  }
}

export function setLangChoice(choice: LangChoice): void {
  try {
    if (choice === 'auto') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, choice)
  } catch {
    // Storage is off: the choice lasts until the window reloads, which is now.
  }
  location.reload()
}

const choice = langChoice()
export const lang: Lang = choice === 'auto' ? langOf(typeof navigator === 'undefined' ? undefined : navigator.language) : choice
setLang(lang)
if (typeof document !== 'undefined') document.documentElement.lang = lang

/** The same for a sentence with markup in it: each {name} is a piece of JSX, a string or a number. */
export function tx(english: string, parts: Record<string, ReactNode>): ReactNode {
  const tr = translated(lang, english)
  return pieces<ReactNode>(tr.lang, tr.text, parts as Record<string, ReactNode | string | number>).map((p, i) => createElement(Fragment, { key: i }, p))
}
