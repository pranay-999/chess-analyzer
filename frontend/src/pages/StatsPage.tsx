import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api, type Split, type Stats, type StatsPoint } from '../api'
import { Legend, LineChart, SegmentedBar, StackedBars, type Part } from '../components/charts'
import { savedChessUsername } from '../prefs'

const WDL = [
  { label: 'Win', color: 'var(--win)' },
  { label: 'Draw', color: 'var(--draw-bg)' },
  { label: 'Loss', color: 'var(--loss)' },
]
// One hue, light -> dark: the darker, the worse (validated as an ordered scale in both themes).
const SEVERITY = [
  { label: 'Inaccuracy', color: 'var(--sev-1)' },
  { label: 'Mistake', color: 'var(--sev-2)' },
  { label: 'Blunder', color: 'var(--sev-3)' },
]

const wdlParts = (s: Split): Part[] => [
  { ...WDL[0], value: s.wins },
  { ...WDL[1], value: s.draws },
  { ...WDL[2], value: s.losses },
]
const score = (s: Split) => (s.games ? Math.round(((s.wins + s.draws / 2) / s.games) * 100) : 0)
const count = (n: number) => n.toLocaleString()

/**
 * "/stats?user=edward_nova&tc=rapid" — your dashboard. Every number is computed by
 * PostgreSQL (StatsService.java); the filters re-query, and the page keeps showing the
 * previous numbers (dimmed) while the new ones load.
 */
