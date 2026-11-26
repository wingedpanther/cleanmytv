// Vite's import.meta.glob, for the files bundled with the app (community themes).
interface ImportMeta {
  glob<T = unknown>(pattern: string, options: { query?: string; import?: string; eager: true }): Record<string, T>
}
