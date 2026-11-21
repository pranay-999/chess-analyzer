import { useEffect, useState, type CSSProperties } from 'react'
import { Chessboard } from 'react-chessboard'
import { useNavigate } from 'react-router'
import { api, type BotLevel, type Color, type GameStatus, type LegalMove } from '../api'
import { formatClock, newClock, pressClock, remaining, TIME_CONTROLS, type Clock, type TimeControl } from '../clock'
import MoveList from '../components/MoveList'
import { useTheme } from '../theme'

const START_FEN = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1'

const REASONS: Record<Exclude<GameStatus, 'ONGOING'>, string> = {
  CHECKMATE: 'checkmate',
  STALEMATE: 'stalemate',
  THREEFOLD_REPETITION: 'threefold repetition',
  FIFTY_MOVE_RULE: 'the 50-move rule',
  INSUFFICIENT_MATERIAL: 'insufficient material',
}

interface GameEnd {
  result: '1-0' | '0-1' | '1/2-1/2'
  reason: string // "checkmate", "timeout", "resignation", ...
}

/** Everything about the game in progress. Replaced (never mutated) on every change. */
interface BotGame {
  id: number // a new number per game, so a slow bot reply from an OLD game is ignored
  you: Color
  bot: BotLevel
  moves: LegalMove[]
  clock: Clock | null
  end: GameEnd | null
}

const sideToMoveOf = (fen: string): Color => (fen.split(' ')[1] === 'w' ? 'WHITE' : 'BLACK')
const other = (c: Color): Color => (c === 'WHITE' ? 'BLACK' : 'WHITE')
const lossFor = (c: Color): GameEnd['result'] => (c === 'WHITE' ? '0-1' : '1-0')

/** Adds a move (yours or the bot's) and presses the mover's clock. */
function addMove(game: BotGame, move: LegalMove, now: number): BotGame {
  const fen = game.moves.at(-1)?.fenAfter ?? START_FEN
  const mover = sideToMoveOf(fen)
  return { ...game, moves: [...game.moves, move], clock: game.clock && pressClock(game.clock, mover, now) }
}

/**
 * "/play" — play against a bot.
 * The browser keeps the game and the clocks; for each bot move it sends all the
 * moves to POST /api/bots/move, where the server checks them and replies.
 */
