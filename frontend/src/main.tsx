// Entry point: React takes over the <div id="root"> in index.html.
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* BrowserRouter lets us have real URLs like /games/42 without reloading the page. */}
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </StrictMode>,
)
