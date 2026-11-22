import { useCallback, useEffect, useState, type CSSProperties } from 'react'
import { Chessboard } from 'react-chessboard'
import { Link, useNavigate, useParams } from 'react-router'
import { api, type Color, type LegalMove, type OnlineGame } from '../api'
import { useAuth } from '../auth'
import { formatClock, timeLabel } from '../clock'
import MoveList from '../components/MoveList'
import { useTopic } from '../live'
import { useTheme } from '../theme'
import { useBoardInput } from '../boardInput'

/**
 * "/online/:id" — a live game. The SERVER owns the game: we send a move with a POST,
 * and everyone (us, the opponent, spectators) receives the new state over the
 * WebSocket on /topic/games/{id}. The page just draws the latest state.
 */
export default function OnlineGamePage() {
  const { id = '' } = useParams()
  const { user } = useAuth()
  const navigate = useNavigate()
  // The game plus WHEN we received it: the clocks count down from that moment.
  const [state, setState] = useState<{ game: OnlineGame; receivedAt: number } | null>(null)
  const [legal, setLegal] = useState<{ fen: string; moves: LegalMove[] } | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [boardKey, setBoardKey] = useState(0) // bump to reset the board after a rejected move
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const { boardColors } = useTheme()

  const receive = useCallback((game: OnlineGame) => setState({ game, receivedAt: Date.now() }), [])
  const reload = useCallback(() => {
    api.onlineGame(id).then(receive).catch((e: Error) => setError(e.message))
  }, [id, receive])

  useEffect(reload, [reload])
  useTopic<OnlineGame>(`/topic/games/${id}`, receive, reload) // live updates (and re-fetch after reconnecting)

  const game = state?.game
  const you: Color | null = !game || !user ? null
    : game.white?.username === user.username ? 'WHITE'
      : game.black?.username === user.username ? 'BLACK' : null
  const yourTurn = game?.status === 'ACTIVE' && you === game.sideToMove

  // Legal moves for a dropped piece, fetched when it's our turn.
  const fen = game?.fen
  useEffect(() => {
    if (!yourTurn || !fen) return
    let cancelled = false
    api.legalMoves(fen).then((m) => !cancelled && setLegal({ fen, moves: m })).catch(() => {})
    return () => {
      cancelled = true
    }
  }, [yourTurn, fen])

  // Redraw the clocks 5x per second while one is running.
  const ticking = game?.clockRunning ?? false
  useEffect(() => {
    if (!ticking) return
    const timer = setInterval(() => setNow(Date.now()), 200)
    return () => clearInterval(timer)
  }, [ticking])

  // Drag or tap to move: only on your turn (hooks must run before the early returns below).
  const input = useBoardInput({
    fen: game?.fen ?? '',
    legal: legal && legal.fen === game?.fen ? legal.moves : null,
    enabled: yourTurn,
    onMove: (move) => sendMove(move),
    premoveColor: game?.status === 'ACTIVE' && you ? (you === 'WHITE' ? 'w' : 'b') : null, // move ahead while they think
  })

  if (error && !game) return <p className="error">{error} <Link to="/online">Back to the lobby</Link></p>
  if (!game || !state) return <p className="muted">Loading game…</p>

  /** Sends your move to the server; the new state comes back (and to your opponent via the WebSocket). */
  function sendMove(move: LegalMove) {
    api.moveOnline(game!.id, move.uci).then(receive).catch((e: Error) => {
      setError(e.message) // e.g. "It's not your turn" or you just ran out of time
      setBoardKey((k) => k + 1) // put the piece back
      reload()
    })
  }

  async function act(action: () => Promise<OnlineGame>) {
    try {
      setError(null)
      receive(await action())
    } catch (e) {
      setError((e as Error).message)
    }
  }

  /** Remaining time for a side right now: the server's value minus what passed since we received it. */
  const clockOf = (color: Color): number | null => {
    const ms = color === 'WHITE' ? game.whiteMs : game.blackMs
    if (ms === null || game.status === 'WAITING') return ms
    return game.clockRunning && game.sideToMove === color ? ms - (now - state.receivedAt) : ms
  }

  const bottom: Color = you ?? 'WHITE'
  const top: Color = bottom === 'WHITE' ? 'BLACK' : 'WHITE'
  const lastMove = game.moves.at(-1)?.uci
  const squareStyles: Record<string, CSSProperties> = lastMove
    ? { [lastMove.slice(0, 2)]: { background: 'rgba(255, 214, 79, 0.55)' }, [lastMove.slice(2, 4)]: { background: 'rgba(255, 214, 79, 0.55)' } }
    : {}
  const inviteLink = `${location.origin}/online/${game.id}`
  const isCreator = user?.username === game.creator

  return (
    <div className="game-layout">
      <div className="board-column">
        <PlayerRow color={top} game={game} ms={clockOf(top)} />
        <div className="board">
          <Chessboard key={boardKey} options={{
            id: 'online-board',
            position: game.fen,
            boardOrientation: bottom === 'WHITE' ? 'white' : 'black',
            allowDragging: game.status === 'ACTIVE' && you !== null,
            canDragPiece: input.canDragPiece,
            onPieceDrop: input.onPieceDrop,
            onSquareClick: input.onSquareClick,   // tap a piece, then tap where it should go
            onSquareMouseDown: input.onSquareMouseDown, // right-click marks a square, right-drag draws an arrow
            onSquareMouseUp: input.onSquareMouseUp,
            squareStyles: { ...squareStyles, ...input.inputStyles },
            animationDurationInMs: 150,
            ...boardColors,
          }} />
        </div>
        <PlayerRow color={bottom} game={game} ms={clockOf(bottom)} />
      </div>

      <aside className="side-column">
        <div className="card compact">
          <strong>Online game · {timeLabel(game.initialMs, game.incrementMs)}</strong>

          {game.status === 'WAITING' && isCreator && (
            <>
              <span className="muted small"><span className="spinner inline" /> Waiting for an opponent. Send them this link:</span>
              <div className="invite">
                <input readOnly value={inviteLink} onFocus={(e) => e.target.select()} aria-label="Invite link" />
                <button className="btn" onClick={() => { void navigator.clipboard?.writeText(inviteLink); setCopied(true) }}>
                  {copied ? 'Copied' : 'Copy'}
                </button>
              </div>
              <button className="btn" onClick={() => act(() => api.cancelOnline(game.id))}>Cancel game</button>
            </>
          )}
          {game.status === 'WAITING' && !isCreator && (
            <>
              <span className="muted small">{game.creator} is waiting for an opponent.</span>
              <button className="btn primary" onClick={() => act(() => api.joinOnline(game.id))}>
                Join as {game.white ? 'Black' : 'White'}
              </button>
            </>
          )}
          {game.status === 'ACTIVE' && (
            <>
              <span className="muted small">
                {you === null ? 'You are watching.' : yourTurn ? 'Your move: drag or tap a piece.' : 'Waiting for your opponent…'}
              </span>
              {you && <button className="btn" onClick={() => act(() => api.resignOnline(game.id))}>⚑ Resign</button>}
            </>
          )}
          {game.status === 'FINISHED' && (
            <>
              <OnlineResult game={game} you={you} />
              {game.analysisGameId && (
                <button className="btn primary" onClick={() => navigate(`/games/${game.analysisGameId}`)}>Analyse this game</button>
              )}
              <Link to="/online" className="btn">Back to the lobby</Link>
            </>
          )}
          {game.status === 'ABORTED' && (
            <>
              <span>This game was cancelled.</span>
              <Link to="/online" className="btn">Back to the lobby</Link>
            </>
          )}
          {error && <span className="error small">{error}</span>}
        </div>

        {game.moves.length > 0 && (
          <div className="card compact">
            <MoveList moves={game.moves.map((m, i) => ({ ...m, ply: i + 1 }))} evals={new Map()}
                      currentPly={game.moves.length} onSelect={() => {}} />
          </div>
        )}
      </aside>
    </div>
  )
}

