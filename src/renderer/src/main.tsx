import '@fontsource-variable/jetbrains-mono'
import './styles.css'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { installMock } from './mock'
import App from './App'
import { lang } from './lib/i18n'

installMock()
// Before anything else, so the main process answers in the same language.
void window.cytv.setLanguage(lang)

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <App />
  </StrictMode>
)
