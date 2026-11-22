import { useCallback, useEffect, useState, type CSSProperties } from 'react'
import { Chessboard } from 'react-chessboard'
import { Link } from 'react-router'
import { api, type LegalMove, type NextPuzzle, type PuzzleAttempt } from '../api'
import { useBoardInput } from '../boardInput'
import { CLASSIFICATION } from '../chess'
import { savedChessUsername } from '../prefs'
import { useTheme } from '../theme'

/** "in 5 minutes", "in 2 days"… */
function fromNow(iso: string | null): string {
  if (!iso) return ''
  const seconds = (new Date(iso).getTime() - Date.now()) / 1000
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })
  if (Math.abs(seconds) < 3600) return rtf.format(Math.round(seconds / 60), 'minute')
  if (Math.abs(seconds) < 86400) return rtf.format(Math.round(seconds / 3600), 'hour')
  return rtf.format(Math.round(seconds / 86400), 'day')
}

/**
 * "/puzzles" — train on your own mistakes. Each puzzle is a position from one of
 * your games where you played a mistake or blunder; find the better move.
 * Solved puzzles come back after 1, 2, 4, 8… days; missed ones come back soon.
 */
export default function PuzzlesPage() {
  const { boardColors } = useTheme()
  const username = savedChessUsername()
  const [onlyMine, setOnlyMine] = useState(!!username)
  const [data, setData] = useState<NextPuzzle | null>(null)
  const [legal, setLegal] = useState<{ fen: string; moves: LegalMove[] } | null>(null)
  const [result, setResult] = useState<(PuzzleAttempt & { yourUci?: string }) | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const player = onlyMine ? username : undefined
  const load = useCallback(() => {
    api.nextPuzzle(player)
      .then((next) => {
        setData(next)
        setResult(null)
        setError(null)
      })
      .catch((e: Error) => setError(e.message))
  }, [player])
  useEffect(load, [load])

  const puzzle = data?.puzzle ?? null
  const fen = puzzle?.fen ?? ''

  // Legal moves of the puzzle position, so a dragged/tapped move can be checked instantly.
  useEffect(() => {
    if (!fen) return
    let cancelled = false
    api.legalMoves(fen).then((m) => !cancelled && setLegal({ fen, moves: m })).catch(() => {})
    return () => {
      cancelled = true
    }
  }, [fen])

  async function answer(move: LegalMove) {
    if (!puzzle) return
    setBusy(true) // Stockfish may need a moment to judge a different move
    try {
      setResult({ ...(await api.attemptPuzzle(puzzle.id, move.uci)), yourUci: move.uci })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  async function reveal() {
    if (!puzzle) return
    setResult(await api.revealPuzzle(puzzle.id))
  }

  const input = useBoardInput({
    fen,
    legal: legal && legal.fen === fen ? legal.moves : null,
    enabled: !!puzzle && !result && !busy,
    onMove: (move) => void answer(move),
  })

  // After answering: green arrow = the engine's move, red arrow = your move (if it was wrong).
  const arrows = result
    ? [
        { startSquare: result.solutionUci.slice(0, 2), endSquare: result.solutionUci.slice(2, 4), color: 'rgba(46, 157, 79, 0.85)' },
        ...(result.yourUci && !result.correct
          ? [{ startSquare: result.yourUci.slice(0, 2), endSquare: result.yourUci.slice(2, 4), color: 'rgba(214, 69, 69, 0.8)' }]
          : []),
      ]
    : []
  const squareStyles: Record<string, CSSProperties> = { ...input.inputStyles }

  const header = (
    <div className="card compact">
      <div className="puzzle-head">
        <strong>🧩 Your puzzles</strong>
        {data && <span className="muted small">{data.due} due · {data.total} total</span>}
      </div>
      {username && (
        <div className="segmented">
          <button className={`seg ${onlyMine ? 'active' : ''}`} onClick={() => setOnlyMine(true)}>{username}'s games</button>
          <button className={`seg ${!onlyMine ? 'active' : ''}`} onClick={() => setOnlyMine(false)}>All analysed games</button>
        </div>
      )}
      <span className="muted small">
        Made from your mistakes and blunders whenever a game is analysed with Stockfish. Solve one and it comes back
        in 1, 2, 4, 8… days; miss it and it returns in a few minutes.
      </span>
    </div>
  )

  if (error && !data) return <p className="error">{error}</p>
  if (!data) return <p className="muted">Loading puzzles…</p>

  if (!puzzle) {
    return (
      <div className="online-layout">
        {header}
        <div className="card">
          {data.total === 0 ? (
            <>
              <h2>No puzzles yet</h2>
              <p className="muted">
                Open one of your games and click <strong>Analyse with Stockfish</strong>. Every mistake or blunder
                you made becomes a puzzle here.
              </p>
              <Link to="/" className="btn primary">Go to my games</Link>
            </>
          ) : (
            <>
              <h2>All caught up 🎉</h2>
              <p className="muted">The next puzzle is due {fromNow(data.nextDueAt)}.</p>
            </>
          )}
        </div>
      </div>
    )
  }

  const you = puzzle.sideToMove
  const opponent = you === 'WHITE' ? puzzle.black : puzzle.white
  const meta = CLASSIFICATION[puzzle.classification]

  return (
    <div className="game-layout">
      <div className="board-column">
        <div className="board">
          <Chessboard options={{
            id: 'puzzle-board',
            position: fen,
            boardOrientation: you === 'WHITE' ? 'white' : 'black',
            allowDragging: !result && !busy,
            canDragPiece: input.canDragPiece,
            onPieceDrop: input.onPieceDrop,
            onSquareClick: input.onSquareClick,
            onSquareMouseDown: input.onSquareMouseDown, // right-click marks a square, right-drag draws an arrow
            onSquareMouseUp: input.onSquareMouseUp,
            arrows,
            squareStyles,
            animationDurationInMs: 150,
            ...boardColors,
          }} />
        </div>
      </div>

      <aside className="side-column">
        {header}
        <div className="card compact insight" style={{ borderLeftColor: result ? (result.correct ? 'var(--best)' : 'var(--blunder)') : meta.color }}>
          <strong>{you === 'WHITE' ? 'White' : 'Black'} to move: find a better move</strong>
          <span className="small">
            In your game vs <strong>{opponent}</strong> ({new Date(puzzle.playedAt).toLocaleDateString()}) you played{' '}
            <strong>{puzzle.playedSan}</strong>{' '}
            <span className="badge" style={{ background: meta.color }}>{meta.symbol} {meta.label}</span>
          </span>

          {busy && <span className="muted small"><span className="spinner inline" /> Checking your move…</span>}

          {result && result.correct && (
            <div className="result-banner win">
              {result.exactMatch ? `✓ ${result.solutionSan} — correct!` : `✓ ${result.yourMoveSan} also works! (Stockfish: ${result.solutionSan})`}
            </div>
          )}
          {result && !result.correct && (
            <div className="result-banner loss">
              {result.yourMoveSan ? `✗ ${result.yourMoveSan} isn't it` : 'Solution'} — best was {result.solutionSan}
            </div>
          )}
          {result && <span className="muted small">This puzzle comes back {fromNow(result.nextDueAt)}.</span>}

          <div className="analyse-controls">
            {!result
              ? <button className="btn" onClick={reveal} disabled={busy}>Show solution</button>
              : <button className="btn primary" onClick={load}>Next puzzle →</button>}
            <Link to={`/games/${puzzle.gameId}`} className="btn">Open the game</Link>
          </div>
          {error && <span className="error small">{error}</span>}
        </div>
        <span className="muted small">Tap or drag a piece to answer. Tried {puzzle.attempts}×, solved {puzzle.solved}×.</span>
      </aside>
    </div>
  )
}
