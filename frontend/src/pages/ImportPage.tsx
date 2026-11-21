import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api'
import { useAuth } from '../auth'
import { pickGameText, savedChessUsername } from '../prefs'

/**
 * "/import" — the single entry point for games coming from OUTSIDE this site:
 *  - Android: Chess.com app → Share → "Chess Analyzer"  (manifest.json share_target → /import?text=…&url=…)
 *  - the browser extension → /import#text=<PGN or link>  (after "#", so long PGNs never hit the server logs)
 *  - any link like /import?text=https://www.chess.com/game/live/123
 * It imports the game and jumps straight to its analysis page.
 */
export default function ImportPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { user, loading } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const started = useRef(false) // React's dev mode runs effects twice; import only once

  const fromHash = new URLSearchParams(window.location.hash.slice(1)).get('text')
  const incoming = pickGameText(fromHash, params.get('text'), params.get('url'), params.get('title'))

  useEffect(() => {
    if (loading || !incoming || started.current) return
    started.current = true
    api.importGame(incoming, 'WHITE', savedChessUsername() ?? user?.username)
      .then((game) => navigate(`/games/${game.id}`, { replace: true }))
      .catch((e: Error) => setError(e.message))
  }, [loading, incoming, user, navigate])

  if (!incoming) {
    return (
      <section className="card">
        <h1>Nothing to import</h1>
        <p className="muted">Share a finished game from the Chess.com or Lichess app, or paste one on the analysis board.</p>
        <Link to="/analyze" className="btn primary">Open the analysis board</Link>
      </section>
    )
  }

  return (
    <section className="card import-card">
      {error ? (
        <>
          <h1>Couldn't import that game</h1>
          <p className="error">{error}</p>
          <pre className="import-preview">{incoming.slice(0, 300)}</pre>
          <div className="analyse-controls">
            <Link to="/analyze" state={{ text: incoming }} className="btn primary">Edit on the analysis board</Link>
          </div>
        </>
      ) : (
        <>
          <h1><span className="spinner inline" /> Importing your game…</h1>
          <p className="muted small">Fetching it and opening the analysis.</p>
          <pre className="import-preview">{incoming.slice(0, 300)}</pre>
        </>
      )}
    </section>
  )
}
