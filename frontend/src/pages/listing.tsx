import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { listingById } from '../api/listings'
import { RelativeTime } from '../components/relative-time'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

export function ListingPage() {
  const { id = '' } = useParams<{ id: string }>()
  const { data: l, isPending, isError } = useQuery({
    queryKey: ['listing', id],
    queryFn: () => listingById(id),
    enabled: !!id,
  })

  if (isPending) return <p className="text-zinc-500">Loading…</p>
  if (isError || !l) return <p className="text-zinc-500">Not found.</p>

  const firstImage = l.images.length > 0 ? l.images[0] : null
  return (
    <article className="space-y-4">
      {firstImage && (
        <img src={firstImage} alt="" className="h-64 w-full rounded-lg object-cover" />
      )}
      <div>
        <div className="flex items-center gap-2">
          <Link
            to={`/c/${l.categorySlug}`}
            className="rounded-full border border-zinc-200 px-2 py-0.5 text-xs text-zinc-700"
          >
            {l.categoryName}
          </Link>
          <span className="text-xs text-zinc-500">{l.providerName}</span>
        </div>
        <h1 className="mt-2 text-2xl font-semibold">{l.title}</h1>
        <p className="mt-1 text-sm text-zinc-600">
          {l.address ?? l.city ?? 'Location TBD'} · <RelativeTime iso={l.startTime} />
        </p>
      </div>

      <p className="text-xl">
        <span className="font-semibold">{money(l.discountedPriceCents, l.currency)}</span>{' '}
        <span className="text-zinc-500 line-through">{money(l.originalPriceCents, l.currency)}</span>
      </p>

      {l.description && (
        <p className="whitespace-pre-line text-sm text-zinc-700">{l.description}</p>
      )}

      <div className="rounded border border-zinc-200 bg-zinc-50 p-3 text-sm text-zinc-700">
        <p>
          <strong>All sales final.</strong> Bookings can't be cancelled by you; full refund if the
          provider doesn't honor your booking.
        </p>
      </div>

      <p className="text-xs text-zinc-500">Booking will be enabled in the next milestone.</p>
    </article>
  )
}
