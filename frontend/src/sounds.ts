// Move sounds, made in the browser with the Web Audio API: no sound files to download.
// An oscillator is a tone generator; a gain node is a volume knob we turn down quickly,
// which turns a beep into a short "tock".

import { useEffect, useRef, useState } from 'react'

type Sound = 'move' | 'capture' | 'check' | 'castle' | 'end' | 'lowTime'

let audio: AudioContext | null = null

// ---- on/off switch (remembered in this browser) ----
const listeners = new Set<(on: boolean) => void>()
function readOn(): boolean {
  try {
    return localStorage.getItem('sound') !== 'off'
  } catch {
    return true
  }
}
let soundOn = readOn()

export function setSoundOn(on: boolean) {
  soundOn = on
  try {
    localStorage.setItem('sound', on ? 'on' : 'off')
  } catch {
    // storage blocked: still works for this visit
  }
  listeners.forEach((l) => l(on))
}

/** For the 🔊 button: the current setting, re-drawn when it changes. */
export function useSoundOn(): boolean {
  const [on, setOn] = useState(soundOn)
  useEffect(() => {
    listeners.add(setOn)
    return () => {
      listeners.delete(setOn)
    }
  }, [])
  return on
}

/** One short tone: frequency in Hz, start offset and length in seconds. */
function tone(ctx: AudioContext, freq: number, at: number, length: number, type: OscillatorType, volume: number) {
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = type
  osc.frequency.value = freq
  const t = ctx.currentTime + at
  gain.gain.setValueAtTime(volume, t)
  gain.gain.exponentialRampToValueAtTime(0.001, t + length) // fade out fast = a "tock", not a beep
  osc.connect(gain).connect(ctx.destination)
  osc.start(t)
  osc.stop(t + length)
}

export function play(sound: Sound) {
  if (!soundOn) return
  try {
    audio ??= new AudioContext()
    const ctx = audio
    if (ctx.state === 'suspended') void ctx.resume() // browsers start audio paused until the first click
    switch (sound) {
      case 'move': tone(ctx, 190, 0, 0.09, 'triangle', 0.5); break
      case 'capture': tone(ctx, 140, 0, 0.12, 'square', 0.18); tone(ctx, 300, 0, 0.06, 'triangle', 0.4); break
      case 'castle': tone(ctx, 190, 0, 0.08, 'triangle', 0.5); tone(ctx, 170, 0.1, 0.08, 'triangle', 0.5); break
      case 'check': tone(ctx, 660, 0, 0.1, 'sine', 0.25); tone(ctx, 880, 0.1, 0.14, 'sine', 0.25); break
      case 'end': [523, 659, 784].forEach((f, i) => tone(ctx, f, i * 0.12, 0.45, 'sine', 0.2)); break
      case 'lowTime': [0, 0.18, 0.36].forEach((at) => tone(ctx, 1000, at, 0.08, 'sine', 0.2)); break
    }
  } catch {
    // no audio in this browser: stay silent
  }
}

/** Which sound a move makes, from its SAN: "Nxe5" captures, "Qh5+" checks, "O-O" castles, "Qh4#" ends the game. */
export function soundFor(san: string): Sound {
  if (san.includes('#')) return 'end'
  if (san.includes('+')) return 'check'
  if (san.startsWith('O-O')) return 'castle'
  return san.includes('x') ? 'capture' : 'move'
}

/**
 * Plays the sound of the latest move whenever `count` (number of moves / current ply)
 * goes UP: a new move arrived or you stepped forward. Not on the first draw, and not
 * when stepping back.
 */
export function useMoveSound(count: number, san: string | undefined) {
  const previous = useRef<number | null>(null)
  useEffect(() => {
    if (previous.current !== null && count > previous.current && san) play(soundFor(san))
    previous.current = count
  }, [count, san])
}

/** Three beeps, once, when your clock drops below 10 seconds. */
export function useLowTimeWarning(ms: number | null, ticking: boolean) {
  const warned = useRef(false)
  const low = ticking && ms !== null && ms > 0 && ms < 10_000
  useEffect(() => {
    if (low && !warned.current) play('lowTime')
    warned.current = low || (warned.current && ticking)
  }, [low, ticking])
}

/** A chime when the game ends (resign, timeout, draw… mate already chimes via its move). */
export function useGameEndSound(over: boolean) {
  const previous = useRef(over)
  useEffect(() => {
    if (over && !previous.current) play('end')
    previous.current = over
  }, [over])
}
