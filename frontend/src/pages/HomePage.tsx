import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { api, type GameSummary, type Page } from '../api'

/**
 * "/" — choose a player, sync from Chess.com, browse their games.
 * The username and page number live in the URL (?user=hikaru&page=2), so the
 * browser's Back button returns you to the same list after viewing a game.
 */
export default function HomePage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()
  const user = params.get('user') ?? ''
  const page = Number(params.get('page') ?? 0)

  const [input, setInput] = useState(user || readSavedUser())
  const [games, setGames] = useState<Page<GameSummary> | null>(null)
  const [message, setMessage] = useState<{ text: string; error?: boolean } | null>(null)
  const [syncing, setSyncing] = useState(false)
  const [reloadKey, setReloadKey] = useState(0) // bump to reload after a sync

  // First visit with a remembered username: show that player's games straight away.
  useEffect(() => {
    if (!user && input) setParams({ user: input, page: '0' }, { replace: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount only
  }, [])

  // Load the list whenever the user, page or reloadKey changes.
  useEffect(() => {
    if (!user) return
    let cancelled = false // ignore answers that arrive after the user moved on
    api
      .listGames(user, page)
      .then((data) => !cancelled && setGames(data))
      .catch((e: Error) => !cancelled && setMessage({ text: e.message, error: true }))
    return () => {
      cancelled = true
    }
  }, [user, page, reloadKey])

  function show(event: FormEvent) {
    event.preventDefault() // stop the browser's normal form submit (a page reload)
    const name = input.trim()
    if (!name) return
    saveUser(name)
    setMessage(null)
    setParams({ user: name, page: '0' })
  }

  async function sync() {
    const name = input.trim()
    if (!name) return
    saveUser(name)
    setSyncing(true)
    setMessage({ text: 'Importing games from Chess.com… the first sync can take a minute.' })
    try {
      const r = await api.syncPlayer(name)
      setMessage({
        text: `Imported ${r.imported} new games (${r.alreadyStored} already stored, ${r.skipped} variants skipped${r.failed ? `, ${r.failed} failed` : ''}).`,
      })
      setParams({ user: name, page: '0' })
      setReloadKey((k) => k + 1)
    } catch (e) {
      setMessage({ text: (e as Error).message, error: true })
    } finally {
      setSyncing(false)
    }
  }

  const goToPage = (p: number) => setParams({ user, page: String(p) })

  return (
    <>
      <section className="card hero">
        <h1>Review your games</h1>
        <p className="muted">
          Import your Chess.com games, replay them move by move, and let Stockfish find your best moves and blunders.
        </p>
        <form className="search" onSubmit={show}>
          <input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Chess.com username"
            aria-label="Chess.com username"
            autoComplete="off"
          />
          <button type="submit" className="btn primary">Show games</button>
          <button type="button" className="btn" onClick={sync} disabled={syncing || !input.trim()}>
            {syncing ? <span className="spinner" /> : '⟳'} Sync from Chess.com
          </button>
        </form>
        {message && <p className={message.error ? 'error' : 'muted'}>{message.text}</p>}
      </section>

      {user && games && (
        <section className="card">
          <div className="list-header">
            <h2>{user}'s games</h2>
            <span className="muted">{games.totalElements} games</span>
          </div>

          {games.totalElements === 0 ? (
            <p className="muted">No games stored yet. Click “Sync from Chess.com”.</p>
          ) : (
            <div className="game-list">
              {games.content.map((g) => (
                <GameRow key={g.id} game={g} onOpen={() => navigate(`/games/${g.id}`)} />
              ))}
            </div>
          )}

          {games.totalPages > 1 && (
            <div className="pager">
              <button className="btn" disabled={page === 0} onClick={() => goToPage(page - 1)}>‹ Newer</button>
              <span className="muted">Page {page + 1} of {games.totalPages}</span>
              <button className="btn" disabled={page + 1 >= games.totalPages} onClick={() => goToPage(page + 1)}>Older ›</button>
            </div>
          )}
        </section>
      )}
    </>
  )
}

/** One game in the list. Props = the inputs a component receives from its parent. */
function GameRow({ game, onOpen }: { game: GameSummary; onOpen: () => void }) {
  const youAreWhite = game.playerColor === 'WHITE'
  const opponent = youAreWhite ? game.black : game.white
  const opponentRating = youAreWhite ? game.blackRating : game.whiteRating
  const yourRating = youAreWhite ? game.whiteRating : game.blackRating

  return (
    <button className="game-row" onClick={onOpen}>
      <span className={`outcome ${game.outcome}`}>{game.outcome === 'UNKNOWN' ? '–' : game.outcome[0]}</span>
      <span className="game-main">
        <span className="game-opponent">
          <span className={`piece-dot ${youAreWhite ? 'black' : 'white'}`} />
          vs {opponent} <span className="muted">({opponentRating ?? '?'})</span>
        </span>
        <span className="muted small">
          You {yourRating ?? '?'} · {game.result} · {game.openingEco ?? '—'}
        </span>
      </span>
      <span className="game-meta">
        <span className="tag">{game.timeClass ?? '?'}</span>
        <span className="muted small">{new Date(game.playedAt).toLocaleDateString()}</span>
      </span>
    </button>
  )
}

// localStorage can throw (private mode, blocked storage), so always wrap it.
function readSavedUser(): string {
  try {
    return localStorage.getItem('username') ?? ''
  } catch {
    return ''
  }
}

function saveUser(name: string) {
  try {
    localStorage.setItem('username', name)
  } catch {
    // ignore
  }
}
