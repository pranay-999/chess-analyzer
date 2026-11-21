// Small per-browser memories. localStorage can throw (private mode, blocked storage), so always wrap it.

/** The Chess.com username last typed on the home page; tells us which side "you" played in imported games. */
export function savedChessUsername(): string | undefined {
  try {
    return localStorage.getItem('username') ?? undefined
  } catch {
    return undefined
  }
}

/**
 * Text shared from another app or the extension often arrives in pieces (title, text, url).
 * Pick the part that is actually a game: a PGN, a game link, or a move list.
 */
export function pickGameText(...parts: (string | null | undefined)[]): string {
  const present = parts.filter((p): p is string => !!p && p.trim() !== '')
  return (
    present.find((p) => p.includes('[') || /chess\.com|lichess\.org/.test(p) || /^\s*1\.\s*\S/.test(p)) ??
    present.join('\n')
  )
}