export default function StatsPage() {
  const [params, setParams] = useSearchParams()
  const user = params.get('user') ?? savedChessUsername() ?? ''
  const timeClass = params.get('tc') ?? ''
  const [input, setInput] = useState(user)
  // The numbers on screen, remembered with the filter they belong to: while a new filter
  // loads, the old numbers stay visible (dimmed) instead of the page jumping.
  const [shown, setShown] = useState<{ key: string; stats: Stats } | null>(null)
  const [classes, setClasses] = useState<string[]>([]) // time controls you play, for the filter row
  const [error, setError] = useState<string | null>(null)
  const key = `${user}|${timeClass}`
  const stats = shown?.stats ?? null
  const loading = shown !== null && shown.key !== key

  useEffect(() => {
    if (!user) return
    let cancelled = false
    api.stats(user, timeClass || undefined)
      .then((s) => {
        if (cancelled) return
        setShown({ key: `${user}|${timeClass}`, stats: s })
        setError(null)
        if (!timeClass) setClasses(s.byTimeClass.map((t) => t.label))
      })
      .catch((e: Error) => !cancelled && setError(e.message))
    return () => {
      cancelled = true
    }
  }, [user, timeClass])

  function show(e: FormEvent) {
    e.preventDefault()
    if (input.trim()) setParams({ user: input.trim() })
  }
  const setTimeClass = (tc: string) => setParams(tc ? { user, tc } : { user })

  return (
    <div className="stats-page">
      {/* Filters: one row, above everything they affect. */}
      <div className="stats-filters">
        <form className="search" onSubmit={show}>
          <input value={input} onChange={(e) => setInput(e.target.value)} placeholder="Chess.com username" aria-label="Username" />
          <button className="btn primary">Show stats</button>
        </form>
        {classes.length > 1 && (
          <div className="segmented wrap" aria-label="Time control">
            <button className={`seg ${!timeClass ? 'active' : ''}`} onClick={() => setTimeClass('')}>All</button>
            {classes.map((c) => (
              <button key={c} className={`seg ${timeClass === c ? 'active' : ''}`} onClick={() => setTimeClass(c)}>{c}</button>
            ))}
          </div>
        )}
      </div>

      {error && <p className="error">{error}</p>}
      {!user && <p className="muted">Enter your Chess.com username (sync your games on <Link to="/">My games</Link> first).</p>}
      {user && !stats && !error && <p className="muted">Loading stats…</p>}
      {stats && stats.total.games === 0 && <p className="muted">No games stored for {user}. Sync them on <Link to="/">My games</Link>.</p>}

      {stats && stats.total.games > 0 && (
        <div className={loading ? 'refreshing' : undefined}>
          <Tiles stats={stats} />

          <div className="stats-grid">
            <section className="card compact">
              <h2>Results</h2>
              <Legend parts={WDL} />
              {stats.byColor.map((c) => (
                <div key={c.label} className="split-row">
                  <span className="split-name">As {c.label === 'WHITE' ? 'White' : 'Black'} <span className="muted small">{count(c.games)} games · score {score(c)}%</span></span>
                  <SegmentedBar parts={wdlParts(c)} label={`Results as ${c.label.toLowerCase()}`} />
                </div>
              ))}
              {!timeClass && stats.byTimeClass.length > 1 && stats.byTimeClass.map((t) => (
                <div key={t.label} className="split-row">
                  <span className="split-name"><span className="cap">{t.label}</span> <span className="muted small">{count(t.games)} games · score {score(t)}%</span></span>
                  <SegmentedBar parts={wdlParts(t)} label={`Results in ${t.label}`} />
                </div>
              ))}
            </section>

            <section className="card compact">
              <h2>Rating by month</h2>
              <span className="muted small">Average of your rating in the games you played that month.</span>
              <LineChart points={stats.rating} label="Rating by month" />
              <DataTable points={stats.rating} valueName="Rating" />
            </section>

            <section className="card compact">
              <h2>Accuracy by month</h2>
              <span className="muted small">From the {stats.analysedGames} game{stats.analysedGames === 1 ? '' : 's'} you analysed with Stockfish.</span>
              {stats.accuracy.length >= 2
                ? <><LineChart points={stats.accuracy} unit="%" label="Accuracy by month" /><DataTable points={stats.accuracy} valueName="Accuracy %" /></>
                : <p className="muted small">Analyse games from at least two different months to see a trend. Open a game and click <em>Analyse with Stockfish</em>.</p>}
            </section>

            <section className="card compact">
              <h2>Your mistakes by game phase</h2>
              <span className="muted small">Per analysed game: where do your errors happen?</span>
              {stats.analysedGames > 0 ? (
                <>
                  <Legend parts={SEVERITY} />
                  <StackedBars rows={stats.mistakesByPhase.map((p) => ({
                    label: p.phase,
                    parts: [
                      { ...SEVERITY[0], value: round1(p.inaccuracies / stats.analysedGames) },
                      { ...SEVERITY[1], value: round1(p.mistakes / stats.analysedGames) },
                      { ...SEVERITY[2], value: round1(p.blunders / stats.analysedGames) },
                    ],
                  }))} />
                  <Link to="/puzzles" className="small">🧩 Train these mistakes as puzzles</Link>
                </>
              ) : <p className="muted small">No analysed games yet.</p>}
            </section>
          </div>

          <section className="card compact">
            <h2>Openings</h2>
            <span className="muted small">Your 12 most played (score = wins + half the draws).</span>
            <div className="table-wrap">
              <table className="openings">
                <thead>
                  <tr><th>Opening</th><th className="num">Games</th><th>Win / draw / loss</th><th className="num">Score</th></tr>
                </thead>
                <tbody>
                  {stats.openings.map((o) => (
                    <tr key={o.label}>
                      <td>{o.label}</td>
                      <td className="num">{count(o.games)}</td>
                      <td className="bar-cell"><SegmentedBar parts={wdlParts(o)} tall={false} label={o.label} /></td>
                      <td className="num"><strong>{score(o)}%</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}

const round1 = (n: number) => Math.round(n * 10) / 10

/** The headline numbers. */
function Tiles({ stats }: { stats: Stats }) {
  const t = stats.total
  const r = stats.rating
  const latest = r.at(-1)
  const delta = r.length >= 2 ? Math.round(r[r.length - 1].value - r[r.length - 2].value) : null
  return (
    <div className="tiles">
      <div className="tile"><span className="tile-label">Games</span><span className="tile-value">{count(t.games)}</span></div>
      <div className="tile">
        <span className="tile-label">Score</span>
        <span className="tile-value">{score(t)}%</span>
        <span className="muted small">{count(t.wins)} W · {count(t.draws)} D · {count(t.losses)} L</span>
      </div>
      <div className="tile">
        <span className="tile-label">Rating</span>
        <span className="tile-value">{latest ? Math.round(latest.value) : '—'}</span>
        {delta !== null && (
          <span className={`small ${delta >= 0 ? 'delta-up' : 'delta-down'}`}>{delta >= 0 ? '▲' : '▼'} {Math.abs(delta)} vs previous month</span>
        )}
      </div>
      <div className="tile">
        <span className="tile-label">Accuracy</span>
        <span className="tile-value">{stats.averageAccuracy !== null ? `${stats.averageAccuracy}%` : '—'}</span>
        <span className="muted small">{stats.analysedGames} analysed game{stats.analysedGames === 1 ? '' : 's'}</span>
      </div>
    </div>
  )
}

/** The same numbers as the chart, as a table (for screen readers, and for reading exact values). */
function DataTable({ points, valueName }: { points: StatsPoint[]; valueName: string }) {
  if (points.length === 0) return null
  return (
    <details className="data-table">
      <summary>Show as table</summary>
      <table>
        <thead><tr><th>Month</th><th className="num">{valueName}</th><th className="num">Games</th></tr></thead>
        <tbody>
          {[...points].reverse().map((p) => (
            <tr key={p.month}><td>{p.month}</td><td className="num">{p.value.toLocaleString()}</td><td className="num">{p.games}</td></tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}
