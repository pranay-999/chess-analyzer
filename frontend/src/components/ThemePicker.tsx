import { useEffect, useRef, useState } from 'react'
import { useTheme } from '../theme'
import { BOARDS, THEMES, type BoardName } from '../themeData'

/** The 🎨 button in the header: pick a site theme and a board colour. */
export default function ThemePicker() {
  const { theme, setTheme, board, setBoard } = useTheme()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // Close when clicking anywhere outside the menu, or pressing Escape.
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const boards: { name: BoardName; label: string; light?: string; dark?: string }[] = [
    { name: 'theme', label: 'Match theme' },
    ...Object.entries(BOARDS).map(([name, b]) => ({ name: name as BoardName, ...b })),
  ]

  return (
    <div className="picker" ref={ref}>
      <button className="btn small-btn" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-label="Appearance">
        🎨<span className="picker-label"> Theme</span>
      </button>
      {open && (
        <div className="picker-menu" role="dialog" aria-label="Appearance">
          <span className="muted small">Theme</span>
          <div className="swatch-grid">
            {THEMES.map((t) => (
              <button key={t.name} className={`swatch ${theme === t.name ? 'active' : ''}`} onClick={() => setTheme(t.name)}>
                <span className="swatch-colors">
                  <span style={{ background: t.swatch[0] }} />
                  <span style={{ background: t.swatch[1] }} />
                </span>
                {t.label}
              </button>
            ))}
          </div>

          <span className="muted small">Board</span>
          <div className="swatch-grid">
            {boards.map((b) => (
              <button key={b.name} className={`swatch ${board === b.name ? 'active' : ''}`} onClick={() => setBoard(b.name)}>
                {b.light && b.dark ? (
                  <span className="mini-board">
                    <span style={{ background: b.light }} /><span style={{ background: b.dark }} />
                    <span style={{ background: b.dark }} /><span style={{ background: b.light }} />
                  </span>
                ) : (
                  <span className="mini-board auto">♟</span>
                )}
                {b.label}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
