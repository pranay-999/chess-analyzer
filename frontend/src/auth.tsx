// "Who is logged in?", available to every component through React Context.
// Context = a value placed high in the tree (in main.tsx) that any child can read
// with useAuth(), instead of passing it down through every component's props.

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, type User } from './api'

interface AuthState {
  user: User | null
  loading: boolean // true until we've asked the server once
  setUser: (user: User | null) => void
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  // On start: ask the server who we are (the session cookie decides). This also
  // gives us the XSRF-TOKEN cookie that every later POST needs.
  useEffect(() => {
    api.me().then(setUser).catch(() => setUser(null)).finally(() => setLoading(false))
  }, [])

  async function logout() {
    await api.logout()
    setUser(null)
    await api.me() // fresh XSRF token for the next login
  }

  return <AuthContext.Provider value={{ user, loading, setUser, logout }}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- a hook belongs next to its provider
export function useAuth(): AuthState {
  const state = useContext(AuthContext)
  if (!state) throw new Error('useAuth must be used inside <AuthProvider>')
  return state
}
