import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  providerTodayBookings,
  redeemCode,
  type ProviderBooking,
  type RedemptionResult,
} from '../../api/bookings'

function money(cents: number, currency: string): string {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency }).format(cents / 100)
}

function timeOnly(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' })
}

export function ProviderBookingsTodayPage() {
  const qc = useQueryClient()
  const { data, isPending, isError } = useQuery({
    queryKey: ['provider-today'],
    queryFn: providerTodayBookings,
    refetchInterval: 30_000,
  })

  const [code, setCode] = useState('')
  const [feedback, setFeedback] = useState<RedemptionResult | { code: 'ERROR'; message: string } | null>(null)

  const m = useMutation({
    mutationFn: (c: string) => redeemCode(c),
    onSuccess: (r) => {
      setFeedback(r)
      if (r.code === 'OK') {
        setCode('')
        qc.invalidateQueries({ queryKey: ['provider-today'] })
      }
    },
    onError: (e: unknown) => {
      const msg = e instanceof Error ? e.message : 'unknown'
      // Parse out the structured body if available
      if (msg.includes('ALREADY_REDEEMED')) {
        setFeedback({ code: 'ALREADY_REDEEMED', bookingId: null, redeemedAt: null, listingTitle: null })
      } else if (msg.includes('CODE_NOT_VALID') || msg.includes('404')) {
        setFeedback({ code: 'CODE_NOT_VALID', bookingId: null, redeemedAt: null, listingTitle: null })
      } else {
        setFeedback({ code: 'ERROR', message: msg })
      }
    },
  })

  const onRedeem = (e: React.FormEvent) => {
    e.preventDefault()
    if (code.trim().length === 8) m.mutate(code.trim().toUpperCase())
  }

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Today's bookings</h1>
        <p className="text-sm text-zinc-600">Punch in a guest's 8-character code to mark them redeemed.</p>
      </header>

      <form onSubmit={onRedeem} className="flex items-end gap-2 rounded border border-zinc-200 bg-zinc-50 p-3">
        <label className="flex-1 text-sm">
          <span className="block font-medium text-zinc-700">Redemption code</span>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())}
            maxLength={8}
            placeholder="ABCD2345"
            className="mt-1 w-full rounded border border-zinc-300 px-2 py-2 font-mono uppercase tracking-widest"
            autoFocus
          />
        </label>
        <button
          type="submit"
          disabled={code.trim().length !== 8 || m.isPending}
          className="rounded bg-zinc-900 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          {m.isPending ? 'Redeeming…' : 'Redeem'}
        </button>
      </form>

      {feedback && (
        <div
          className={`rounded border px-3 py-2 text-sm ${
            feedback.code === 'OK'
              ? 'border-green-300 bg-green-50 text-green-800'
              : feedback.code === 'ALREADY_REDEEMED'
                ? 'border-amber-300 bg-amber-50 text-amber-900'
                : 'border-red-300 bg-red-50 text-red-800'
          }`}
        >
          {feedback.code === 'OK' && (
            <p>
              ✓ Redeemed{feedback.listingTitle ? ` for "${feedback.listingTitle}"` : ''}
            </p>
          )}
          {feedback.code === 'ALREADY_REDEEMED' && <p>Already redeemed earlier.</p>}
          {feedback.code === 'CODE_NOT_VALID' && (
            <p>That code isn't valid for any booking today.</p>
          )}
          {feedback.code === 'ERROR' && <p>Could not redeem: {feedback.message}</p>}
        </div>
      )}

      <section>
        <h2 className="text-lg font-medium">Confirmed for today</h2>
        {isPending && <p className="text-sm text-zinc-500">Loading…</p>}
        {isError && <p className="text-sm text-red-600">Could not load bookings.</p>}
        {data && data.length === 0 && (
          <p className="mt-2 text-sm text-zinc-500">Nothing booked for today yet.</p>
        )}
        {data && data.length > 0 && (
          <ul className="mt-3 divide-y divide-zinc-200 rounded border border-zinc-200">
            {data.map((b: ProviderBooking) => (
              <li key={b.id} className="flex items-center justify-between px-3 py-3">
                <div>
                  <p className="font-medium">{b.listingTitle}</p>
                  <p className="text-xs text-zinc-600">
                    {timeOnly(b.startTime)} · {b.consumerEmail}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <span className="font-mono text-sm tracking-widest text-zinc-700">
                    {b.redemptionCode}
                  </span>
                  <span className="text-xs text-zinc-500">
                    {money(b.providerPayoutCents, b.currency)} payout
                  </span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
