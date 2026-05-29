import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getBooking, getRefundRequests, type Booking } from '../api/bookings'
import { RefundRequestButton } from '../components/refund-request-button'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

const STATUS_LABEL: Record<Booking['status'], string> = {
  pending: 'Awaiting payment',
  confirmed: 'Confirmed',
  cancelled: 'Cancelled',
  completed: 'Redeemed',
  no_show: 'Marked no-show',
}

const STATUS_STYLE: Record<Booking['status'], string> = {
  pending: 'bg-amber-50 text-amber-800 border-amber-200',
  confirmed: 'bg-green-50 text-green-800 border-green-200',
  cancelled: 'bg-zinc-100 text-zinc-600 border-zinc-200',
  completed: 'bg-blue-50 text-blue-800 border-blue-200',
  no_show: 'bg-red-50 text-red-800 border-red-200',
}

export function BookingDetailPage() {
  const { id = '' } = useParams<{ id: string }>()

  const { data: b, isPending, isError } = useQuery({
    queryKey: ['booking', id],
    queryFn: () => getBooking(id),
    enabled: !!id,
  })

  const { data: refunds } = useQuery({
    queryKey: ['refund-requests', id],
    queryFn: () => getRefundRequests(id),
    enabled: !!id && b?.status !== 'pending',
  })

  if (isPending) return <p className="text-zinc-500">Loading…</p>
  if (isError || !b) return <p className="text-zinc-500">Booking not found.</p>

  const start = new Date(b.startTime)
  const end = new Date(b.listingEndTime)

  return (
    <article className="space-y-6">
      <header className="space-y-2">
        <p className="text-xs text-zinc-500">
          <Link to="/bookings" className="underline">
            ← All bookings
          </Link>
        </p>
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold">{b.listingTitle}</h1>
          <span
            className={`rounded-full border px-2 py-0.5 text-xs ${STATUS_STYLE[b.status]}`}
          >
            {STATUS_LABEL[b.status]}
          </span>
        </div>
        <p className="text-sm text-zinc-600">{b.providerName}</p>
      </header>

      {(b.status === 'confirmed' || b.status === 'completed') && b.redemptionCode && (
        <section className="rounded border border-green-200 bg-green-50 p-4">
          <p className="text-xs font-medium uppercase text-green-800">
            Show this at the door
          </p>
          <p className="mt-1 font-mono text-3xl tracking-widest text-green-900">
            {b.redemptionCode}
          </p>
          {b.redeemedAt && (
            <p className="mt-1 text-xs text-green-700">
              Redeemed {new Date(b.redeemedAt).toLocaleString()}
            </p>
          )}
        </section>
      )}

      <section className="space-y-1">
        <h2 className="text-sm font-semibold uppercase text-zinc-500">When</h2>
        <p>
          {start.toLocaleString()} – {end.toLocaleTimeString()}{' '}
          <span className="text-xs text-zinc-500">({b.listingTimezone})</span>
        </p>
      </section>

      {(b.listingAddress || b.listingCity) && (
        <section className="space-y-1">
          <h2 className="text-sm font-semibold uppercase text-zinc-500">Where</h2>
          <p>{b.listingAddress ?? b.listingCity}</p>
        </section>
      )}

      <section className="space-y-1">
        <h2 className="text-sm font-semibold uppercase text-zinc-500">Payment</h2>
        <p>
          {money(b.amountPaidCents, b.currency)} ·{' '}
          <Link to="/" className="text-xs text-zinc-500 underline">
            All sales final
          </Link>
        </p>
        {b.cancellationReason && (
          <p className="text-xs text-zinc-500">Cancelled: {b.cancellationReason}</p>
        )}
      </section>

      {b.status !== 'pending' && b.status !== 'cancelled' && (
        <section className="space-y-2">
          <h2 className="text-sm font-semibold uppercase text-zinc-500">Problem with this booking?</h2>
          <RefundRequestButton bookingId={b.id} bookingStatus={b.status} />
        </section>
      )}

      {refunds && refunds.length > 0 && (
        <section className="space-y-1">
          <h2 className="text-sm font-semibold uppercase text-zinc-500">Refund history</h2>
          <ul className="text-sm">
            {refunds.map((r) => (
              <li key={r.id} className="text-zinc-700">
                {new Date(r.createdAt).toLocaleDateString()} — {r.reason} — <strong>{r.status}</strong>
                {r.adminNotes && <span className="text-xs text-zinc-500"> · {r.adminNotes}</span>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </article>
  )
}
