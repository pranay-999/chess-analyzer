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
  chesscomUrl: string
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

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
  if (!response.ok) {
    let detail: string | undefined
    try {
      detail = (await response.json()).detail
    } catch {
      // body wasn't JSON
    }
    throw new ApiError(response.status, detail ?? `Request failed (${response.status})`)
  }
  return response.json() as Promise<T>
}

const user = (username: string) => encodeURIComponent(username.trim())

export const api = {
  listGames: (username: string, page: number) =>
    request<Page<GameSummary>>(`/api/players/${user(username)}/games?page=${page}&size=20`),

  syncPlayer: (username: string) =>
    request<SyncResult>(`/api/players/${user(username)}/sync`, { method: 'POST' }),

  getGame: (id: string) => request<GameDetail>(`/api/games/${encodeURIComponent(id)}`),

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

  startAnalysis: (gameId: number, engine: EngineName, depth: number) =>
    request<Analysis>(`/api/games/${gameId}/analysis?engine=${engine}&depth=${depth}`, { method: 'POST' }),
}
