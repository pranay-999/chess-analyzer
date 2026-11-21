// Small, dependency-free charts for the stats page (plain SVG + HTML).
// Conventions: 2px lines, a 10% area wash, end dot with a ring in the surface colour,
// hairline grid, 2px gaps between touching blocks, text never coloured like the data,
// a tooltip on hover AND keyboard focus, and every number also reachable without hovering.

import { useLayoutEffect, useRef, useState, type FocusEvent, type KeyboardEvent, type PointerEvent, type ReactNode } from 'react'

// ------------------------------------------------------------------ tooltip

/** One floating tooltip per chart. Content is React nodes, so names are inserted as text (never as HTML). */
function useTooltip() {
  const [tip, setTip] = useState<{ x: number; y: number; content: ReactNode } | null>(null)
  return {
    show: (x: number, y: number, content: ReactNode) => setTip({ x, y, content }),
    hide: () => setTip(null),
    node: tip && (
      <div className="chart-tooltip" role="status" style={{ left: tip.x, top: tip.y }}>{tip.content}</div>
    ),
  }
}

/**
 * Width of an element, kept up to date (so SVG charts draw crisp at any screen size).
 * Measured once right away, before the browser paints (useLayoutEffect), so the chart
 * appears immediately; the ResizeObserver then follows later size changes.
 */
function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  const [width, setWidth] = useState(0)
  useLayoutEffect(() => {
    if (!ref.current) return
    // eslint-disable-next-line react-hooks/set-state-in-effect -- measuring the DOM is what layout effects are for
    setWidth(ref.current.getBoundingClientRect().width)
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width))
    observer.observe(ref.current)
    return () => observer.disconnect()
  }, [])
  return [ref, width] as const
}

/** Round numbers for axis ticks: steps of 1, 2 or 5 × 10^k. */
function niceTicks(min: number, max: number, count = 4): number[] {
  if (min === max) { min -= 1; max += 1 }
  const raw = (max - min) / count
  const mag = 10 ** Math.floor(Math.log10(raw))
  const step = [1, 2, 5, 10].map((m) => m * mag).find((s) => s >= raw)!
  const ticks = []
  for (let t = Math.floor(min / step) * step; t <= max + step * 0.001; t += step) ticks.push(Math.round(t * 1e6) / 1e6)
  if (ticks[ticks.length - 1] < max) ticks.push(ticks[ticks.length - 1] + step)
  return ticks
}

const fmt = (n: number) => n.toLocaleString(undefined, { maximumFractionDigits: 1 })
const monthLabel = (ym: string) => new Date(`${ym}-01T00:00:00`).toLocaleDateString(undefined, { month: 'short', year: 'numeric' })
const monthIndex = (ym: string) => Number(ym.slice(0, 4)) * 12 + Number(ym.slice(5, 7)) - 1

// ------------------------------------------------------------------ line chart

export interface MonthPoint {
  month: string // "2026-11"
  value: number
  games: number
}

/**
 * One series over months (single series: no legend; the card title names it).
 * The x axis is real time, so a gap of several months stays a gap.
 */
