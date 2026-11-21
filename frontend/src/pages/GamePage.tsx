import { useEffect, useState, type CSSProperties } from 'react'
import { Chessboard } from 'react-chessboard'
import { Link, useParams } from 'react-router'
import {
  api, type Analysis, type Color, type EngineName, type GameDetail, type LegalMove, type MoveEval, type PositionEval,
} from '../api'
import { CLASSIFICATION, formatEval, moverOf, whiteWinPercent } from '../chess'
import EvalBar from '../components/EvalBar'
import EvalGraph from '../components/EvalGraph'
import MoveList from '../components/MoveList'
import VariationCard from '../components/VariationCard'
import { asMoveEval, playInVariation, stepState, type Variation } from '../variation'
import { useTheme } from '../theme'

/** The two engines the backend offers, with sensible depth choices for each. */
const ENGINES: Record<EngineName, { label: string; depths: { value: number; label: string }[]; defaultDepth: number }> = {
  stockfish: {
    label: 'Stockfish 17',
    depths: [
      { value: 12, label: 'Fast (depth 12)' },
      { value: 16, label: 'Normal (depth 16)' },
      { value: 20, label: 'Deep (depth 20)' },
    ],
    defaultDepth: 16,
  },
  'my-engine': {
    label: 'My Engine (Java)',
    depths: [
      { value: 3, label: 'Fast (depth 3)' },
      { value: 4, label: 'Normal (depth 4)' },
      { value: 5, label: 'Deep (depth 5)' },
    ],
    defaultDepth: 4,
  },
}

/**
 * "/games/:id" — replay one game and analyse it with Stockfish.
 *
 * React basics used here:
 *  - useState: a value that, when changed with its setter, re-draws the component.
 *  - useEffect: run code AFTER drawing (load data, start timers, add key listeners);
 *    the function it returns cleans up (cancel timer, remove listener).
 *  - Everything else (fen, arrows, highlights) is computed from state on each draw.
 */
