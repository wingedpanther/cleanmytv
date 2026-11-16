// The log stays in English: this runs a message builder once more in English, for the log line.
import { currentLang, setLang } from '@shared/i18n'

export function inEnglish<T>(fn: () => T): T {
  const lang = currentLang()
  setLang('en')
  try {
    return fn()
  } finally {
    setLang(lang)
  }
}
