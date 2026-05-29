import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { myBookings } from '../api/bookings'
import { RefundRequestButton } from '../components/refund-request-button'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

const statusPill: Record<string, string> = {
  pending: 'bg-zinc-100 text-zinc-700',
  confirmed: 'bg-green-100 text-green-800',
  cancelled: 'bg-red-100 text-red-800',
  completed: 'bg-blue-100 text-blue-800',
  no_show: 'bg-amber-100 text-amber-800',
}

export function MyBookingsPage() {
  const { data, isPending, isError } = useQuery({
    queryKey: ['my-bookings'],
    queryFn: () => myBookings(),
  })

  if (isPending) return <p className="text-zinc-500">Loading…</p>
  if (isError) return <p className="text-zinc-500">Couldn't load your bookings.</p>
  if (!data || data.length === 0) {
    return (
      <div className="text-center py-12">
        <p className="text-zinc-500">No bookings yet.</p>
        <Link to="/" className="mt-2 inline-block text-blue-600 underline">
          Browse tonight's deals
        </Link>
      </div>
    )
  }

  return (
    <section>
      <h1 className="mb-4 text-xl font-semibold">Your bookings</h1>
      <ul className="space-y-2">
        {data.map(b => (
          <li
            key={b.id}
            className="space-y-2 rounded border border-zinc-200 p-3"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{b.listingTitle}</p>
                <p className="text-xs text-zinc-500">
                  {money(b.amountPaidCents, b.currency)} ·{' '}
                  {new Date(b.startTime).toLocaleString()}
                </p>
                {b.status === 'confirmed' && b.redemptionCode && (
                  <p className="mt-1 font-mono text-sm">code: {b.redemptionCode}</p>
                )}
              </div>
              <span
                className={`rounded px-2 py-1 text-xs font-medium ${statusPill[b.status] ?? 'bg-zinc-100'}`}
              >
                {b.status}
              </span>
            </div>
            <RefundRequestButton bookingId={b.id} bookingStatus={b.status} />
          </li>
        ))}
      </ul>
    </section>
  )
}
