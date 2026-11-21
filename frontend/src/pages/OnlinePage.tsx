import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api, type Color, type LobbyEntry, type OnlineGame } from '../api'
import { useAuth } from '../auth'
import { TIME_CONTROLS, timeLabel, type TimeControl } from '../clock'
import { useTopic } from '../live'

/**
 * "/online" — the lobby: create a game (and share its link), or join someone else's.
 * The open-games list updates live: the server pushes it to /topic/lobby on every change.
 */
export default function OnlinePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const [lobby, setLobby] = useState<LobbyEntry[]>([])
  const [mine, setMine] = useState<OnlineGame[]>([])
  const [timeControl, setTimeControl] = useState<TimeControl>(TIME_CONTROLS[2])
  const [color, setColor] = useState<Color | 'RANDOM'>('RANDOM')
  const [listed, setListed] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const refresh = () => {
    api.openGames().then(setLobby).catch((e: Error) => setError(e.message))
    api.myOnlineGames().then(setMine).catch(() => {})
  }
  useEffect(refresh, [])
  useTopic<LobbyEntry[]>('/topic/lobby', setLobby, refresh)

  async function create() {
    setError(null)
    try {
      const game = await api.createOnline(timeControl.minutes, timeControl.increment, color, listed)
      navigate(`/online/${game.id}`)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  async function join(id: string) {
    try {
      await api.joinOnline(id)
      navigate(`/online/${id}`)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const others = lobby.filter((g) => g.creator !== user?.username)

  return (
    <div className="online-layout">
      <section className="card compact">
        <h2>New online game</h2>
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
        <div className="setup-row">
          <span className="muted small">I play</span>
          <div className="segmented">
            {(['WHITE', 'RANDOM', 'BLACK'] as const).map((c) => (
              <button key={c} className={`seg ${color === c ? 'active' : ''}`} onClick={() => setColor(c)}>
                {c === 'WHITE' ? 'White' : c === 'BLACK' ? 'Black' : 'Random'}
              </button>
            ))}
          </div>
        </div>
        <label className="check">
          <input type="checkbox" checked={listed} onChange={(e) => setListed(e.target.checked)} />
          Show in the lobby (untick for a private game: only people with the link can join)
        </label>
        <button className="btn primary" onClick={create}>Create game</button>
        {error && <p className="error">{error}</p>}
      </section>

      {mine.length > 0 && (
        <section className="card compact">
          <h2>Your games</h2>
          {mine.map((g) => (
            <Link key={g.id} to={`/online/${g.id}`} className="lobby-row">
              <span className={`tag plain ${g.status === 'ACTIVE' ? 'you' : ''}`}>{g.status === 'ACTIVE' ? 'Playing' : 'Waiting'}</span>
              <span>{g.white?.username ?? '?'} vs {g.black?.username ?? '?'}</span>
              <span className="muted small">{timeLabel(g.initialMs, g.incrementMs)}</span>
            </Link>
          ))}
        </section>
      )}

      <section className="card compact">
        <h2>Open games <span className="muted small">(updates live)</span></h2>
        {others.length === 0 && <p className="muted small">No one is waiting right now. Create a game, or send its link to a friend.</p>}
        {others.map((g) => (
          <div key={g.id} className="lobby-row">
            <span className={`piece-dot ${g.joinerColor === 'WHITE' ? 'white' : 'black'}`} title={`You play ${g.joinerColor.toLowerCase()}`} />
            <strong>{g.creator}</strong>
            {g.creatorGuest && <span className="tag plain">guest</span>}
            <span className="muted small">{timeLabel(g.initialMs, g.incrementMs)}</span>
            <button className="btn small-btn" onClick={() => join(g.id)}>Join</button>
          </div>
        ))}
      </section>
    </div>
  )
}
