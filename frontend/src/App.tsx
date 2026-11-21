import type { ReactNode } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useParams } from 'react-router'
import { useAuth } from './auth'
import AnalyzePage from './pages/AnalyzePage'
import GamePage from './pages/GamePage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import OnlineGamePage from './pages/OnlineGamePage'
import OnlinePage from './pages/OnlinePage'
import PlayPage from './pages/PlayPage'

/** The frame around every page: header + whichever page matches the URL. */
export default function App() {
  const { user, logout } = useAuth()
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-icon">♞</span> Chess Analyzer
        </Link>
        <nav className="topnav">
          <NavLink to="/" end>My games</NavLink>
          <NavLink to="/play">Play</NavLink>
          <NavLink to="/online">Online</NavLink>
          <NavLink to="/analyze">Analysis board</NavLink>
        </nav>
        <div className="account">
          {user ? (
            <>
              <span className="muted small">{user.username}</span>
              <button className="btn small-btn" onClick={() => void logout()}>Log out</button>
            </>
          ) : (
            <Link to="/login" className="btn small-btn">Log in</Link>
          )}
        </div>
      </header>
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/analyze" element={<AnalyzePage />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/online" element={<RequireLogin><OnlinePage /></RequireLogin>} />
          <Route path="/online/:id" element={<RequireLogin><OnlineGameRoute /></RequireLogin>} />
          <Route path="/games/:id" element={<GameRoute />} />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}

/** Shows the page only when logged in; otherwise sends you to /login and back here afterwards. */
function RequireLogin({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <p className="muted">Loading…</p>
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname)}`} replace />
  return children
}

/** key={id}: when the id in the URL changes, React throws away the old page and its state. */
function GameRoute() {
  const { id } = useParams()
  return <GamePage key={id} />
}

function OnlineGameRoute() {
  const { id } = useParams()
  return <OnlineGamePage key={id} />
}
