import { Component, type ReactNode } from 'react'

/**
 * If anything inside throws while drawing (e.g. the board library), show a small
 * message for THIS page instead of a blank screen for the whole app.
 *
 * Error boundaries are the one thing React still requires a class component for:
 * React calls getDerivedStateFromError when a child throws during rendering.
 * App gives it key={pathname}, so moving to another page starts fresh.
 */
export default class ErrorBoundary extends Component<{ children: ReactNode }, { error: Error | null }> {
  state: { error: Error | null } = { error: null }

  static getDerivedStateFromError(error: Error) {
    return { error }
  }

  render() {
    if (!this.state.error) return this.props.children
    return (
      <section className="card">
        <h2>Something went wrong on this page</h2>
        <p className="muted small">{this.state.error.message}</p>
        <button className="btn primary" onClick={() => window.location.reload()}>Reload</button>
      </section>
    )
  }
}
