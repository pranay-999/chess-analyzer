// Everything that talks to the Spring backend lives here.
// The TypeScript types below mirror the Java records the API returns
// (GameSummary.java, GameDetail.java, AnalysisView.java ...). If you change a
// Java record, change its type here too.

export type Color = 'WHITE' | 'BLACK'
export type Outcome = 'WIN' | 'LOSS' | 'DRAW' | 'UNKNOWN'
export type Classification = 'BEST' | 'GOOD' | 'INACCURACY' | 'MISTAKE' | 'BLUNDER'
export type AnalysisStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'
export type EngineName = 'stockfish' | 'my-engine' // must match EngineFactory.STOCKFISH / MY_ENGINE

export interface GameSummary {
  id: number
  white: string
  black: string
  whiteRating: number | null
  blackRating: number | null
  playerColor: Color
  result: string
  outcome: Outcome
  timeClass: string | null
  openingEco: string | null
  playedAt: string // ISO date, e.g. "2026-11-19T01:35:02Z"
  source: 'CHESSCOM' | 'PASTED'
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface MoveView {
  ply: number
  san: string
  uci: string
  fenAfter: string
}

export interface GameDetail {
  summary: GameSummary
  chesscomUrl: string | null // null for pasted games
  startFen: string
  moves: MoveView[]
}

export interface MoveEval {
  ply: number
  evalCp: number | null // after the move, white's view
  mateIn: number | null
  bestMoveUci: string | null // best move in the position BEFORE this move
  bestMoveSan: string | null
  cpLoss: number | null
  classification: Classification
}

export interface Analysis {
  id: number
  gameId: number
  engine: EngineName
  depth: number
  status: AnalysisStatus
  analyzedPlies: number
  totalPlies: number
  accuracyWhite: number | null
  accuracyBlack: number | null
  error: string | null
  moves: MoveEval[]
}

export interface LegalMove {
  uci: string
  san: string
  fenAfter: string
}

export interface PositionEval {
  cp: number | null // white's view
  mate: number | null
  bestMoveUci: string | null
  bestMoveSan: string | null
  engine: EngineName
  depth: number
}

export type GameStatus =
  | 'ONGOING' | 'CHECKMATE' | 'STALEMATE' | 'THREEFOLD_REPETITION' | 'FIFTY_MOVE_RULE' | 'INSUFFICIENT_MATERIAL'

export interface BotLevel {
  level: number
  name: string
  description: string
  stockfishSkill: number | null
  depth: number
  approxElo: number
}

export interface BotReply {
  move: LegalMove | null // null when your move already ended the game
  status: GameStatus
  result: string // "1-0", "0-1", "1/2-1/2" or "*"
  winner: Color | null
}

export interface User {
  id: number
  username: string
  guest: boolean
}

export type OnlineStatus = 'WAITING' | 'ACTIVE' | 'FINISHED' | 'ABORTED'

export interface OnlinePlayer {
  username: string
  guest: boolean
}

/** Mirrors OnlineGameView.java. Pushed over the WebSocket after every change. */
export interface OnlineGame {
  id: string
  status: OnlineStatus
  white: OnlinePlayer | null
  black: OnlinePlayer | null
  creator: string
  initialMs: number | null
  incrementMs: number
  whiteMs: number | null
  blackMs: number | null
  clockRunning: boolean
  sideToMove: Color
  fen: string
  moves: LegalMove[]
  result: string | null
  reason: string | null
  analysisGameId: number | null
}

export interface LobbyEntry {
  id: string
  creator: string
  creatorGuest: boolean
  initialMs: number | null
  incrementMs: number
  joinerColor: Color
}

/** A puzzle made from one of your mistakes (PuzzleService.PuzzleView). The answer is not included. */
export interface PuzzleView {
  id: number
  fen: string
  sideToMove: Color
  playedSan: string
  classification: Classification
  gameId: number
  white: string
  black: string
  playedAt: string
  attempts: number
  solved: number
}

export interface NextPuzzle {
  puzzle: PuzzleView | null
  due: number
  total: number
  nextDueAt: string | null
}

export interface PuzzleAttempt {
  correct: boolean
  exactMatch: boolean // false = a different but equally good move
  yourMoveSan: string | null
  solutionUci: string
  solutionSan: string
  playedSan: string
  nextDueAt: string
}

/** StatsService.java records */
export interface Split {
  label: string
  games: number
  wins: number
  losses: number
  draws: number
}

export interface StatsPoint {
  month: string
  value: number
  games: number
}

export interface Stats {
  player: string
  timeClass: string | null
  total: Split
  byColor: Split[]
  byTimeClass: Split[]
  openings: Split[]
  rating: StatsPoint[]
  accuracy: StatsPoint[]
  analysedGames: number
  averageAccuracy: number | null
  mistakesByPhase: { phase: string; inaccuracies: number; mistakes: number; blunders: number }[]
}

export interface SyncResult {
  username: string
  monthsFetched: number
  imported: number
  alreadyStored: number
  skipped: number
  failed: number
}

/** An HTTP error with the `detail` message from Spring's ProblemDetail JSON. */
export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

/** Reads a cookie the server set, e.g. XSRF-TOKEN. */
function readCookie(name: string): string | undefined {
  return document.cookie.split('; ').find((c) => c.startsWith(name + '='))?.split('=')[1]
}

async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  // CSRF protection (see SecurityConfig): every POST must echo the XSRF-TOKEN cookie
  // back in a header. Other websites can't read our cookies, so they can't do this.
  const headers = new Headers(init.headers)
  const method = (init.method ?? 'GET').toUpperCase()
  const csrf = readCookie('XSRF-TOKEN')
  if (method !== 'GET' && csrf) headers.set('X-XSRF-TOKEN', decodeURIComponent(csrf))