export default function PlayPage() {
  const navigate = useNavigate()
  const [bots, setBots] = useState<BotLevel[]>([])
  const [level, setLevel] = useState(3)
  const [colorChoice, setColorChoice] = useState<Color | 'RANDOM'>('WHITE')
  const [timeControl, setTimeControl] = useState<TimeControl>(TIME_CONTROLS[3])
  const [game, setGame] = useState<BotGame | null>(null)
  const [legal, setLegal] = useState<{ fen: string; moves: LegalMove[] } | null>(null)
  const [now, setNow] = useState(() => Date.now())
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const { boardColors } = useTheme()

  const fen = game?.moves.at(-1)?.fenAfter ?? START_FEN
  const sideToMove = sideToMoveOf(fen)
  const playing = game !== null && game.end === null
  const yourTurn = playing && sideToMove === game.you
  const botTurn = playing && sideToMove !== game.you

  // Load the bot list once.
  useEffect(() => {
    api.listBots().then(setBots).catch((e: Error) => setError(e.message))
  }, [])

  // Your turn: fetch the legal moves, so a dropped piece can be checked instantly.
  useEffect(() => {
    if (!yourTurn) return
    let cancelled = false
    api.legalMoves(fen).then((m) => !cancelled && setLegal({ fen, moves: m })).catch(() => {})
    return () => {
      cancelled = true
    }
  }, [yourTurn, fen])

  // Bot's turn: ask the server. (Also detects when YOUR move just ended the game.)
  const gameId = game?.id
  const movesKey = game?.moves.map((m) => m.uci).join(' ') ?? ''
  useEffect(() => {
    if (!botTurn || !game) return
    let cancelled = false
    const forGame = game.id
    api.botMove(game.bot.level, game.moves.map((m) => m.uci))
      .then((reply) => {
        if (cancelled) return
        setGame((g) => {
          if (!g || g.id !== forGame || g.end) return g   // a newer game, or already over (e.g. on time)
          let next = reply.move ? addMove(g, reply.move, Date.now()) : g
          if (reply.status !== 'ONGOING') {
            next = { ...next, end: { result: reply.result as GameEnd['result'], reason: REASONS[reply.status] } }
          }
          return next
        })
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- re-run only when the position changes
  }, [botTurn, gameId, movesKey])

  // Clock: redraw 5x per second, and end the game when the side to move runs out of time.
  useEffect(() => {
    if (!playing || !game?.clock) return
    const timer = setInterval(() => {
      const t = Date.now()
      setNow(t)
      setGame((g) => {
        if (!g || !g.clock || g.end) return g
        const toMove = sideToMoveOf(g.moves.at(-1)?.fenAfter ?? START_FEN)
        if (remaining(g.clock, toMove, toMove, t) > 0) return g   // same object = React skips the update
        const flagged = toMove === 'WHITE' ? { ...g.clock, white: 0 } : { ...g.clock, black: 0 }
        return { ...g, clock: flagged, end: { result: lossFor(toMove), reason: 'timeout' } }
      })
    }, 200)
    return () => clearInterval(timer)
  }, [playing, game?.clock, gameId])

  function start() {
    const bot = bots.find((b) => b.level === level)
    if (!bot) return
    const you: Color = colorChoice === 'RANDOM' ? (Math.random() < 0.5 ? 'WHITE' : 'BLACK') : colorChoice
    const t = Date.now()
    setError(null)
    setNow(t)
    setGame({ id: t, you, bot, moves: [], clock: newClock(timeControl, t), end: null })
  }

  function onPieceDrop({ sourceSquare, targetSquare }: { sourceSquare: string; targetSquare: string | null }): boolean {
    if (!yourTurn || !targetSquare || legal?.fen !== fen) return false
    const candidates = legal.moves.filter((m) => m.uci.startsWith(sourceSquare + targetSquare))
    if (candidates.length === 0) return false
    const move = candidates.find((m) => m.uci.length === 4 || m.uci.endsWith('q'))!   // promotions: queen
    setGame((g) => (g ? addMove(g, move, Date.now()) : g))
    return true
  }

  function resign() {
    setGame((g) => (g && !g.end ? { ...g, end: { result: lossFor(g.you), reason: 'resignation' } } : g))
  }

  async function analyse() {
    if (!game?.end) return
    setSaving(true)
    try {
      const saved = await api.importGame(toPgn(game), game.you)
      navigate(`/games/${saved.id}`)
    } catch (e) {
      setError((e as Error).message)
      setSaving(false)
    }
  }

  // ---------- drawing ----------
  const you = game?.you ?? (colorChoice === 'BLACK' ? 'BLACK' : 'WHITE')
  const lastMove = game?.moves.at(-1)?.uci
  const squareStyles: Record<string, CSSProperties> = lastMove
    ? { [lastMove.slice(0, 2)]: { background: 'rgba(255, 214, 79, 0.55)' }, [lastMove.slice(2, 4)]: { background: 'rgba(255, 214, 79, 0.55)' } }
    : {}
  const yourChar = you === 'WHITE' ? 'w' : 'b'

  return (
    <div className="game-layout">
      <div className="board-column">
        <PlayerRow name={game ? `${game.bot.name} (bot)` : 'Bot'} rating={game?.bot.approxElo}
                   color={other(you)} game={game} sideToMove={sideToMove} now={now} thinking={botTurn} />
        <div className="board">
          <Chessboard options={{
            id: 'play-board',
            position: fen,
            boardOrientation: you === 'WHITE' ? 'white' : 'black',
            allowDragging: yourTurn,
            canDragPiece: ({ piece }) => piece.pieceType[0] === yourChar,
            onPieceDrop,
            allowDrawingArrows: false,
            squareStyles,
            animationDurationInMs: 150,
            ...boardColors,
          }} />
        </div>
        <PlayerRow name="You" color={you} game={game} sideToMove={sideToMove} now={now} thinking={false} />
      </div>

      <aside className="side-column">
        {error && <p className="error">{error}</p>}

        {!playing && (
          <div className="card compact">
            {game?.end && <ResultBanner end={game.end} you={game.you} />}
            {game?.end && (
              <div className="analyse-controls">
                <button className="btn primary" onClick={analyse} disabled={saving}>Analyse this game</button>
                <button className="btn" onClick={start}>Rematch</button>
              </div>
            )}

            <strong className="setup-title">{game ? 'New game' : 'Play against a bot'}</strong>
            <div className="bot-grid">
              {bots.map((b) => (
                <button key={b.level} className={`bot-card ${b.level === level ? 'active' : ''}`} onClick={() => setLevel(b.level)}>
                  <span className="bot-name">{b.name}</span>
                  <span className="muted small">~{b.approxElo}</span>
                </button>
              ))}
            </div>
            <span className="muted small">{bots.find((b) => b.level === level)?.description}</span>

            <div className="setup-row">
              <span className="muted small">Play as</span>
              <div className="segmented">
                {(['WHITE', 'RANDOM', 'BLACK'] as const).map((c) => (
                  <button key={c} className={`seg ${colorChoice === c ? 'active' : ''}`} onClick={() => setColorChoice(c)}>
                    {c === 'WHITE' ? 'White' : c === 'BLACK' ? 'Black' : 'Random'}
                  </button>
                ))}
              </div>
            </div>
            <div className="setup-row">
              <span className="muted small">Time</span>
              <div className="segmented wrap">
                {TIME_CONTROLS.map((tc) => (
                  <button key={tc.label} className={`seg ${timeControl === tc ? 'active' : ''}`} onClick={() => setTimeControl(tc)}>
                    {tc.label}
                  </button>
                ))}
              </div>
            </div>
            <button className="btn primary" onClick={start} disabled={bots.length === 0}>Start game</button>
          </div>
        )}

        {playing && (
          <div className="card compact">
            <strong>vs {game.bot.name} <span className="muted small">~{game.bot.approxElo}</span></strong>
            <span className="muted small">{yourTurn ? 'Your move: drag a piece.' : `${game.bot.name} is thinking…`}</span>
            <button className="btn" onClick={resign}>⚑ Resign</button>
          </div>
        )}

        {game && game.moves.length > 0 && (
          <div className="card compact">
            <MoveList moves={game.moves.map((m, i) => ({ ...m, ply: i + 1 }))} evals={new Map()}
                      currentPly={game.moves.length} onSelect={() => {}} />
          </div>
        )}
      </aside>
    </div>
  )
}

/** Name + clock above/below the board. */
function PlayerRow({ name, rating, color, game, sideToMove, now, thinking }: {
  name: string
  rating?: number
  color: Color
  game: BotGame | null
  sideToMove: Color
  now: number
  thinking: boolean
}) {
  const running = game !== null && game.end === null && sideToMove === color
  const ms = game?.clock ? remaining(game.clock, color, game.end ? other(color) : sideToMove, game.end ? game.clock.runningSince : now) : null
  return (
    <div className="player-bar">
      <span className={`piece-dot ${color === 'WHITE' ? 'white' : 'black'}`} />
      <strong>{name}</strong>
      {rating && <span className="muted">(~{rating})</span>}
      {thinking && <span className="spinner" />}
      {ms !== null && (
        <span className={`clock ${running ? 'running' : ''} ${ms < 20_000 ? 'low' : ''}`}>{formatClock(ms)}</span>
      )}
    </div>
  )
}

function ResultBanner({ end, you }: { end: GameEnd; you: Color }) {
  const draw = end.result === '1/2-1/2'
  const won = !draw && (end.result === '1-0') === (you === 'WHITE')
  const text = draw ? `Draw by ${end.reason}` : won ? `You won by ${end.reason}` : `You lost by ${end.reason}`
  return <div className={`result-banner ${draw ? 'draw' : won ? 'win' : 'loss'}`}>{text} <span>{end.result}</span></div>
}

/** Turns the finished game into PGN text, so it can be stored and analysed like any other game. */
function toPgn(game: BotGame): string {
  const botName = `${game.bot.name} (bot)`
  const white = game.you === 'WHITE' ? 'You' : botName
  const black = game.you === 'BLACK' ? 'You' : botName
  const d = new Date()
  const date = `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, '0')}.${String(d.getDate()).padStart(2, '0')}`
  const botElo = String(game.bot.approxElo)
  const headers = [
    ['Event', 'Game vs bot'], ['Date', date], ['White', white], ['Black', black],
    ['Result', game.end!.result], ['Termination', game.end!.reason],
    [game.you === 'WHITE' ? 'BlackElo' : 'WhiteElo', botElo],
  ].map(([k, v]) => `[${k} "${v}"]`).join('\n')
  const moves = game.moves.map((m, i) => (i % 2 === 0 ? `${i / 2 + 1}. ${m.san}` : m.san)).join(' ')
  return `${headers}\n\n${moves} ${game.end!.result}\n`
}
