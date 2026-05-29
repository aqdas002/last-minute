import { Link } from 'react-router-dom'
import { RelativeTime } from './relative-time'
import type { Listing } from '../api/listings'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

export function ListingCard({ l }: { l: Listing }) {
  const firstImage = l.images.length > 0 ? l.images[0] : null
  return (
    <Link
      to={`/l/${l.id}`}
      className="block rounded-lg border border-zinc-200 p-3 hover:bg-zinc-50"
    >
      {firstImage && (
        <img src={firstImage} alt="" className="mb-2 h-32 w-full rounded object-cover" />
      )}
      <h3 className="font-medium">{l.title}</h3>
      <p className="text-sm text-zinc-600">
        {l.city ?? 'Nearby'} · <RelativeTime iso={l.startTime} />
      </p>
      <p className="mt-1 text-sm">
        <span className="font-semibold">{money(l.discountedPriceCents, l.currency)}</span>{' '}
        <span className="text-zinc-500 line-through">{money(l.originalPriceCents, l.currency)}</span>
      </p>
    </Link>
  )
}
