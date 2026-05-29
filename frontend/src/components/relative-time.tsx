import { useEffect, useState } from 'react'

function format(diffMs: number): string {
  const minutes = Math.round(diffMs / 60_000)
  if (Math.abs(minutes) < 1) return 'just now'
  if (minutes > 0) {
    if (minutes < 60) return `starts in ${minutes}m`
    const h = Math.round(minutes / 60)
    if (h < 24) return `starts in ${h}h`
    const d = Math.round(h / 24)
    return `starts in ${d}d`
  } else {
    const m = -minutes
    if (m < 60) return `${m}m ago`
    const h = Math.round(m / 60)
    return `${h}h ago`
  }
}

export function RelativeTime({ iso }: { iso: string }) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const i = setInterval(() => setNow(Date.now()), 30_000)
    return () => clearInterval(i)
  }, [])
  return <span>{format(new Date(iso).getTime() - now)}</span>
}