function PlayerRow({ color, game, ms }: { color: Color; game: OnlineGame; ms: number | null }) {
  const player = color === 'WHITE' ? game.white : game.black
  const running = game.clockRunning && game.sideToMove === color
  return (
    <div className="player-bar">
      <span className={`piece-dot ${color === 'WHITE' ? 'white' : 'black'}`} />
      <strong>{player?.username ?? 'Waiting…'}</strong>
      {player?.guest && <span className="tag plain">guest</span>}
      {ms !== null && (
        <span className={`clock ${running ? 'running' : ''} ${running && ms < 20_000 ? 'low' : ''}`}>{formatClock(ms)}</span>
      )}
    </div>
  )
}

function OnlineResult({ game, you }: { game: OnlineGame; you: Color | null }) {
  const draw = game.result === '1/2-1/2'
  const whiteWon = game.result === '1-0'
  const text = draw ? `Draw by ${game.reason}`
    : you === null ? `${whiteWon ? 'White' : 'Black'} won by ${game.reason}`
      : (whiteWon === (you === 'WHITE')) ? `You won by ${game.reason}` : `You lost by ${game.reason}`
  const tone = draw || you === null ? 'draw' : whiteWon === (you === 'WHITE') ? 'win' : 'loss'
  return <div className={`result-banner ${tone}`}>{text} <span>{game.result}</span></div>
}
