// Chess clock maths, kept free of React so it's easy to follow (and reusable for online games).

import type { Color } from './api'

export interface TimeControl {
  label: string
  minutes: number | null // null = no clock
  increment: number // seconds added after each of your moves
}

export const TIME_CONTROLS: TimeControl[] = [
  { label: 'Unlimited', minutes: null, increment: 0 },
  { label: '3 | 2', minutes: 3, increment: 2 },
  { label: '5 | 0', minutes: 5, increment: 0 },
  { label: '10 | 0', minutes: 10, increment: 0 },
  { label: '15 | 10', minutes: 15, increment: 10 },
]

/**
 * Stored remaining time per side, plus WHEN the current side's clock started running.
 * The time shown on screen is computed from that ("remaining - (now - runningSince)"),
 * so the clock is right even if the browser tab was in the background.
 */
export interface Clock {
  white: number // ms
  black: number // ms
  runningSince: number // Date.now() when the side to move started thinking
  increment: number // ms
}

export function newClock(tc: TimeControl, now: number): Clock | null {
  if (tc.minutes === null) return null
  const ms = tc.minutes * 60_000
  return { white: ms, black: ms, runningSince: now, increment: tc.increment * 1000 }
}

/** Time left for `color` right now (only the side to move is ticking). */
export function remaining(clock: Clock, color: Color, sideToMove: Color, now: number): number {
  const stored = color === 'WHITE' ? clock.white : clock.black
  return color === sideToMove ? stored - (now - clock.runningSince) : stored
}

/** After `mover` moves: charge the time spent, add the increment, start the other clock. */
export function pressClock(clock: Clock, mover: Color, now: number): Clock {
  const spent = now - clock.runningSince
  const left = (mover === 'WHITE' ? clock.white : clock.black) - spent + clock.increment
  return mover === 'WHITE'
    ? { ...clock, white: left, runningSince: now }
    : { ...clock, black: left, runningSince: now }
}

/** "5 | 0" for 5 minutes, no increment; "Unlimited" without a clock. */
export function timeLabel(initialMs: number | null, incrementMs: number): string {
  return initialMs === null ? 'Unlimited' : `${initialMs / 60_000} | ${incrementMs / 1000}`
}

/** 5:07, or 0:09.4 in the last ten seconds. */
export function formatClock(ms: number): string {
  const safe = Math.max(0, ms)
  const minutes = Math.floor(safe / 60_000)
  const seconds = Math.floor((safe % 60_000) / 1000)
  if (safe < 10_000) return `0:0${seconds}.${Math.floor((safe % 1000) / 100)}`
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}