  const response = await fetch(url, { ...init, headers })
  if (!response.ok) {
    let detail: string | undefined
    try {
      detail = (await response.json()).detail
    } catch {
      // body wasn't JSON
    }
    throw new ApiError(response.status, detail ?? `Request failed (${response.status})`)
  }
  const text = await response.text() // some answers (logout) have no body
  return (text ? JSON.parse(text) : undefined) as T
}

const json = (body: unknown): RequestInit => ({
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

const user = (username: string) => encodeURIComponent(username.trim())

export const api = {
  listGames: (username: string, page: number) =>
    request<Page<GameSummary>>(`/api/players/${user(username)}/games?page=${page}&size=20`),

  syncPlayer: (username: string) =>
    request<SyncResult>(`/api/players/${user(username)}/sync`, { method: 'POST' }),

  getGame: (id: string) => request<GameDetail>(`/api/games/${encodeURIComponent(id)}`),

  /** Store a pasted PGN or FEN as a game; returns it so we can open /games/{id}. */
  /** text: a PGN, a FEN, or a Chess.com/Lichess game link. asPlayer: your username, to detect your side. */
  importGame: (text: string, playerColor: Color, asPlayer?: string) =>
    request<GameSummary>('/api/games/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }, // tells Spring the body is JSON (@RequestBody)
      body: JSON.stringify({ text, playerColor, asPlayer }),
    }),

  /** Returns null when the game has never been analysed by this engine (the API answers 404). */
  getAnalysis: async (gameId: number, engine: EngineName): Promise<Analysis | null> => {
    try {
      return await request<Analysis>(`/api/games/${gameId}/analysis?engine=${engine}`)
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null
      throw e
    }
  },

  /** All legal moves in a position (FEN). Used to accept or reject a dragged piece. */
  legalMoves: (fen: string) =>
    request<LegalMove[]>(`/api/positions/moves?fen=${encodeURIComponent(fen)}`),

  /** Ask an engine about any position, e.g. one you reached by moving pieces yourself. */
  evaluatePosition: (fen: string, engine: EngineName, depth: number) =>
    request<PositionEval>(`/api/positions/eval?fen=${encodeURIComponent(fen)}&engine=${engine}&depth=${depth}`),

  // ---- accounts ----
  me: () => request<{ user: User | null }>('/api/auth/me').then((r) => r.user),
  signup: (username: string, password: string) =>
    request<{ user: User }>('/api/auth/signup', json({ username, password })).then((r) => r.user),
  login: (username: string, password: string) =>
    request<{ user: User }>('/api/auth/login', json({ username, password })).then((r) => r.user),
  guest: () => request<{ user: User }>('/api/auth/guest', { method: 'POST' }).then((r) => r.user),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),

  // ---- online play ----
  createOnline: (minutes: number | null, increment: number, color: Color | 'RANDOM', listed: boolean) =>
    request<OnlineGame>('/api/online/games', json({ minutes, increment, color, listed })),
  openGames: () => request<LobbyEntry[]>('/api/online/games/open'),
  myOnlineGames: () => request<OnlineGame[]>('/api/online/games/mine'),
  onlineGame: (id: string) => request<OnlineGame>(`/api/online/games/${encodeURIComponent(id)}`),
  joinOnline: (id: string) => request<OnlineGame>(`/api/online/games/${encodeURIComponent(id)}/join`, { method: 'POST' }),
  moveOnline: (id: string, uci: string) =>
    request<OnlineGame>(`/api/online/games/${encodeURIComponent(id)}/moves`, json({ uci })),
  resignOnline: (id: string) => request<OnlineGame>(`/api/online/games/${encodeURIComponent(id)}/resign`, { method: 'POST' }),
  cancelOnline: (id: string) => request<OnlineGame>(`/api/online/games/${encodeURIComponent(id)}/cancel`, { method: 'POST' }),

  stats: (username: string, timeClass?: string) =>
    request<Stats>(`/api/players/${user(username)}/stats${timeClass ? `?timeClass=${encodeURIComponent(timeClass)}` : ''}`),

  // ---- puzzles from your mistakes ----
  nextPuzzle: (player?: string) =>
    request<NextPuzzle>(`/api/puzzles/next${player ? `?player=${encodeURIComponent(player)}` : ''}`),
  attemptPuzzle: (id: number, uci: string) => request<PuzzleAttempt>(`/api/puzzles/${id}/attempt`, json({ uci })),
  revealPuzzle: (id: number) => request<PuzzleAttempt>(`/api/puzzles/${id}/reveal`, { method: 'POST' }),

  listBots: () => request<BotLevel[]>('/api/bots'),

  /** Send every move so far; the server checks them and answers with the bot's move. */
  botMove: (level: number, moves: string[]) =>
    request<BotReply>('/api/bots/move', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ level, moves }),
    }),

  startAnalysis: (gameId: number, engine: EngineName, depth: number) =>
    request<Analysis>(`/api/games/${gameId}/analysis?engine=${engine}&depth=${depth}`, { method: 'POST' }),
}
