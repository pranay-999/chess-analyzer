import { Link, NavLink, Route, Routes, useParams } from 'react-router'
import AnalyzePage from './pages/AnalyzePage'
import GamePage from './pages/GamePage'
import PlayPage from './pages/PlayPage'
import HomePage from './pages/HomePage'

/** The frame around every page: header + whichever page matches the URL. */
export default function App() {
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-icon">♞</span> Chess Analyzer
        </Link>
        <nav className="topnav">
          <NavLink to="/" end>My games</NavLink>
          <NavLink to="/play">Play</NavLink>
          <NavLink to="/analyze">Analysis board</NavLink>
        </nav>
      </header>
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/analyze" element={<AnalyzePage />} />
          <Route path="/play" element={<PlayPage />} />
          <Route path="/games/:id" element={<GameRoute />} />
          <Route path="*" element={<p className="muted">Page not found.</p>} />
        </Routes>
      </main>
    </>
  )
}

/** key={id}: when the id in the URL changes, React throws away the old page and its state. */
function GameRoute() {
  const { id } = useParams()
  return <GamePage key={id} />
}
