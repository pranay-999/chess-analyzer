// Themes and board colours, chosen in the header's 🎨 menu and remembered per browser.
//
// How it works: the CSS (index.css) has one block of colour variables per theme,
// selected by <html data-theme="...">. Switching theme only changes that attribute;
// every colour on the page follows. The board is drawn by react-chessboard from
// JavaScript, so its square colours come from this context instead of CSS.

import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { BOARDS, THEME_BOARD, type BoardName, type ThemeName } from './themeData'

interface ThemeState {
  theme: ThemeName
  setTheme: (t: ThemeName) => void
  board: BoardName
  setBoard: (b: BoardName) => void
  /** Ready to pass to <Chessboard options={{ ...boardColors }} /> */
  boardColors: { lightSquareStyle: { backgroundColor: string }; darkSquareStyle: { backgroundColor: string } }
}

const ThemeContext = createContext<ThemeState | null>(null)

function load<T extends string>(key: string, fallback: T): T {
  try {
    return (localStorage.getItem(key) as T | null) ?? fallback
  } catch {
    return fallback // storage can be blocked (private mode); just use the default
  }
}

function save(key: string, value: string) {
  try {
    localStorage.setItem(key, value)
  } catch {
    // ignore
  }
}

const prefersDark = () => window.matchMedia('(prefers-color-scheme: dark)').matches

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeName>(() => load('theme', 'auto'))
  const [board, setBoardState] = useState<BoardName>(() => load('board', 'theme'))
  const [systemDark, setSystemDark] = useState(prefersDark)

  // In "auto", follow the device when it switches between light and dark (e.g. at sunset).
  useEffect(() => {
    const query = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = () => setSystemDark(query.matches)
    query.addEventListener('change', onChange)
    return () => query.removeEventListener('change', onChange)
  }, [])

  const resolved: Exclude<ThemeName, 'auto'> = theme === 'auto' ? (systemDark ? 'dark' : 'light') : theme

  // Apply to <html data-theme="..."> (index.html does the same before React loads, to avoid a flash).
  useEffect(() => {
    document.documentElement.dataset.theme = resolved
  }, [resolved])

  const boardStyle = BOARDS[board === 'theme' ? THEME_BOARD[resolved] : board]
  const value: ThemeState = {
    theme,
    setTheme: (t) => {
      setThemeState(t)
      save('theme', t)
    },
    board,
    setBoard: (b) => {
      setBoardState(b)
      save('board', b)
    },
    boardColors: {
      lightSquareStyle: { backgroundColor: boardStyle.light },
      darkSquareStyle: { backgroundColor: boardStyle.dark },
    },
  }
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components -- a hook belongs next to its provider
export function useTheme(): ThemeState {
  const state = useContext(ThemeContext)
  if (!state) throw new Error('useTheme must be used inside <ThemeProvider>')
  return state
}
