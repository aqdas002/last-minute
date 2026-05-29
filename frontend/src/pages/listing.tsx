import { useParams, Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { listingById } from '../api/listings'
import { createBooking } from '../api/bookings'
import { RelativeTime } from '../components/relative-time'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

export function ListingPage() {
  const { id = '' } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [bookStatus, setBookStatus] = useState<'idle' | 'booking' | 'error'>('idle')
  const [bookError, setBookError] = useState('')

  const { data: l, isPending, isError } = useQuery({
    queryKey: ['listing', id],
    queryFn: () => listingById(id),
    enabled: !!id,
  })

  if (isPending) return <p className="text-zinc-500">Loading…</p>
  if (isError || !l) return <p className="text-zinc-500">Not found.</p>

  const firstImage = l.images.length > 0 ? l.images[0] : null

  const onBook = async () => {
    setBookStatus('booking')
    try {
      const booking = await createBooking(l.id)
      if (booking.checkoutUrlIfAny && booking.checkoutUrlIfAny !== 'existing') {
        window.location.assign(booking.checkoutUrlIfAny)
      } else {
        navigate(`/bookings/${booking.id}/success`)
      }
    } catch (err: unknown) {
      setBookStatus('error')
      const msg = err instanceof Error ? err.message : 'book_failed'
      if (msg.includes('401')) {
        // Not signed in — send them to /signin with return_to=/l/{id}
        navigate(`/signin?return_to=/l/${l.id}`)
        return
      }
      setBookError(msg)
    }
  }

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

      <button
        type="button"
        disabled={bookStatus === 'booking'}
        onClick={onBook}
        className="w-full rounded bg-zinc-900 px-3 py-3 text-sm font-semibold text-white disabled:opacity-50"
      >
        {bookStatus === 'booking'
          ? 'Redirecting to checkout…'
          : `Book for ${money(l.discountedPriceCents, l.currency)}`}
      </button>

      {bookStatus === 'error' && (
        <div className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
          {bookError.includes('SOLD_OUT')
            ? 'Just sold out — check back tomorrow or browse similar deals.'
            : bookError.includes('LISTING_EXPIRED')
              ? 'This deal just expired.'
              : `Could not book: ${bookError}`}
        </div>
      )}

      <div className="rounded border border-zinc-200 bg-zinc-50 p-3 text-sm text-zinc-700">
        <p>
          <strong>All sales final.</strong> Bookings can't be cancelled by you; full refund if
          the provider doesn't honor your booking.
        </p>
      </div>
    </article>
  )
}
