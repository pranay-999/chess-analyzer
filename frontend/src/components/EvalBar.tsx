/** Vertical bar beside the board: the white part grows as white's winning chance grows. */
export default function EvalBar({ whitePercent, label, flipped }: {
  whitePercent: number
  label: string
  flipped: boolean // black at the bottom -> white's part grows from the top
}) {
  const whiteWinning = whitePercent >= 50
  return (
    <div className={`eval-bar ${flipped ? 'flipped' : ''}`} title="Engine evaluation">
      <div className="eval-white" style={{ height: `${whitePercent}%` }} />
      <span className={`eval-label ${whiteWinning ? 'on-white' : 'on-black'}`}>{label}</span>
    </div>
  )
}
