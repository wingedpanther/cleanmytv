import { parseTheme } from '@shared/themes'
import type { HomeTheme } from '@shared/api'

/** A theme from themes/community, with who made it. */
export interface CommunityTheme extends HomeTheme {
  author: string
}

// Bundled with the app: one .cmtvtheme file each, read as text.
const files = import.meta.glob<string>('../../../../themes/community/*.cmtvtheme', { query: '?raw', import: 'default', eager: true })

/** Every community theme that loads, A to Z. A broken file is left out; the tests catch it first. */
export const COMMUNITY_THEMES: CommunityTheme[] = Object.values(files)
  .flatMap((text) => {
    try {
      const author = (JSON.parse(text) as { author?: unknown }).author
      return [{ ...parseTheme(text), wallpaper: undefined, author: typeof author === 'string' ? author.trim().slice(0, 40) : '' }]
    } catch {
      return []
    }
  })
  .sort((a, b) => a.name.localeCompare(b.name))