export default function GamePage() {
  const { id = '' } = useParams() // from the URL /games/:id
  const [game, setGame] = useState<GameDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [ply, setPly] = useState(0) // 0 = start position, N = after move N
  const [flipped, setFlipped] = useState(false)
  const [analysis, setAnalysis] = useState<Analysis | null>(null)
  const [engine, setEngine] = useState<EngineName>('stockfish')
  const [depth, setDepth] = useState(ENGINES.stockfish.defaultDepth)
  const [starting, setStarting] = useState(false)
  const { boardColors } = useTheme() // square colours from the 🎨 menu
  // Your own line, if you've dragged a piece (null = following the real game).
  const [variation, setVariation] = useState<Variation | null>(null)
  // Legal moves / live engine eval, each remembered together with the FEN they belong to.
  const [legal, setLegal] = useState<{ fen: string; moves: LegalMove[] } | null>(null)
  const [liveEval, setLiveEval] = useState<{ fen: string; eval: PositionEval } | null>(null)

  // The position on the board: inside your variation, or the game's position at `ply`.
  const mainFen = game ? (ply === 0 ? game.startFen : game.moves[ply - 1].fenAfter) : null
  const exploringFen = variation && variation.index > 0 ? variation.moves[variation.index - 1].fenAfter : null
  const boardFen = exploringFen ?? mainFen

  // 1. Load the game. (App gives this page key={id}, so a different game id
  //    creates a brand-new GamePage with fresh state.)
  useEffect(() => {
    let cancelled = false
    api.getGame(id)
      .then((g) => {
        if (cancelled) return
        setGame(g)
        setFlipped(g.summary.playerColor === 'BLACK') // your pieces at the bottom
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [id])

  // 2. Load the latest analysis by the chosen engine (again whenever the engine changes).
  const gameId = game?.summary.id
  useEffect(() => {
    if (gameId === undefined) return
    let cancelled = false
    api.getAnalysis(gameId, engine)
      .then((a) => !cancelled && setAnalysis(a))
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [gameId, engine])

  // 3. While an analysis runs, ask the server for progress every second.
  const running = analysis?.status === 'PENDING' || analysis?.status === 'RUNNING'
  useEffect(() => {
    if (!running || gameId === undefined) return
    const timer = setTimeout(() => {
      api.getAnalysis(gameId, engine).then(setAnalysis).catch((e: Error) => setError(e.message))
    }, 1000)
    return () => clearTimeout(timer)
  }, [analysis, running, gameId, engine])

  function chooseEngine(name: EngineName) {
    setEngine(name)
    setDepth(ENGINES[name].defaultDepth)
  }

  // 4. Fetch the legal moves of whatever is on the board, so a dragged piece can be checked instantly.
  useEffect(() => {
    if (!boardFen) return
    let cancelled = false
    api.legalMoves(boardFen).then((m) => !cancelled && setLegal({ fen: boardFen, moves: m })).catch(() => {})
    return () => {
      cancelled = true
    }
  }, [boardFen])

  // 5. While exploring, ask Stockfish about each new position.
  useEffect(() => {
    if (!exploringFen) return
    let cancelled = false
    api.evaluatePosition(exploringFen, 'stockfish', 14)
      .then((e) => !cancelled && setLiveEval({ fen: exploringFen, eval: e }))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [exploringFen])

  // 6. Keyboard: ← → step (inside the variation if one is open), Home/End jump, Esc leaves the variation.
  useEffect(() => {
    if (!game) return
    const total = game.moves.length
    function onKey(event: KeyboardEvent) {
      if (event.target instanceof HTMLInputElement || event.target instanceof HTMLSelectElement) return
      if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
        const [v, p] = stepState(variation, ply, event.key === 'ArrowLeft' ? -1 : 1, total)
        setVariation(v)
        setPly(p)
      } else if (event.key === 'Home' || event.key === 'End') {
        setVariation(null)
        setPly(event.key === 'Home' ? 0 : total)
      } else if (event.key === 'Escape' && variation) {
        setVariation(null)
      } else {
        return
      }
      event.preventDefault()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [game, variation, ply])

  async function startAnalysis() {
    if (!game) return
    setStarting(true)
    try {
      setAnalysis(await api.startAnalysis(game.summary.id, engine, depth))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setStarting(false)
    }
  }

  if (error) return <p className="error">{error} <Link to="/">Back to games</Link></p>
  if (!game) return <p className="muted">Loading game…</p>

  // ---------- everything below is derived from state ----------
  const { summary, moves, startFen } = game
  const total = moves.length
  const fen = boardFen ?? startFen

  /** Jump to a game move (closes any variation). */
  const go = (p: number) => {
    setVariation(null)
    setPly(Math.max(0, Math.min(total, p)))
  }
  const step = (delta: number) => {
    const [v, p] = stepState(variation, ply, delta, total)
    setVariation(v)
    setPly(p)
  }

  const evals = analysis?.status === 'DONE' ? analysis.moves : []
  const evalByPly = new Map(evals.map((e) => [e.ply, e]))
  const currentEval = evalByPly.get(ply)
  const hasEvals = evals.length > 0
  const exploring = variation !== null && variation.index > 0
  const variationEval = exploring && liveEval?.fen === exploringFen ? liveEval.eval : null
  const variationPly = variation ? variation.fromPly + variation.index : ply

  // Highlight the last move. Game moves: yellow, or red/orange for a mistake/blunder. Your moves: blue.
  let lastMove: string | null = ply > 0 ? moves[ply - 1].uci : null
  let tint = currentEval && ['INACCURACY', 'MISTAKE', 'BLUNDER'].includes(currentEval.classification)
    ? `color-mix(in srgb, ${CLASSIFICATION[currentEval.classification].color} 60%, transparent)`
    : 'rgba(255, 214, 79, 0.55)'
  if (exploring) {
    lastMove = variation.moves[variation.index - 1].uci
    tint = 'rgba(90, 160, 255, 0.5)'
  }
  const squareStyles: Record<string, CSSProperties> = lastMove
    ? { [lastMove.slice(0, 2)]: { background: tint }, [lastMove.slice(2, 4)]: { background: tint } }
    : {}

  // Green arrow = the engine's best move in the CURRENT position
  // (for game positions it's stored with the next move; for your own line it's the live eval).
  const nextBest = evalByPly.get(ply + 1)
  const bestUci = exploring ? variationEval?.bestMoveUci : nextBest?.bestMoveUci
  const arrows = bestUci
    ? [{ startSquare: bestUci.slice(0, 2), endSquare: bestUci.slice(2, 4), color: 'rgba(129, 182, 76, 0.85)' }]
    : []

  // Eval bar: live eval inside your variation, otherwise the stored analysis.
  const barEval = exploring ? (variationEval ? asMoveEval(variationEval, variationPly) : undefined) : currentEval
  const showBar = exploring ? variationEval !== null : hasEvals
  const barPly = exploring ? variationPly : ply

  /** Only the side to move may pick up a piece ("wP" = white pawn, FEN field 2 = "w" or "b"). */
  const sideToMove = fen.split(' ')[1]
  const canDragPiece = ({ piece }: { piece: { pieceType: string } }) => piece.pieceType[0] === sideToMove

  /**
   * Called by the board when you drop a piece. Return true = keep it there, false = snap back.
   * We check it against the legal moves the backend gave us for this exact position.
   */
  function onPieceDrop({ sourceSquare, targetSquare }: { sourceSquare: string; targetSquare: string | null }): boolean {
    if (!targetSquare || legal?.fen !== fen) return false
    const candidates = legal.moves.filter((m) => m.uci.startsWith(sourceSquare + targetSquare))
    if (candidates.length === 0) return false                                  // illegal move
    const move = candidates.find((m) => m.uci.length === 4 || m.uci.endsWith('q'))! // promotions: always a queen

    // Played the same move as the real game, from a game position? Just follow the game.
    const atGamePosition = !variation || variation.index === 0
    const gamePly = variation?.fromPly ?? ply
    if (atGamePosition && moves[gamePly]?.uci === move.uci) {
      go(gamePly + 1)
      return true
    }
    setVariation(playInVariation(variation, ply, move))
    return true
  }

  const bottom: Color = flipped ? 'BLACK' : 'WHITE'
  const top: Color = flipped ? 'WHITE' : 'BLACK'

  return (
    <div className="game-layout">
      {/* ----- left: board ----- */}
      <div className="board-column">
        <PlayerBar color={top} game={game} analysis={analysis} />
        <div className="board-wrap">
          {showBar && (
            <EvalBar whitePercent={barPly === 0 ? 50 : whiteWinPercent(barEval, barPly, startFen)}
                     label={barPly === 0 ? '0.0' : formatEval(barEval, barPly, startFen)} flipped={flipped} />
          )}
          <div className="board">
            <Chessboard options={{
              id: 'game-board',
              position: fen,
              boardOrientation: flipped ? 'black' : 'white',
              allowDragging: true,
              canDragPiece,
              onPieceDrop,
              allowDrawingArrows: false,
              arrows,
              squareStyles,
              animationDurationInMs: 150,
              ...boardColors,
            }} />
          </div>
        </div>
        <PlayerBar color={bottom} game={game} analysis={analysis} />

        <div className="controls">
          <button className="btn icon" onClick={() => go(0)} title="Start (Home)">⏮</button>
          <button className="btn icon" onClick={() => step(-1)} title="Previous (←)">◀</button>
          <span className="ply-counter">
            {variation ? `line ${variation.index} / ${variation.moves.length}` : `${ply} / ${total}`}
          </span>
          <button className="btn icon" onClick={() => step(1)} title="Next (→)">▶</button>
          <button className="btn icon" onClick={() => go(total)} title="End (End)">⏭</button>
          <button className="btn icon" onClick={() => setFlipped((f) => !f)} title="Flip board">⇅</button>
        </div>
        {!variation && <p className="hint muted small">Tip: drag a piece to try your own move from this position.</p>}

        {hasEvals && <EvalGraph evals={evals} startFen={startFen} currentPly={ply} onSelect={go} />}
      </div>

      {/* ----- right: info, analysis, moves ----- */}
      <aside className="side-column">
        <div className="card compact">
          <div className="game-title">
            <span className={`outcome ${summary.outcome}`}>{summary.outcome === 'UNKNOWN' ? '–' : summary.outcome[0]}</span>
            <div>
              <strong>{summary.white} vs {summary.black}</strong>
              <div className="muted small">
                {/* Only show the parts we know (a pasted FEN has no result, time control or opening). */}
                {[summary.result !== '*' && summary.result, summary.timeClass, summary.openingEco,
                  new Date(summary.playedAt).toLocaleDateString(), summary.source === 'PASTED' && 'pasted']
                  .filter(Boolean).join(' · ')}
                {game.chesscomUrl && <>{' · '}<a href={game.chesscomUrl} target="_blank" rel="noopener noreferrer">Chess.com ↗</a></>}
              </div>
            </div>
          </div>
        </div>

        {variation
          ? <VariationCard variation={variation} startFen={startFen} evaluation={variationEval}
                           onSelect={(index) => setVariation({ ...variation, index })}
                           onExit={() => setVariation(null)} />
          : <MoveInsight ply={ply} game={game} evaluation={currentEval} nextBest={nextBest} />}

        {total > 0 && (
          <div className="card compact">
            <MoveList moves={moves} evals={evalByPly} currentPly={ply} onSelect={go} />
          </div>
        )}

        {/* A pasted FEN has no moves to analyse; drag pieces to explore it instead. */}
        {total > 0 && (
          <AnalysisCard analysis={analysis} running={running} starting={starting}
                        engine={engine} setEngine={chooseEngine} depth={depth}
                        setDepth={setDepth} onStart={startAnalysis} startFen={startFen} />
        )}
      </aside>
    </div>
  )
}

/** Name, rating and (after analysis) accuracy, shown above/below the board. */
function PlayerBar({ color, game, analysis }: { color: Color; game: GameDetail; analysis: Analysis | null }) {
  const s = game.summary
  const name = color === 'WHITE' ? s.white : s.black
  const rating = color === 'WHITE' ? s.whiteRating : s.blackRating
  const accuracy = analysis?.status === 'DONE' ? (color === 'WHITE' ? analysis.accuracyWhite : analysis.accuracyBlack) : null
  return (
    <div className="player-bar">
      <span className={`piece-dot ${color === 'WHITE' ? 'white' : 'black'}`} />
      <strong>{name}</strong>
      <span className="muted">({rating ?? '?'})</span>
      {color === s.playerColor && <span className="tag you">you</span>}
      {accuracy !== null && <span className="accuracy" title="Accuracy">{accuracy.toFixed(1)}%</span>}
    </div>
  )
}

/** The box that explains the current move: its verdict and what was better. */
function MoveInsight({ ply, game, evaluation, nextBest }: {
  ply: number
  game: GameDetail
  evaluation: MoveEval | undefined
  nextBest: MoveEval | undefined
}) {
  if (ply === 0) {
    const noMoves = game.moves.length === 0   // a pasted FEN
    return (
      <div className="card compact insight">
        <strong>{noMoves ? 'Position from FEN' : 'Starting position'}</strong>
        <span className="muted small">
          {noMoves ? 'Drag a piece to start exploring. Stockfish evaluates every move you try.' : 'Use ← → or click a move.'}
        </span>
      </div>
    )
  }
  const move = game.moves[ply - 1]
  const white = moverOf(ply, game.startFen) === 'WHITE'
  const number = `${Math.ceil(ply / 2)}${white ? '.' : '...'}`
  const meta = evaluation ? CLASSIFICATION[evaluation.classification] : null

  return (
    <div className="card compact insight" style={meta ? { borderLeftColor: meta.color } : undefined}>
      <div className="insight-head">
        <strong>{number} {move.san}</strong>
        {meta && <span className="badge" style={{ background: meta.color }}>{meta.symbol} {meta.label}</span>}
        {evaluation && <span className="eval-chip">{formatEval(evaluation, ply, game.startFen)}</span>}
      </div>
      {evaluation && evaluation.classification !== 'BEST' && evaluation.bestMoveSan && (
        <span className="small">Best was <strong>{evaluation.bestMoveSan}</strong>
          {evaluation.cpLoss ? <span className="muted"> (you lost {(evaluation.cpLoss / 100).toFixed(1)} pawns)</span> : null}
        </span>
      )}
      {nextBest?.bestMoveSan && (
        <span className="muted small">Engine's move here: <strong className="best-text">{nextBest.bestMoveSan}</strong> (green arrow)</span>
      )}
    </div>
  )
}

/** Start an analysis, show its progress, then a summary of mistakes per player. */
function AnalysisCard({ analysis, running, starting, engine, setEngine, depth, setDepth, onStart, startFen }: {
  analysis: Analysis | null
  running: boolean
  starting: boolean
  engine: EngineName
  setEngine: (e: EngineName) => void
  depth: number
  setDepth: (d: number) => void
  onStart: () => void
  startFen: string
}) {
  const engineLabel = ENGINES[engine].label

  // Switching engine shows that engine's own analysis (or none yet).
  const engineTabs = (
    <div className="engine-tabs" role="tablist" aria-label="Engine">
      {(Object.keys(ENGINES) as EngineName[]).map((name) => (
        <button key={name} role="tab" aria-selected={name === engine}
                className={`engine-tab ${name === engine ? 'active' : ''}`}
                onClick={() => setEngine(name)} disabled={running}>
          {ENGINES[name].label}
        </button>
      ))}
    </div>
  )

  if (analysis && running) {
    const percent = analysis.totalPlies ? Math.round((analysis.analyzedPlies / analysis.totalPlies) * 100) : 0
    return (
      <div className="card compact">
        {engineTabs}
        <strong>{engineLabel} is analysing… </strong>
        <span className="muted small">{analysis.analyzedPlies} / {analysis.totalPlies || '?'} moves (depth {analysis.depth})</span>
        <div className="progress"><div style={{ width: `${percent}%` }} /></div>
      </div>
    )
  }

  const controls = (
    <div className="analyse-controls">
      <select value={depth} onChange={(e) => setDepth(Number(e.target.value))} aria-label="Analysis depth">
        {ENGINES[engine].depths.map((d) => <option key={d.value} value={d.value}>{d.label}</option>)}
      </select>
      <button className="btn primary" onClick={onStart} disabled={starting}>
        {analysis?.status === 'DONE' ? 'Re-analyse' : `Analyse with ${engineLabel}`}
      </button>
    </div>
  )

  if (analysis?.status !== 'DONE') {
    return (
      <div className="card compact">
        {engineTabs}
        <strong>Engine analysis</strong>
        <span className="muted small">
          {engine === 'my-engine'
            ? 'Your own Java engine: alpha-beta search + piece-square tables. Much weaker than Stockfish. Compare them!'
            : 'Find your best moves, mistakes and blunders.'}
        </span>
        {analysis?.status === 'FAILED' && <span className="error small">Last run failed: {analysis.error}</span>}
        {controls}
      </div>
    )
  }

  // Count verdicts per player.
  const kinds = ['BEST', 'INACCURACY', 'MISTAKE', 'BLUNDER'] as const
  const count = (color: Color, kind: string) =>
    analysis.moves.filter((m) => m.classification === kind && moverOf(m.ply, startFen) === color).length

  return (
    <div className="card compact">
      {engineTabs}
      <strong>Analysis <span className="muted small">{engineLabel} · depth {analysis.depth}</span></strong>
      <table className="summary">
        <thead>
          <tr><th /><th>White</th><th>Black</th></tr>
        </thead>
        <tbody>
          <tr>
            <td>Accuracy</td>
            <td><strong>{analysis.accuracyWhite?.toFixed(1) ?? '–'}%</strong></td>
            <td><strong>{analysis.accuracyBlack?.toFixed(1) ?? '–'}%</strong></td>
          </tr>
          {kinds.map((k) => (
            <tr key={k}>
              <td><span style={{ color: CLASSIFICATION[k].color }}>{CLASSIFICATION[k].symbol}</span> {CLASSIFICATION[k].label}</td>
              <td>{count('WHITE', k)}</td>
              <td>{count('BLACK', k)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {controls}
    </div>
  )
}