export function LineChart({ points, unit = '', label }: { points: MonthPoint[]; unit?: string; label: string }) {
  const [ref, width] = useWidth<HTMLDivElement>()
  const tooltip = useTooltip()
  const [active, setActive] = useState<number | null>(null)
  const height = 200
  const m = { top: 14, right: 56, bottom: 26, left: 44 }

  if (points.length === 0) return <p className="muted small">No data yet.</p>

  const x0 = monthIndex(points[0].month)
  const x1 = Math.max(monthIndex(points[points.length - 1].month), x0 + 1)
  const values = points.map((p) => p.value)
  const ticks = niceTicks(Math.min(...values), Math.max(...values))
  const [yMin, yMax] = [ticks[0], ticks[ticks.length - 1]]
  const innerW = Math.max(10, width - m.left - m.right)
  const innerH = height - m.top - m.bottom
  const x = (p: MonthPoint) => m.left + ((monthIndex(p.month) - x0) / (x1 - x0)) * innerW
  const y = (v: number) => m.top + (1 - (v - yMin) / (yMax - yMin)) * innerH

  const line = points.map((p, i) => `${i === 0 ? 'M' : 'L'}${x(p).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
  const area = `${line} L${x(points[points.length - 1]).toFixed(1)},${y(yMin)} L${x(points[0]).toFixed(1)},${y(yMin)} Z`
  const last = points[points.length - 1]

  // Year labels along the bottom (January of each year in range).
  const years: { label: string; px: number }[] = []
  for (let yr = Math.ceil(x0 / 12); yr * 12 <= x1; yr++) {
    years.push({ label: String(yr), px: m.left + ((yr * 12 - x0) / (x1 - x0)) * innerW })
  }

  function focusPoint(i: number) {
    const p = points[i]
    setActive(i)
    const box = ref.current!.getBoundingClientRect()
    tooltip.show(box.left + x(p), box.top + y(p.value), (
      <>
        <strong>{fmt(p.value)}{unit}</strong>
        <span>{monthLabel(p.month)} · {p.games} game{p.games === 1 ? '' : 's'}</span>
      </>
    ))
  }

  // The crosshair snaps to the nearest month: you aim at a date, not at a 2px line.
  function onPointerMove(e: PointerEvent<SVGSVGElement>) {
    const px = e.clientX - e.currentTarget.getBoundingClientRect().left
    let best = 0
    points.forEach((p, i) => { if (Math.abs(x(p) - px) < Math.abs(x(points[best]) - px)) best = i })
    focusPoint(best)
  }

  function onKeyDown(e: KeyboardEvent<SVGSVGElement>) {
    if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return
    e.preventDefault()
    const i = active ?? points.length - 1
    focusPoint(Math.max(0, Math.min(points.length - 1, i + (e.key === 'ArrowLeft' ? -1 : 1))))
  }

  const clear = () => { setActive(null); tooltip.hide() }

  return (
    <div ref={ref} className="line-chart">
      {width > 0 && (
        <svg width={width} height={height} role="img" tabIndex={0} aria-label={`${label}: ${points.length} months, latest ${fmt(last.value)}${unit}. Use the arrow keys to read each month.`}
             onPointerMove={onPointerMove} onPointerLeave={clear} onBlur={clear}
             onFocus={() => focusPoint(points.length - 1)} onKeyDown={onKeyDown}>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={m.left} x2={m.left + innerW} y1={y(t)} y2={y(t)} className="chart-grid" />
              <text x={m.left - 8} y={y(t)} className="chart-tick" textAnchor="end" dominantBaseline="middle">{fmt(t)}</text>
            </g>
          ))}
          {years.map((yr) => (
            <text key={yr.label} x={yr.px} y={height - 6} className="chart-tick" textAnchor="middle">{yr.label}</text>
          ))}
          <path d={area} className="chart-area" />
          <path d={line} className="chart-line" />
          {active !== null && (
            <line x1={x(points[active])} x2={x(points[active])} y1={m.top} y2={m.top + innerH} className="chart-crosshair" />
          )}
          {/* end dot + end label (the one value labelled directly) */}
          <circle cx={x(last)} cy={y(last.value)} r={4} className="chart-dot" />
          <text x={x(last) + 9} y={y(last.value)} className="chart-end-label" dominantBaseline="middle">{fmt(last.value)}{unit}</text>
          {active !== null && <circle cx={x(points[active])} cy={y(points[active].value)} r={4} className="chart-dot" />}
        </svg>
      )}
      {tooltip.node}
    </div>
  )
}

// ------------------------------------------------------------------ segmented & stacked bars

export interface Part {
  label: string
  value: number
  color: string // a CSS colour, e.g. 'var(--win)'
}

/** Show the tooltip for one block, on hover or keyboard focus. */
function partHandlers(tooltip: ReturnType<typeof useTooltip>, content: ReactNode) {
  const at = (el: Element) => {
    const r = el.getBoundingClientRect()
    tooltip.show(r.left + r.width / 2, r.top, content)
  }
  return {
    tabIndex: 0,
    onPointerEnter: (e: PointerEvent<HTMLElement>) => at(e.currentTarget),
    onPointerLeave: tooltip.hide,
    onFocus: (e: FocusEvent<HTMLElement>) => at(e.currentTarget),
    onBlur: tooltip.hide,
  }
}

/**
 * One whole split into parts (e.g. wins / draws / losses).
 * tall: 28px blocks with the share printed inside when it fits; thin: an 8px bar, no text.
 */
export function SegmentedBar({ parts, tall = true, label }: { parts: Part[]; tall?: boolean; label: string }) {
  const tooltip = useTooltip()
  const total = parts.reduce((s, p) => s + p.value, 0)
  if (total === 0) return null
  return (
    <div className={`segbar ${tall ? 'tall' : 'thin'}`} role="img"
         aria-label={`${label}: ${parts.map((p) => `${p.label} ${p.value}`).join(', ')}`}>
      {parts.filter((p) => p.value > 0).map((p) => {
        const share = (p.value / total) * 100
        return (
          <div key={p.label} className="segbar-part" style={{ flexGrow: p.value, background: p.color }}
               {...partHandlers(tooltip, <><strong>{fmt(p.value)} · {Math.round(share)}%</strong><span>{p.label}</span></>)}>
            {tall && share >= 9 && <span className="segbar-label">{Math.round(share)}%</span>}
          </div>
        )
      })}
      {tooltip.node}
    </div>
  )
}

/** Horizontal stacked bars on one shared scale, total at the tip. */
export function StackedBars({ rows, unit = '' }: { rows: { label: string; parts: Part[] }[]; unit?: string }) {
  const tooltip = useTooltip()
  const max = Math.max(...rows.map((r) => r.parts.reduce((s, p) => s + p.value, 0)), 0.0001)
  return (
    <div className="stacked">
      {rows.map((r) => {
        const total = r.parts.reduce((s, p) => s + p.value, 0)
        return (
          <div key={r.label} className="stacked-row">
            <span className="stacked-label">{r.label}</span>
            <div className="stacked-track">
              <div className="stacked-bar" style={{ width: `${(total / max) * 100}%` }}>
                {r.parts.filter((p) => p.value > 0).map((p) => (
                  <div key={p.label} className="stacked-part" style={{ flexGrow: p.value, background: p.color }}
                       {...partHandlers(tooltip, <><strong>{fmt(p.value)}{unit}</strong><span>{p.label} · {r.label}</span></>)} />
                ))}
              </div>
              <span className="stacked-total">{fmt(total)}{unit}</span>
            </div>
          </div>
        )
      })}
      {tooltip.node}
    </div>
  )
}

export function Legend({ parts }: { parts: { label: string; color: string }[] }) {
  return (
    <div className="legend">
      {parts.map((p) => (
        <span key={p.label} className="legend-item"><span className="legend-swatch" style={{ background: p.color }} />{p.label}</span>
      ))}
    </div>
  )
}
