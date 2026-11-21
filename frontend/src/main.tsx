// Entry point: React takes over the <div id="root"> in index.html.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'
import { AuthProvider } from './auth.tsx'
import { ThemeProvider } from './theme.tsx'

// Register the service worker (public/sw.js) so phones offer "Install app".
// Browsers only allow this on HTTPS sites (and on localhost for development).
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(() => {})
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* BrowserRouter lets us have real URLs like /games/42 without reloading the page. */}
    <BrowserRouter>
      {/* AuthProvider: every page can ask "who is logged in?" with useAuth() */}
      <ThemeProvider>
        <AuthProvider>
          <App />
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  </StrictMode>,
)
