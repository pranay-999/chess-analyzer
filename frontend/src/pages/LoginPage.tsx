import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api'
import { useAuth } from '../auth'

/** "/login?next=/online" — log in, sign up, or continue as a guest, then go to `next`. */
export default function LoginPage() {
  const { user, setUser } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = params.get('next') ?? '/online'
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={next} replace />

  async function run(action: () => Promise<Awaited<ReturnType<typeof api.me>>>) {
    setBusy(true)
    setError(null)
    try {
      setUser(await action())
      navigate(next, { replace: true })
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    void run(() => (mode === 'login' ? api.login(username, password) : api.signup(username, password)))
  }

  return (
    <section className="card auth-card">
      <h1>{mode === 'login' ? 'Log in' : 'Create an account'}</h1>
      <p className="muted">To play online against other people. Your account is only on this site, not Chess.com.</p>

      <div className="segmented auth-tabs">
        <button className={`seg ${mode === 'login' ? 'active' : ''}`} onClick={() => setMode('login')}>Log in</button>
        <button className={`seg ${mode === 'signup' ? 'active' : ''}`} onClick={() => setMode('signup')}>Sign up</button>
      </div>

      <form className="auth-form" onSubmit={submit}>
        <label>
          <span className="muted small">Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required />
        </label>
        <label>
          <span className="muted small">Password {mode === 'signup' && '(at least 8 characters)'}</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                 autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required />
        </label>
        {error && <p className="error">{error}</p>}
        <button className="btn primary" disabled={busy}>{mode === 'login' ? 'Log in' : 'Create account'}</button>
      </form>

      <div className="divider"><span>or</span></div>
      <button className="btn" onClick={() => run(api.guest)} disabled={busy}>Play as guest</button>
      <span className="muted small">You get a name like Guest-4821. Guest accounts can't log in again later.</span>
    </section>
  )
}
