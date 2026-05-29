import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getBooking, refreshBookingStatus, type Booking } from '../api/bookings'

const POLL_INTERVAL_MS = 1000
const POLL_MAX = 30 // ~30 s of polling

export function BookingSuccessPage() {
  const { id = '' } = useParams<{ id: string }>()
  const [booking, setBooking] = useState<Booking | null>(null)
  const [polls, setPolls] = useState(0)
  const [stage, setStage] = useState<'polling' | 'confirmed' | 'processing' | 'error'>('polling')
  const [errorMsg, setErrorMsg] = useState('')

  // Poll until confirmed or timeout.
  useEffect(() => {
    let cancelled = false
    if (stage !== 'polling') return
    const t = setTimeout(async () => {
      try {
        const b = await getBooking(id)
        if (cancelled) return
        setBooking(b)
        if (b.status === 'confirmed') {
          setStage('confirmed')
        } else if (b.status === 'cancelled') {
          setStage('error')
          setErrorMsg('booking_cancelled')
        } else if (polls + 1 >= POLL_MAX) {
          setStage('processing')
        } else {
          setPolls(p => p + 1)
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setStage('error')
          setErrorMsg(err instanceof Error ? err.message : 'fetch_failed')
        }
      }
    }, polls === 0 ? 200 : POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearTimeout(t)
    }
  }, [id, polls, stage])

  const onRefresh = async () => {
    try {
      const b = await refreshBookingStatus(id)
      setBooking(b)
      if (b.status === 'confirmed') setStage('confirmed')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'refresh_failed'
      if (msg.includes('429')) setErrorMsg('refresh_too_soon')
      else setErrorMsg(msg)
    }
  }

  return (
    <div className="mx-auto max-w-md py-8">
      <h1 className="text-2xl font-semibold">
        {stage === 'confirmed' ? "You're booked." : 'Confirming your booking…'}
      </h1>

      {booking && (
        <p className="mt-1 text-sm text-zinc-600">
          {booking.listingTitle} ·{' '}
          {new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: booking.currency,
          }).format(booking.amountPaidCents / 100)}
        </p>
      )}

      {stage === 'polling' && (
        <p className="mt-6 text-sm text-zinc-500">Checking with Stripe…</p>
      )}

      {stage === 'confirmed' && booking?.redemptionCode && (
        <section className="mt-6 rounded-lg border border-green-200 bg-green-50 p-4 text-center">
          <p className="text-xs uppercase tracking-wide text-green-700">Redemption code</p>
          <p className="mt-2 text-3xl font-mono font-bold tracking-wider text-green-900">
            {booking.redemptionCode}
          </p>
          <p className="mt-2 text-xs text-green-700">
            Show this code at the venue. We've also emailed it to you.
          </p>
        </section>
      )}

      {stage === 'processing' && (
        <section className="mt-6 rounded-lg border border-amber-200 bg-amber-50 p-4">
          <p className="text-sm text-amber-900">
            <strong>Processing — usually under an hour.</strong> Your payment is with Stripe; we'll
            email your confirmation as soon as it lands.
          </p>
          <button
            type="button"
            onClick={onRefresh}
            className="mt-3 rounded border border-amber-300 px-3 py-1 text-sm"
          >
            Refresh status
          </button>
          {errorMsg && (
            <p className="mt-2 text-xs text-amber-900">
              {errorMsg === 'refresh_too_soon'
                ? "We're rate-limited on Stripe checks; try again in a moment."
                : errorMsg}
            </p>
          )}
        </section>
      )}

      {stage === 'error' && (
        <section className="mt-6 rounded border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          {errorMsg === 'booking_cancelled'
            ? "This booking was cancelled. If you were charged, we'll refund automatically."
            : `Could not load booking: ${errorMsg}`}
        </section>
      )}
    </div>
  )
}
