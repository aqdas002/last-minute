import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { providerAllBookings, type BookingStatus, type ProviderBooking } from '../../api/bookings'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  })
}

const STATUS_LABELS: Record<BookingStatus, string> = {
  pending: 'Pending payment',
  confirmed: 'Confirmed',
  cancelled: 'Cancelled',
  completed: 'Redeemed',
  no_show: 'No show',
}

const STATUS_STYLES: Record<BookingStatus, string> = {
  pending: 'bg-amber-50 text-amber-800 border-amber-200',
  confirmed: 'bg-blue-50 text-blue-800 border-blue-200',
  cancelled: 'bg-zinc-100 text-zinc-700 border-zinc-200',
  completed: 'bg-green-50 text-green-800 border-green-200',
  no_show: 'bg-red-50 text-red-800 border-red-200',
}

export function ProviderBookingsAllPage() {
  const [filter, setFilter] = useState<'all' | BookingStatus>('all')

  const { data, isPending, isError } = useQuery({
    queryKey: ['provider-all'],
    queryFn: providerAllBookings,
  })

  const filtered = useMemo(
    () => (data ? (filter === 'all' ? data : data.filter((b) => b.status === filter)) : []),
    [data, filter],
  )

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">All bookings</h1>
        <p className="text-sm text-zinc-600">Recent history across all your listings.</p>
      </header>

      <div className="flex flex-wrap gap-2">
        {(['all', 'confirmed', 'completed', 'cancelled', 'pending'] as const).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setFilter(s)}
            className={`rounded-full border px-3 py-1 text-xs ${
              filter === s
                ? 'border-zinc-900 bg-zinc-900 text-white'
                : 'border-zinc-300 bg-white text-zinc-700'
            }`}
          >
            {s === 'all' ? 'All' : STATUS_LABELS[s as BookingStatus]}
          </button>
        ))}
      </div>

      {isPending && <p className="text-sm text-zinc-500">Loading…</p>}
      {isError && <p className="text-sm text-red-600">Could not load bookings.</p>}
      {data && filtered.length === 0 && (
        <p className="text-sm text-zinc-500">No bookings match this filter.</p>
      )}

      {filtered.length > 0 && (
        <ul className="divide-y divide-zinc-200 rounded border border-zinc-200">
          {filtered.map((b: ProviderBooking) => (
            <li key={b.id} className="flex items-center justify-between gap-3 px-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium">{b.listingTitle}</p>
                <p className="text-xs text-zinc-600">
                  {formatDateTime(b.startTime)} · {b.consumerEmail}
                </p>
              </div>
              <span
                className={`rounded-full border px-2 py-0.5 text-xs ${STATUS_STYLES[b.status]}`}
              >
                {STATUS_LABELS[b.status]}
              </span>
              <span className="w-20 text-right text-xs text-zinc-600">
                {money(b.providerPayoutCents, b.currency)}
              </span>
            </li>
          ))}
        </ul>
      )}

      <p className="text-xs text-zinc-500">
        <Link to="/provider/dashboard" className="underline">
          Back to dashboard
        </Link>
      </p>
    </div>
  )
}
